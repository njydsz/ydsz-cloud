-- ====================================================================
-- 南京云顶 PMIS 数据库初始化脚本
-- V1.0.0
-- 对应 PRD V3.2 + 开发计划 V1.0
-- ====================================================================

-- 启用扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ====================================================================
-- Schema 划分
-- ====================================================================
CREATE SCHEMA IF NOT EXISTS pmis;        -- 主业务
CREATE SCHEMA IF NOT EXISTS pmis_log;    -- 日志
CREATE SCHEMA IF NOT EXISTS pmis_cfg;    -- 配置
COMMENT ON SCHEMA pmis IS 'PMIS 主业务表';
COMMENT ON SCHEMA pmis_log IS 'PMIS 日志表';
COMMENT ON SCHEMA pmis_cfg IS 'PMIS 配置表';

-- ====================================================================
-- 1. 字典/枚举值模块
-- ====================================================================

-- 字典类型表
CREATE TABLE pmis.pmis_dict_type (
    id              BIGSERIAL      PRIMARY KEY,
    type_code       VARCHAR(64)    NOT NULL,
    type_name       VARCHAR(128)   NOT NULL,
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_dict_type_code UNIQUE (type_code, deleted)
);
COMMENT ON TABLE pmis.pmis_dict_type IS '字典类型表';

CREATE INDEX idx_pmis_dict_type_status ON pmis.pmis_dict_type (status) WHERE deleted = 0;

-- 字典项表
CREATE TABLE pmis.pmis_dict_item (
    id              BIGSERIAL      PRIMARY KEY,
    type_code       VARCHAR(64)    NOT NULL,
    item_code       VARCHAR(64)    NOT NULL,
    item_value      VARCHAR(255)   NOT NULL,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    parent_id       BIGINT         NOT NULL DEFAULT 0,
    description     TEXT,
    ext_json        JSONB,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_dict_item UNIQUE (type_code, item_code, deleted)
);
COMMENT ON TABLE pmis.pmis_dict_item IS '字典项表';
COMMENT ON COLUMN pmis.pmis_dict_item.parent_id IS '父级 ID（0=根）';

CREATE INDEX idx_pmis_dict_item_type ON pmis.pmis_dict_item (type_code) WHERE deleted = 0;
CREATE INDEX idx_pmis_dict_item_status ON pmis.pmis_dict_item (status) WHERE deleted = 0;

-- 字典版本表
CREATE TABLE pmis.pmis_dict_version (
    id              BIGSERIAL      PRIMARY KEY,
    type_code       VARCHAR(64)    NOT NULL,
    version         VARCHAR(32)    NOT NULL,
    change_log      TEXT,
    effective_date  TIMESTAMP      NOT NULL,
    created_by      BIGINT         NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis.pmis_dict_version IS '字典版本表';

-- ====================================================================
-- 2. RBAC 权限模块
-- ====================================================================

-- 角色表
CREATE TABLE pmis.pmis_role (
    id              BIGSERIAL      PRIMARY KEY,
    role_code       VARCHAR(64)    NOT NULL,
    role_name       VARCHAR(64)    NOT NULL,
    description     TEXT,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    data_scope      VARCHAR(16)    NOT NULL DEFAULT 'SELF',
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_role_code UNIQUE (role_code, deleted)
);
COMMENT ON TABLE pmis.pmis_role IS '角色表';
COMMENT ON COLUMN pmis.pmis_role.data_scope IS '数据权限: ALL/DEPT/SELF/CUSTOM';

CREATE INDEX idx_pmis_role_status ON pmis.pmis_role (status) WHERE deleted = 0;

-- 权限/菜单表
CREATE TABLE pmis.pmis_permission (
    id              BIGSERIAL      PRIMARY KEY,
    parent_id       BIGINT         NOT NULL DEFAULT 0,
    perm_code       VARCHAR(128)   NOT NULL,
    perm_name       VARCHAR(64)    NOT NULL,
    perm_type       VARCHAR(16)    NOT NULL,
    path            VARCHAR(255),
    component       VARCHAR(255),
    icon            VARCHAR(64),
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    visible         SMALLINT       NOT NULL DEFAULT 1,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_permission_code UNIQUE (perm_code, deleted)
);
COMMENT ON TABLE pmis.pmis_permission IS '权限/菜单表';
COMMENT ON COLUMN pmis.pmis_permission.perm_type IS 'MENU/BUTTON/API';
COMMENT ON COLUMN pmis.pmis_permission.perm_code IS '权限标识 (例: system:user:create)';

CREATE INDEX idx_pmis_permission_parent ON pmis.pmis_permission (parent_id);
CREATE INDEX idx_pmis_permission_type ON pmis.pmis_permission (perm_type) WHERE deleted = 0;

-- 用户-角色关联表
CREATE TABLE pmis.pmis_user_role (
    id              BIGSERIAL      PRIMARY KEY,
    user_id         BIGINT         NOT NULL,
    role_id         BIGINT         NOT NULL,
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_user_role UNIQUE (user_id, role_id, deleted)
);
COMMENT ON TABLE pmis.pmis_user_role IS '用户-角色关联表';

CREATE INDEX idx_pmis_user_role_user ON pmis.pmis_user_role (user_id) WHERE deleted = 0;
CREATE INDEX idx_pmis_user_role_role ON pmis.pmis_user_role (role_id) WHERE deleted = 0;

-- 角色-权限关联表
CREATE TABLE pmis.pmis_role_permission (
    id              BIGSERIAL      PRIMARY KEY,
    role_id         BIGINT         NOT NULL,
    permission_id   BIGINT         NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_role_permission UNIQUE (role_id, permission_id, deleted)
);
COMMENT ON TABLE pmis.pmis_role_permission IS '角色-权限关联表';

-- ====================================================================
-- 3. 组织/人员模块
-- ====================================================================

-- 部门表
CREATE TABLE pmis.pmis_department (
    id              BIGSERIAL      PRIMARY KEY,
    dept_code       VARCHAR(64)    NOT NULL,
    dept_name       VARCHAR(128)   NOT NULL,
    parent_id       BIGINT         NOT NULL DEFAULT 0,
    dept_path       VARCHAR(512),
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    leader_id       BIGINT,
    phone           VARCHAR(32),
    email           VARCHAR(128),
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_department_code UNIQUE (dept_code, deleted)
);
COMMENT ON TABLE pmis.pmis_department IS '部门表';
COMMENT ON COLUMN pmis.pmis_department.dept_path IS '部门路径 (例: /1/3/5)';

CREATE INDEX idx_pmis_department_parent ON pmis.pmis_department (parent_id);
CREATE INDEX idx_pmis_department_status ON pmis.pmis_department (status) WHERE deleted = 0;

-- 岗位表
CREATE TABLE pmis.pmis_position (
    id              BIGSERIAL      PRIMARY KEY,
    position_code   VARCHAR(64)    NOT NULL,
    position_name   VARCHAR(128)   NOT NULL,
    department_id   BIGINT         NOT NULL,
    level_code      VARCHAR(8)     NOT NULL,
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_position_code UNIQUE (position_code, deleted)
);
COMMENT ON TABLE pmis.pmis_position IS '岗位表';

CREATE INDEX idx_pmis_position_dept ON pmis.pmis_position (department_id) WHERE deleted = 0;

-- 职级表 (L1-L18)
CREATE TABLE pmis.pmis_job_level (
    id              BIGSERIAL      PRIMARY KEY,
    level_code      VARCHAR(8)     NOT NULL,
    level_name      VARCHAR(64)    NOT NULL,
    level_segment   VARCHAR(16)    NOT NULL,
    sort_order      INTEGER        NOT NULL,
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_job_level_code UNIQUE (level_code, deleted)
);
COMMENT ON TABLE pmis.pmis_job_level IS '职级表 (L1-L18)';
COMMENT ON COLUMN pmis.pmis_job_level.level_segment IS '职级段: PRIMARY(初级)/MIDDLE(中级)/SENIOR(高级)/EXPERT(专家)/STRATEGIC(战略)';

-- 职级费率表 (对外人天 / 对内人天)
CREATE TABLE pmis.pmis_job_level_rate (
    id                  BIGSERIAL      PRIMARY KEY,
    level_code          VARCHAR(8)     NOT NULL,
    external_daily      NUMERIC(10,2)  NOT NULL,
    internal_daily      NUMERIC(10,2)  NOT NULL,
    base_salary         NUMERIC(10,2)  NOT NULL,
    social_company      NUMERIC(10,2)  NOT NULL,
    social_personal     NUMERIC(10,2)  NOT NULL,
    fund_company        NUMERIC(10,2)  NOT NULL,
    fund_personal       NUMERIC(10,2)  NOT NULL,
    take_home           NUMERIC(10,2)  NOT NULL,
    total_cost          NUMERIC(10,2)  NOT NULL,
    billable_target     NUMERIC(5,4)   NOT NULL,
    effective_date      DATE           NOT NULL,
    expire_date         DATE,
    version             INTEGER        NOT NULL DEFAULT 1,
    description         TEXT,
    created_by          BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT         NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_job_level_rate UNIQUE (level_code, version, deleted)
);
COMMENT ON TABLE pmis.pmis_job_level_rate IS '职级费率表 (双费率)';
COMMENT ON COLUMN pmis.pmis_job_level_rate.external_daily IS '对外人天 (元/天)';
COMMENT ON COLUMN pmis.pmis_job_level_rate.internal_daily IS '对内人天 (元/天)';
COMMENT ON COLUMN pmis.pmis_job_level_rate.billable_target IS '可计费利用率目标';

CREATE INDEX idx_pmis_job_level_rate_code ON pmis.pmis_job_level_rate (level_code) WHERE deleted = 0;
CREATE INDEX idx_pmis_job_level_rate_effective ON pmis.pmis_job_level_rate (effective_date, expire_date);

-- 员工表
CREATE TABLE pmis.pmis_employee (
    id              BIGSERIAL      PRIMARY KEY,
    user_id         BIGINT         NOT NULL,
    emp_code        VARCHAR(64)    NOT NULL,
    emp_name        VARCHAR(64)    NOT NULL,
    id_card         VARCHAR(32),
    id_card_enc     VARCHAR(255),
    gender          VARCHAR(8)     NOT NULL DEFAULT 'U',
    birth_date      DATE,
    phone           VARCHAR(32),
    phone_enc       VARCHAR(255),
    email           VARCHAR(128),
    department_id   BIGINT         NOT NULL,
    position_id     BIGINT,
    level_code      VARCHAR(8)     NOT NULL,
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
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_emp_code UNIQUE (emp_code, deleted)
);
COMMENT ON TABLE pmis.pmis_employee IS '员工表';
COMMENT ON COLUMN pmis.pmis_employee.work_status IS 'ACTIVE/LEAVE/SUSPEND';
COMMENT ON COLUMN pmis.pmis_employee.bench_status IS 'YES/NO';

CREATE INDEX idx_pmis_emp_user ON pmis.pmis_employee (user_id);
CREATE INDEX idx_pmis_emp_dept ON pmis.pmis_employee (department_id) WHERE deleted = 0;
CREATE INDEX idx_pmis_emp_level ON pmis.pmis_employee (level_code) WHERE deleted = 0;
CREATE INDEX idx_pmis_emp_bench ON pmis.pmis_employee (bench_status, bench_start) WHERE deleted = 0;

-- 员工标签表
CREATE TABLE pmis.pmis_employee_tag (
    id              BIGSERIAL      PRIMARY KEY,
    employee_id     BIGINT         NOT NULL,
    tag_type        VARCHAR(32)    NOT NULL,
    tag_code        VARCHAR(64)    NOT NULL,
    tag_value       VARCHAR(255),
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis.pmis_employee_tag IS '员工标签表';
COMMENT ON COLUMN pmis.pmis_employee_tag.tag_type IS 'TECH_STACK/INDUSTRY/CERTIFICATE/SKILL';

CREATE INDEX idx_pmis_emp_tag_emp ON pmis.pmis_employee_tag (employee_id);
CREATE INDEX idx_pmis_emp_tag_code ON pmis.pmis_employee_tag (tag_code);

-- ====================================================================
-- 4. 用户账号
-- ====================================================================

-- 用户账号表
CREATE TABLE pmis.pmis_user_account (
    id              BIGSERIAL      PRIMARY KEY,
    username        VARCHAR(64)    NOT NULL,
    password        VARCHAR(128)   NOT NULL,
    salt            VARCHAR(32)    NOT NULL,
    employee_id     BIGINT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    last_login_time TIMESTAMP,
    last_login_ip   VARCHAR(64),
    login_fail_count INTEGER       NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP,
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_user_username UNIQUE (username, deleted)
);
COMMENT ON TABLE pmis.pmis_user_account IS '用户账号表';

CREATE INDEX idx_pmis_user_status ON pmis.pmis_user_account (status) WHERE deleted = 0;

-- ====================================================================
-- 5. 通知中心
-- ====================================================================

CREATE TABLE pmis.pmis_notification (
    id              BIGSERIAL      PRIMARY KEY,
    title           VARCHAR(255)   NOT NULL,
    content         TEXT,
    level           VARCHAR(16)    NOT NULL DEFAULT 'INFO',
    category        VARCHAR(32)    NOT NULL,
    sender_id       BIGINT,
    receiver_id     BIGINT         NOT NULL,
    biz_type        VARCHAR(64),
    biz_id          VARCHAR(64),
    read_status     SMALLINT       NOT NULL DEFAULT 0,
    read_time       TIMESTAMP,
    expired_at      TIMESTAMP,
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis.pmis_notification IS '通知表';
COMMENT ON COLUMN pmis.pmis_notification.level IS 'INFO/WARN/ERROR/URGENT';
COMMENT ON COLUMN pmis.pmis_notification.category IS 'SYSTEM/WORKFLOW/ALERT/TODO';

CREATE INDEX idx_pmis_notif_receiver ON pmis.pmis_notification (receiver_id, read_status) WHERE deleted = 0;
CREATE INDEX idx_pmis_notif_biz ON pmis.pmis_notification (biz_type, biz_id) WHERE deleted = 0;

-- ====================================================================
-- 6. 系统配置
-- ====================================================================

CREATE TABLE pmis_cfg.pmis_config (
    id              BIGSERIAL      PRIMARY KEY,
    config_group    VARCHAR(64)    NOT NULL,
    config_key      VARCHAR(128)   NOT NULL,
    config_value    TEXT,
    value_type      VARCHAR(16)    NOT NULL DEFAULT 'STRING',
    default_value   TEXT,
    description     TEXT,
    is_public       SMALLINT       NOT NULL DEFAULT 0,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_config_key UNIQUE (config_group, config_key, deleted)
);
COMMENT ON TABLE pmis_cfg.pmis_config IS '系统配置表';
COMMENT ON COLUMN pmis_cfg.pmis_config.value_type IS 'STRING/NUMBER/BOOLEAN/JSON';

CREATE INDEX idx_pmis_config_group ON pmis_cfg.pmis_config (config_group) WHERE deleted = 0;

-- ====================================================================
-- 7. 操作日志
-- ====================================================================

CREATE TABLE pmis_log.pmis_operation_log (
    id              BIGSERIAL      PRIMARY KEY,
    user_id         BIGINT,
    username        VARCHAR(64),
    module          VARCHAR(64)    NOT NULL,
    action          VARCHAR(64)    NOT NULL,
    method          VARCHAR(8),
    request_url     VARCHAR(512),
    request_method  VARCHAR(255),
    request_params  TEXT,
    response_data   TEXT,
    ip              VARCHAR(64),
    user_agent      VARCHAR(512),
    cost_ms         BIGINT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'SUCCESS',
    error_message   TEXT,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE pmis_log.pmis_operation_log IS '操作日志表';

CREATE INDEX idx_pmis_oplog_user ON pmis_log.pmis_operation_log (user_id);
CREATE INDEX idx_pmis_oplog_module ON pmis_log.pmis_operation_log (module, action);
CREATE INDEX idx_pmis_oplog_created ON pmis_log.pmis_operation_log (created_at);

-- ====================================================================
-- 8. 初始化数据
-- ====================================================================

-- 初始化超级管理员
-- 默认 admin 账号 (盐: pmis_salt_8, 密码: admin123, 哈希: MD5('admin123pmis_salt_8'))
INSERT INTO pmis.pmis_user_account (username, password, salt, status, created_by)
VALUES ('admin', MD5('admin123' || 'pmis_salt_8'), 'pmis_salt_8', 'ENABLED', 0)
ON CONFLICT (username, deleted) DO NOTHING;

-- 初始化职级 (L1-L18)
INSERT INTO pmis.pmis_job_level (level_code, level_name, level_segment, sort_order, description, created_by)
VALUES
    ('L1',  '助理工程师',   'PRIMARY',   1,  '0-1 年（应届大专/中专）', 0),
    ('L2',  '初级开发工程师','PRIMARY',  2,  '0-1 年（应届本科）', 0),
    ('L3',  '开发工程师',   'PRIMARY',   3,  '1-2 年', 0),
    ('L4',  '中级工程师',   'MIDDLE',    4,  '2-3 年', 0),
    ('L5',  '高级工程师',   'MIDDLE',    5,  '3-5 年', 0),
    ('L6',  '资深工程师',   'MIDDLE',    6,  '4-6 年', 0),
    ('L7',  '高级工程师/项目经理', 'SENIOR',  7,  '5-7 年', 0),
    ('L8',  '资深工程师/高级项目经理','SENIOR',  8,  '6-8 年', 0),
    ('L9',  '架构师/项目总监', 'SENIOR',  9,  '7-10 年', 0),
    ('L10', '资深架构师',   'EXPERT',   10,  '8-12 年', 0),
    ('L11', '技术专家/交付总监', 'EXPERT', 11, '10-13 年', 0),
    ('L12', '资深技术专家', 'EXPERT',   12, '12-15 年', 0),
    ('L13', '首席架构师',   'STRATEGIC', 13, '13-17 年', 0),
    ('L14', '技术总监/事业部副总经理', 'STRATEGIC', 14, '15-20 年', 0),
    ('L15', 'CTO/事业部总经理', 'STRATEGIC', 15, '20 年以上', 0),
    ('L16', '技术副总裁/首席架构师', 'STRATEGIC', 16, '22 年以上', 0),
    ('L17', '执行副总裁/CTO', 'STRATEGIC', 17, '25 年以上', 0),
    ('L18', '董事会技术顾问/首席科学家', 'STRATEGIC', 18, '28 年以上', 0)
ON CONFLICT DO NOTHING;

-- 初始化职级费率 (V3.2 双列直出)
INSERT INTO pmis.pmis_job_level_rate
(level_code, external_daily, internal_daily, base_salary, social_company, social_personal, fund_company, fund_personal, take_home, total_cost, billable_target, effective_date, version, created_by)
VALUES
    ('L1',  400,  200,  4000,  980,  430,  200,  200,  3370,  5180,  0.78, '2026-01-01', 1, 0),
    ('L2',  500,  250,  5000,  1225, 535,  250,  250,  4215,  6475,  0.78, '2026-01-01', 1, 0),
    ('L3',  600,  300,  6000,  1470, 640,  300,  300,  5060,  7770,  0.80, '2026-01-01', 1, 0),
    ('L4',  700,  350,  7000,  1715, 745,  350,  350,  5905,  9065,  0.82, '2026-01-01', 1, 0),
    ('L5',  800,  400,  8000,  1960, 850,  400,  400,  6750,  10360, 0.82, '2026-01-01', 1, 0),
    ('L6',  900,  450,  9000,  2205, 955,  450,  450,  7595,  11655, 0.82, '2026-01-01', 1, 0),
    ('L7',  1000, 500,  10000, 2450, 1060, 500,  500,  8440,  12950, 0.80, '2026-01-01', 1, 0),
    ('L8',  1100, 550,  11000, 2695, 1165, 550,  550,  9285,  14245, 0.80, '2026-01-01', 1, 0),
    ('L9',  1200, 600,  12000, 2940, 1270, 600,  600,  10130, 15540, 0.75, '2026-01-01', 1, 0),
    ('L10', 1300, 650,  13000, 3185, 1375, 650,  650,  10975, 16835, 0.70, '2026-01-01', 1, 0),
    ('L11', 1400, 700,  14000, 3430, 1480, 700,  700,  11820, 18130, 0.70, '2026-01-01', 1, 0),
    ('L12', 1500, 750,  15000, 3675, 1585, 750,  750,  12665, 19425, 0.65, '2026-01-01', 1, 0),
    ('L13', 1600, 800,  16000, 3920, 1690, 800,  800,  13510, 20720, 0.60, '2026-01-01', 1, 0),
    ('L14', 1700, 850,  17000, 4165, 1795, 850,  850,  14355, 22015, 0.55, '2026-01-01', 1, 0),
    ('L15', 1800, 900,  18000, 4410, 1900, 900,  900,  15200, 23310, 0.50, '2026-01-01', 1, 0),
    ('L16', 1900, 950,  19000, 4655, 2005, 950,  950,  16045, 24605, 0.45, '2026-01-01', 1, 0),
    ('L17', 2000, 1000, 20000, 4900, 2110, 1000, 1000, 16890, 25900, 0.40, '2026-01-01', 1, 0),
    ('L18', 2100, 1050, 21000, 5145, 2215, 1050, 1050, 17735, 27195, 0.40, '2026-01-01', 1, 0)
ON CONFLICT DO NOTHING;

-- 初始化根部门
INSERT INTO pmis.pmis_department (dept_code, dept_name, parent_id, dept_path, sort_order, status, created_by)
VALUES ('ROOT', '南京云顶数字科技有限公司', 0, '/1', 0, 'ENABLED', 0)
ON CONFLICT DO NOTHING;

-- 初始化超级管理员角色
INSERT INTO pmis.pmis_role (role_code, role_name, data_scope, sort_order, status, created_by)
VALUES ('SUPER_ADMIN', '超级管理员', 'ALL', 0, 'ENABLED', 0)
ON CONFLICT DO NOTHING;

-- 初始化字典类型（PRD 2.3 节要求）
INSERT INTO pmis.pmis_dict_type (type_code, type_name, description, created_by) VALUES
    ('procurement_method', '招采方式', '招采方式枚举', 0),
    ('project_type', '项目类型', '8 类项目类型', 0),
    ('product_type', '产品类型', '产品线', 0),
    ('delivery_mode', '开发交付方式', '驻场/远程/混合', 0),
    ('pricing_mode', '定价方式', '固定总价/单价/框架等', 0),
    ('settlement_mode', '结算方式', '里程碑/月度/季度等', 0),
    ('expense_category', '费用类别', '差旅/团建等', 0),
    ('procurement_category', '采购类别', '硬件/软件/服务等', 0),
    ('project_phase', '项目阶段', '调研/开发/测试等', 0),
    ('project_level', '项目级别', 'A/B/C 级', 0)
ON CONFLICT DO NOTHING;

-- 初始化项目类型字典项
INSERT INTO pmis.pmis_dict_item (type_code, item_code, item_value, sort_order, created_by) VALUES
    ('project_type', 'SYSTEM_DEV',     '系统开发',   1, 0),
    ('project_type', 'SYSTEM_INTEG',   '系统集成',   2, 0),
    ('project_type', 'SYSTEM_MAINT',   '系统维护',   3, 0),
    ('project_type', 'SOFTWARE_PROD',  '软件产品',   4, 0),
    ('project_type', 'HARDWARE_PROD',  '硬件产品',   5, 0),
    ('project_type', 'TECH_CONSULT',   '技术咨询',   6, 0),
    ('project_type', 'HARDWARE_MAINT', '硬件运维',   7, 0),
    ('project_type', 'STAFF_OUTSRC',   '人力外包服务', 8, 0)
ON CONFLICT DO NOTHING;

-- 初始化项目阶段字典项
INSERT INTO pmis.pmis_dict_item (type_code, item_code, item_value, sort_order, created_by) VALUES
    ('project_phase', 'REQUIREMENT', '需求调研', 1, 0),
    ('project_phase', 'DEVELOPMENT', '功能开发', 2, 0),
    ('project_phase', 'TESTING',     '测试阶段', 3, 0),
    ('project_phase', 'DEPLOYMENT',  '实施上线', 4, 0),
    ('project_phase', 'ACCEPTANCE',  '项目验收', 5, 0),
    ('project_phase', 'WARRANTY',    '质保运维', 6, 0)
ON CONFLICT DO NOTHING;

-- 初始化系统配置
INSERT INTO pmis_cfg.pmis_config (config_group, config_key, config_value, value_type, description, created_by) VALUES
    ('system', 'system.name', 'PMIS 项目运营管理系统', 'STRING', '系统名称', 0),
    ('system', 'system.version', '1.0.0', 'STRING', '系统版本', 0),
    ('rate', 'rate.social.company.rate', '0.245', 'NUMBER', '公司社保比例', 0),
    ('rate', 'rate.fund.company.rate', '0.05', 'NUMBER', '公司公积金比例', 0),
    ('rate', 'rate.workdays.per.month', '21.75', 'NUMBER', '月计薪天数', 0),
    ('rate', 'rate.hours.per.day', '8', 'NUMBER', '日标准工时', 0),
    ('workflow', 'workflow.engine', 'flowable', 'STRING', '工作流引擎', 0),
    ('alert', 'alert.cpi.yellow', '0.95', 'NUMBER', 'CPI 黄色预警阈值', 0),
    ('alert', 'alert.cpi.red', '0.85', 'NUMBER', 'CPI 红色预警阈值', 0),
    ('alert', 'alert.spi.yellow', '0.90', 'NUMBER', 'SPI 黄色预警阈值', 0),
    ('alert', 'alert.spi.red', '0.80', 'NUMBER', 'SPI 红色预警阈值', 0),
    ('alert', 'alert.bench.days.yellow', '7', 'NUMBER', 'Bench 黄色预警天数', 0),
    ('alert', 'alert.bench.days.red', '15', 'NUMBER', 'Bench 红色预警天数', 0)
ON CONFLICT DO NOTHING;
