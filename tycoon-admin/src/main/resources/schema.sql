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
