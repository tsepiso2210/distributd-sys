-- =============================================================
-- Distributed Bulk Email Sender - PostgreSQL Schema
-- =============================================================
-- Assignment requirement: "Database storage for logs, reports, templates, tasks, and statuses"
-- Run this script ONCE to create the database schema.
-- Execute: psql -U emailuser -d emaildb -f schema.sql
-- =============================================================

-- Create the database (run as superuser if needed):
-- CREATE DATABASE emaildb;
-- CREATE USER emailuser WITH PASSWORD 'emailpass';
-- GRANT ALL PRIVILEGES ON DATABASE emaildb TO emailuser;

-- =============================================================
-- TABLE: email_templates
-- Reusable email body + subject templates with placeholders.
-- Assignment requirement: "Email templates with dynamic placeholders"
-- Placeholders: {{name}}, {{studentNumber}}, {{course}}, etc.
-- =============================================================
CREATE TABLE IF NOT EXISTS email_templates (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL UNIQUE,
    subject_template VARCHAR(500) NOT NULL,
    body_template    TEXT NOT NULL,
    description      TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================
-- TABLE: email_campaigns
-- A campaign groups many email tasks for one bulk send operation.
-- Assignment requirement: "Distributed system for sending bulk email requests"
-- =============================================================
CREATE TABLE IF NOT EXISTS email_campaigns (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    subject           VARCHAR(500) NOT NULL,
    template_id       BIGINT REFERENCES email_templates(id) ON DELETE SET NULL,
    raw_body          TEXT,
    sender_email      VARCHAR(255) NOT NULL,
    sender_name       VARCHAR(255),
    total_recipients  INTEGER DEFAULT 0,
    sent_count        INTEGER DEFAULT 0,
    failed_count      INTEGER DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================
-- TABLE: email_tasks
-- One row per email to one recipient.
-- Assignment requirements:
--   - "Email status tracking: sent, delivered, bounced, failed"
--   - "Priority messaging so high-priority emails are processed first"
--   - "Retry mechanism for failed email deliveries using exponential backoff"
--   - "Automatic failover to another email provider if one fails"
-- =============================================================
CREATE TABLE IF NOT EXISTS email_tasks (
    id                  BIGSERIAL PRIMARY KEY,
    campaign_id         BIGINT NOT NULL REFERENCES email_campaigns(id) ON DELETE CASCADE,
    recipient_email     VARCHAR(255) NOT NULL,
    recipient_name      VARCHAR(255),
    template_variables  TEXT,                        -- JSON map of placeholder values

    -- Priority: HIGH or NORMAL
    -- HIGH tasks published to email.high-priority Kafka topic
    priority            VARCHAR(10) NOT NULL DEFAULT 'NORMAL'
                            CHECK (priority IN ('HIGH', 'NORMAL')),

    -- Status lifecycle: PENDING -> QUEUED -> SENDING -> SENT -> DELIVERED
    --                                                        -> BOUNCED
    --                                        -> FAILED -> RETRYING -> DEAD_LETTERED
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','QUEUED','SENDING','SENT',
                                              'DELIVERED','BOUNCED','FAILED',
                                              'RETRYING','DEAD_LETTERED')),

    -- Which email provider was last used
    provider_used       VARCHAR(20) DEFAULT 'NONE'
                            CHECK (provider_used IN ('MAILGUN','SENDGRID','NONE')),

    -- Retry tracking for exponential backoff
    retry_count         INTEGER NOT NULL DEFAULT 0,
    last_error          TEXT,

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_email_tasks_status       ON email_tasks(status);
CREATE INDEX IF NOT EXISTS idx_email_tasks_campaign     ON email_tasks(campaign_id);
CREATE INDEX IF NOT EXISTS idx_email_tasks_priority     ON email_tasks(priority);
CREATE INDEX IF NOT EXISTS idx_email_tasks_recipient    ON email_tasks(recipient_email);

-- =============================================================
-- TABLE: email_status_logs
-- Immutable audit log - one row per status change per task.
-- Assignment requirements:
--   - "Every send attempt must be logged in PostgreSQL"
--   - "Maintain email status history"
-- =============================================================
CREATE TABLE IF NOT EXISTS email_status_logs (
    id              BIGSERIAL PRIMARY KEY,
    email_task_id   BIGINT NOT NULL REFERENCES email_tasks(id) ON DELETE CASCADE,
    status          VARCHAR(20) NOT NULL
                        CHECK (status IN ('PENDING','QUEUED','SENDING','SENT',
                                          'DELIVERED','BOUNCED','FAILED',
                                          'RETRYING','DEAD_LETTERED')),
    provider        VARCHAR(20)
                        CHECK (provider IN ('MAILGUN','SENDGRID','NONE')),
    message         TEXT,                    -- Success confirmation or error details
    response_code   INTEGER,                 -- HTTP status code from provider
    timestamp       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_status_logs_task      ON email_status_logs(email_task_id);
CREATE INDEX IF NOT EXISTS idx_status_logs_timestamp ON email_status_logs(timestamp);

-- =============================================================
-- Auto-update updated_at trigger for email_tasks
-- =============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_email_tasks_updated_at
    BEFORE UPDATE ON email_tasks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_email_templates_updated_at
    BEFORE UPDATE ON email_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Done! Schema created successfully.
-- Run seed.sql next to insert sample data.
