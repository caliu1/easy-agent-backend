-- MCP / Skill profile tables (v1.7)
-- MySQL 8+
SELECT VERSION();

CREATE TABLE IF NOT EXISTS `ai_agent_mcp_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` VARCHAR(128) NOT NULL COMMENT 'owner user id',
    `mcp_type` VARCHAR(32) NOT NULL COMMENT 'sse|streamableHttp',
    `mcp_name` VARCHAR(128) NOT NULL COMMENT 'mcp server name',
    `mcp_desc` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'mcp description',
    `config_json` LONGTEXT NOT NULL COMMENT 'full mcp json config',
    `is_deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_user_mcp_name_type_deleted` (`user_id`, `mcp_name`, `mcp_type`, `is_deleted`),
    KEY `idx_user_update_time` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user mcp profiles';

CREATE TABLE IF NOT EXISTS `ai_agent_skill_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` VARCHAR(128) NOT NULL COMMENT 'owner user id',
    `skill_name` VARCHAR(128) NOT NULL COMMENT 'display name in dropdown',
    `oss_path` VARCHAR(1024) NOT NULL COMMENT 'skill path in oss, usually starts with easyagent/skills/',
    `is_deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_user_skill_name_deleted` (`user_id`, `skill_name`, `is_deleted`),
    KEY `idx_skill_user_update_time` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user skill profiles';
