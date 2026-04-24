-- Session history tables (v1.5)
-- MySQL 8+
SELECT VERSION();

CREATE TABLE IF NOT EXISTS `ai_agent_session_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'pk',
    `session_id` VARCHAR(128) NOT NULL COMMENT 'chat session id',
    `agent_id` VARCHAR(64) NOT NULL COMMENT 'business agent id',
    `user_id` VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'user id',
    `session_title` VARCHAR(256) NOT NULL DEFAULT '' COMMENT 'session title',
    `latest_message` VARCHAR(1024) NOT NULL DEFAULT '' COMMENT 'latest message preview',
    `message_count` BIGINT NOT NULL DEFAULT 0 COMMENT 'message count in session',
    `total_tokens` BIGINT NOT NULL DEFAULT 0 COMMENT 'total model tokens in session',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_session_id` (`session_id`),
    KEY `idx_user_agent_update` (`user_id`, `agent_id`, `update_time`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='session history summary';

CREATE TABLE IF NOT EXISTS `ai_agent_session_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'pk',
    `session_id` VARCHAR(128) NOT NULL COMMENT 'chat session id',
    `agent_id` VARCHAR(64) NOT NULL COMMENT 'business agent id',
    `user_id` VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'user id',
    `role` VARCHAR(16) NOT NULL COMMENT 'user|assistant|system|tool',
    `content` LONGTEXT NOT NULL COMMENT 'message content',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_session_id_id` (`session_id`, `id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='session history messages';
