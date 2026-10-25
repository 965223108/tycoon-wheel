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
