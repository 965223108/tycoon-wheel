-- =============================================================
-- 富豪大转盘 (Tycoon Wheel) — 数据库初始化脚本
-- MySQL 8.0+ / utf8mb4
--
-- 说明：
--   1. 每个微服务启动时也会执行自己 module 内的 schema.sql（幂等，CREATE TABLE IF NOT EXISTS），
--      本文件是三个服务的合并版，用于「先建库再启动」的场景。
--   2. 首次启动时 admin 服务会自动创建管理员账号 admin/admin123 和 8 个默认商品，
--      所以本脚本不插入种子数据。
-- =============================================================

CREATE DATABASE IF NOT EXISTS tycoon
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE tycoon;

-- -------------------------------------------------------------
-- 1) tycoon-wheel：用户账号 / 抽奖记录 / 会话
-- -------------------------------------------------------------
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


-- -------------------------------------------------------------
-- 2) tycoon-user：用户资料 / 奖励发放
-- -------------------------------------------------------------
-- ---------------------------------------------------------
-- user-service 负责的表
-- 说明：users 表由最早的 wheel 创建，这里只追加新表，避免 ALTER
-- ---------------------------------------------------------

-- 用户档案（昵称 / 黑名单状态）
CREATE TABLE IF NOT EXISTS user_profile (
    user_id          BIGINT       NOT NULL COMMENT '账号 id',
    nickname         VARCHAR(32)  DEFAULT NULL COMMENT '昵称',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / BLACKLISTED',
    blacklist_reason VARCHAR(255) DEFAULT NULL COMMENT '拉黑原因',
    updated_at       DATETIME(3)  NOT NULL COMMENT '更新时间（UTC）',
    PRIMARY KEY (user_id),
    KEY idx_profile_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户档案';

-- 用户奖励 / 奖品发放记录
CREATE TABLE IF NOT EXISTS user_rewards (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT       NOT NULL COMMENT '账号 id',
    prize_key  VARCHAR(32)  NOT NULL COMMENT '奖品 key',
    label      VARCHAR(64)  NOT NULL COMMENT '奖品名称',
    icon       VARCHAR(16)  DEFAULT NULL COMMENT '图标',
    status     VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ISSUED/CANCELLED',
    remark     VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME(3)  NOT NULL COMMENT '发放时间（UTC）',
    PRIMARY KEY (id),
    KEY idx_reward_user (user_id),
    KEY idx_reward_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户奖励';


-- -------------------------------------------------------------
-- 3) tycoon-admin：管理员 / 游戏商品配置
-- -------------------------------------------------------------
-- ---------------------------------------------------------
-- admin-service 负责的表
-- ---------------------------------------------------------

-- 后台账号
CREATE TABLE IF NOT EXISTS admin_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(32)  NOT NULL COMMENT '登录名',
    salt          VARCHAR(64)  NOT NULL COMMENT '口令盐',
    password_hash VARCHAR(128) NOT NULL COMMENT 'PBKDF2 哈希',
    role          VARCHAR(16)  NOT NULL DEFAULT 'ADMIN' COMMENT 'ADMIN / OPERATOR',
    created_at    DATETIME(3)  NOT NULL COMMENT '创建时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台账号';

-- 游戏商品 / 转盘扇区配置
CREATE TABLE IF NOT EXISTS game_segments (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    idx         INT          NOT NULL COMMENT '转盘顺序',
    seg_key     VARCHAR(32)  NOT NULL COMMENT '唯一 key',
    label       VARCHAR(64)  NOT NULL COMMENT '展示名称',
    icon        VARCHAR(16)  DEFAULT NULL COMMENT '图标',
    type        VARCHAR(16)  NOT NULL COMMENT 'points/none/prize/coupon',
    description VARCHAR(255) DEFAULT NULL COMMENT '描述',
    percent     DOUBLE       NOT NULL COMMENT '中奖概率(%)',
    points      INT          NOT NULL DEFAULT 0 COMMENT '积分奖励',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME(3)  NOT NULL COMMENT '创建时间（UTC）',
    updated_at  DATETIME(3)  NOT NULL COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_seg_key (seg_key),
    KEY idx_seg_enabled (enabled, idx)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏商品配置';
