-- =====================================================
-- PMIS 消息通道模块 DDL
-- 版本: V1.0.0_007
-- 描述: 短信/邮件/推送发送日志
-- =====================================================

DROP TABLE IF EXISTS pmis_message_log;
CREATE TABLE pmis_message_log (
    id              BIGSERIAL PRIMARY KEY,
    channel         VARCHAR(32)  NOT NULL,
    biz_type        VARCHAR(64),
    biz_id          VARCHAR(64),
    receiver        VARCHAR(256) NOT NULL,
    template_code   VARCHAR(128),
    template_params TEXT,
    content         TEXT,
    status          VARCHAR(32)  NOT NULL,
    error_message   TEXT,
    cost_ms         BIGINT,
    trace_id        VARCHAR(64),
    tenant_id       BIGINT       DEFAULT 1,
    create_by       BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_message_log IS '消息发送日志';
COMMENT ON COLUMN pmis_message_log.channel IS '通道: SMS/EMAIL/PUSH';
COMMENT ON COLUMN pmis_message_log.status IS '状态: SUCCESS/FAILED/PENDING';

CREATE INDEX idx_pml_channel ON pmis_message_log(channel);
CREATE INDEX idx_pml_status ON pmis_message_log(status);
CREATE INDEX idx_pml_biz ON pmis_message_log(biz_type, biz_id);
CREATE INDEX idx_pml_receiver ON pmis_message_log(receiver);
CREATE INDEX idx_pml_tenant ON pmis_message_log(tenant_id);

DROP TABLE IF EXISTS pmis_message_template;
CREATE TABLE pmis_message_template (
    id              BIGSERIAL PRIMARY KEY,
    template_code   VARCHAR(128) NOT NULL,
    channel         VARCHAR(32)  NOT NULL,
    subject         VARCHAR(256),
    content         TEXT         NOT NULL,
    provider        VARCHAR(64),
    provider_key    VARCHAR(128),
    sign_name       VARCHAR(64),
    status          VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    description     VARCHAR(512),
    tenant_id       BIGINT       DEFAULT 1,
    create_by       BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_message_template IS '消息模板';
COMMENT ON COLUMN pmis_message_template.channel IS '通道: SMS/EMAIL/PUSH';

CREATE UNIQUE INDEX uk_pmt_code_channel ON pmis_message_template(template_code, channel, tenant_id);
CREATE INDEX idx_pmt_channel ON pmis_message_template(channel);
CREATE INDEX idx_pmt_status ON pmis_message_template(status);
