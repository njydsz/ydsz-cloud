-- ============================================================
-- PMIS userinfo module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================

-- ====================================================================
-- 2. RBAC 权限模块
-- ====================================================================

-- 角色表
CREATE TABLE IF NOT EXISTS pmis_role(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    role_code       VARCHAR(64)    NOT NULL,
    role_name       VARCHAR(64)    NOT NULL,
    description     TEXT,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    data_scope      VARCHAR(16)    NOT NULL DEFAULT 'SELF',
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_role_code UNIQUE (role_code, deleted),
    CONSTRAINT ck_pr_status_enum    CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pr_data_scope     CHECK (data_scope IN ('ALL', 'DEPT', 'DEPT_AND_CHILD', 'SELF', 'CUSTOM')),
    CONSTRAINT ck_pr_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_role IS '角色表: RBAC 角色定义,关联权限与数据范围';

COMMENT ON COLUMN pmis_role.id IS '主键 ID';

COMMENT ON COLUMN pmis_role.role_code IS '角色编码(全局唯一,如 SUPER_ADMIN/PM)';

COMMENT ON COLUMN pmis_role.role_name IS '角色名称(中文展示名)';

COMMENT ON COLUMN pmis_role.description IS '角色业务说明(职责、适用场景)';

COMMENT ON COLUMN pmis_role.sort_order IS '角色排序号(升序)';

COMMENT ON COLUMN pmis_role.data_scope IS '数据权限范围: ALL 全部 / DEPT 本部门 / DEPT_AND_CHILD 本部门及下级 / SELF 本人 / CUSTOM 自定义';

COMMENT ON COLUMN pmis_role.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN pmis_role.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_role.created_at IS '创建时间';

COMMENT ON COLUMN pmis_role.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_role.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_role.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_role.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_role_status ON pmis_role (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_role_tenant_created
    ON pmis_role (tenant_id, created_at DESC) WHERE deleted = 0;

-- 权限/菜单表
CREATE TABLE IF NOT EXISTS pmis_permission(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    parent_id       VARCHAR(20)         NOT NULL DEFAULT 0,
    perm_code       VARCHAR(128)   NOT NULL,
    perm_name       VARCHAR(64)    NOT NULL,
    perm_type       VARCHAR(16)    NOT NULL,
    path            VARCHAR(255),
    component       VARCHAR(255),
    icon            VARCHAR(64),
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    visible         SMALLINT       NOT NULL DEFAULT 1,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_permission_code UNIQUE (perm_code, deleted),
    CONSTRAINT ck_pp_perm_type    CHECK (perm_type IN ('MENU', 'BUTTON', 'API')),
    CONSTRAINT ck_pp_status_enum  CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pp_visible_enum CHECK (visible IN (0, 1)),
    CONSTRAINT ck_pp_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_permission IS '权限/菜单表: 树形结构,涵盖菜单/按钮/API 三类权限';

COMMENT ON COLUMN pmis_permission.id IS '主键 ID';

COMMENT ON COLUMN pmis_permission.parent_id IS '父级权限 ID(0=根,支持多级树)';

COMMENT ON COLUMN pmis_permission.perm_code IS '权限编码(全局唯一,格式: module:entity:action,如 project:contract:create)';

COMMENT ON COLUMN pmis_permission.perm_name IS '权限名称(中文展示名)';

COMMENT ON COLUMN pmis_permission.perm_type IS '权限类型: MENU 菜单 / BUTTON 按钮 / API 接口';

COMMENT ON COLUMN pmis_permission.path IS '前端路由路径(菜单/按钮可空)';

COMMENT ON COLUMN pmis_permission.component IS '前端组件路径(对应 views 目录)';

COMMENT ON COLUMN pmis_permission.icon IS '菜单图标(Element Plus 图标名)';

COMMENT ON COLUMN pmis_permission.sort_order IS '排序号(同级升序)';

COMMENT ON COLUMN pmis_permission.visible IS '是否显示: 1 显示 / 0 隐藏(按钮权限一般隐藏)';

COMMENT ON COLUMN pmis_permission.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN pmis_permission.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_permission.created_at IS '创建时间';

COMMENT ON COLUMN pmis_permission.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_permission.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_permission.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_permission.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_permission_parent ON pmis_permission (parent_id);

CREATE INDEX IF NOT EXISTS idx_pmis_permission_type ON pmis_permission (perm_type) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_permission_tenant ON pmis_permission(tenant_id);

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS pmis_user_role(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    user_id         VARCHAR(20)         NOT NULL,
    role_id         VARCHAR(20)         NOT NULL,
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_user_role UNIQUE (user_id, role_id, deleted),
    CONSTRAINT ck_pur_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_user_role IS '用户-角色关联表: 多对多,用户可同时拥有多个角色';

COMMENT ON COLUMN pmis_user_role.id IS '主键 ID';

COMMENT ON COLUMN pmis_user_role.user_id IS '用户 ID(关联 pmis_user_account.id)';

COMMENT ON COLUMN pmis_user_role.role_id IS '角色 ID(关联 pmis_role.id)';

COMMENT ON COLUMN pmis_user_role.created_by IS '授权人 ID';

COMMENT ON COLUMN pmis_user_role.created_at IS '授权时间';

COMMENT ON COLUMN pmis_user_role.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_user_role.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_user_role_user ON pmis_user_role (user_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_user_role_role ON pmis_user_role (role_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_user_role_tenant ON pmis_user_role(tenant_id);

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS pmis_role_permission(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    role_id         VARCHAR(20)         NOT NULL,
    permission_id   VARCHAR(20)         NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_role_permission UNIQUE (role_id, permission_id, deleted),
    CONSTRAINT ck_prp_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_role_permission IS '角色-权限关联表: 多对多,角色绑定具体可访问的权限点';

COMMENT ON COLUMN pmis_role_permission.id IS '主键 ID';

COMMENT ON COLUMN pmis_role_permission.role_id IS '角色 ID(关联 pmis_role.id)';

COMMENT ON COLUMN pmis_role_permission.permission_id IS '权限 ID(关联 pmis_permission.id)';

COMMENT ON COLUMN pmis_role_permission.created_at IS '授权时间';

COMMENT ON COLUMN pmis_role_permission.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_role_permission.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_role_permission_deleted ON pmis_role_permission(deleted);

CREATE INDEX IF NOT EXISTS idx_role_permission_tenant ON pmis_role_permission(tenant_id);

CREATE INDEX IF NOT EXISTS idx_role_permission_perm
    ON pmis_role_permission(permission_id) WHERE deleted = 0;

-- 岗位表
CREATE TABLE IF NOT EXISTS pmis_position(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    position_code   VARCHAR(64)    NOT NULL,
    position_name   VARCHAR(128)   NOT NULL,
    department_id   VARCHAR(20)         NOT NULL,
    level_code      VARCHAR(8)     NOT NULL,
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_position_code UNIQUE (position_code, deleted),
    CONSTRAINT ck_pp_status_enum  CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pp_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_position IS '岗位表: 部门下的具体岗位定义(如开发工程师/PM/HRBP)';

COMMENT ON COLUMN pmis_position.id IS '主键 ID';

COMMENT ON COLUMN pmis_position.position_code IS '岗位编码(全局唯一)';

COMMENT ON COLUMN pmis_position.position_name IS '岗位名称';

COMMENT ON COLUMN pmis_position.department_id IS '所属部门 ID(关联 pmis_department.id)';

COMMENT ON COLUMN pmis_position.level_code IS '岗位职级(关联 pmis_job_level.level_code)';

COMMENT ON COLUMN pmis_position.description IS '岗位职责说明';

COMMENT ON COLUMN pmis_position.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN pmis_position.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_position.created_at IS '创建时间';

COMMENT ON COLUMN pmis_position.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_position.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_position.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_position.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_position_dept ON pmis_position (department_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_position_tenant ON pmis_position(tenant_id);

CREATE INDEX IF NOT EXISTS idx_position_tenant_created
    ON pmis_position(tenant_id, created_at DESC) WHERE deleted = 0;

-- 员工表
CREATE TABLE IF NOT EXISTS pmis_employee(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    user_id         VARCHAR(20)         NOT NULL,
    emp_code        VARCHAR(64)    NOT NULL,
    emp_name        VARCHAR(64)    NOT NULL,
    id_card         VARCHAR(32),
    id_card_enc     VARCHAR(255),
    gender          VARCHAR(8)     NOT NULL DEFAULT 'U',
    birth_date      DATE,
    phone           VARCHAR(32),
    phone_enc       VARCHAR(255),
    email           VARCHAR(128),
    department_id   VARCHAR(20)         NOT NULL,
    position_id     VARCHAR(20),
    level_code      VARCHAR(8)     NOT NULL,
employee_type   VARCHAR(16)    NOT NULL DEFAULT 'FULL_TIME',
part_time_rate_id VARCHAR(20),
outsource_rate_id VARCHAR(20),
    hire_date       DATE           NOT NULL,
    leave_date      DATE,
    work_status     VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
    bench_status    VARCHAR(16)    NOT NULL DEFAULT 'NO',
    bench_start     DATE,
    avatar          VARCHAR(255),
    address         VARCHAR(255),
    emergency_contact VARCHAR(64),
    emergency_phone VARCHAR(32),
    description     TEXT,
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_emp_code UNIQUE (emp_code, deleted),
    CONSTRAINT ck_pe_gender_enum      CHECK (gender IN ('M', 'F', 'U')),
    CONSTRAINT ck_pe_employee_type   CHECK (employee_type IN ('FULL_TIME', 'PART_TIME', 'OUTSOURCE')),
    CONSTRAINT ck_pe_work_status     CHECK (work_status IN ('ACTIVE', 'LEAVE', 'SUSPEND', 'PROBATION')),
    CONSTRAINT ck_pe_bench_status     CHECK (bench_status IN ('YES', 'NO', 'TRAINING')),
    CONSTRAINT ck_pe_dates_valid      CHECK (leave_date IS NULL OR leave_date >= hire_date),
    CONSTRAINT ck_pe_deleted_enum     CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_employee IS '员工表: 员工主数据,关联用户账号/部门/岗位/职级,敏感字段加密存储';

COMMENT ON COLUMN pmis_employee.id IS '主键 ID';

COMMENT ON COLUMN pmis_employee.user_id IS '关联用户账号 ID(关联 pmis_user_account.id)';

COMMENT ON COLUMN pmis_employee.emp_code IS '工号(全局唯一,如 E20260001)';

COMMENT ON COLUMN pmis_employee.emp_name IS '员工姓名';

COMMENT ON COLUMN pmis_employee.id_card IS '身份证号(脱敏显示,完整数据见 id_card_enc)';

COMMENT ON COLUMN pmis_employee.id_card_enc IS '身份证号 SM4 加密密文';

COMMENT ON COLUMN pmis_employee.gender IS '性别: M 男 / F 女 / U 未知';

COMMENT ON COLUMN pmis_employee.birth_date IS '出生日期';

COMMENT ON COLUMN pmis_employee.phone IS '手机号(脱敏显示,完整数据见 phone_enc)';

COMMENT ON COLUMN pmis_employee.phone_enc IS '手机号 SM4 加密密文';

COMMENT ON COLUMN pmis_employee.email IS '企业邮箱';

COMMENT ON COLUMN pmis_employee.department_id IS '所属部门 ID(关联 pmis_department.id)';

COMMENT ON COLUMN pmis_employee.position_id IS '岗位 ID(关联 pmis_position.id)';

COMMENT ON COLUMN pmis_employee.level_code IS '职级编码(全职 L1-L18 / 兼职 P1-P18,关联 pmis_job_level 或 pmis_part_time_rate)';

COMMENT ON COLUMN pmis_employee.employee_type IS '雇佣类型: FULL_TIME 全职 / PART_TIME 兼职 / OUTSOURCE 外包';

COMMENT ON COLUMN pmis_employee.part_time_rate_id IS '兼职费率 ID(仅 PART_TIME 类型填写,关联 pmis_part_time_rate.id)';

COMMENT ON COLUMN pmis_employee.outsource_rate_id IS '外包费率 ID(仅 OUTSOURCE 类型填写,关联 pmis_outsource_rate.id)';

COMMENT ON COLUMN pmis_employee.hire_date IS '入职日期';

COMMENT ON COLUMN pmis_employee.leave_date IS '离职日期(在职为 NULL)';

COMMENT ON COLUMN pmis_employee.work_status IS '在职状态: ACTIVE 在职 / LEAVE 离职 / SUSPEND 停薪留职 / PROBATION 试用期';

COMMENT ON COLUMN pmis_employee.bench_status IS 'Bench 状态: YES 闲置中 / NO 在项目中 / TRAINING 培训期';

COMMENT ON COLUMN pmis_employee.bench_start IS '进入 Bench 的起始日期';

COMMENT ON COLUMN pmis_employee.avatar IS '头像 URL';

COMMENT ON COLUMN pmis_employee.address IS '家庭住址';

COMMENT ON COLUMN pmis_employee.emergency_contact IS '紧急联系人姓名';

COMMENT ON COLUMN pmis_employee.emergency_phone IS '紧急联系人电话';

COMMENT ON COLUMN pmis_employee.description IS '备注';

COMMENT ON COLUMN pmis_employee.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_employee.created_at IS '创建时间';

COMMENT ON COLUMN pmis_employee.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_employee.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_employee.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_employee.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_emp_user ON pmis_employee (user_id);

CREATE INDEX IF NOT EXISTS idx_pmis_emp_dept ON pmis_employee (department_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_emp_level ON pmis_employee (level_code) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_emp_type ON pmis_employee (employee_type) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_emp_bench ON pmis_employee (bench_status, bench_start) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_employee_tenant ON pmis_employee(tenant_id);

CREATE INDEX IF NOT EXISTS idx_employee_tenant_created
    ON pmis_employee(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_emp_position
    ON pmis_employee(position_id) WHERE deleted = 0;

-- 兼职职级费率表 (P1-P18, 时薪核算月薪+商业保险+差旅)
CREATE TABLE IF NOT EXISTS pmis_part_time_rate(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    rate_code           VARCHAR(8)     NOT NULL,
    rate_name           VARCHAR(64)    NOT NULL,
    level_segment       VARCHAR(16)    NOT NULL,
    hourly_rate         NUMERIC(10,2)  NOT NULL DEFAULT 0,
    monthly_hours       NUMERIC(8,2)   NOT NULL DEFAULT 176,
    monthly_salary      NUMERIC(10,2)  NOT NULL,
    commercial_insurance NUMERIC(10,2) NOT NULL DEFAULT 0,
    travel_reimbursement NUMERIC(10,2) NOT NULL DEFAULT 0,
    travel_allowance    NUMERIC(10,2)  NOT NULL DEFAULT 0,
    total_cost          NUMERIC(10,2)  NOT NULL,
    external_daily      NUMERIC(10,2)  NOT NULL DEFAULT 0,
    internal_daily      NUMERIC(10,2)  NOT NULL DEFAULT 0,
    billable_target     NUMERIC(5,4)   NOT NULL DEFAULT 0.70,
    sort_order          INTEGER        NOT NULL DEFAULT 0,
    effective_date      DATE           NOT NULL,
    expire_date         DATE,
    version             INTEGER        NOT NULL DEFAULT 1,
    description         TEXT,
    status              VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_part_time_rate UNIQUE (rate_code, version, deleted),
    CONSTRAINT ck_ptr_segment         CHECK (level_segment IN ('PRIMARY', 'MIDDLE', 'SENIOR', 'EXPERT', 'STRATEGIC')),
    CONSTRAINT ck_ptr_status_enum     CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_ptr_dates_valid     CHECK (expire_date IS NULL OR expire_date >= effective_date),
    CONSTRAINT ck_ptr_deleted_enum    CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_ptr_salary_valid    CHECK (monthly_salary = ROUND(hourly_rate * monthly_hours, 2)),
    CONSTRAINT ck_ptr_cost_valid      CHECK (total_cost = monthly_salary + commercial_insurance + travel_reimbursement + travel_allowance)
);

COMMENT ON TABLE pmis_part_time_rate IS '兼职职级费率表(P1-P18): 时薪核算月薪+商业保险+差旅成本拆解,支持版本化生效';

COMMENT ON COLUMN pmis_part_time_rate.id IS '主键 ID';

COMMENT ON COLUMN pmis_part_time_rate.rate_code IS '兼职级别编码(P1-P18)';

COMMENT ON COLUMN pmis_part_time_rate.rate_name IS '级别名称(如兼职初级工程师)';

COMMENT ON COLUMN pmis_part_time_rate.level_segment IS '级别段: PRIMARY 初级(P1-P3) / MIDDLE 中级(P4-P6) / SENIOR 高级(P7-P9) / EXPERT 专家(P10-P12) / STRATEGIC 战略(P13-P18)';

COMMENT ON COLUMN pmis_part_time_rate.hourly_rate IS '时薪(元/小时,兼职核心计价单元)';

COMMENT ON COLUMN pmis_part_time_rate.monthly_hours IS '月工时数(默认176小时=22天×8小时)';

COMMENT ON COLUMN pmis_part_time_rate.monthly_salary IS '月度薪资(元/月,= hourly_rate × monthly_hours)';

COMMENT ON COLUMN pmis_part_time_rate.commercial_insurance IS '商业保险-公司承担部分(元/月)';

COMMENT ON COLUMN pmis_part_time_rate.travel_reimbursement IS '差旅报销-公司承担部分(元/月)';

COMMENT ON COLUMN pmis_part_time_rate.travel_allowance IS '差旅补贴-公司承担部分(元/月)';

COMMENT ON COLUMN pmis_part_time_rate.total_cost IS '公司总人力成本(元/月,=monthly_salary+commercial_insurance+travel_reimbursement+travel_allowance)';

COMMENT ON COLUMN pmis_part_time_rate.external_daily IS '对外人天单价(元/天,用于向客户报价)';

COMMENT ON COLUMN pmis_part_time_rate.internal_daily IS '对内人天成本(元/天,用于内部利润核算)';

COMMENT ON COLUMN pmis_part_time_rate.billable_target IS '可计费利用率目标(0.0-1.0)';

COMMENT ON COLUMN pmis_part_time_rate.sort_order IS '排序序号';

COMMENT ON COLUMN pmis_part_time_rate.effective_date IS '生效日期';

COMMENT ON COLUMN pmis_part_time_rate.expire_date IS '失效日期(NULL 表示长期有效)';

COMMENT ON COLUMN pmis_part_time_rate.version IS '版本号(同级别可有多版本,通过 effective_date 区分)';

COMMENT ON COLUMN pmis_part_time_rate.description IS '费率版本说明';

COMMENT ON COLUMN pmis_part_time_rate.status IS '状态: ACTIVE 启用 / INACTIVE 停用';

COMMENT ON COLUMN pmis_part_time_rate.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_part_time_rate.created_at IS '创建时间';

COMMENT ON COLUMN pmis_part_time_rate.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_part_time_rate.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_part_time_rate.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_part_time_rate.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ptr_code ON pmis_part_time_rate (rate_code) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ptr_effective ON pmis_part_time_rate (effective_date, expire_date);

CREATE INDEX IF NOT EXISTS idx_ptr_tenant ON pmis_part_time_rate(tenant_id);

CREATE INDEX IF NOT EXISTS idx_ptr_tenant_sort ON pmis_part_time_rate(tenant_id, sort_order) WHERE deleted = 0;

-- 外包职级费率表 (V1-V18, 人天核算月薪+差旅报销+差旅补贴)
CREATE TABLE IF NOT EXISTS pmis_outsource_rate(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    rate_code           VARCHAR(8)     NOT NULL,
    rate_name           VARCHAR(64)    NOT NULL,
    level_segment       VARCHAR(16)    NOT NULL,
    daily_rate          NUMERIC(10,2)  NOT NULL DEFAULT 0,
    monthly_days        NUMERIC(8,2)   NOT NULL DEFAULT 22,
    monthly_salary      NUMERIC(10,2)  NOT NULL,
    travel_reimbursement NUMERIC(10,2) NOT NULL DEFAULT 0,
    travel_allowance    NUMERIC(10,2)  NOT NULL DEFAULT 0,
    total_cost          NUMERIC(10,2)  NOT NULL,
    external_daily      NUMERIC(10,2)  NOT NULL DEFAULT 0,
    internal_daily      NUMERIC(10,2)  NOT NULL DEFAULT 0,
    billable_target     NUMERIC(5,4)   NOT NULL DEFAULT 0.70,
    sort_order          INTEGER        NOT NULL DEFAULT 0,
    effective_date      DATE           NOT NULL,
    expire_date         DATE,
    version             INTEGER        NOT NULL DEFAULT 1,
    description         TEXT,
    status              VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_outsource_rate UNIQUE (rate_code, version, deleted),
    CONSTRAINT ck_por_segment         CHECK (level_segment IN ('PRIMARY', 'MIDDLE', 'SENIOR', 'EXPERT', 'STRATEGIC')),
    CONSTRAINT ck_por_status_enum     CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_por_dates_valid     CHECK (expire_date IS NULL OR expire_date >= effective_date),
    CONSTRAINT ck_por_deleted_enum    CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_por_salary_valid    CHECK (monthly_salary = ROUND(daily_rate * monthly_days, 2)),
    CONSTRAINT ck_por_cost_valid      CHECK (total_cost = monthly_salary + travel_reimbursement + travel_allowance)
);

COMMENT ON TABLE pmis_outsource_rate IS '外包职级费率表(V1-V18): 人天核算月薪+差旅报销+差旅补贴成本拆解,支持版本化生效';

COMMENT ON COLUMN pmis_outsource_rate.id IS '主键 ID';

COMMENT ON COLUMN pmis_outsource_rate.rate_code IS '外包级别编码(V1-V18)';

COMMENT ON COLUMN pmis_outsource_rate.rate_name IS '级别名称(如外包初级工程师)';

COMMENT ON COLUMN pmis_outsource_rate.level_segment IS '级别段: PRIMARY 初级(V1-V3) / MIDDLE 中级(V4-V6) / SENIOR 高级(V7-V9) / EXPERT 专家(V10-V12) / STRATEGIC 战略(V13-V18)';

COMMENT ON COLUMN pmis_outsource_rate.daily_rate IS '人天单价(元/天,外包核心计价单元)';

COMMENT ON COLUMN pmis_outsource_rate.monthly_days IS '月工作天数(默认22天)';

COMMENT ON COLUMN pmis_outsource_rate.monthly_salary IS '月度薪资(元/月,= daily_rate × monthly_days)';

COMMENT ON COLUMN pmis_outsource_rate.travel_reimbursement IS '差旅报销-公司承担部分(元/月)';

COMMENT ON COLUMN pmis_outsource_rate.travel_allowance IS '差旅补贴-公司承担部分(元/月)';

COMMENT ON COLUMN pmis_outsource_rate.total_cost IS '公司总人力成本(元/月,=monthly_salary+travel_reimbursement+travel_allowance)';

COMMENT ON COLUMN pmis_outsource_rate.external_daily IS '对外人天单价(元/天,用于向客户报价)';

COMMENT ON COLUMN pmis_outsource_rate.internal_daily IS '对内人天成本(元/天,用于内部利润核算)';

COMMENT ON COLUMN pmis_outsource_rate.billable_target IS '可计费利用率目标(0.0-1.0)';

COMMENT ON COLUMN pmis_outsource_rate.sort_order IS '排序序号';

COMMENT ON COLUMN pmis_outsource_rate.effective_date IS '生效日期';

COMMENT ON COLUMN pmis_outsource_rate.expire_date IS '失效日期(NULL 表示长期有效)';

COMMENT ON COLUMN pmis_outsource_rate.version IS '版本号(同级别可有多版本,通过 effective_date 区分)';

COMMENT ON COLUMN pmis_outsource_rate.description IS '费率版本说明';

COMMENT ON COLUMN pmis_outsource_rate.status IS '状态: ACTIVE 启用 / INACTIVE 停用';

COMMENT ON COLUMN pmis_outsource_rate.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_outsource_rate.created_at IS '创建时间';

COMMENT ON COLUMN pmis_outsource_rate.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_outsource_rate.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_outsource_rate.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_outsource_rate.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_por_code ON pmis_outsource_rate (rate_code) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_por_effective ON pmis_outsource_rate (effective_date, expire_date);

CREATE INDEX IF NOT EXISTS idx_por_tenant ON pmis_outsource_rate(tenant_id);

CREATE INDEX IF NOT EXISTS idx_por_tenant_sort ON pmis_outsource_rate(tenant_id, sort_order) WHERE deleted = 0;

-- 员工标签表
CREATE TABLE IF NOT EXISTS pmis_employee_tag(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    employee_id     VARCHAR(20)         NOT NULL,
    tag_type        VARCHAR(32)    NOT NULL,
    tag_code        VARCHAR(64)    NOT NULL,
    tag_value       VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT ck_pet_tag_type CHECK (tag_type IN ('TECH_STACK', 'INDUSTRY', 'DOMAIN', 'CERTIFICATE', 'SKILL')),
    CONSTRAINT ck_pet_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_employee_tag IS '员工标签表: 技能/行业/资质/认证,支持资源池精准匹配';

COMMENT ON COLUMN pmis_employee_tag.id IS '主键 ID';

COMMENT ON COLUMN pmis_employee_tag.employee_id IS '员工 ID(关联 pmis_employee.id)';

COMMENT ON COLUMN pmis_employee_tag.tag_type IS '标签类型: TECH_STACK 技术栈 / INDUSTRY 行业经验 / DOMAIN 业务领域 / CERTIFICATE 资质证书 / SKILL 软技能';

COMMENT ON COLUMN pmis_employee_tag.tag_code IS '标签编码(同类型下唯一,如 Java/Python/FinTech)';

COMMENT ON COLUMN pmis_employee_tag.tag_value IS '标签值(中文展示名)';

COMMENT ON COLUMN pmis_employee_tag.created_at IS '创建时间';

COMMENT ON COLUMN pmis_employee_tag.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_employee_tag.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_emp_tag_emp ON pmis_employee_tag (employee_id);

CREATE INDEX IF NOT EXISTS idx_pmis_emp_tag_code ON pmis_employee_tag (tag_code);

CREATE INDEX IF NOT EXISTS idx_emp_tag_tenant ON pmis_employee_tag(tenant_id);

CREATE INDEX IF NOT EXISTS idx_pmis_emp_tag_deleted ON pmis_employee_tag(deleted);

-- ====================================================================
-- 4. 用户账号
-- ====================================================================

-- 用户账号表
CREATE TABLE IF NOT EXISTS pmis_user_account(
    id                 VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    username           VARCHAR(64)    NOT NULL,
    password           VARCHAR(128)   NOT NULL,
    salt               VARCHAR(32)    NOT NULL,
    employee_id        VARCHAR(20),
    -- V1.0.0_054 内联: 组织关系字段(支持 dept:/leader:/position: 审批人展开)
    dept_id            VARCHAR(20),
    leader_id          VARCHAR(20),
    position_code      VARCHAR(64),
    -- V1.0.0_016 内联: 数据权限 + MFA + 密码策略字段
    data_scope         VARCHAR(16)    NOT NULL DEFAULT 'SELF',
    custom_dept_ids    TEXT,
    mfa_enabled        BOOLEAN        NOT NULL DEFAULT FALSE,
    mfa_type           VARCHAR(16)    NOT NULL DEFAULT 'NONE',
    last_pwd_change_at TIMESTAMPTZ,
    pwd_change_count   INT            NOT NULL DEFAULT 0,
    status             VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    last_login_time    TIMESTAMPTZ,
    last_login_ip      VARCHAR(64),
    login_fail_count   INTEGER        NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    created_by         VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT       NOT NULL DEFAULT 0,
    tenant_id          VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_user_username UNIQUE (username, deleted),
    CONSTRAINT ck_pua_status_enum   CHECK (status IN ('ENABLED', 'DISABLED', 'LOCKED')),
    CONSTRAINT ck_pua_data_scope    CHECK (data_scope IN ('ALL', 'DEPT', 'DEPT_AND_CHILD', 'SELF', 'CUSTOM')),
    CONSTRAINT ck_pua_mfa_type      CHECK (mfa_type IN ('NONE', 'TOTP', 'SMS')),
    CONSTRAINT ck_pua_fail_nonneg   CHECK (login_fail_count >= 0 AND pwd_change_count >= 0),
    CONSTRAINT ck_pua_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_user_account IS '用户账号表: 登录凭证,存储密码哈希+盐值,支持登录失败锁定与 MFA';

COMMENT ON COLUMN pmis_user_account.id IS '主键 ID';

COMMENT ON COLUMN pmis_user_account.username IS '登录用户名(全局唯一)';

COMMENT ON COLUMN pmis_user_account.password IS '密码哈希(BCrypt)';

COMMENT ON COLUMN pmis_user_account.salt IS '密码盐值';

COMMENT ON COLUMN pmis_user_account.employee_id IS '关联员工 ID(关联 pmis_employee.id,1 个账号对应 1 个员工)';

COMMENT ON COLUMN pmis_user_account.status IS '账号状态: ENABLED 启用 / DISABLED 停用 / LOCKED 锁定';

COMMENT ON COLUMN pmis_user_account.last_login_time IS '最近登录时间';

COMMENT ON COLUMN pmis_user_account.last_login_ip IS '最近登录 IP';

COMMENT ON COLUMN pmis_user_account.login_fail_count IS '连续登录失败次数(达到阈值触发锁定)';

COMMENT ON COLUMN pmis_user_account.locked_until IS '锁定截止时间(到期自动解锁)';

COMMENT ON COLUMN pmis_user_account.dept_id IS '所属部门 ID(关联 pmis_department.id,支持 dept: 审批人展开)';

COMMENT ON COLUMN pmis_user_account.leader_id IS '直属上级用户 ID(关联 pmis_user_account.id,支持 leader: 审批人展开)';

COMMENT ON COLUMN pmis_user_account.position_code IS '岗位编码(如 PM/DEV/QA/SA,支持 position: 审批人展开)';

COMMENT ON COLUMN pmis_user_account.data_scope IS '数据权限范围: ALL 全部 / DEPT 本部门 / DEPT_AND_CHILD 本部门及下级 / SELF 本人 / CUSTOM 自定义';

COMMENT ON COLUMN pmis_user_account.custom_dept_ids IS '自定义数据权限部门 ID 列表(逗号分隔,data_scope=CUSTOM 时生效)';

COMMENT ON COLUMN pmis_user_account.mfa_enabled IS '是否启用双因素认证';

COMMENT ON COLUMN pmis_user_account.mfa_type IS '双因素认证类型: NONE 未启用 / TOTP 基于时间的一次性密码 / SMS 短信验证码';

COMMENT ON COLUMN pmis_user_account.last_pwd_change_at IS '最近密码修改时间';

COMMENT ON COLUMN pmis_user_account.pwd_change_count IS '密码修改次数(用于强制定期改密)';

COMMENT ON COLUMN pmis_user_account.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_user_account.created_at IS '创建时间';

COMMENT ON COLUMN pmis_user_account.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_user_account.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_user_account.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_user_account.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_user_status ON pmis_user_account (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_user_account_tenant ON pmis_user_account(tenant_id);

CREATE INDEX IF NOT EXISTS idx_user_account_tenant_created
    ON pmis_user_account(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_user_employee
    ON pmis_user_account(employee_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pua_dept_id
    ON pmis_user_account(dept_id) WHERE deleted = 0 AND dept_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pua_position_code
    ON pmis_user_account(position_code) WHERE deleted = 0 AND position_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pua_leader_id
    ON pmis_user_account(leader_id) WHERE deleted = 0 AND leader_id IS NOT NULL;

-- ====================================================================
-- 8. 初始化数据
-- ====================================================================

-- 初始化超级管理员
-- 默认 admin 账号 (密码: admin123, 哈希算法: BCrypt, 成本因子: 10)
-- BCrypt 自带盐,无需单独存储 salt 字段(此处置空字符串)。
-- 密码使用 BCrypt 哈希,首次登录建议强制修改。
INSERT INTO pmis_user_account (username, password, salt, status, created_by)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMy.Mrq8BpVLqMDvQXEvCJ5DEmCJWP1tCaa', '', 'ENABLED', 0)
ON CONFLICT (username, deleted) DO NOTHING;

-- 初始化兼职职级费率 (P1-P18, 时薪核算月薪+商业保险+差旅)
INSERT INTO pmis_part_time_rate
(rate_code, rate_name, level_segment, hourly_rate, monthly_hours, monthly_salary, commercial_insurance, travel_reimbursement, travel_allowance, total_cost, external_daily, internal_daily, billable_target, sort_order, effective_date, version, status, created_by)
VALUES
    ('P1',  '兼职助理工程师',     'PRIMARY',   17.05, 176,  3000.80,  50,  300,  200,  3550.80,  300,  150,  0.78,  1, '2026-01-01', 1, 'ACTIVE', 0),
    ('P2',  '兼职初级开发工程师', 'PRIMARY',   19.89, 176,  3500.64,  50,  300,  200,  4050.64,  350,  175,  0.78,  2, '2026-01-01', 1, 'ACTIVE', 0),
    ('P3',  '兼职开发工程师',     'PRIMARY',   22.73, 176,  4000.48,  50,  300,  200,  4550.48,  400,  200,  0.80,  3, '2026-01-01', 1, 'ACTIVE', 0),
    ('P4',  '兼职中级工程师',     'MIDDLE',    28.41, 176,  5000.16,  80,  500,  300,  5880.16,  500,  250,  0.82,  4, '2026-01-01', 1, 'ACTIVE', 0),
    ('P5',  '兼职高级工程师',     'MIDDLE',    34.09, 176,  5999.84,  80,  500,  300,  6879.84,  600,  300,  0.82,  5, '2026-01-01', 1, 'ACTIVE', 0),
    ('P6',  '兼职资深工程师',     'MIDDLE',    39.77, 176,  6999.52,  80,  500,  300,  7879.52,  700,  350,  0.82,  6, '2026-01-01', 1, 'ACTIVE', 0),
    ('P7',  '兼职高级工程师/项目经理', 'SENIOR', 45.45, 176,  7999.20, 100,  800,  500,  9399.20,  800,  400,  0.80,  7, '2026-01-01', 1, 'ACTIVE', 0),
    ('P8',  '兼职资深工程师/高级项目经理', 'SENIOR', 51.14, 176,  9000.64, 100,  800,  500, 10400.64,  900,  450,  0.80,  8, '2026-01-01', 1, 'ACTIVE', 0),
    ('P9',  '兼职架构师/项目总监', 'SENIOR',   56.82, 176, 10000.32, 100,  800,  500, 11400.32, 1000,  500,  0.75,  9, '2026-01-01', 1, 'ACTIVE', 0),
    ('P10', '兼职资深架构师',     'EXPERT',    62.50, 176, 11000.00, 120, 1000,  600, 12720.00, 1100,  550,  0.70, 10, '2026-01-01', 1, 'ACTIVE', 0),
    ('P11', '兼职技术专家/交付总监', 'EXPERT',  68.18, 176, 11999.68, 120, 1000,  600, 13719.68, 1200,  600,  0.70, 11, '2026-01-01', 1, 'ACTIVE', 0),
    ('P12', '兼职资深技术专家',   'EXPERT',    73.86, 176, 12999.36, 120, 1000,  600, 14719.36, 1300,  650,  0.65, 12, '2026-01-01', 1, 'ACTIVE', 0),
    ('P13', '兼职首席架构师',     'STRATEGIC', 79.55, 176, 14000.80, 150, 1500,  800, 16450.80, 1400,  700,  0.60, 13, '2026-01-01', 1, 'ACTIVE', 0),
    ('P14', '兼职技术总监',       'STRATEGIC', 85.23, 176, 15000.48, 150, 1500,  800, 17450.48, 1500,  750,  0.55, 14, '2026-01-01', 1, 'ACTIVE', 0),
    ('P15', '兼职CTO/事业部总经理', 'STRATEGIC',90.91, 176, 16000.16, 150, 1500,  800, 18450.16, 1600,  800,  0.50, 15, '2026-01-01', 1, 'ACTIVE', 0),
    ('P16', '兼职技术副总裁',     'STRATEGIC', 96.59, 176, 16999.84, 150, 1500,  800, 19449.84, 1700,  850,  0.45, 16, '2026-01-01', 1, 'ACTIVE', 0),
    ('P17', '兼职执行副总裁',     'STRATEGIC',102.27, 176, 17999.52, 200, 2000, 1000, 21199.52, 1800,  900,  0.40, 17, '2026-01-01', 1, 'ACTIVE', 0),
    ('P18', '兼职首席科学家',     'STRATEGIC',107.95, 176, 18999.20, 200, 2000, 1000, 22199.20, 1900,  950,  0.40, 18, '2026-01-01', 1, 'ACTIVE', 0)
ON CONFLICT DO NOTHING;

-- 初始化外包职级费率 (V1-V18, 人天核算月薪+差旅报销+差旅补贴)
INSERT INTO pmis_outsource_rate
(rate_code, rate_name, level_segment, daily_rate, monthly_days, monthly_salary, travel_reimbursement, travel_allowance, total_cost, external_daily, internal_daily, billable_target, sort_order, effective_date, version, status, created_by)
VALUES
    ('V1',  '外包助理工程师',     'PRIMARY',   113.64, 22,  2500.08,  300,  200,  3000.08,  250,  120,  0.78,  1, '2026-01-01', 1, 'ACTIVE', 0),
    ('V2',  '外包初级开发工程师', 'PRIMARY',   136.36, 22,  2999.92,  300,  200,  3499.92,  300,  150,  0.78,  2, '2026-01-01', 1, 'ACTIVE', 0),
    ('V3',  '外包开发工程师',     'PRIMARY',   159.09, 22,  3499.98,  300,  200,  3999.98,  350,  180,  0.80,  3, '2026-01-01', 1, 'ACTIVE', 0),
    ('V4',  '外包中级工程师',     'MIDDLE',    181.82, 22,  4000.04,  500,  300,  4800.04,  400,  200,  0.82,  4, '2026-01-01', 1, 'ACTIVE', 0),
    ('V5',  '外包高级工程师',     'MIDDLE',    227.27, 22,  4999.94,  500,  300,  5799.94,  500,  250,  0.82,  5, '2026-01-01', 1, 'ACTIVE', 0),
    ('V6',  '外包资深工程师',     'MIDDLE',    272.73, 22,  6000.06,  500,  300,  6800.06,  600,  300,  0.82,  6, '2026-01-01', 1, 'ACTIVE', 0),
    ('V7',  '外包高级工程师/项目经理', 'SENIOR', 318.18, 22,  6999.96,  800,  500,  8299.96,  700,  350,  0.80,  7, '2026-01-01', 1, 'ACTIVE', 0),
    ('V8',  '外包资深工程师/高级项目经理', 'SENIOR', 363.64, 22,  8000.08,  800,  500,  9300.08,  800,  400,  0.80,  8, '2026-01-01', 1, 'ACTIVE', 0),
    ('V9',  '外包架构师/项目总监', 'SENIOR',   409.09, 22,  8999.98,  800,  500, 10299.98,  900,  450,  0.75,  9, '2026-01-01', 1, 'ACTIVE', 0),
    ('V10', '外包资深架构师',     'EXPERT',    454.55, 22, 10000.10, 1000,  600, 11600.10, 1000,  500,  0.70, 10, '2026-01-01', 1, 'ACTIVE', 0),
    ('V11', '外包技术专家/交付总监', 'EXPERT',  500.00, 22, 11000.00, 1000,  600, 12600.00, 1100,  550,  0.70, 11, '2026-01-01', 1, 'ACTIVE', 0),
    ('V12', '外包资深技术专家',   'EXPERT',    545.45, 22, 12000.00, 1000,  600, 13600.00, 1200,  600,  0.65, 12, '2026-01-01', 1, 'ACTIVE', 0),
    ('V13', '外包首席架构师',     'STRATEGIC', 590.91, 22, 12999.98, 1500,  800, 15299.98, 1300,  650,  0.60, 13, '2026-01-01', 1, 'ACTIVE', 0),
    ('V14', '外包技术总监',       'STRATEGIC', 636.36, 22, 13999.92, 1500,  800, 16299.92, 1400,  700,  0.55, 14, '2026-01-01', 1, 'ACTIVE', 0),
    ('V15', '外包CTO/事业部总经理', 'STRATEGIC',681.82, 22, 15000.04, 1500,  800, 17300.04, 1500,  750,  0.50, 15, '2026-01-01', 1, 'ACTIVE', 0),
    ('V16', '外包技术副总裁',     'STRATEGIC', 727.27, 22, 15999.94, 1500,  800, 18299.94, 1600,  800,  0.45, 16, '2026-01-01', 1, 'ACTIVE', 0),
    ('V17', '外包执行副总裁',     'STRATEGIC', 772.73, 22, 16999.94, 2000, 1000, 19999.94, 1700,  850,  0.40, 17, '2026-01-01', 1, 'ACTIVE', 0),
    ('V18', '外包首席科学家',     'STRATEGIC', 818.18, 22, 17999.96, 2000, 1000, 20999.96, 1800,  900,  0.40, 18, '2026-01-01', 1, 'ACTIVE', 0)
ON CONFLICT DO NOTHING;

-- 初始化超级管理员角色
INSERT INTO pmis_role (role_code, role_name, data_scope, sort_order, status, created_by)
VALUES ('SUPER_ADMIN', '超级管理员', 'ALL', 0, 'ENABLED', 0)
ON CONFLICT DO NOTHING;

-- --------------------------------------------------------------------

-- ============================ [014_1] init pmis resource bench schema ============================

-- =====================================================
-- PMIS 批次11 DDL：资源池 + 人员标签 + 资源分配 + Bench 闲置
-- 版本: V1.0.0_014
-- 描述: 资源池(pmis_resource_pool)、人员标签(pmis_employee_tag)、
--       资源分配(pmis_resource_assignment)、Bench 闲置(pmis_bench_record)
-- =====================================================

-- P1-7 fix: pmis_employee_tag 已在 [001] 章节以 tag_value 字段先建,本节扩展新字段
ALTER TABLE pmis_employee_tag
    ADD COLUMN IF NOT EXISTS tag_name     VARCHAR(256) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS proficiency  INTEGER      NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS years_exp    INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS remark       TEXT,
    ADD COLUMN IF NOT EXISTS provider_trace_id VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS created_by   VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN IF NOT EXISTS updated_by   VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 放宽 tag_type 枚举约束(原[001]是 TECH_STACK/INDUSTRY/DOMAIN/CERTIFICATE/SKILL,本节扩展为含 CERT)
ALTER TABLE pmis_employee_tag
    DROP CONSTRAINT IF EXISTS ck_pet_tag_type;

ALTER TABLE pmis_employee_tag
    ADD CONSTRAINT ck_pet_tag_type CHECK (tag_type IN ('SKILL', 'INDUSTRY', 'DOMAIN', 'CERT', 'TECH_STACK', 'CERTIFICATE'));

-- =====================================================
-- 2. 人员标签表 pmis_employee_tag (已在 [001] 章节创建, [014_1] 已 ALTER 扩展新字段)
-- =====================================================
-- 注意:历史 [SKIPPED-CLEANUP-REBUILD] 标记下的旧版 DDL 已废弃,字段定义以 [001]+[014_1] 为准
-- 本节保留 COMMENT ON COLUMN 用于覆盖 [001] 的简短注释,提供更详细的字段说明
-- (以下 CREATE TABLE IF NOT EXISTS 因表已存在会被跳过,不会重建)
-- =====================================================
COMMENT ON TABLE  pmis_employee_tag IS '人员标签表: 员工的技能/行业/领域/资质标签,支撑资源推荐智能体匹配';

COMMENT ON COLUMN pmis_employee_tag.employee_id IS '员工 ID';

COMMENT ON COLUMN pmis_employee_tag.tag_type IS '标签类型: SKILL 技能 / INDUSTRY 行业 / DOMAIN 领域 / CERT 资质';

COMMENT ON COLUMN pmis_employee_tag.tag_code IS '标签编码: 业务唯一,如 JAVA / BANKING';

COMMENT ON COLUMN pmis_employee_tag.tag_name IS '标签名称';

COMMENT ON COLUMN pmis_employee_tag.proficiency IS '熟练度: 1=入门 2=了解 3=熟练 4=精通 5=专家';

COMMENT ON COLUMN pmis_employee_tag.years_exp IS '相关经验年限';

COMMENT ON COLUMN pmis_employee_tag.remark IS '备注';

COMMENT ON COLUMN pmis_employee_tag.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_employee_tag.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_employee_tag.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pet_emp / idx_pet_type)
CREATE INDEX IF NOT EXISTS idx_pet_tenant_emp
    ON pmis_employee_tag(tenant_id, employee_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pet_tenant_type_code
    ON pmis_employee_tag(tenant_id, tag_type, tag_code)
    WHERE deleted = 0;

-- 注释说明: 上方 CREATE TABLE 已包含 tag_name / proficiency / years_exp / remark / tenant_id / provider_trace_id
-- 字段及其 COMMENT,此处不再重复定义,避免与上方 COMMENT 重复执行。

-- --------------------------------------------------------------------

-- ============================ [014] init pmis admin full perm ============================

-- ====================================================================
-- 9. 初始化菜单权限 + 角色授权 (admin 拥有全部权限)
-- ====================================================================

-- 一. 初始化菜单权限
-- 拆成多步插入：先插入顶层节点（parent_id=0），再插入二级子菜单，
-- 最后插入三级按钮权限。每一步都通过 perm_code 关联父节点。
-- 关键：PostgreSQL 在单条 INSERT VALUES 中，所有子查询都在语句开始时求值，
--       看不到同语句中正在插入的行；因此必须分多语句执行。

-- 步骤 1：插入顶层节点
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    (0, 'dashboard',  '仪表盘',   'MENU', '/dashboard',  'dashboard/index', 'odometer',  1, 1, 'ENABLED', 0),
    (0, 'system',     '系统管理', 'MENU', '/system',     'Layout',          'setting',   2, 1, 'ENABLED', 0),
    (0, 'business',   '业务管理', 'MENU', '/business',   'Layout',          'briefcase', 3, 1, 'ENABLED', 0),
    (0, 'execution',  '项目执行', 'MENU', '/execution',  'Layout',          'cpu',       4, 1, 'ENABLED', 0),
    (0, 'finance',    '财务收支', 'MENU', '/finance',    'Layout',          'credit-card', 5, 1, 'ENABLED', 0),
    (0, 'report',     '经营报表', 'MENU', '/report',     'Layout',          'data-analysis', 6, 1, 'ENABLED', 0),
    (0, 'ai',         '智能助手', 'MENU', '/ai',         'Layout',          'magic-stick',  7, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2a：插入系统管理子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:user',     '用户管理',     'MENU', '/system/user',     'system/user/index',     'user',     1, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:role',     '角色管理',     'MENU', '/system/role',     'system/role/index',     'avatar',   2, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:menu',     '菜单管理',     'MENU', '/system/menu',     'system/menu/index',     'menu',     3, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:dept',     '部门管理',     'MENU', '/system/dept',     'system/dept/index',     'office-building', 4, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:dict',     '数据字典',     'MENU', '/system/dict',     'system/dict/index',     'collection', 5, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:job-level','职级管理',     'MENU', '/system/job-level','system/job-level/index', 'medal',    6, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:config',   '参数配置',     'MENU', '/system/config',   'system/config/index',   'tools',    7, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2b：插入业务根子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:opportunity', '商机管理', 'MENU', '/business/opportunity', 'business/opportunity/index', 'lightbulb', 1, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:initiation',  '立项管理', 'MENU', '/business/initiation',  'business/initiation/index',  'document', 2, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:contract',     '合同管理', 'MENU', '/business/contract',     'business/contract/index',     'tickets', 3, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:change',       '变更管理', 'MENU', '/business/change',       'business/change/index',       'refresh', 4, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2c：插入执行根子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:wbs',         'WBS 任务',  'MENU', '/execution/wbs',         'execution/wbs/index',         'list',     1, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:timesheet',   '工时管理',  'MENU', '/execution/timesheet',   'execution/timesheet/index',   'timer',    2, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:attendance',  '考勤管理',  'MENU', '/execution/attendance',  'execution/attendance/index',  'calendar', 3, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:cost',        '成本管理',  'MENU', '/execution/cost',        'execution/cost/index',        'money',    4, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:revenue',     '收入管理',  'MENU', '/execution/revenue',     'execution/revenue/index',     'wallet',   5, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:risk',        '风险登记',  'MENU', '/execution/risk',        'execution/risk/index',        'warning',  6, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:profit',      '利润分析',  'MENU', '/execution/profit',      'execution/profit/index',      'data-line',7, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:delivery',    '交付管理',  'MENU', '/execution/delivery',    'execution/delivery/index',    'box',      8, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2d：插入财务根子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'finance'), 'finance:invoice',  '发票管理', 'MENU', '/finance/invoice',  'finance/invoice/index',  'document-copy', 1, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'finance'), 'finance:payment',  '回款管理', 'MENU', '/finance/payment',  'finance/payment/index',  'bank-card',    2, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2e：插入报表根子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'report'), 'report:profit',    '项目利润',   'MENU', '/report/profit',    'report/profit/index',    'pie-chart',    1, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'report'), 'report:cost',      '成本明细',   'MENU', '/report/cost',      'report/cost/index',      'data-board',   2, 1, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'report'), 'report:lifecycle', '生命周期台账', 'MENU', '/report/lifecycle', 'report/lifecycle/index', 'connection',   3, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2f：插入 AI 根子菜单
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'ai'), 'ai:agents',  'AI Agents', 'MENU', '/ai/agents', 'ai/agents/index', 'chat-dot-round', 1, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 3：插入按钮级权限（依赖步骤 2 的二级菜单）
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:create', '新增用户', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:update', '编辑用户', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:delete', '删除用户', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:reset',  '重置密码', 'BUTTON', null, null, null, 4, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:create', '新增角色', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:update', '编辑角色', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:delete', '删除角色', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:assign', '分配权限', 'BUTTON', null, null, null, 4, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:menu'), 'auth:perm:create', '新增菜单', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:menu'), 'auth:perm:update', '编辑菜单', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
    ((SELECT id FROM pmis_permission WHERE perm_code = 'system:menu'), 'auth:perm:delete', '删除菜单', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 二. SUPER_ADMIN 角色绑定所有权限
INSERT INTO pmis_role_permission (role_id, permission_id)
SELECT
    (SELECT id FROM pmis_role WHERE role_code = 'SUPER_ADMIN'),
    p.id
FROM pmis_permission p
WHERE p.deleted = 0
  AND p.status = 'ENABLED'
ON CONFLICT DO NOTHING;

-- 三. admin 用户绑定到 SUPER_ADMIN 角色
INSERT INTO pmis_user_role (user_id, role_id, created_by)
SELECT
    (SELECT id FROM pmis_user_account WHERE username = 'admin'),
    (SELECT id FROM pmis_role WHERE role_code = 'SUPER_ADMIN'),
    0
ON CONFLICT DO NOTHING;

-- ----------------------------
-- 3) 双因素认证
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_user_2fa (
    id              VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    user_id         VARCHAR(20)        NOT NULL,
    mfa_type        VARCHAR(16)   NOT NULL DEFAULT 'TOTP',
    secret          VARCHAR(128)  NOT NULL,
    binding_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at    TIMESTAMPTZ,
    backup_codes    TEXT,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    tenant_id       VARCHAR(20)        NOT NULL DEFAULT '1',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_2fa_uid_type     UNIQUE (user_id, mfa_type, deleted),
    CONSTRAINT ck_user_2fa_type         CHECK (mfa_type IN ('TOTP','SMS','EMAIL')),
    CONSTRAINT ck_user_2fa_deleted      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_user_2fa IS '用户双因素认证表: 基于 TOTP（Time-based OTP）的双因素认证,使用 constant-time 比对防时序攻击';

COMMENT ON COLUMN pmis_user_2fa.user_id IS '用户 ID';

COMMENT ON COLUMN pmis_user_2fa.mfa_type IS 'MFA 类型: TOTP 时间型 / SMS 短信 / EMAIL 邮件';

COMMENT ON COLUMN pmis_user_2fa.secret IS 'TOTP 密钥: Base32 编码,扫描二维码';

COMMENT ON COLUMN pmis_user_2fa.binding_at IS '绑定时间';

COMMENT ON COLUMN pmis_user_2fa.last_used_at IS '最近使用时间';

COMMENT ON COLUMN pmis_user_2fa.backup_codes IS '备份码（密文）: 一次性,小写 hex 存储,已使用标记为 _used_<timestamp>';

COMMENT ON COLUMN pmis_user_2fa.enabled IS '是否启用: true=启用';

COMMENT ON COLUMN pmis_user_2fa.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_user_2fa.deleted IS '逻辑删除: 0=未删除,1=已删除';

CREATE INDEX IF NOT EXISTS idx_user_2fa_tenant_user
    ON pmis_user_2fa(tenant_id, user_id)
    WHERE deleted = 0;

-- ----------------------------
-- 6) 用户会话（单点登录/强制下线）
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_user_session (
    id              VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    user_id         VARCHAR(20)        NOT NULL,
    session_id      VARCHAR(64)   NOT NULL,
    token_jti       VARCHAR(64),
    login_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at  TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at       TIMESTAMPTZ   NOT NULL,
    client_ip       VARCHAR(64),
    user_agent      VARCHAR(512),
    device_type     VARCHAR(32),
    status          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    logout_at       TIMESTAMPTZ,
    logout_reason   VARCHAR(64),
    trace_id        VARCHAR(20),
    tenant_id       VARCHAR(20)        NOT NULL DEFAULT '1',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_session_id        UNIQUE (session_id),
    CONSTRAINT ck_user_session_status    CHECK (status IN ('ACTIVE','EXPIRED','KICKED','LOGOUT')),
    CONSTRAINT ck_user_session_device    CHECK (device_type IS NULL OR device_type IN ('WEB','IOS','ANDROID','DESKTOP','API')),
    CONSTRAINT ck_user_session_expire    CHECK (expire_at > login_at),
    CONSTRAINT ck_user_session_deleted   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_user_session IS '用户活跃会话表: 单点登录/强制下线管理,SessionService 维护生命周期';

COMMENT ON COLUMN pmis_user_session.user_id IS '用户 ID';

COMMENT ON COLUMN pmis_user_session.session_id IS '会话 ID: 唯一';

COMMENT ON COLUMN pmis_user_session.token_jti IS 'JWT ID: 用于 token 失效';

COMMENT ON COLUMN pmis_user_session.login_at IS '登录时间';

COMMENT ON COLUMN pmis_user_session.last_active_at IS '最近活跃时间';

COMMENT ON COLUMN pmis_user_session.expire_at IS '过期时间';

COMMENT ON COLUMN pmis_user_session.client_ip IS '客户端 IP';

COMMENT ON COLUMN pmis_user_session.user_agent IS '浏览器 UA';

COMMENT ON COLUMN pmis_user_session.device_type IS '设备类型: WEB / IOS / ANDROID / DESKTOP';

COMMENT ON COLUMN pmis_user_session.status IS '会话状态: ACTIVE 活跃 / EXPIRED 过期 / KICKED 踢出 / LOGOUT 主动登出';

COMMENT ON COLUMN pmis_user_session.logout_at IS '登出时间';

COMMENT ON COLUMN pmis_user_session.logout_reason IS '登出原因: USER_LOGOUT / ADMIN_KICK / EXPIRED';

COMMENT ON COLUMN pmis_user_session.trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_user_session.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_user_session.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_user_session_*)
CREATE INDEX IF NOT EXISTS idx_user_session_tenant_user_status
    ON pmis_user_session(tenant_id, user_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_user_session_tenant_expire
    ON pmis_user_session(tenant_id, expire_at)
    WHERE deleted = 0 AND status = 'ACTIVE';

-- 4. 角色
ALTER TABLE pmis_role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_role_tenant ON pmis_role(tenant_id);

-- 5. 权限
ALTER TABLE pmis_permission ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_permission_tenant ON pmis_permission(tenant_id);

-- 6. 用户-角色关联
ALTER TABLE pmis_user_role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_user_role_tenant ON pmis_user_role(tenant_id);

-- 7. 角色-权限关联
ALTER TABLE pmis_role_permission ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_role_permission_tenant ON pmis_role_permission(tenant_id);

-- 9. 岗位
ALTER TABLE pmis_position ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_position_tenant ON pmis_position(tenant_id);

-- 12. 员工
ALTER TABLE pmis_employee ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_employee_tenant ON pmis_employee(tenant_id);

-- 13. 员工标签
ALTER TABLE pmis_employee_tag ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_emp_tag_tenant ON pmis_employee_tag(tenant_id);

-- 14. 用户账号
ALTER TABLE pmis_user_account ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_user_account_tenant ON pmis_user_account(tenant_id);

CREATE INDEX IF NOT EXISTS idx_role_tenant_created
    ON pmis_role(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_position_tenant_created
    ON pmis_position(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_employee_tenant_created
    ON pmis_employee(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_user_account_tenant_created
    ON pmis_user_account(tenant_id, created_at DESC) WHERE deleted = 0;

-- ============================================================
-- 三、外键关联列反向查询索引（H2.3）
-- ============================================================

-- 角色-权限：按 permission_id 反向查询"该权限被哪些角色引用"
CREATE INDEX IF NOT EXISTS idx_role_permission_perm
    ON pmis_role_permission(permission_id) WHERE deleted = 0;

-- 员工-岗位：按 position_id 查询"该岗位下的员工"
CREATE INDEX IF NOT EXISTS idx_pmis_emp_position
    ON pmis_employee(position_id) WHERE deleted = 0;

-- 用户账号-员工：按 employee_id 反向查询
CREATE INDEX IF NOT EXISTS idx_pmis_user_employee
    ON pmis_user_account(employee_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_role_permission_deleted ON pmis_role_permission(deleted);

CREATE INDEX IF NOT EXISTS idx_pmis_emp_tag_deleted ON pmis_employee_tag(deleted);

ANALYZE pmis_role;

ANALYZE pmis_permission;

ANALYZE pmis_user_role;

ANALYZE pmis_role_permission;

ANALYZE pmis_position;

ANALYZE pmis_employee;

ANALYZE pmis_employee_tag;

ANALYZE pmis_user_account;

CREATE INDEX IF NOT EXISTS idx_pmis_employee_tag_trace
    ON pmis_employee_tag (provider_trace_id)
    WHERE provider_trace_id <> '';

