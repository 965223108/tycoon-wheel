# 部署运维手册

## 一、环境准备

### 系统要求

| 项 | 最低 | 推荐 |
|---|---|---|
| CPU | 4 核 | 8 核+ |
| 内存 | 8 GB | 16 GB |
| 磁盘 | 20 GB | 40 GB |
| Docker | 20.10+ | 最新 |

### 初始化

```bash
# 1. 启动 Swarm（单节点即可）
docker swarm init

# 2. 确认镜像源可达（国内环境）
# 如果拉不到镜像，配置 /etc/docker/daemon.json 加镜像加速
```

> **国内网络注意**：直接拉 Docker Hub 常超时。脚本里对 `registry:2` 做了
> `docker.m.daocloud.io` 兜底，其他基础镜像（mysql/nacos/nginx/ES 等）
> 需要你自行配置镜像加速或提前 `docker pull`。

### 创建 Nacos 的 dubbo 命名空间

启动后访问 http://127.0.0.1:8848/nacos → 命名空间 → 新建：

- 命名空间 ID：`dubbo`
- 命名空间名：`Dubbo-Registry`

> 不建也能跑，但 `public` 里会混入 Dubbo 的接口级注册条目。

## 二、构建

```bash
./deploy/scripts/build.sh
```

做的事：

1. 确保本地镜像仓库 `127.0.0.1:5000` 在跑（没有就拉 `registry:2` 起一个）
2. `mvn clean package` 打包全部模块
3. 把 jar 和 SkyWalking agent 放进构建上下文
4. `docker build` 出 4 个镜像
5. `docker tag` + `docker push` 到本地仓库

**为什么要本地仓库**：Swarm 对"本地构建、没有 registry digest"的镜像支持有问题——
daemon 重启后它会去公网 registry 找同名镜像，找不到就卡在 `Preparing`。
推到本地仓库后这个问题消失。

只构建某个服务：

```bash
./deploy/scripts/build.sh user
```

带 SkyWalking 探针：

```bash
# 先下载 agent 并解压
curl -LO https://archive.apache.org/dist/skywalking/java-agent/9.8.0/apache-skywalking-java-agent-9.8.0.tgz
tar xzf apache-skywalking-java-agent-9.8.0.tgz

./deploy/scripts/build.sh   # 会自动使用解压出来的 skywalking-agent 目录
# 或显式指定
AGENT_DIR=/path/to/skywalking-agent ./deploy/scripts/build.sh
```

不带探针时镜像里没有 agent，`SW_AGENT_ENABLED=false` 即可，功能不受影响（只是没有链路追踪）。

## 三、部署

```bash
./deploy/scripts/install.sh
```

做的事：

1. `rsync deploy/ → /opt/tycoon-wheel/`（容器要挂载里面的配置文件）
2. 等 Nacos 就绪
3. 发布 4 个 data-id 的配置
4. `docker stack deploy --resolve-image never`
5. 等收敛 + 冒烟

首次部署大约需要 **3~5 分钟**（MySQL 初始化 + 各服务启动）。

## 四、日常运维

### 查看状态

```bash
./deploy/scripts/status.sh
```

输出副本状态、未就绪列表、业务容器资源占用、宿主内存。

### 冒烟测试

```bash
./deploy/scripts/smoke.sh
```

9 项断言：玩家接口、网关鉴权、后台登录、概览接口、错误密码拒绝、Nacos 服务列表干净度。

### 查看日志

```bash
docker service logs -f --tail 100 tycoon_app
docker service logs -f --tail 100 tycoon_gateway
```

日志格式带 traceId：

```
2026-10-25 13:41:47.967 INFO  [TID:564d...30033] [http-nio-...-exec-5] c.t.s.WheelService - ...
```

### 滚动更新某个服务

```bash
# 改完代码后
./deploy/scripts/build.sh user
docker service update --detach --force tycoon_user
```

### 扩容

```bash
docker service scale tycoon_app=4
docker service scale tycoon_user=3
```

> `tycoon-user` 扩容前记得确认内存够用（每个副本 560 MB）。

### 缩容以省内存

```bash
docker service scale tycoon_kibana=0        # 省 560 MB
docker service scale tycoon_skywalking-ui=0 # 省 320 MB
```

### 停止 / 卸载

```bash
./deploy/scripts/stop.sh           # 停服务，保留数据
./deploy/scripts/stop.sh --purge   # 连数据卷一起删
```

## 五、常见问题

### 服务一直 `0/N`，任务 `Preparing`

Swarm 试图从公网 registry 拉同名镜像失败。

```bash
# 确认本地仓库有镜像
curl http://127.0.0.1:5000/v2/_catalog

# 重新部署并禁止公网解析
docker stack deploy --resolve-image never -c /opt/tycoon-wheel/swarm/docker-stack.yml tycoon
```

### 服务 `1/2` 卡住不动

滚动更新留下的僵尸任务：

```bash
docker service update --detach --replicas 0 tycoon_gateway
sleep 10
docker service update --detach --replicas 2 tycoon_gateway
```

### 容器频繁重启，`ExitCode=137`

被 OOM Killer 杀了。看内存：

```bash
docker stats --no-stream
free -m
```

JVM 参数里的 `-XX:MaxRAMPercentage=65` 是相对**容器限额**算的，
如果限额太小（比如 512M），堆只有 333M，加上 agent 和元空间很容易爆。

### `USER_SERVICE_UNAVAILABLE`

wheel 调不到 user-service。看 wheel 日志：

```bash
docker service logs --tail 200 tycoon_app | grep -i "No provider"
```

如果有 `No provider available after connectivity filter`，说明 user-service
要么挂了、要么 GC 停顿太久被 Dubbo 客户端摘除。检查 user 容器内存占用。

### 日志里 `[TID:Ignored_Trace]`

那一刻探针连不上 OAP，不是代码问题。等 OAP 恢复即可。

### 中文显示成 `????`

MySQL 客户端字符集问题，不是数据损坏：

```bash
docker exec -i $(docker ps -q --filter name=tycoon_mysql) \
  mysql -utycoon -ptycoon123 --default-character-set=utf8mb4 tycoon -e "SELECT * FROM spin_records LIMIT 5;"
```

## 六、配置修改

### 改游戏规则（概率、抽奖成本、限流）

Nacos 控制台 → 配置管理 → `tycoon-wheel.yaml`：

```yaml
game:
  cost-per-spin: 10          # 每次抽奖消耗积分
  max-spins-per-minute: 30   # 每用户每分钟上限
```

发布后 wheel 会在 10 秒内生效（`WheelCatalog` 缓存过期）。

### 改路由

`tycoon-gateway.yaml`：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: admin
          uri: lb://tycoon-admin
          predicates:
            - Path=/api/admin/**,/admin/**
```

### 改管理员密码

目前没有 UI，直接改库（密码是 PBKDF2 哈希）：

```bash
# 最简单：删掉重建，重启 admin 服务会自动创建 admin/admin123
docker exec -i $(docker ps -q --filter name=tycoon_mysql) \
  mysql -utycoon -ptycoon123 tycoon -e "DELETE FROM admin_users WHERE username='admin';"
docker service update --force tycoon_admin
```

### 改 JWT 密钥

`tycoon-admin.yaml` 和 `tycoon-gateway.yaml` 里的 `JWT_SECRET` 必须**一致**。
改完要同时重启 gateway 和 admin，所有人需要重新登录。

## 七、备份

```bash
# 数据库
docker exec $(docker ps -q --filter name=tycoon_mysql) \
  mysqldump -utycoon -ptycoon123 --single-transaction tycoon > backup-$(date +%F).sql

# 恢复
docker exec -i $(docker ps -q --filter name=tycoon_mysql) \
  mysql -utycoon -ptycoon123 tycoon < backup-2026-10-25.sql
```

## 八、监控告警（待完善）

当前只有 Grafana 大盘，没有告警规则。建议补充：

- 服务副本数 < 期望值 → 告警
- 容器内存 > 90% → 告警
- 接口错误率 > 1% → 告警
- P95 延迟 > 1s → 告警
