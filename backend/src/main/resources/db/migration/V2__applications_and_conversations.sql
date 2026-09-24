-- Existing personal data remains in legacy columns, but application code never
-- writes or serves those columns after this migration.
ALTER TABLE applications ADD CONSTRAINT uk_application_once UNIQUE (item_id, applicant_id, type);

CREATE TABLE conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    publisher_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    publisher_read_id BIGINT NOT NULL DEFAULT 0,
    applicant_read_id BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME,
    UNIQUE KEY uk_conversation_application (application_id),
    INDEX idx_conversations_publisher (publisher_id, created_at),
    INDEX idx_conversations_applicant (applicant_id, created_at),
    INDEX idx_conversations_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chat_messages_conversation (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
