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
    provider_trace_id VARCHAR(128),
    cost_ms         BIGINT,
    trace_id        VARCHAR(64),
    tenant_id       BIGINT       DEFAULT 1,
    create_by       BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_message_log IS '消息发送日志: 短信/邮件/推送/站内信发送全量记录,支持按业务/接收人查询';
COMMENT ON COLUMN pmis_message_log.id IS '主键 ID';
COMMENT ON COLUMN pmis_message_log.channel IS '发送通道: SMS 短信 / EMAIL 邮件 / PUSH 移动推送 / IN_APP 站内信 / WEBHOOK 企业微信/钉钉机器人';
COMMENT ON COLUMN pmis_message_log.biz_type IS '业务类型(如 alert/notice/verify_code)';
COMMENT ON COLUMN pmis_message_log.biz_id IS '业务单据 ID';
COMMENT ON COLUMN pmis_message_log.receiver IS '接收人(手机号/邮箱/设备号/user_id)';
COMMENT ON COLUMN pmis_message_log.template_code IS '消息模板编码(关联 pmis_message_template)';
COMMENT ON COLUMN pmis_message_log.template_params IS '模板参数 JSON(实际渲染值)';
COMMENT ON COLUMN pmis_message_log.content IS '发送内容(最终渲染后的文本)';
COMMENT ON COLUMN pmis_message_log.status IS '发送状态: PENDING 待发送 / SUCCESS 成功 / FAILED 失败 / RETRY 重试中';
COMMENT ON COLUMN pmis_message_log.error_message IS '失败原因';
COMMENT ON COLUMN pmis_message_log.provider_trace_id IS '三方服务商回执 ID(阿里云/腾讯云返回的流水号)';
COMMENT ON COLUMN pmis_message_log.cost_ms IS '发送耗时(毫秒)';
COMMENT ON COLUMN pmis_message_log.trace_id IS '系统链路追踪 ID';
COMMENT ON COLUMN pmis_message_log.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_message_log.create_by IS '创建人 ID(系统发送为 0)';
COMMENT ON COLUMN pmis_message_log.create_time IS '发送时间';
COMMENT ON COLUMN pmis_message_log.update_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_message_log.update_time IS '最后修改时间';
COMMENT ON COLUMN pmis_message_log.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

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

COMMENT ON TABLE pmis_message_template IS '消息模板表: 短信/邮件/推送/站内信模板,支持 ${var} 占位符嵌套替换';
COMMENT ON COLUMN pmis_message_template.id IS '主键 ID';
COMMENT ON COLUMN pmis_message_template.template_code IS '模板编码(全局唯一,如 ALERT_BUDGET_YELLOW)';
COMMENT ON COLUMN pmis_message_template.channel IS '通道: SMS 短信 / EMAIL 邮件 / PUSH 移动推送 / IN_APP 站内信 / WEBHOOK';
COMMENT ON COLUMN pmis_message_template.subject IS '主题(邮件专属 Subject)';
COMMENT ON COLUMN pmis_message_template.content IS '模板内容(支持 ${var} 占位符,可嵌套)';
COMMENT ON COLUMN pmis_message_template.provider IS '三方服务商(阿里云/腾讯云/极光/SendCloud)';
COMMENT ON COLUMN pmis_message_template.provider_key IS '服务商侧模板 ID';
COMMENT ON COLUMN pmis_message_template.sign_name IS '签名(如"PMIS"出现在短信/邮件落款)';
COMMENT ON COLUMN pmis_message_template.status IS '启用状态: ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_message_template.description IS '模板说明';
COMMENT ON COLUMN pmis_message_template.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_message_template.create_by IS '创建人 ID';
COMMENT ON COLUMN pmis_message_template.create_time IS '创建时间';
COMMENT ON COLUMN pmis_message_template.update_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_message_template.update_time IS '最后修改时间';
COMMENT ON COLUMN pmis_message_template.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE UNIQUE INDEX uk_pmt_code_channel ON pmis_message_template(template_code, channel, tenant_id);
CREATE INDEX idx_pmt_channel ON pmis_message_template(channel);
CREATE INDEX idx_pmt_status ON pmis_message_template(status);
