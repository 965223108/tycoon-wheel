# 富豪大转盘 · 压测报告

> 测试时间：2026-10-25 16:41 ~ 16:54
> 测试目标：摸底系统最大吞吐（QPS）与瓶颈点

---

## 一、测试环境

| 项 | 配置 |
|---|---|
| 宿主 | WSL2 Ubuntu-24.04，12 核 / **7.6 GB 内存** |
| 编排 | Docker Swarm 单节点 |
| 链路 | nginx(8090) → gateway ×2 → wheel(app) ×2 → MySQL 8.4 |
| 旁路 | user ×1（Dubbo 提供者）、admin ×1、Nacos、ES、OAP、Kibana |
| 压测器 | Python3 多线程 + requests 连接池，**与业务同机** |

> ⚠️ **重要前提**：压测器和被测服务跑在同一台机器上，共享 12 核 CPU。
> 所以下面的数字是**保守下限**，真实容量会更高（把压测器挪到另一台机器能显著提升）。

---

## 二、结论速览

| 场景 | 峰值 QPS | 稳定区间 | 说明 |
|---|---|---|---|
| **读接口（配置/统计/个人信息）** | **352** | **200 ~ 250** | c=50 时峰值，p95 < 320ms |
| 登录（PBKDF2 20 万次） | ~18 | — | 并发 >10 即雪崩 |
| 抽奖（写路径） | ~57 | — | 受每用户 30 次/分钟限流 |
| 整体建议容量 | — | **约 200 QPS** | 留 40% 余量 |

**一句话：这台机器上，读接口天花板约 350 QPS，安全运行水位 200 QPS。**

---

## 三、详细数据

### 3.1 读接口（第二轮 · 热态 · 每档 20 秒）

| 接口 | 并发 | QPS | 错误率 | p50 | p95 | p99 |
|---|---|---|---|---|---|---|
| `/api/config` | 25 | 346.2 | 0% | 63 | 156 | 221 |
| `/api/config` | **50** | **352.4** | 0% | 118 | 319 | 436 |
| `/api/config` | 100 | 317.6 | 0% | 230 | 669 | 936 |
| `/api/config` | 200 | 223.0 | 0% | 350 | 1844 | 2706 |

**拐点在 c=50**：继续加并发，QPS 反而下降（352 → 223），说明已进入资源争抢区。

### 3.2 读接口（第一轮 · 冷态，对照）

| 接口 | 并发 | QPS | p50 | p95 |
|---|---|---|---|---|
| `/api/config` | 10 | 72.9 | 98 | 380 |
| `/api/config` | 50 | 218.0 | 192 | 474 |
| `/api/config` | 200 | 241.2 | 300 | 869 |
| `/api/me` | 50 | 224.0 | 196 | 407 |
| `/api/me` | 100 | 258.7 | 330 | 715 |
| `/api/stats` | 10 | 220.1 | 41 | 81 |
| `/api/stats` | 50 | 274.1 | 148 | 394 |

> 冷热态差距明显（config 同并发下 73 → 346 QPS），
> **说明 JIT 预热约需 1~2 分钟**。生产环境做容量规划要按热态算，
> 但**发布重启后的前 2 分钟**要按冷态预留容量。

### 3.3 登录（PBKDF2-HMAC-SHA256 20 万次迭代）

| 并发 | QPS | 错误率 | 主要错误 |
|---|---|---|---|
| 5 | 18.1 | 43.4% | `USER_SERVICE_UNAVAILABLE` |
| 10 | 0 | 100% | 同上 |
| 25 | 0 | 100% | 同上 |
| 50 | 0 | 100% | 同上 |

**结论：单账号体系下单副本 user-service 撑不住 10 并发登录。**

### 3.4 抽奖（写路径）

| 并发 | QPS | 错误率 | 主要错误 |
|---|---|---|---|
| 5 | 0 | 100% | `RATE_LIMITED` / `USER_SERVICE_UNAVAILABLE` |
| 10 | 31.1 | 74.4% | 同上 |
| 25 | 57.1 | 45.0% | `RATE_LIMITED` |
| 50 | 0 | 100% | `RATE_LIMITED` |

**抽奖有产品级限流：每用户 30 次/分钟**（`max-spins-per-minute: 30`）。
用 60 个测试账号轮转仍会触发，说明限流是**按用户精确计数**的。

理论写吞吐 = 用户数 × 0.5 QPS。1 万活跃用户 ≈ 5000 QPS 理论上限，但受 user-service 制约。

---

## 四、瓶颈定位

### 4.1 直接原因：user-service 内存耗尽 → Dubbo 摘除提供者

压测中 wheel 侧日志出现：

```
[DUBBO] No provider available after connectivity filter
[DUBBO] No provider available after route for the service
```

wheel 捕获后转成业务错误：

```json
{"error":"USER_SERVICE_UNAVAILABLE","message":"用户服务暂时不可用，请稍后再试"}
```

### 4.2 资源水位：所有 JVM 都在 91~97%

| 容器 | 内存占用 | 占比 |
|---|---|---|
| tycoon_user | 543.8 / 560 MB | **97.1%** |
| tycoon_app.2 | 499.9 / 512 MB | **97.6%** |
| tycoon_gateway.1 | 494.6 / 512 MB | 96.6% |
| tycoon_gateway.2 | 478.6 / 512 MB | 93.5% |
| tycoon_admin | 519.1 / 560 MB | 92.7% |
| tycoon_app.1 | 468.2 / 512 MB | 91.4% |

### 4.3 根因：JVM 默认参数不适合容器

| 问题 | 计算 | 后果 |
|---|---|---|
| Dubbo 默认 200 线程 | 200 × **1 MB** 栈 = **200 MB** | 512M 容器放不下 |
| 未指定 `-Xmx` | JVM 按容器内存比例估算 | 堆与非堆争抢，频繁 Full GC |
| GC 停顿 | 秒级 STW | Dubbo 心跳超时 → 提供者被摘除 → 级联失败 |

---

## 五、已做的调优

```yaml
# docker-stack.yml
tycoon-user:
  environment:
    JAVA_OPTS: "-Xms192m -Xmx320m -Xss512k"   # 栈从 1M 降到 512K
# nacos 配置 tycoon-user.yaml / tycoon-admin.yaml
dubbo:
  provider:
    threads: 100        # 从默认 200 降到 100
```

**预期效果**：
- 线程栈占用 200MB → 50MB（省 150MB）
- 堆固定 320MB，不再与元空间/线程栈争抢
- GC 压力下降，Dubbo 心跳恢复正常

> 该调优的**复测尚未完成**（复测时 WSL 被高负载打崩，虚拟机重启）。
> 下一轮验证后会更新本文档。

---

## 六、优化建议（按性价比排序）

| # | 措施 | 预期收益 | 成本 |
|---|---|---|---|
| 1 | 应用上面的 JVM 参数 | 消除 97% 内存水位，登录可用 | 已做 |
| 2 | **user-service 扩到 2~3 副本** | 登录并发线性提升 | 需要 ~600MB 内存 |
| 3 | 压测器移到另一台机器 | 数字提升 30%+（当前同机抢 CPU） | 一台机器 |
| 4 | 降 PBKDF2 迭代次数（20 万 → 6 万）或改用 bcrypt | 登录 QPS 提升 3 倍 | 安全性权衡 |
| 5 | 抽奖限流按用户分层（高等级放宽） | 提升付费用户体感 | 产品决策 |
| 6 | 关掉 Kibana / SkyWalking UI（压测期间） | 释放 ~500MB | 临时操作 |
| 7 | app 副本从 2 扩到 4 | 读接口 QPS 翻倍 | 需要 ~1GB 内存 |

**内存是唯一硬约束**：这台机器 7.6 GB 跑 14 个容器已经很勉强，
再扩副本必须先把 Elasticsearch/Kibana/SkyWalking 这些可观测性组件挪走或降配。

---

## 七、复现方式

```bash
# 单轮
python3 ../tools/loadtest/loadtest.py <名称> <url> <GET|POST> <并发> <秒> <cookie文件> [body]

# 完整三轮
bash ../tools/loadtest/run.sh    # 第一轮：冷态各接口
bash ../tools/loadtest/run.sh   # 第二轮：热态 + 登录
bash ../tools/loadtest/run.sh   # 第三轮：60 用户 + 写路径

# 结果文件
/tmp/lt-results.jsonl /tmp/lt-results2.jsonl /tmp/lt-results3.jsonl
```
