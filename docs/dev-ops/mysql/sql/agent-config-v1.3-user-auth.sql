-- User account persistence (v1.3)
-- MySQL 8+

CREATE TABLE IF NOT EXISTS `ai_user_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'pk',
    `user_id` VARCHAR(64) NOT NULL COMMENT 'login user id',
    `nickname` VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'user display name',
    `password_hash` VARCHAR(128) NOT NULL COMMENT 'sha256(salt:password)',
    `password_salt` VARCHAR(64) NOT NULL COMMENT 'password salt',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|LOCKED',
    `last_login_time` DATETIME DEFAULT NULL COMMENT 'last login time',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'soft delete',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_user_id` (`user_id`),
    KEY `idx_status_deleted` (`status`, `is_deleted`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user account';
