-- =====================================================================
--  ydsz-cronjob 集成测试 Schema（最小化建表，仅覆盖 Repository 集成测试所需）
-- =====================================================================

DROP TABLE IF EXISTS ydsz_job_main CASCADE;

CREATE TABLE ydsz_job_main (
  id                        VARCHAR(32)   PRIMARY KEY,
  job_name                  VARCHAR(128)  NOT NULL,
  job_group                 VARCHAR(128)  DEFAULT NULL,
  job_key                   VARCHAR(64)   NOT NULL,
  handler                   VARCHAR(128)  NOT NULL,
  cron_expression           VARCHAR(64)   NOT NULL,
  schedule_type             VARCHAR(32)   DEFAULT NULL,
  fixed_rate_ms             BIGINT        DEFAULT NULL,
  fixed_delay_ms            BIGINT        DEFAULT NULL,
  params_json               JSONB         DEFAULT NULL,
  job_remark                VARCHAR(512)  DEFAULT NULL,
  next_fire_time            TIMESTAMP     DEFAULT NULL,
  last_fire_time            TIMESTAMP     DEFAULT NULL,
  fire_count                BIGINT        NOT NULL DEFAULT 0,
  success_count             BIGINT        NOT NULL DEFAULT 0,
  fail_count                BIGINT        NOT NULL DEFAULT 0,
  lock_ttl_ms               BIGINT        DEFAULT NULL,
  timeout_ms                BIGINT        DEFAULT NULL,
  sla_ms                    BIGINT        DEFAULT NULL,
  slow_threshold_ms         BIGINT        DEFAULT NULL,
  misfire_policy            VARCHAR(32)   DEFAULT NULL,
  shard_total               INTEGER       DEFAULT NULL,
  job_type                  VARCHAR(32)   DEFAULT NULL,
  max_retries               INTEGER       NOT NULL DEFAULT 0,
  retry_interval_ms         BIGINT        DEFAULT NULL,
  retry_backoff             VARCHAR(32)   DEFAULT NULL,
  block_strategy            VARCHAR(32)   DEFAULT NULL,
  consecutive_fail_count    INTEGER       NOT NULL DEFAULT 0,
  max_consecutive_fails     INTEGER       DEFAULT NULL,
  auto_resume_after_minutes INTEGER       DEFAULT NULL,
  priority                  INTEGER       NOT NULL DEFAULT 0,
  version                   INTEGER       NOT NULL DEFAULT 1,
  timezone                  VARCHAR(64)   DEFAULT NULL,
  cluster                   VARCHAR(64)   DEFAULT NULL,
  canary_ratio              INTEGER       DEFAULT NULL,
  canary_handler            VARCHAR(128)  DEFAULT NULL,
  status                    VARCHAR(32)   DEFAULT NULL,
  sort                      INTEGER       DEFAULT 0,
  revision                  INTEGER       NOT NULL DEFAULT 0,
  tenant_id                 VARCHAR(64)   NOT NULL DEFAULT '0',
  is_deleted                SMALLINT      NOT NULL DEFAULT 0,
  created_by                VARCHAR(64)   DEFAULT NULL,
  created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by                VARCHAR(64)   DEFAULT NULL,
  updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_ydsz_job_job_key UNIQUE (job_key, tenant_id)
);

CREATE INDEX idx_ydsz_job_job_group ON ydsz_job_main (job_group);
CREATE INDEX idx_ydsz_job_tenant_is_deleted ON ydsz_job_main (tenant_id, is_deleted);
