-- =====================================================
-- PMIS 批次12 DDL：考勤管理(出勤/加班/请假)
-- 版本: V1.0.0_015
-- 描述: 出勤(pmis_attendance) + 加班(pmis_overtime) + 请假(pmis_leave)
-- =====================================================

-- =====================================================
-- 1. 出勤记录表 pmis_attendance
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_attendance (
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
COMMENT ON TABLE  pmis_attendance IS '员工出勤记录表: 每日打卡 + 工作时长统计,支撑项目工时分配';
COMMENT ON COLUMN pmis_attendance.employee_id IS '员工 ID';
COMMENT ON COLUMN pmis_attendance.employee_name IS '员工姓名（冗余）';
COMMENT ON COLUMN pmis_attendance.attendance_date IS '出勤日期';
COMMENT ON COLUMN pmis_attendance.check_in_time IS '上班打卡时间';
COMMENT ON COLUMN pmis_attendance.check_out_time IS '下班打卡时间';
COMMENT ON COLUMN pmis_attendance.work_hours IS '工作时长(小时)';
COMMENT ON COLUMN pmis_attendance.overtime_hours IS '加班时长(小时)';
COMMENT ON COLUMN pmis_attendance.status IS '出勤状态: NORMAL 正常 / LATE 迟到 / EARLY 早退 / ABSENT 缺勤 / LEAVE 请假 / OVERTIME 加班';
COMMENT ON COLUMN pmis_attendance.work_type IS '日期类型: WORKDAY 工作日 / WEEKEND 周末 / HOLIDAY 节假日';
COMMENT ON COLUMN pmis_attendance.remark IS '备注';
COMMENT ON COLUMN pmis_attendance.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_attendance.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_attendance.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX idx_pa_emp ON pmis_attendance(employee_id);
CREATE INDEX idx_pa_date ON pmis_attendance(attendance_date);
CREATE INDEX idx_pa_status ON pmis_attendance(status);

-- =====================================================
-- 2. 加班申请表 pmis_overtime
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_overtime (
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
COMMENT ON TABLE  pmis_overtime IS '加班申请表: WORKDAY 1.5x / WEEKEND 2.0x / HOLIDAY 3.0x 法定倍数';
COMMENT ON COLUMN pmis_overtime.overtime_code IS '加班单号: 业务唯一,如 OT-2026-001';
COMMENT ON COLUMN pmis_overtime.employee_id IS '员工 ID';
COMMENT ON COLUMN pmis_overtime.employee_name IS '员工姓名（冗余）';
COMMENT ON COLUMN pmis_overtime.overtime_date IS '加班日期';
COMMENT ON COLUMN pmis_overtime.start_time IS '加班开始时间';
COMMENT ON COLUMN pmis_overtime.end_time IS '加班结束时间';
COMMENT ON COLUMN pmis_overtime.overtime_hours IS '加班时长(小时)';
COMMENT ON COLUMN pmis_overtime.overtime_type IS '加班类型: WORKDAY 工作日 / WEEKEND 周末 / HOLIDAY 节假日';
COMMENT ON COLUMN pmis_overtime.pay_rate IS '加班倍数: 1.5/2.0/3.0 倍,用于薪资计算';
COMMENT ON COLUMN pmis_overtime.reason IS '加班原因';
COMMENT ON COLUMN pmis_overtime.approval_id IS '审批流实例 ID: 关联工作流引擎';
COMMENT ON COLUMN pmis_overtime.approval_status IS '审批状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回 / CANCELLED 已取消';
COMMENT ON COLUMN pmis_overtime.approver_id IS '审批人 ID';
COMMENT ON COLUMN pmis_overtime.approver_name IS '审批人姓名（冗余）';
COMMENT ON COLUMN pmis_overtime.approval_time IS '审批时间';
COMMENT ON COLUMN pmis_overtime.approval_remark IS '审批意见';
COMMENT ON COLUMN pmis_overtime.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_overtime.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_overtime.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX idx_pot_emp ON pmis_overtime(employee_id);
CREATE INDEX idx_pot_date ON pmis_overtime(overtime_date);
CREATE INDEX idx_pot_status ON pmis_overtime(approval_status);

-- =====================================================
-- 3. 请假申请表 pmis_leave
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_leave (
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
COMMENT ON TABLE  pmis_leave IS '请假申请表: 7 种假期类型,自动算 leave_days';
COMMENT ON COLUMN pmis_leave.leave_code IS '请假单号: 业务唯一,如 LV-2026-001';
COMMENT ON COLUMN pmis_leave.employee_id IS '员工 ID';
COMMENT ON COLUMN pmis_leave.employee_name IS '员工姓名（冗余）';
COMMENT ON COLUMN pmis_leave.leave_type IS '假期类型: ANNUAL 年假 / SICK 病假 / PERSONAL 事假 / MARRIAGE 婚假 / MATERNITY 产假 / BEREAVEMENT 丧假 / OTHER 其他';
COMMENT ON COLUMN pmis_leave.start_date IS '请假开始日期';
COMMENT ON COLUMN pmis_leave.end_date IS '请假结束日期';
COMMENT ON COLUMN pmis_leave.leave_days IS '请假天数(天)';
COMMENT ON COLUMN pmis_leave.reason IS '请假原因';
COMMENT ON COLUMN pmis_leave.attachment_url IS '证明附件 URL: 病假条/结婚证等';
COMMENT ON COLUMN pmis_leave.approval_id IS '审批流实例 ID';
COMMENT ON COLUMN pmis_leave.approval_status IS '审批状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回 / CANCELLED 已取消';
COMMENT ON COLUMN pmis_leave.approver_id IS '审批人 ID';
COMMENT ON COLUMN pmis_leave.approver_name IS '审批人姓名（冗余）';
COMMENT ON COLUMN pmis_leave.approval_time IS '审批时间';
COMMENT ON COLUMN pmis_leave.approval_remark IS '审批意见';
COMMENT ON COLUMN pmis_leave.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_leave.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_leave.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX idx_pl_emp ON pmis_leave(employee_id);
CREATE INDEX idx_pl_date ON pmis_leave(start_date, end_date);
CREATE INDEX idx_pl_type ON pmis_leave(leave_type);
CREATE INDEX idx_pl_status ON pmis_leave(approval_status);
