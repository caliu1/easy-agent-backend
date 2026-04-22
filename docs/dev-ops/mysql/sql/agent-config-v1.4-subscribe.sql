-- Agent subscribe relation (v1.4)
-- MySQL 8+

CREATE TABLE IF NOT EXISTS `ai_agent_subscribe` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'pk',
    `user_id` VARCHAR(64) NOT NULL COMMENT 'subscriber user id',
    `agent_id` VARCHAR(64) NOT NULL COMMENT 'subscribed plaza agent id',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'soft delete',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_user_agent` (`user_id`, `agent_id`),
    KEY `idx_user_deleted` (`user_id`, `is_deleted`),
    KEY `idx_agent_deleted` (`agent_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user subscribed plaza agents';
