-- ============================================================
-- PMIS P2-10 增量表：报表订阅 + 仪表盘布局 + 异步导出记录
-- ============================================================
-- 本脚本包含 P2-10 迭代新增的 3 张表：
--   1) pmis_report_subscription      — 报表订阅
--   2) pmis_report_subscription_log  — 报表订阅执行日志
--   3) pmis_dashboard_layout         — 用户仪表盘布局（跨设备同步）
-- ============================================================

-- ============================ 报表订阅 ============================
CREATE TABLE IF NOT EXISTS pmis_report_subscription (
    id              VARCHAR(32)    NOT NULL PRIMARY KEY,
    report_type     VARCHAR(64)    NOT NULL,
    report_name     VARCHAR(128)   NOT NULL,
    cron_expression VARCHAR(128)   NOT NULL,
    delivery_channels VARCHAR(256) NOT NULL DEFAULT 'EMAIL',
    delivery_emails VARCHAR(1024),
    params          JSONB          NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'PAUSED')),
    last_run_at     TIMESTAMPTZ,
    last_run_status VARCHAR(16),
    created_by      VARCHAR(64)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       BIGINT         NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_prs_created_by
    ON pmis_report_subscription(created_by)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_prs_status
    ON pmis_report_subscription(status, cron_expression)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_prs_tenant
    ON pmis_report_subscription(tenant_id, created_at DESC)
    WHERE deleted = 0;

-- ============================ 报表订阅执行日志 ============================
CREATE TABLE IF NOT EXISTS pmis_report_subscription_log (
    id              BIGSERIAL      PRIMARY KEY,
    subscription_id VARCHAR(32)    NOT NULL,
    run_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    run_status      VARCHAR(16)    NOT NULL DEFAULT 'RUNNING'
                    CHECK (run_status IN ('RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    file_url        VARCHAR(1024),
    error_message   TEXT,
    duration_ms     BIGINT,
    file_size       BIGINT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_prsl_subscription
    ON pmis_report_subscription_log(subscription_id, run_at DESC);

-- ============================ 用户仪表盘布局 ============================
CREATE TABLE IF NOT EXISTS pmis_dashboard_layout (
    id              BIGSERIAL      PRIMARY KEY,
    user_id         VARCHAR(64)    NOT NULL,
    layout_key      VARCHAR(64)    NOT NULL DEFAULT 'main',
    layout_config   JSONB          NOT NULL DEFAULT '{}'::jsonb,
    created_by      VARCHAR(64)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    tenant_id       BIGINT         NOT NULL DEFAULT 1,
    UNIQUE (user_id, layout_key)
);

CREATE INDEX IF NOT EXISTS idx_pdl_user
    ON pmis_dashboard_layout(user_id, layout_key);

-- ============================ 异步导出记录（补齐） ============================
-- 注：AsyncExportService 已自行管理表结构，此处仅做索引补齐
-- 如表不存在则创建
CREATE TABLE IF NOT EXISTS pmis_async_export_record (
    id              VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id         VARCHAR(64)    NOT NULL,
    export_type     VARCHAR(64)    NOT NULL,
    status          VARCHAR(16)    NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'EXPIRED')),
    progress        SMALLINT       NOT NULL DEFAULT 0,
    file_name       VARCHAR(256),
    file_size       BIGINT,
    file_format     VARCHAR(16)    DEFAULT 'XLSX',
    file_url        VARCHAR(1024),
    error_message   TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    expired_at      TIMESTAMPTZ,
    tenant_id       BIGINT         NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_aer_user
    ON pmis_async_export_record(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_aer_status
    ON pmis_async_export_record(status)
    WHERE status IN ('PENDING', 'RUNNING');
