-- H2 test schema for OutboxRepositoryTest
CREATE TABLE IF NOT EXISTS pmis_outbox (
    id                  VARCHAR(64)   NOT NULL,
    aggregate_id        VARCHAR(128)  NOT NULL,
    aggregate_type      VARCHAR(128)  NOT NULL,
    event_type          VARCHAR(128)  NOT NULL,
    payload             CLOB          NOT NULL,
    headers             CLOB,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    retry_count         INT           NOT NULL DEFAULT 0,
    max_retries         INT           NOT NULL DEFAULT 5,
    next_retry_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at             TIMESTAMP,
    error_message       CLOB,
    tenant_id           VARCHAR(64),
    deduplication_id    VARCHAR(64),
    schema_version      VARCHAR(32)   DEFAULT 'v1.0.0',
    content_type        VARCHAR(128),
    priority            INT           NOT NULL DEFAULT 5,
    trace_id            VARCHAR(64),
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_retry
    ON pmis_outbox (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_outbox_priority_created
    ON pmis_outbox (status, priority DESC, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_dedup
    ON pmis_outbox (deduplication_id);
