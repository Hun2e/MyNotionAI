CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    nickname VARCHAR(255) NOT NULL,
    provider VARCHAR(20) DEFAULT 'LOCAL' NOT NULL,
    provider_user_id VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_provider_user_id UNIQUE (provider, provider_user_id)
);

CREATE TABLE schedules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    memo TEXT,
    created_by VARCHAR(20) DEFAULT 'USER' NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedules_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE chat_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE ai_actions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    action_type VARCHAR(255) NOT NULL,
    target_schedule_id BIGINT,
    payload_json TEXT NOT NULL,
    status VARCHAR(255) NOT NULL,
    fail_reason VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_actions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ai_actions_schedule FOREIGN KEY (target_schedule_id) REFERENCES schedules (id)
);

CREATE TABLE reminder_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    remind_at DATETIME(6) NOT NULL,
    schedule_start_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reminder_schedule UNIQUE (schedule_id),
    CONSTRAINT fk_reminder_logs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reminder_logs_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id)
);

CREATE INDEX idx_user_start ON schedules (user_id, start_at);
CREATE INDEX idx_chat_user_created ON chat_logs (user_id, created_at);
CREATE INDEX idx_ai_user_created ON ai_actions (user_id, created_at);
CREATE INDEX idx_schedule ON ai_actions (target_schedule_id);
CREATE INDEX idx_reminder_created ON reminder_logs (created_at);
CREATE INDEX idx_reminder_user ON reminder_logs (user_id);
