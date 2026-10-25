# 富豪大转盘 · 后端版

把原本纯前端的转盘游戏改成 **前后端分离**：账号、积分、抽奖结果、中奖记录全部由服务端负责。
前端只做两件事——**展示** 和 **把轮盘转到服务端指定的那一格**。

---

## 一、技术栈

| 层 | 选择 |
|---|---|
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 3.3.5 |
| Web | Spring MVC（内嵌 Tomcat 10.1） |
| 持久化 | Spring Data JPA + Hibernate 6.5 |
| 数据库 | H2 文件数据库（`./data/tycoon.mv.db`） |
| 构建 | Maven 3.8+ |
| 前端 | 原生 HTML/CSS/JS（无框架、无构建），由 Spring Boot 直接托管 |

**零外部服务依赖**：不用另外装 MySQL/Redis，`java -jar` 一条命令就能跑。

---

## 二、为什么必须上后端

纯前端版本有个致命问题：

> 积分存在浏览器的 `localStorage` 里，用户按 F12 改一行数字就能有无限积分；
> 抽奖概率写在 JS 里，改一下 `SEGMENTS` 就能把「手机」的概率改成 100%。

改成后端后：

- **抽奖结果只在服务端产生**（`WheelCatalog.pick()` 用 `SecureRandom` 按权重抽取）
- 前端拿到的只是 `segmentIndex`，它的唯一作用是**把轮盘转到那个位置**
- 扣分和发奖在同一个数据库事务里完成
- 前端改任何代码都改不了概率，也跳不过扣分
- 密码用 **PBKDF2-HMAC-SHA256（20 万次迭代 + 每账号随机盐）** 存储，不是明文、不是可逆加密

---

## 三、目录结构

```
tycoon-wheel-java/
├── pom.xml
├── run.bat                             # 一键打包 + 启动（Windows）
├── README.md
├── data/                               # 运行时自动创建：H2 数据库文件
│   └── tycoon.mv.db
└── src/main/
    ├── java/com/tycoon/
    │   ├── TycoonApplication.java       # 启动类
    │   ├── config/GameProperties.java   # 绑定 application.yml 的 game.*
    │   ├── domain/                      # 实体（表结构）
    │   │   ├── User.java                #   账号 + 积分 + 每日计数（带 @Version 乐观锁）
    │   │   ├── SpinRecord.java          #   每一次抽奖的流水
    │   │   └── SessionToken.java        #   服务端会话
    │   ├── repo/                        # Spring Data 仓库
    │   ├── game/
    │   │   ├── Segment.java             #   扇区定义
    │   │   └── WheelCatalog.java        #   ★ 盘面 + 按权重抽奖
    │   ├── service/
    │   │   ├── PasswordHasher.java      #   PBKDF2 口令哈希
    │   │   ├── AuthService.java         #   注册 / 登录 / 会话
    │   │   └── WheelService.java        #   ★ 抽奖事务（扣分、抽奖、发奖、记账）
    │   └── web/
    │       ├── ApiController.java       #   全部 HTTP 接口
    │       ├── Dto.java                 #   出入参
    │       ├── ApiException.java        #   业务异常
    │       ├── ApiExceptionHandler.java #   统一错误响应
    │       └── NoCacheFilter.java       #   /api/** 禁缓存
    └── resources/
        ├── application.yml              # 端口、数据库、游戏规则
        └── static/index.html            # 前端页面（由服务端托管）
```

---

## 四、运行

### 方式一：双击 `run.bat`

首次会自动 `mvn package`，之后直接启动。

### 方式二：命令行

```bash
cd tycoon-wheel-java
mvn -DskipTests package
java -jar target/tycoon-wheel-1.0.0.jar
```

启动后打开 **http://127.0.0.1:8787**

> 启动大约需要 15–20 秒（Hibernate 初始化 + 建表），看到
> `Started TycoonApplication` 就绪。

### H2 控制台（可直接看表）

浏览器打开 http://127.0.0.1:8787/h2-console

| 字段 | 值 |
|---|---|
| JDBC URL | `jdbc:h2:file:./data/tycoon` |
| 用户名 | `sa` |
| 密码 | 留空 |

---

## 五、接口文档

所有接口都在 `/api` 下，会话靠 `HttpOnly` Cookie `TYCOON_SID`，**不传任何 token 参数**。

| 方法 | 路径 | 说明 | 需登录 |
|---|---|---|---|
| GET | `/api/config` | 盘面、概率、单次消耗、初始积分 | — |
| POST | `/api/register` | 注册，成功后自动登录 | — |
| POST | `/api/login` | 登录 | — |
| POST | `/api/logout` | 退出，清除会话 | — |
| GET | `/api/me` | 当前账号：积分、段位、可抽次数、中奖记录 | ✅ |
| POST | `/api/spin` | **抽一次**，返回中奖扇区下标与结果 | ✅ |
| GET | `/api/stats` | 运营统计：用户数、抽奖数、奖品/券发放数 | — |

### 请求示例

```bash
# 注册
curl -c cookies.txt -H "Content-Type: application/json" \
     -d '{"username":"张三","password":"pass1234"}' \
     http://127.0.0.1:8787/api/register

# 抽奖
curl -b cookies.txt -X POST http://127.0.0.1:8787/api/spin
```

### `/api/spin` 响应

```json
{
  "segmentIndex": 5,
  "segment": {
    "index": 5, "key": "sofa", "label": "懒人沙发", "icon": "🛋️",
    "type": "prize", "desc": "懒人沙发一台！…", "percent": 4.0
  },
  "message": "懒人沙发一台！…",
  "pointsDelta": -10,
  "points": 90,
  "spinsLeft": 9,
  "totalSpins": 1,
  "history": [ { "label": "懒人沙发", "icon": "🛋️", "type": "prize", "pointsDelta": -10, "at": 1792851325966 } ]
}
```

### 错误响应

```json
{ "error": "INSUFFICIENT_POINTS", "message": "积分不足，抽奖需要 10 积分" }
```

| error | HTTP | 含义 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 未登录 / 会话过期 |
| `BAD_USERNAME` `BAD_PASSWORD` | 400 | 参数不合法 |
| `USERNAME_TAKEN` | 409 | 账号已注册 |
| `INSUFFICIENT_POINTS` | 400 | 积分不足 |
| `RATE_LIMITED` | 429 | 抽太快 / 今日次数用完 |
| `CONCURRENT_UPDATE` | 409 | 并发冲突，重试即可 |
| `INTERNAL` | 500 | 服务端异常 |

---

## 六、数据库表

| 表 | 作用 | 关键字段 |
|---|---|---|
| `users` | 账号 | `username`(唯一)、`salt`、`password_hash`、`points`、`total_spins`、`day_key`、`day_count`、`version` |
| `spin_records` | 抽奖流水 | `user_id`、`segment_key`、`label`、`type`、`points_delta`、`points_after`、`created_at` |
| `sessions` | 会话 | `token`(主键)、`user_id`、`expires_at` |

`spin_records` 是**唯一的奖品事实来源**。要发奖就问它：

```sql
-- 今天发出的实物奖品
SELECT label, COUNT(*) FROM spin_records
WHERE type = 'prize' AND created_at >= CURRENT_DATE
GROUP BY label;

-- 谁中了手机
SELECT u.username, r.created_at FROM spin_records r
JOIN users u ON u.id = r.user_id WHERE r.segment_key = 'phone';
```

---

## 七、改玩法

全部在 `src/main/resources/application.yml`：

```yaml
game:
  cost-per-spin: 10       # 每次消耗积分
  initial-points: 100     # 新账号初始积分
  daily-limit: 0          # 每人每天最多抽几次；0 = 不限。改成 1 就是"每晚一次"
  session-ttl-hours: 168  # 登录有效期
  history-keep: 60        # 返回给前端的历史条数
  max-spins-per-minute: 30 # 同账号每分钟最多抽几次（防脚本）
```

概率在 `WheelCatalog.java` 里改 `weight`（相对权重，会自动换算百分比，总和不必是 100）：

```java
new Segment(7, "phone", "手机", "📱", "prize", 0, "…", 1),   // 最后一个数字就是权重
```

---

## 八、已做的安全措施

| 风险 | 处理 |
|---|---|
| 前端伪造中奖 | 结果由服务端 `SecureRandom` 产生，前端只负责动画 |
| 前端改积分 | 积分只存在数据库，接口不接受客户端传入的积分数 |
| 密码泄露 | PBKDF2-HMAC-SHA256，20 万次迭代，每账号独立盐 |
| XSS 偷 Cookie | 会话 Cookie 带 `HttpOnly`，JS 读不到 |
| CSRF | Cookie 用 `SameSite=Lax`，且写操作只接受 POST |
| 并发重复扣分 | `User` 有 `@Version` 乐观锁，冲突返回 409 |
| 脚本刷奖 | 每账号每分钟限次 + 每日限次（可配） |
| 缓存导致积分显示过期 | `/api/**` 统一 `Cache-Control: no-store` |
| 越权看别人记录 | 所有查询都以会话里的 userId 为准，接口不接受 userId 参数 |

---

## 九、上生产前必须做的事

这个版本可以直接跑，但**要对外提供服务还需要处理**：

1. **改监听地址**：`application.yml` 里 `server.address` 现在是 `127.0.0.1`（只允许本机）。对外要改成 `0.0.0.0`，并且**必须**放在 Nginx/Caddy 后面。
2. **上 HTTPS**：现在 Cookie 没加 `Secure` 标记。有 HTTPS 之后要给 Cookie 加 `.secure(true)`。
3. **换数据库**：H2 适合单机和演示。多人并发建议换 MySQL/PostgreSQL —— 只需改 `spring.datasource.*` 和加对应 JDBC 驱动，JPA 代码不用动。
4. **关掉 H2 控制台**：`spring.h2.console.enabled: false`。
5. **加 CORS**：如果前端要独立部署到别的域名，需要配 `CorsConfiguration` + `allowCredentials(true)`。
6. **加运营后台**：现在只有只读的 `/api/stats`，发货、核销、封号这些操作还没做。

---

## 十、和纯前端版的区别

| | 纯前端版 `tycoon-wheel.html` | 后端版 |
|---|---|---|
| 账号 | 浏览器 localStorage | 数据库 `users` 表 |
| 积分 | 可被用户任意修改 | 服务端权威，改不了 |
| 抽奖概率 | 写在前端 JS 里 | 服务端 `SecureRandom` |
| 中奖记录 | localStorage，清缓存即丢失 | `spin_records` 表，永久留存 |
| 换设备 | 数据不通用 | 登录即可，数据跟着账号 |
| 密码 | 前端玩具哈希 | PBKDF2 20 万次迭代 |
| 部署 | 双击 HTML 即可 | 需要跑 Java 服务 |

纯前端版仍然保留在 `../tycoon-wheel.html`，适合做**纯演示**（不需要真实发奖的场景）。
