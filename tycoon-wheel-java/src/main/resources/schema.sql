-- ============================================================
-- 富豪大转盘 · MySQL 建表脚本
-- 字符集 utf8mb4，引擎 InnoDB
-- 全部 CREATE TABLE IF NOT EXISTS，可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(32)  NOT NULL                COMMENT '账号（小写归一化后唯一）',
    salt          VARCHAR(64)  NOT NULL                COMMENT '口令盐（Base64）',
    password_hash VARCHAR(128) NOT NULL                COMMENT 'PBKDF2-HMAC-SHA256 哈希',
    points        INT          NOT NULL DEFAULT 0      COMMENT '当前积分',
    total_spins   INT          NOT NULL DEFAULT 0      COMMENT '累计抽奖次数',
    day_key       VARCHAR(10)  NULL                    COMMENT '当日限次日期键 yyyy-MM-dd',
    day_count     INT          NOT NULL DEFAULT 0      COMMENT '当日已抽次数',
    version       BIGINT       NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at    DATETIME(3)  NOT NULL                COMMENT '注册时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '账号';

CREATE TABLE IF NOT EXISTS spin_records (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT      NOT NULL                COMMENT '账号 id',
    segment_key  VARCHAR(24) NOT NULL                COMMENT '扇区标识，如 phone / sofa',
    label        VARCHAR(24) NOT NULL                COMMENT '奖品名称快照',
    icon         VARCHAR(8)  NOT NULL                COMMENT '图标快照',
    type         VARCHAR(12) NOT NULL                COMMENT 'points/prize/coupon/none',
    points_delta INT         NOT NULL                COMMENT '对积分的净影响（含抽奖成本）',
    points_after INT         NOT NULL                COMMENT '抽奖后余额，便于对账',
    created_at   DATETIME(3) NOT NULL                COMMENT '抽奖时间（UTC）',
    PRIMARY KEY (id),
    KEY idx_spin_user (user_id, id),
    KEY idx_spin_type (type),
    KEY idx_spin_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '抽奖流水';

CREATE TABLE IF NOT EXISTS sessions (
    token      VARCHAR(64) NOT NULL COMMENT '会话令牌',
    user_id    BIGINT      NOT NULL COMMENT '账号 id',
    expires_at DATETIME(3) NOT NULL COMMENT '过期时间（UTC）',
    created_at DATETIME(3) NOT NULL COMMENT '签发时间（UTC）',
    PRIMARY KEY (token),
    KEY idx_sessions_user (user_id),
    KEY idx_sessions_exp (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '登录会话';
