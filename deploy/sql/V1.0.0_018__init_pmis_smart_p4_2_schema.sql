-- ============================================================
-- V1.0.0_018  智能化升级 P4-1/P4-2/P4-3  脚本
-- ============================================================
-- 说明：批次 15 智能化升级-系统内部数据管理（PRD 4.2）
-- 1) 工时表新增 billable 字段（可计费标识）
-- 2) 预警分级推送表 pmis_alert_dispatch
-- 3) 每日对账表 pmis_reconcile_daily
-- ============================================================

-- ----------------------------
-- 1) 工时表新增 billable 字段
-- ----------------------------
ALTER TABLE pmis_execution_time_entry
    ADD COLUMN IF NOT EXISTS billable SMALLINT NOT NULL DEFAULT 1;
COMMENT ON COLUMN pmis_execution_time_entry.billable IS '可计费标识';

-- ----------------------------
-- 2) 预警分级推送表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_alert_dispatch (
    id                  BIGSERIAL PRIMARY KEY,
    alert_code          VARCHAR(64)  NOT NULL UNIQUE,
    alert_type          VARCHAR(32)  NOT NULL,
    alert_level         VARCHAR(8)   NOT NULL,
    source_type         VARCHAR(32)  NOT NULL,
    source_id           VARCHAR(64),
    title               VARCHAR(256) NOT NULL,
    content             TEXT,
    target_role         VARCHAR(64)  NOT NULL,
    target_user_ids     VARCHAR(1024),
    push_channels       VARCHAR(64)  NOT NULL DEFAULT 'IN_APP',
    dispatched_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_by       VARCHAR(64),
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    sent_at             TIMESTAMP,
    fail_reason         VARCHAR(512),
    retry_count         INT          NOT NULL DEFAULT 0,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_alert_dispatch IS '预警分级推送（黄/红不同层级触达）';

CREATE INDEX IF NOT EXISTS idx_alert_dispatch_level   ON pmis_alert_dispatch(alert_level, deleted);
CREATE INDEX IF NOT EXISTS idx_alert_dispatch_type    ON pmis_alert_dispatch(alert_type, deleted);
CREATE INDEX IF NOT EXISTS idx_alert_dispatch_status  ON pmis_alert_dispatch(status, deleted);
CREATE INDEX IF NOT EXISTS idx_alert_dispatch_source  ON pmis_alert_dispatch(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_alert_dispatch_target  ON pmis_alert_dispatch(target_role, deleted);

-- ----------------------------
-- 3) 每日对账表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_reconcile_daily (
    id                  BIGSERIAL PRIMARY KEY,
    reconcile_date      DATE         NOT NULL,
    reconcile_type      VARCHAR(32)  NOT NULL,
    initiation_id       BIGINT,
    expected_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    actual_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,
    diff_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    diff_pct            NUMERIC(8,4) NOT NULL DEFAULT 0,
    status              VARCHAR(16)  NOT NULL DEFAULT 'OK',
    detail              TEXT,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_reconcile_daily IS '每日自动对账（成本/收入/回款/开票 跨模块校验）';

CREATE INDEX IF NOT EXISTS idx_reconcile_daily_date    ON pmis_reconcile_daily(reconcile_date, deleted);
CREATE INDEX IF NOT EXISTS idx_reconcile_daily_type    ON pmis_reconcile_daily(reconcile_type, deleted);
CREATE INDEX IF NOT EXISTS idx_reconcile_daily_init    ON pmis_reconcile_daily(initiation_id, deleted);
CREATE INDEX IF NOT EXISTS idx_reconcile_daily_status  ON pmis_reconcile_daily(status, deleted);

-- 唯一约束：每天每个维度只能有一条
CREATE UNIQUE INDEX IF NOT EXISTS uk_reconcile_daily
    ON pmis_reconcile_daily(reconcile_date, reconcile_type, COALESCE(initiation_id, 0), deleted);
