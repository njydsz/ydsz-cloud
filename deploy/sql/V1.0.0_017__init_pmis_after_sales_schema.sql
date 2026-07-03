-- ============================================================
-- V1.0.0_017  项目售后管理  脚本
-- ============================================================
-- 说明：批次 14 项目售后管理（PRD 3.8）
-- 1) 质保期：pmis_warranty
-- 2) 运维工单：pmis_ops_ticket
-- 3) 满意度评价：pmis_satisfaction
-- ============================================================

-- ----------------------------
-- 1) 质保期
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_warranty (
    id                  BIGSERIAL PRIMARY KEY,
    warranty_code       VARCHAR(64)  NOT NULL UNIQUE,
    initiation_id       BIGINT       NOT NULL,
    contract_id         BIGINT,
    project_type        VARCHAR(32),
    project_level       VARCHAR(8),
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    duration_months     INT          NOT NULL DEFAULT 12,
    notice_days         INT          NOT NULL DEFAULT 30,
    notice_sent         BOOLEAN      NOT NULL DEFAULT FALSE,
    notice_sent_at      TIMESTAMP,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    terminated_at       TIMESTAMP,
    terminated_reason   VARCHAR(256),
    contact_name        VARCHAR(64),
    contact_phone       VARCHAR(32),
    remark              VARCHAR(512),
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64),
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE  pmis_warranty IS '项目质保期表: 项目结项后自动创建,到期前 N 天提醒客户,status 状态机 ACTIVE/EXPIRING/EXPIRED/TERMINATED';
COMMENT ON COLUMN pmis_warranty.warranty_code IS '质保单号: 业务唯一,如 WAR-2026-001';
COMMENT ON COLUMN pmis_warranty.initiation_id IS '所属立项 ID';
COMMENT ON COLUMN pmis_warranty.contract_id IS '所属合同 ID';
COMMENT ON COLUMN pmis_warranty.project_type IS '项目类型: 冗余字段';
COMMENT ON COLUMN pmis_warranty.project_level IS '项目级别: L1-L18,冗余字段';
COMMENT ON COLUMN pmis_warranty.start_date IS '质保开始日期';
COMMENT ON COLUMN pmis_warranty.end_date IS '质保结束日期';
COMMENT ON COLUMN pmis_warranty.duration_months IS '质保期时长(月): 默认 12';
COMMENT ON COLUMN pmis_warranty.notice_days IS '提前提醒天数: 到期前 N 天发通知,默认 30';
COMMENT ON COLUMN pmis_warranty.notice_sent IS '是否已发送到期提醒: true=已发';
COMMENT ON COLUMN pmis_warranty.notice_sent_at IS '提醒发送时间';
COMMENT ON COLUMN pmis_warranty.status IS '状态: ACTIVE 生效中 / EXPIRING 即将到期 / EXPIRED 已到期 / TERMINATED 已终止';
COMMENT ON COLUMN pmis_warranty.terminated_at IS '终止时间';
COMMENT ON COLUMN pmis_warranty.terminated_reason IS '终止原因';
COMMENT ON COLUMN pmis_warranty.contact_name IS '客户联系人姓名';
COMMENT ON COLUMN pmis_warranty.contact_phone IS '客户联系人电话';
COMMENT ON COLUMN pmis_warranty.remark IS '备注';
COMMENT ON COLUMN pmis_warranty.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_warranty.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_warranty.deleted IS '逻辑删除: 0=未删除,1=已删除';

CREATE INDEX IF NOT EXISTS idx_warranty_initiation ON pmis_warranty(initiation_id, deleted);
CREATE INDEX IF NOT EXISTS idx_warranty_status_end ON pmis_warranty(status, end_date, deleted);
CREATE INDEX IF NOT EXISTS idx_warranty_code ON pmis_warranty(warranty_code);

-- ----------------------------
-- 2) 运维工单
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_ops_ticket (
    id                  BIGSERIAL PRIMARY KEY,
    ticket_code         VARCHAR(64)  NOT NULL UNIQUE,
    initiation_id       BIGINT       NOT NULL,
    warranty_id         BIGINT,
    title               VARCHAR(128) NOT NULL,
    description         TEXT,
    category            VARCHAR(32)  NOT NULL DEFAULT 'OTHER',
    priority            VARCHAR(8)   NOT NULL DEFAULT 'P3',
    status              VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    reporter_id         BIGINT,
    reporter_name       VARCHAR(64),
    reporter_phone      VARCHAR(32),
    assignee_id         BIGINT,
    assignee_name       VARCHAR(64),
    accepted_at         TIMESTAMP,
    started_at          TIMESTAMP,
    resolved_at         TIMESTAMP,
    closed_at           TIMESTAMP,
    response_due_at     TIMESTAMP    NOT NULL,
    resolve_due_at      TIMESTAMP    NOT NULL,
    response_breached   BOOLEAN      NOT NULL DEFAULT FALSE,
    resolve_breached    BOOLEAN      NOT NULL DEFAULT FALSE,
    resolution_note     TEXT,
    customer_score      INT,
    customer_comment    VARCHAR(512),
    file_ids            VARCHAR(1024),
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64),
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE  pmis_ops_ticket IS '运维工单表: 项目售后全流程闭环,SLA 跟踪（P1 4h/P2 1d/P3 3d/P4 7d）';
COMMENT ON COLUMN pmis_ops_ticket.ticket_code IS '工单号: 业务唯一,如 TK-2026-001';
COMMENT ON COLUMN pmis_ops_ticket.initiation_id IS '所属立项 ID';
COMMENT ON COLUMN pmis_ops_ticket.warranty_id IS '所属质保期 ID';
COMMENT ON COLUMN pmis_ops_ticket.title IS '工单标题';
COMMENT ON COLUMN pmis_ops_ticket.description IS '问题描述';
COMMENT ON COLUMN pmis_ops_ticket.category IS '工单类别: BUG 缺陷 / CHANGE 变更 / CONSULT 咨询 / COMPLAINT 投诉 / OTHER 其他';
COMMENT ON COLUMN pmis_ops_ticket.priority IS '优先级: P1 紧急 / P2 高 / P3 中 / P4 低';
COMMENT ON COLUMN pmis_ops_ticket.status IS '工单状态: OPEN 待受理 / ACCEPTED 已受理 / IN_PROGRESS 处理中 / RESOLVED 已解决 / CLOSED 已关闭 / CANCELLED 已取消';
COMMENT ON COLUMN pmis_ops_ticket.reporter_id IS '报修人 ID';
COMMENT ON COLUMN pmis_ops_ticket.reporter_name IS '报修人姓名（冗余）';
COMMENT ON COLUMN pmis_ops_ticket.reporter_phone IS '报修人电话';
COMMENT ON COLUMN pmis_ops_ticket.assignee_id IS '处理人 ID';
COMMENT ON COLUMN pmis_ops_ticket.assignee_name IS '处理人姓名（冗余）';
COMMENT ON COLUMN pmis_ops_ticket.accepted_at IS '受理时间';
COMMENT ON COLUMN pmis_ops_ticket.started_at IS '开始处理时间';
COMMENT ON COLUMN pmis_ops_ticket.resolved_at IS '解决时间';
COMMENT ON COLUMN pmis_ops_ticket.closed_at IS '关闭时间';
COMMENT ON COLUMN pmis_ops_ticket.response_due_at IS '响应 SLA 截止时间';
COMMENT ON COLUMN pmis_ops_ticket.resolve_due_at IS '解决 SLA 截止时间';
COMMENT ON COLUMN pmis_ops_ticket.response_breached IS '响应 SLA 是否超期: true=超期';
COMMENT ON COLUMN pmis_ops_ticket.resolve_breached IS '解决 SLA 是否超期: true=超期';
COMMENT ON COLUMN pmis_ops_ticket.resolution_note IS '解决方案说明';
COMMENT ON COLUMN pmis_ops_ticket.customer_score IS '客户评分: 1-5';
COMMENT ON COLUMN pmis_ops_ticket.customer_comment IS '客户评价';
COMMENT ON COLUMN pmis_ops_ticket.file_ids IS '附件 ID 列表: 引用 pmis_file_metadata.id';
COMMENT ON COLUMN pmis_ops_ticket.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_ops_ticket.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_ops_ticket.deleted IS '逻辑删除: 0=未删除,1=已删除';

CREATE INDEX IF NOT EXISTS idx_ops_initiation ON pmis_ops_ticket(initiation_id, deleted);
CREATE INDEX IF NOT EXISTS idx_ops_warranty ON pmis_ops_ticket(warranty_id, deleted);
CREATE INDEX IF NOT EXISTS idx_ops_status ON pmis_ops_ticket(status, deleted);
CREATE INDEX IF NOT EXISTS idx_ops_assignee_status ON pmis_ops_ticket(assignee_id, status, deleted);
CREATE INDEX IF NOT EXISTS idx_ops_priority ON pmis_ops_ticket(priority, status, deleted);
CREATE INDEX IF NOT EXISTS idx_ops_code ON pmis_ops_ticket(ticket_code);

-- ----------------------------
-- 3) 满意度评价
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_satisfaction (
    id                  BIGSERIAL PRIMARY KEY,
    survey_code         VARCHAR(64)  NOT NULL UNIQUE,
    initiation_id       BIGINT       NOT NULL,
    ticket_id           BIGINT,
    warranty_id         BIGINT,
    score               INT          NOT NULL,
    level               VARCHAR(16)  NOT NULL,
    professionalism     INT,
    timeliness          INT,
    quality             INT,
    attitude            INT,
    comments            VARCHAR(1024),
    suggest             VARCHAR(1024),
    anonymous           BOOLEAN      NOT NULL DEFAULT FALSE,
    evaluator_id        BIGINT,
    evaluator_name      VARCHAR(64),
    evaluated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    follow_up           BOOLEAN      NOT NULL DEFAULT FALSE,
    follow_up_note      VARCHAR(512),
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64),
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE  pmis_satisfaction IS '服务满意度评价表: 工单关闭/质保期结束可触发,4 维度评分（专业/及时/质量/态度）';
COMMENT ON COLUMN pmis_satisfaction.survey_code IS '评价单号: 业务唯一,如 SAT-2026-001';
COMMENT ON COLUMN pmis_satisfaction.initiation_id IS '所属立项 ID';
COMMENT ON COLUMN pmis_satisfaction.ticket_id IS '关联工单 ID: 工单触发的评价';
COMMENT ON COLUMN pmis_satisfaction.warranty_id IS '关联质保期 ID: 质保期触发的评价';
COMMENT ON COLUMN pmis_satisfaction.score IS '总评分: 1-5';
COMMENT ON COLUMN pmis_satisfaction.level IS '评价等级: VERY_SATISFIED 非常满意 / SATISFIED 满意 / NEUTRAL 一般 / UNSATISFIED 不满意';
COMMENT ON COLUMN pmis_satisfaction.professionalism IS '专业度评分: 1-5';
COMMENT ON COLUMN pmis_satisfaction.timeliness IS '及时性评分: 1-5';
COMMENT ON COLUMN pmis_satisfaction.quality IS '质量评分: 1-5';
COMMENT ON COLUMN pmis_satisfaction.attitude IS '态度评分: 1-5';
COMMENT ON COLUMN pmis_satisfaction.comments IS '评价内容';
COMMENT ON COLUMN pmis_satisfaction.suggest IS '改进建议';
COMMENT ON COLUMN pmis_satisfaction.anonymous IS '是否匿名: true=匿名';
COMMENT ON COLUMN pmis_satisfaction.evaluator_id IS '评价人 ID';
COMMENT ON COLUMN pmis_satisfaction.evaluator_name IS '评价人姓名（冗余）';
COMMENT ON COLUMN pmis_satisfaction.evaluated_at IS '评价时间';
COMMENT ON COLUMN pmis_satisfaction.follow_up IS '是否需要回访: true=需要';
COMMENT ON COLUMN pmis_satisfaction.follow_up_note IS '回访记录';
COMMENT ON COLUMN pmis_satisfaction.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_satisfaction.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_satisfaction.deleted IS '逻辑删除: 0=未删除,1=已删除';

CREATE INDEX IF NOT EXISTS idx_satisfaction_initiation ON pmis_satisfaction(initiation_id, deleted);
CREATE INDEX IF NOT EXISTS idx_satisfaction_ticket ON pmis_satisfaction(ticket_id, deleted);
CREATE INDEX IF NOT EXISTS idx_satisfaction_level ON pmis_satisfaction(level, deleted);
CREATE INDEX IF NOT EXISTS idx_satisfaction_code ON pmis_satisfaction(survey_code);
