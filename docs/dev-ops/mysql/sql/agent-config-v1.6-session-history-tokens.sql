-- Session history token statistics (v1.6)
-- MySQL 8+
SELECT VERSION();

ALTER TABLE `ai_agent_session_history`
    ADD COLUMN IF NOT EXISTS `total_tokens` BIGINT NOT NULL DEFAULT 0 COMMENT 'total model tokens in session' AFTER `message_count`;

