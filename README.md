# 富豪大转盘 · Tycoon Wheel

> 一个用 **Spring Boot 3 + Spring Cloud Alibaba** 搭建的微服务抽奖系统，
> 包含玩家端转盘、管理后台，以及完整的监控 / 链路追踪 / 日志采集。

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.3-blue)](https://spring.io/projects/spring-cloud)
[![Dubbo](https://img.shields.io/badge/Dubbo-3.3.0-red)](https://dubbo.apache.org/)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

---

## 一、这是什么

一个"大转盘"抽奖游戏：用户登录后有积分，花积分抽奖，中奖概率由后台配置。

**它不是一个玩具 demo**，而是一套完整可运行的微服务工程，包含：

- 5 个 Maven 模块（1 个共享 API + 4 个服务）
- 服务注册发现（Nacos）+ 配置中心（Nacos）
- 服务间调用（Dubbo）
- 统一网关 + JWT 鉴权（Spring Cloud Gateway + Spring Security）
- 全套可观测性（Prometheus + Grafana + SkyWalking + ELK）
- Docker Swarm 编排 + 一键部署脚本

---

## 二、架构

```
                       浏览器
                         │
                         ▼
                ┌─────────────────┐
                │      nginx      │  :8090
                └────────┬────────┘
                         │
                         ▼
                ┌─────────────────┐
                │ tycoon-gateway  │ ×2  Spring Cloud Gateway + Spring Security
                │   JWT 鉴权       │      /api/admin/** 需要 ROLE_ADMIN
                └────────┬────────┘
                         │ lb:// 负载均衡
           ┌─────────────┴─────────────┐
           ▼                           ▼
   ┌───────────────┐          ┌───────────────┐
   │ tycoon-wheel  │ ×2       │ tycoon-admin  │ ×1
   │  玩家端 API    │          │  管理后台 API  │
   │  :8787        │          │  :8789        │
   └───────┬───────┘          └───────┬───────┘
           │        Dubbo RPC         │
           └────────────┬─────────────┘
                        ▼
                ┌───────────────┐
                │ tycoon-user   │ ×1
                │  用户/积分/奖励 │  :8788 / Dubbo :20881
                └───────┬───────┘
                        │ JDBC
                        ▼
                ┌───────────────┐
                │    MySQL 8    │
                └───────────────┘

   注册中心/配置中心: Nacos :8848
   可观测性: Prometheus :9090 · Grafana :3000 · SkyWalking :8082 · Kibana :5601
```

### 为什么这样拆

| 服务 | 职责 | 拆分理由 |
|---|---|---|
| `tycoon-wheel` | 抽奖、概率计算、游戏配置读取 | 面向 C 端，流量最大，需要独立扩容 |
| `tycoon-user` | 账号、积分、奖励、黑名单 | 被 wheel 和 admin 共用，抽出来避免重复实现 |
| `tycoon-admin` | 后台管理、商品配置、管理员账号 | 面向内部，安全要求不同，独立部署 |
| `tycoon-gateway` | 路由、鉴权、限流入口 | 统一出口，鉴权逻辑收敛到一处 |
| `tycoon-api` | Dubbo 接口与 DTO | 服务间契约，避免重复定义 |

---

## 三、技术栈

| 类别 | 选型 |
|---|---|
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 3.3.5 |
| 微服务 | Spring Cloud 2023.0.3 / Spring Cloud Alibaba 2023.0.3.2 |
| RPC | **Dubbo 3.3.0**（Nacos 注册中心，应用级注册） |
| 网关 | Spring Cloud Gateway（WebFlux） |
| 鉴权 | Spring Security + JWT（jjwt 0.12.6，HS384） |
| 持久层 | MyBatis 3.0.3 + HikariCP |
| 数据库 | MySQL 8.4 |
| 注册/配置 | Nacos 2.4.3 |
| 容器编排 | Docker Swarm |
| 监控 | Prometheus 2.55 + Grafana 11.3 |
| 链路追踪 | SkyWalking 10.1（ES 存储） |
| 日志 | Filebeat 8.15 → Elasticsearch 8.15 → Kibana 8.15 |

---

## 四、快速开始

### 环境要求

- Linux / WSL2（Docker Engine 20.10+，已初始化 Swarm）
- JDK 21 + Maven 3.9+
- 内存建议 **≥ 8 GB**（全套 14 个容器，含 ES/Kibana/SkyWalking）
- 磁盘 ≥ 20 GB

### 三步启动

```bash
# 1. 初始化 Swarm（单节点即可）
docker swarm init

# 2. 构建镜像（自动拉起本地镜像仓库 127.0.0.1:5000）
./deploy/scripts/build.sh

# 3. 部署
./deploy/scripts/install.sh
```

访问：

| 入口 | 地址 | 账号 |
|---|---|---|
| 玩家端 | http://127.0.0.1:8090 | 首次启动无账号，先注册 |
| 管理后台 | http://127.0.0.1:8090/admin/index.html | `admin` / `admin123` |
| Nacos | http://127.0.0.1:8848/nacos | - |
| Grafana | http://127.0.0.1:3000 | `admin` / `admin123` |
| SkyWalking | http://127.0.0.1:8082 | - |
| Kibana | http://127.0.0.1:5601 | - |

验证：

```bash
./deploy/scripts/status.sh   # 看副本状态
./deploy/scripts/smoke.sh    # 接口冒烟（会跑 9 项断言）
```

### 只用 Docker Compose（开发模式）

如果不需要 Swarm 的多副本和滚动更新：

```bash
cd deploy/compose
docker compose up -d
```

---

## 五、模块说明

```
tycoon-wheel/
├── pom.xml                  # Maven 聚合工程（parent）
├── tycoon-api/              # 共享 Dubbo 接口 + DTO（被其他模块依赖）
├── tycoon-user/             # 用户服务      :8788 / Dubbo :20881
├── tycoon-admin/            # 管理后台服务  :8789 / Dubbo :20882
├── tycoon-wheel-java/       # 玩家端服务    :8787
├── tycoon-gateway/          # 网关          :8080
├── deploy/
│   ├── swarm/               # Swarm 编排
│   ├── compose/             # Compose 编排（开发用）
│   ├── docker/              # Dockerfile ×4 + 启动脚本
│   ├── config/              # Nacos / nginx / prometheus / filebeat / grafana 配置
│   ├── database/schema.sql  # 建库建表（7 张表）
│   └── scripts/             # build / install / status / smoke / stop
├── tools/loadtest/          # 压测脚本
└── docs/                    # 架构、部署、压测文档
```

### 编译

```bash
mvn clean package              # 全部模块，约 20 秒
mvn -pl tycoon-user -am package # 只编某个模块（含依赖）
```

构建顺序由 Maven reactor 自动处理：`api → user / admin / wheel / gateway`。

---

## 六、配置管理

**所有业务配置都在 Nacos**，本地 `application.yml` 只保留引导信息：

```yaml
spring:
  application:
    name: tycoon-user
  config:
    import:
      - optional:nacos:tycoon-user.yaml?group=DEFAULT_GROUP&refreshEnabled=true
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:127.0.0.1:8848}
```

| data-id | 内容 |
|---|---|
| `tycoon-user.yaml` | 数据源、Dubbo、MyBatis |
| `tycoon-admin.yaml` | 数据源、Dubbo、JWT 密钥、初始管理员 |
| `tycoon-wheel.yaml` | 数据源、Dubbo、**游戏规则**（抽奖成本、限流） |
| `tycoon-gateway.yaml` | **路由规则**、JWT 密钥 |

改配置不用重新打包，改 Nacos 即可（支持热更新）。

### Nacos 命名空间划分

| 命名空间 | 内容 |
|---|---|
| `public` | 只放应用名：`tycoon-wheel` / `tycoon-user` / `tycoon-admin` / `tycoon-gateway` |
| `dubbo`（需手动创建） | Dubbo 接口级注册（`providers:com.tycoon.api.*`） |

> 这样 Spring Cloud 的服务列表不会被 Dubbo 的接口名污染。
> 创建命名空间：Nacos 控制台 → 命名空间 → 新建，ID 填 `dubbo`。

---

## 七、可观测性

| 能力 | 实现 | 入口 |
|---|---|---|
| 指标 | Prometheus 抓取 `/actuator/prometheus` | :9090 |
| 大盘 | Grafana（uid `tycoon-overview`） | :3000 |
| 链路 | SkyWalking Agent → OAP → ES | :8082 |
| 日志 | Filebeat → ES → Kibana | :5601 |

### traceID 串联链路和日志

日志格式里带 SkyWalking 注入的 `%tid`：

```
2026-10-25 13:41:47.967 INFO  [TID:564d496149d54de39a48d80728f695e1.245.17929069079330033] [DubboServerHandler-...] UserServiceImpl - 拉黑用户 userId=2
```

排查问题时的用法：

1. 从日志拿到 `TID` 的 32 位前缀 → `564d496149d54de39a48d80728f695e1`
2. SkyWalking → `服务 → Trace` 标签页（`/General-Service/Services/tab/2`）→「追踪ID」粘贴 → 看到完整跨服务调用链
3. 反过来也行：Kibana 里用 KQL 通配符 `*traceId*` 找到对应日志

> **注意**：`queryTrace` API 需要**完整的 traceId**（含 `.段ID.spanID` 后缀），只传 32 位基 ID 会返回空。

---

## 八、压测数据

在这台机器（12 核 / 7.6 GB）上的实测结果，详见 [docs/LOAD-TEST.md](docs/LOAD-TEST.md)：

| 场景 | 峰值 QPS | 稳定区间 |
|---|---|---|
| 读接口（配置/统计/个人信息） | 352 | 200 ~ 250 |
| 登录（PBKDF2 20 万次） | ~18 | 并发 >10 即雪崩 |
| 抽奖（写路径） | ~57 | 受每用户 30 次/分钟限流 |

**已知瓶颈**：单副本 `tycoon-user` + 容器内存紧张（JVM 到 97% 水位）→
GC 停顿导致 Dubbo 摘除提供者 → `USER_SERVICE_UNAVAILABLE`。
优化方向见文档第六节。

---

## 九、文档

- [架构设计](docs/ARCHITECTURE.md)
- [部署运维](docs/DEPLOY.md)
- [压测报告](docs/LOAD-TEST.md)
- [界面截图与效果](docs/screenshots/README.md)

---

## 十、已知限制

1. **Nacos / nginx 单点**：生产建议 Nacos 3 节点集群 + 双 nginx
2. **未配 HTTPS**：Cookie 没加 `Secure`
3. **无分布式事务**：积分扣减和抽奖记录在两个服务，异常时可能不一致
4. **管理员密码**：默认 `admin/admin123`，无修改入口（直接改库）
5. **JWT 密钥**：`tycoon-admin.yaml` 里是占位值，生产必须替换

---

## 十一、License

[Apache License 2.0](LICENSE)
