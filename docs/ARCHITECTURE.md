# 架构设计

## 一、整体拓扑

```
┌──────────────────────────────────────────────────────────────────┐
│                           客户端                                  │
│  浏览器 / App                                                     │
└───────────────────────────┬──────────────────────────────────────┘
                            │ HTTP :8090
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│  nginx  (1 副本)                                                  │
│  · 静态资源直接返回 /admin/index.html                              │
│  · /api/** 反代到 gateway                                         │
└───────────────────────────┬──────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│  tycoon-gateway  (2 副本)  Spring Cloud Gateway + Spring Security │
│  · 路由：/api/admin/**, /admin/**  → lb://tycoon-admin            │
│          /**                      → lb://tycoon-wheel            │
│  · JwtAuthWebFilter：验签、解析 role、塞入 SecurityContext         │
│  · 只有 /api/admin/** 需要 ROLE_ADMIN，玩家端接口公开              │
└───────────────┬──────────────────────────────┬───────────────────┘
                │                              │
                ▼                              ▼
┌───────────────────────────────┐  ┌──────────────────────────────┐
│  tycoon-wheel  (2 副本)        │  │  tycoon-admin  (1 副本)       │
│  · /api/login /register       │  │  · /api/admin/auth/login      │
│  · /api/spin  抽奖            │  │  · /api/admin/users/**        │
│  · /api/config 转盘配置        │  │  · /api/admin/segments/**     │
│  · /api/me  /api/stats        │  │  · /api/admin/rewards/**      │
│  · WheelCatalog 10s 缓存       │  │  · 静态后台页面                │
└───────────────┬───────────────┘  └──────────────┬───────────────┘
                │      Dubbo RPC（接口见 tycoon-api）│
                └────────────────┬─────────────────┘
                                 ▼
                ┌──────────────────────────────────┐
                │  tycoon-user  (1 副本)            │
                │  UserDubboService                │
                │  GameConfigDubboService（admin 提供）│
                │  · authenticate / register       │
                │  · addPoints / deductPoints      │
                │  · blacklist / unblacklist       │
                │  · grantReward                   │
                └──────────────────┬───────────────┘
                                   │ JDBC
                                   ▼
                         ┌──────────────────┐
                         │     MySQL 8      │
                         └──────────────────┘
```

## 二、服务边界

### tycoon-wheel（玩家端）

面向 C 端，QPS 最高。只负责"游戏逻辑"，用户数据全部委托给 `tycoon-user`。

- `AuthService` → 调 `UserDubboService.authenticate/register`
- `WheelService` → 概率计算 + 调 `UserDubboService.deductPoints/grantReward`
- `WheelCatalog` → 通过 Dubbo 从 admin 拉商品配置，**10 秒本地缓存**，拉不到时用内置默认值兜底

### tycoon-user（用户域）

被两个上游共用，避免重复实现。所有写操作在这里做事务。

- 账号、积分、奖励、黑名单
- 密码哈希：PBKDF2-HMAC-SHA256，20 万次迭代
- 抽奖记录表在 wheel 侧（`spin_records`），奖励发放记录在 user 侧（`user_rewards`）

### tycoon-admin（管理域）

面向内部，安全边界独立。

- `AdminAuthFilter` 做 JWT 校验（网关校验之外的第二道）
- `GameConfigServiceImpl` 实现 `GameConfigDubboService`，给 wheel 提供商品配置
- `admin_users` / `game_segments` 两张表由它管理

### tycoon-api（契约层）

只有接口和 DTO，无实现、无 Spring 依赖，被其他模块 `compile` 依赖。

- `UserDubboService` / `GameConfigDubboService`
- `UserDto` / `UserRewardDto` / `GameSegmentDto` / `PageResult<T>`
- `RpcBusinessException`（跨服务的业务异常载体）

## 三、关键设计决策

### 1. 会话存在 MySQL，不存内存

`Cookie: TYCOON_SID=<sid>` → `sessions` 表。

**好处**：任意一个 wheel 实例都能校验任意请求，网关轮询即可，**不需要 session 粘滞**，可以随意加副本。

### 2. Dubbo 用应用级注册

```yaml
dubbo:
  application:
    register-mode: application
```

Nacos `public` 命名空间里只显示 4 个应用名，而不是一堆 `providers:com.tycoon.api.xxx::`。

> **实测**：Dubbo 3.3 + Nacos 2.4.3 下 `register-mode: application` 仍会同时写接口级条目，
> 所以额外把 Dubbo 注册中心指向独立的 `dubbo` 命名空间做隔离。

### 3. 配置全部外置到 Nacos

本地只留 12 行引导配置。改路由、改概率、改密钥都不用重新打包。

### 4. 抽奖限流

```yaml
game:
  max-spins-per-minute: 30   # 每用户每分钟最多 30 次
```

实现是内存里的滑动窗口（`WheelService.rateLimit`）。单机有效，多副本时每副本独立计数。

## 四、数据模型

| 表 | 归属服务 | 说明 |
|---|---|---|
| `users` | wheel | 账号、积分、抽奖次数 |
| `spin_records` | wheel | 每次抽奖的记录（奖品、积分变化前后） |
| `sessions` | wheel | 登录会话（sid → user_id） |
| `user_profile` | user | 昵称、等级等扩展资料 |
| `user_rewards` | user | 奖品发放记录（ISSUED / PENDING / CANCELLED） |
| `admin_users` | admin | 管理员账号 |
| `game_segments` | admin | 转盘商品配置（名称、概率、积分、启用状态） |

完整 DDL 见 [deploy/database/schema.sql](../deploy/database/schema.sql)。

**概率归一化**：后台允许配置的概率合计 ≠ 100%，玩家端读取时会自动归一化。
后台概览页会把 ≠100% 的数值标红提示。

## 五、请求链路示例

一次抽奖 `POST /api/spin`：

```
1. nginx :8090 收到请求，反代到 tycoon_gateway:8080
2. gateway 路由到 lb://tycoon-wheel（轮询 2 个实例之一）
3. wheel 从 Cookie 取 TYCOON_SID → 查 sessions 表拿到 userId
4. wheel 调 rateLimit() 检查每分钟次数
5. wheel 调 UserDubboService.deductPoints(userId, 10)
   └─ Dubbo → tycoon-user:20881 → UPDATE users SET points = points - 10
6. wheel 按 game_segments 的概率权重抽一个结果
7. 若中实物奖品 → 调 UserDubboService.grantReward()  → 写 user_rewards
   若中积分     → 调 UserDubboService.addPoints()
8. wheel 写 spin_records
9. 返回结果 JSON

整个链路共用一个 traceId，在 SkyWalking 里能看到完整 26 个 span，
在日志里能用同一个 traceId 检索到 3 个服务的日志。
```

## 六、可观测性设计

| 维度 | 采集方式 |
|---|---|
| 指标 | 各服务暴露 `/actuator/prometheus`，Prometheus 每 15s 抓取 |
| 链路 | SkyWalking Java Agent（`-javaagent`），gRPC 上报到 OAP:11800 |
| 日志 | 容器 stdout → Filebeat（`/var/lib/docker/containers/*/*.log`）→ ES |

**traceID 打通**：logback 配置 `TraceIdPatternLogbackLayout` 输出 `%tid`，
Filebeat 用 grok 把 traceId 抽成独立字段（`message` 是 text，直接搜会被分词器切碎）。

## 七、容量与扩展

当前单节点 14 个容器的资源分配：

| 服务 | 副本 | 内存限额 | 说明 |
|---|---|---|---|
| tycoon-wheel | 2 | 512 MB | 主要压力在这里 |
| tycoon-gateway | 2 | 512 MB | WebFlux，内存需求小但 CPU 高 |
| tycoon-user | 1 | 560 MB | **瓶颈**，所有用户操作都过它 |
| tycoon-admin | 1 | 560 MB | 后台流量小 |
| MySQL | 1 | 512 MB | |
| Elasticsearch | 1 | 1 GB | |
| SkyWalking OAP | 1 | 1.5 GB | |
| Kibana | 1 | 560 MB | 不常用可缩容到 0 |

**扩容优先级**：`tycoon-user` 加到 2~3 副本 > `tycoon-wheel` 加到 4 副本 > 拆 MySQL 读写分离。

**内存是硬约束**：7.6 GB 跑 14 个容器已经很勉强，
再扩副本建议先把 ES/Kibana/SkyWalking 挪到另一台机器。
