-- ============================================================
-- V1.0.0_031  P1-5 报表订阅与导出记录表
-- ============================================================
-- 说明：定时报表生成与分发（P1-5）
--   pmis_report_subscription   报表订阅表
--   pmis_report_export_record  报表导出记录表
-- ============================================================

-- 报表订阅表
CREATE TABLE IF NOT EXISTS pmis_report_subscription (
    id              BIGSERIAL    PRIMARY KEY,
    subscriber_id   BIGINT       NOT NULL,
    report_type     VARCHAR(50)  NOT NULL,
    frequency       VARCHAR(20)  NOT NULL DEFAULT 'DAILY',
    channels        VARCHAR(200),
    recipients      VARCHAR(500),
    enabled         SMALLINT     NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_report_subscription IS '报表订阅表';
COMMENT ON COLUMN pmis_report_subscription.subscriber_id IS '订阅人ID';
COMMENT ON COLUMN pmis_report_subscription.report_type IS '报表类型 (COCKPIT/EVM/PROFIT/UTILIZATION/BENCH_COST/RISK)';
COMMENT ON COLUMN pmis_report_subscription.frequency IS '推送频率 (DAILY/WEEKLY/MONTHLY)';
COMMENT ON COLUMN pmis_report_subscription.channels IS '推送渠道，逗号分隔 (EMAIL/DINGTALK/IN_APP)';
COMMENT ON COLUMN pmis_report_subscription.recipients IS '接收人邮箱，逗号分隔';
COMMENT ON COLUMN pmis_report_subscription.enabled IS '是否启用 (1=启用, 0=停用)';

CREATE INDEX idx_report_subscriber ON pmis_report_subscription (subscriber_id);
CREATE INDEX idx_report_type_freq ON pmis_report_subscription (report_type, frequency) WHERE deleted = 0;

-- 报表导出记录表
CREATE TABLE IF NOT EXISTS pmis_report_export_record (
    id              BIGSERIAL    PRIMARY KEY,
    subscription_id BIGINT,
    report_type     VARCHAR(50)  NOT NULL,
    file_key        VARCHAR(500),
    file_url        VARCHAR(1000),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    error_message   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP
);

COMMENT ON TABLE pmis_report_export_record IS '报表导出记录表';
COMMENT ON COLUMN pmis_report_export_record.subscription_id IS '关联订阅ID';
COMMENT ON COLUMN pmis_report_export_record.report_type IS '报表类型';
COMMENT ON COLUMN pmis_report_export_record.file_key IS 'MinIO 文件 key';
COMMENT ON COLUMN pmis_report_export_record.file_url IS '下载 URL';
COMMENT ON COLUMN pmis_report_export_record.status IS '状态 (PENDING/GENERATING/SENT/FAILED)';
COMMENT ON COLUMN pmis_report_export_record.error_message IS '错误信息';

CREATE INDEX idx_report_export_status ON pmis_report_export_record (status) WHERE completed_at IS NULL;
