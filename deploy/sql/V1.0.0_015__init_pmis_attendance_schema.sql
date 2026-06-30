-- =====================================================
-- PMIS 批次12 DDL：考勤管理(出勤/加班/请假)
-- 版本: V1.0.0_015
-- 描述: 出勤(pmis_attendance) + 加班(pmis_overtime) + 请假(pmis_leave)
-- Schema: pmis
-- =====================================================

-- =====================================================
-- 1. 出勤记录表 pmis_attendance
-- =====================================================
DROP TABLE IF EXISTS pmis.pmis_attendance;
CREATE TABLE pmis.pmis_attendance (
    id                  BIGSERIAL PRIMARY KEY,
    employee_id         BIGINT       NOT NULL,
    employee_name       VARCHAR(64),
    attendance_date     DATE         NOT NULL,
    check_in_time       TIMESTAMP,
    check_out_time      TIMESTAMP,
    work_hours          NUMERIC(5,2) NOT NULL DEFAULT 0.0,
    overtime_hours      NUMERIC(5,2) NOT NULL DEFAULT 0.0,
    status              VARCHAR(32)  NOT NULL DEFAULT 'NORMAL',  -- NORMAL/LATE/EARLY/ABSENT/LEAVE/OVERTIME
    work_type           VARCHAR(16)  NOT NULL DEFAULT 'WORKDAY',  -- WORKDAY/WEEKEND/HOLIDAY
    remark              TEXT,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pa_emp_date UNIQUE (employee_id, attendance_date, deleted)
);
COMMENT ON TABLE pmis.pmis_attendance IS '员工出勤记录';
CREATE INDEX idx_pa_emp ON pmis.pmis_attendance(employee_id);
CREATE INDEX idx_pa_date ON pmis.pmis_attendance(attendance_date);
CREATE INDEX idx_pa_status ON pmis.pmis_attendance(status);

-- =====================================================
-- 2. 加班申请表 pmis_overtime
-- =====================================================
DROP TABLE IF EXISTS pmis.pmis_overtime;
CREATE TABLE pmis.pmis_overtime (
    id                  BIGSERIAL PRIMARY KEY,
    overtime_code       VARCHAR(64)  NOT NULL,
    employee_id         BIGINT       NOT NULL,
    employee_name       VARCHAR(64),
    overtime_date       DATE         NOT NULL,
    start_time          TIMESTAMP    NOT NULL,
    end_time            TIMESTAMP    NOT NULL,
    overtime_hours      NUMERIC(5,2) NOT NULL,
    overtime_type       VARCHAR(32)  NOT NULL,                   -- WORKDAY/WEEKEND/HOLIDAY
    pay_rate            NUMERIC(5,2) NOT NULL DEFAULT 1.5,       -- 1.5/2.0/3.0 倍
    reason              TEXT,
    approval_id         BIGINT,
    approval_status     VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED
    approver_id         BIGINT,
    approver_name       VARCHAR(64),
    approval_time       TIMESTAMP,
    approval_remark     TEXT,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pot_code UNIQUE (overtime_code, deleted)
);
COMMENT ON TABLE pmis.pmis_overtime IS '加班申请记录';
CREATE INDEX idx_pot_emp ON pmis.pmis_overtime(employee_id);
CREATE INDEX idx_pot_date ON pmis.pmis_overtime(overtime_date);
CREATE INDEX idx_pot_status ON pmis.pmis_overtime(approval_status);

-- =====================================================
-- 3. 请假申请表 pmis_leave
-- =====================================================
DROP TABLE IF EXISTS pmis.pmis_leave;
CREATE TABLE pmis.pmis_leave (
    id                  BIGSERIAL PRIMARY KEY,
    leave_code          VARCHAR(64)  NOT NULL,
    employee_id         BIGINT       NOT NULL,
    employee_name       VARCHAR(64),
    leave_type          VARCHAR(32)  NOT NULL,                   -- ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/BEREAVEMENT/OTHER
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    leave_days          NUMERIC(5,2) NOT NULL,
    reason              TEXT,
    attachment_url      VARCHAR(512),
    approval_id         BIGINT,
    approval_status     VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED
    approver_id         BIGINT,
    approver_name       VARCHAR(64),
    approval_time       TIMESTAMP,
    approval_remark     TEXT,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pl_code UNIQUE (leave_code, deleted)
);
COMMENT ON TABLE pmis.pmis_leave IS '请假申请记录';
CREATE INDEX idx_pl_emp ON pmis.pmis_leave(employee_id);
CREATE INDEX idx_pl_date ON pmis.pmis_leave(start_date, end_date);
CREATE INDEX idx_pl_type ON pmis.pmis_leave(leave_type);
CREATE INDEX idx_pl_status ON pmis.pmis_leave(approval_status);
