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
    notice_days         INT          NOT NULL DEFAULT 30
        COMMENT '到期提前提醒天数',
    notice_sent         BOOLEAN      NOT NULL DEFAULT FALSE,
    notice_sent_at      TIMESTAMP,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE/EXPIRING_SOON/EXPIRED/TERMINATED',
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
COMMENT ON TABLE pmis_warranty IS '项目质保期（结项后自动创建，到期前 N 天提醒）';

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
    category            VARCHAR(32)  NOT NULL DEFAULT 'OTHER'
        COMMENT 'BUG/DATA/CONFIG/PROCESS/OTHER',
    priority            VARCHAR(8)   NOT NULL DEFAULT 'P3'
        COMMENT 'P1/P2/P3/P4',
    status              VARCHAR(16)  NOT NULL DEFAULT 'OPEN'
        COMMENT 'OPEN/ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED/CANCELLED',
    reporter_id         BIGINT,
    reporter_name       VARCHAR(64),
    reporter_phone      VARCHAR(32),
    assignee_id         BIGINT,
    assignee_name       VARCHAR(64),
    accepted_at         TIMESTAMP,
    started_at          TIMESTAMP,
    resolved_at         TIMESTAMP,
    closed_at           TIMESTAMP,
    response_due_at     TIMESTAMP    NOT NULL
        COMMENT '首次响应 SLA 截止时间',
    resolve_due_at      TIMESTAMP    NOT NULL
        COMMENT '解决 SLA 截止时间',
    response_breached   BOOLEAN      NOT NULL DEFAULT FALSE,
    resolve_breached    BOOLEAN      NOT NULL DEFAULT FALSE,
    resolution_note     TEXT,
    customer_score      INT
        COMMENT '1-5 星',
    customer_comment    VARCHAR(512),
    file_ids            VARCHAR(1024)
        COMMENT '附件 ID 列表（逗号分隔）',
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64),
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_ops_ticket IS '运维工单（项目售后全流程闭环：P1-P4 SLA 跟踪）';

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
    score               INT          NOT NULL
        COMMENT '1-5 星',
    level               VARCHAR(16)  NOT NULL
        COMMENT 'VERY_SATISFIED/SATISFIED/NEUTRAL/DISSATISFIED/VERY_DISSATISFIED',
    professionalism     INT
        COMMENT '专业度 1-5',
    timeliness          INT
        COMMENT '及时性 1-5',
    quality             INT
        COMMENT '质量 1-5',
    attitude            INT
        COMMENT '服务态度 1-5',
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
COMMENT ON TABLE pmis_satisfaction IS '服务满意度评价（工单关闭/质保期结束可触发）';

CREATE INDEX IF NOT EXISTS idx_satisfaction_initiation ON pmis_satisfaction(initiation_id, deleted);
CREATE INDEX IF NOT EXISTS idx_satisfaction_ticket ON pmis_satisfaction(ticket_id, deleted);
CREATE INDEX IF NOT EXISTS idx_satisfaction_level ON pmis_satisfaction(level, deleted);
CREATE INDEX IF NOT EXISTS idx_satisfaction_code ON pmis_satisfaction(survey_code);
