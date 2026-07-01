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
-- ====================================================================
-- 1. 字典/枚举值模块
-- ====================================================================

-- 字典类型表
CREATE TABLE pmis_dict_type (
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
COMMENT ON TABLE pmis_dict_type IS '字典类型表: 业务字典分类定义(如项目类型、招采方式、计费方式)';
COMMENT ON COLUMN pmis_dict_type.id IS '主键 ID';
COMMENT ON COLUMN pmis_dict_type.type_code IS '字典类型编码(全局唯一,如 project_type/expense_category)';
COMMENT ON COLUMN pmis_dict_type.type_name IS '字典类型名称(中文展示名)';
COMMENT ON COLUMN pmis_dict_type.description IS '字典类型业务说明';
COMMENT ON COLUMN pmis_dict_type.status IS '启用状态: ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_dict_type.created_by IS '创建人 ID(0=系统初始化)';
COMMENT ON COLUMN pmis_dict_type.created_at IS '创建时间';
COMMENT ON COLUMN pmis_dict_type.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_dict_type.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_dict_type.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX idx_pmis_dict_type_status ON pmis_dict_type (status) WHERE deleted = 0;

-- 字典项表
CREATE TABLE pmis_dict_item (
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
COMMENT ON TABLE pmis_dict_item IS '字典项表: 字典类型下的具体枚举值(如项目类型下的 SYSTEM_DEV/T_M 等)';
COMMENT ON COLUMN pmis_dict_item.id IS '主键 ID';
COMMENT ON COLUMN pmis_dict_item.type_code IS '所属字典类型编码(关联 pmis_dict_type.type_code)';
COMMENT ON COLUMN pmis_dict_item.item_code IS '字典项编码(type_code 下唯一,如 SYSTEM_DEV/T_M)';
COMMENT ON COLUMN pmis_dict_item.item_value IS '字典项展示值(中文)';
COMMENT ON COLUMN pmis_dict_item.sort_order IS '字典项排序号(升序)';
COMMENT ON COLUMN pmis_dict_item.parent_id IS '父级字典项 ID(0=根,支持树形字典)';
COMMENT ON COLUMN pmis_dict_item.description IS '字典项业务说明';
COMMENT ON COLUMN pmis_dict_item.ext_json IS '扩展属性 JSONB(如颜色/图标/跳转链接)';
COMMENT ON COLUMN pmis_dict_item.status IS '启用状态: ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_dict_item.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_dict_item.created_at IS '创建时间';
COMMENT ON COLUMN pmis_dict_item.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_dict_item.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_dict_item.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX idx_pmis_dict_item_type ON pmis_dict_item (type_code) WHERE deleted = 0;
CREATE INDEX idx_pmis_dict_item_status ON pmis_dict_item (status) WHERE deleted = 0;

-- 字典版本表
CREATE TABLE pmis_dict_version (
    id              BIGSERIAL      PRIMARY KEY,
    type_code       VARCHAR(64)    NOT NULL,
    version         VARCHAR(32)    NOT NULL,
    change_log      TEXT,
    effective_date  TIMESTAMP      NOT NULL,
    created_by      BIGINT         NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_dict_version IS '字典版本表: 字典变更历史快照,支持回滚与变更审计';
COMMENT ON COLUMN pmis_dict_version.id IS '主键 ID';
COMMENT ON COLUMN pmis_dict_version.type_code IS '字典类型编码';
COMMENT ON COLUMN pmis_dict_version.version IS '版本号(语义化版本,如 1.0.0)';
COMMENT ON COLUMN pmis_dict_version.change_log IS '变更说明';
COMMENT ON COLUMN pmis_dict_version.effective_date IS '生效时间';
COMMENT ON COLUMN pmis_dict_version.created_by IS '发布人 ID';
COMMENT ON COLUMN pmis_dict_version.created_at IS '发布时间';
COMMENT ON COLUMN pmis_dict_version.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- ====================================================================
-- 2. RBAC 权限模块
-- ====================================================================

-- 角色表
CREATE TABLE pmis_role (
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
COMMENT ON TABLE pmis_role IS '角色表: RBAC 角色定义,关联权限与数据范围';
COMMENT ON COLUMN pmis_role.id IS '主键 ID';
COMMENT ON COLUMN pmis_role.role_code IS '角色编码(全局唯一,如 SUPER_ADMIN/PM)';
COMMENT ON COLUMN pmis_role.role_name IS '角色名称(中文展示名)';
COMMENT ON COLUMN pmis_role.description IS '角色业务说明(职责、适用场景)';
COMMENT ON COLUMN pmis_role.sort_order IS '角色排序号(升序)';
COMMENT ON COLUMN pmis_role.data_scope IS '数据权限范围: ALL 全部 / DEPT 本部门 / DEPT_AND_SUB 本部门及下级 / SELF 本人 / CUSTOM 自定义';
COMMENT ON COLUMN pmis_role.status IS '启用状态: ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_role.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_role.created_at IS '创建时间';
COMMENT ON COLUMN pmis_role.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_role.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_role.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX idx_pmis_role_status ON pmis_role (status) WHERE deleted = 0;

-- 权限/菜单表
CREATE TABLE pmis_permission (
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

CREATE INDEX idx_pmis_permission_parent ON pmis_permission (parent_id);
CREATE INDEX idx_pmis_permission_type ON pmis_permission (perm_type) WHERE deleted = 0;

-- 用户-角色关联表
CREATE TABLE pmis_user_role (
    id              BIGSERIAL      PRIMARY KEY,
    user_id         BIGINT         NOT NULL,
    role_id         BIGINT         NOT NULL,
    created_by      BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_user_role UNIQUE (user_id, role_id, deleted)
);
COMMENT ON TABLE pmis_user_role IS '用户-角色关联表: 多对多,用户可同时拥有多个角色';
COMMENT ON COLUMN pmis_user_role.id IS '主键 ID';
COMMENT ON COLUMN pmis_user_role.user_id IS '用户 ID(关联 pmis_user_account.id)';
COMMENT ON COLUMN pmis_user_role.role_id IS '角色 ID(关联 pmis_role.id)';
COMMENT ON COLUMN pmis_user_role.created_by IS '授权人 ID';
COMMENT ON COLUMN pmis_user_role.created_at IS '授权时间';
COMMENT ON COLUMN pmis_user_role.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX idx_pmis_user_role_user ON pmis_user_role (user_id) WHERE deleted = 0;
CREATE INDEX idx_pmis_user_role_role ON pmis_user_role (role_id) WHERE deleted = 0;

-- 角色-权限关联表
CREATE TABLE pmis_role_permission (
    id              BIGSERIAL      PRIMARY KEY,
    role_id         BIGINT         NOT NULL,
    permission_id   BIGINT         NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_pmis_role_permission UNIQUE (role_id, permission_id, deleted)
);
COMMENT ON TABLE pmis_role_permission IS '角色-权限关联表: 多对多,角色绑定具体可访问的权限点';
COMMENT ON COLUMN pmis_role_permission.id IS '主键 ID';
COMMENT ON COLUMN pmis_role_permission.role_id IS '角色 ID(关联 pmis_role.id)';
COMMENT ON COLUMN pmis_role_permission.permission_id IS '权限 ID(关联 pmis_permission.id)';
COMMENT ON COLUMN pmis_role_permission.created_at IS '授权时间';
COMMENT ON COLUMN pmis_role_permission.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- ====================================================================
-- 3. 组织/人员模块
-- ====================================================================

-- 部门表
CREATE TABLE pmis_department (
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
COMMENT ON TABLE pmis_department IS '部门表: 树形组织架构,支持多级嵌套与路径检索';
COMMENT ON COLUMN pmis_department.id IS '主键 ID';
COMMENT ON COLUMN pmis_department.dept_code IS '部门编码(全局唯一,如 TECH/HR)';
COMMENT ON COLUMN pmis_department.dept_name IS '部门名称';
COMMENT ON COLUMN pmis_department.parent_id IS '父级部门 ID(0=根)';
COMMENT ON COLUMN pmis_department.dept_path IS '部门路径(以斜杠分隔的祖先链路,如 /1/3/5,用于子树查询)';
COMMENT ON COLUMN pmis_department.sort_order IS '部门排序号(同级升序)';
COMMENT ON COLUMN pmis_department.leader_id IS '部门负责人 ID(关联 pmis_employee.id)';
COMMENT ON COLUMN pmis_department.phone IS '部门电话';
COMMENT ON COLUMN pmis_department.email IS '部门邮箱';
COMMENT ON COLUMN pmis_department.description IS '部门职责说明';
COMMENT ON COLUMN pmis_department.status IS '启用状态: ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_department.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_department.created_at IS '创建时间';
COMMENT ON COLUMN pmis_department.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_department.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_department.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX idx_pmis_department_parent ON pmis_department (parent_id);
CREATE INDEX idx_pmis_department_status ON pmis_department (status) WHERE deleted = 0;

-- 岗位表
CREATE TABLE pmis_position (
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

CREATE INDEX idx_pmis_position_dept ON pmis_position (department_id) WHERE deleted = 0;

-- 职级表 (L1-L18)
CREATE TABLE pmis_job_level (
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
COMMENT ON TABLE pmis_job_level IS '职级表: L1-L18 共 18 级,定义能力晋升阶梯';
COMMENT ON COLUMN pmis_job_level.id IS '主键 ID';
COMMENT ON COLUMN pmis_job_level.level_code IS '职级编码(L1-L18)';
COMMENT ON COLUMN pmis_job_level.level_name IS '职级名称(如助理工程师/开发工程师/架构师)';
COMMENT ON COLUMN pmis_job_level.level_segment IS '职级段: PRIMARY 初级(L1-L3) / MIDDLE 中级(L4-L6) / SENIOR 高级(L7-L9) / EXPERT 专家(L10-L12) / STRATEGIC 战略(L13-L18)';
COMMENT ON COLUMN pmis_job_level.sort_order IS '职级排序号(升序,L1=1)';
COMMENT ON COLUMN pmis_job_level.description IS '职级能力要求说明';
COMMENT ON COLUMN pmis_job_level.status IS '启用状态: ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_job_level.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_job_level.created_at IS '创建时间';
COMMENT ON COLUMN pmis_job_level.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_job_level.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_job_level.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- 职级费率表 (对外人天 / 对内人天)
CREATE TABLE pmis_job_level_rate (
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
COMMENT ON TABLE pmis_job_level_rate IS '职级费率表(双费率): 对外报价人天 / 对内成本人天 / 五险一金成本拆解,支持版本化生效';
COMMENT ON COLUMN pmis_job_level_rate.id IS '主键 ID';
COMMENT ON COLUMN pmis_job_level_rate.level_code IS '职级编码(L1-L18,关联 pmis_job_level.level_code)';
COMMENT ON COLUMN pmis_job_level_rate.external_daily IS '对外人天单价(元/天,用于向客户报价)';
COMMENT ON COLUMN pmis_job_level_rate.internal_daily IS '对内人天成本(元/天,用于内部利润核算)';
COMMENT ON COLUMN pmis_job_level_rate.base_salary IS '月度基础工资(元)';
COMMENT ON COLUMN pmis_job_level_rate.social_company IS '公司社保部分(元/月)';
COMMENT ON COLUMN pmis_job_level_rate.social_personal IS '个人社保部分(元/月,从工资扣除)';
COMMENT ON COLUMN pmis_job_level_rate.fund_company IS '公司公积金部分(元/月)';
COMMENT ON COLUMN pmis_job_level_rate.fund_personal IS '个人公积金部分(元/月,从工资扣除)';
COMMENT ON COLUMN pmis_job_level_rate.take_home IS '税后到手工资(元/月)';
COMMENT ON COLUMN pmis_job_level_rate.total_cost IS '公司总人力成本(元/月,=base_salary+social_company+fund_company)';
COMMENT ON COLUMN pmis_job_level_rate.billable_target IS '可计费利用率目标(0.0-1.0,如 0.78=78%)';
COMMENT ON COLUMN pmis_job_level_rate.effective_date IS '生效日期';
COMMENT ON COLUMN pmis_job_level_rate.expire_date IS '失效日期(NULL 表示长期有效)';
COMMENT ON COLUMN pmis_job_level_rate.version IS '版本号(同职级可有多版本,通过 effective_date 区分)';
COMMENT ON COLUMN pmis_job_level_rate.description IS '费率版本说明';
COMMENT ON COLUMN pmis_job_level_rate.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_job_level_rate.created_at IS '创建时间';
COMMENT ON COLUMN pmis_job_level_rate.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_job_level_rate.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_job_level_rate.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX idx_pmis_job_level_rate_code ON pmis_job_level_rate (level_code) WHERE deleted = 0;
CREATE INDEX idx_pmis_job_level_rate_effective ON pmis_job_level_rate (effective_date, expire_date);

-- 员工表
CREATE TABLE pmis_employee (
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
COMMENT ON COLUMN pmis_employee.level_code IS '职级编码(L1-L18,关联 pmis_job_level.level_code)';
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

CREATE INDEX idx_pmis_emp_user ON pmis_employee (user_id);
CREATE INDEX idx_pmis_emp_dept ON pmis_employee (department_id) WHERE deleted = 0;
CREATE INDEX idx_pmis_emp_level ON pmis_employee (level_code) WHERE deleted = 0;
CREATE INDEX idx_pmis_emp_bench ON pmis_employee (bench_status, bench_start) WHERE deleted = 0;

-- 员工标签表
CREATE TABLE pmis_employee_tag (
    id              BIGSERIAL      PRIMARY KEY,
    employee_id     BIGINT         NOT NULL,
    tag_type        VARCHAR(32)    NOT NULL,
    tag_code        VARCHAR(64)    NOT NULL,
    tag_value       VARCHAR(255),
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_employee_tag IS '员工标签表: 技能/行业/资质/认证,支持资源池精准匹配';
COMMENT ON COLUMN pmis_employee_tag.id IS '主键 ID';
COMMENT ON COLUMN pmis_employee_tag.employee_id IS '员工 ID(关联 pmis_employee.id)';
COMMENT ON COLUMN pmis_employee_tag.tag_type IS '标签类型: TECH_STACK 技术栈 / INDUSTRY 行业经验 / DOMAIN 业务领域 / CERTIFICATE 资质证书 / SKILL 软技能';
COMMENT ON COLUMN pmis_employee_tag.tag_code IS '标签编码(同类型下唯一,如 Java/Python/FinTech)';
COMMENT ON COLUMN pmis_employee_tag.tag_value IS '标签值(中文展示名)';
COMMENT ON COLUMN pmis_employee_tag.created_at IS '创建时间';
COMMENT ON COLUMN pmis_employee_tag.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX idx_pmis_emp_tag_emp ON pmis_employee_tag (employee_id);
CREATE INDEX idx_pmis_emp_tag_code ON pmis_employee_tag (tag_code);

-- ====================================================================
-- 4. 用户账号
-- ====================================================================

-- 用户账号表
CREATE TABLE pmis_user_account (
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
COMMENT ON COLUMN pmis_user_account.data_scope IS '数据权限范围: ALL 全部 / DEPT 本部门 / DEPT_AND_SUB 本部门及下级 / SELF 本人 / CUSTOM 自定义';
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

CREATE INDEX idx_pmis_user_status ON pmis_user_account (status) WHERE deleted = 0;

-- ====================================================================
-- 5. 通知中心
-- ====================================================================

CREATE TABLE pmis_notification (
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
COMMENT ON TABLE pmis_notification IS '通知表: 系统消息/待办/预警统一入口,支持业务关联跳转';
COMMENT ON COLUMN pmis_notification.id IS '主键 ID';
COMMENT ON COLUMN pmis_notification.title IS '通知标题';
COMMENT ON COLUMN pmis_notification.content IS '通知内容(支持富文本/Markdown)';
COMMENT ON COLUMN pmis_notification.level IS '通知级别: INFO 提示 / WARN 警告 / ERROR 错误 / URGENT 紧急';
COMMENT ON COLUMN pmis_notification.category IS '通知分类: SYSTEM 系统消息 / WORKFLOW 流程审批 / ALERT 预警通知 / TODO 待办 / ANNOUNCE 公告';
COMMENT ON COLUMN pmis_notification.sender_id IS '发送人 ID(系统通知为 0)';
COMMENT ON COLUMN pmis_notification.receiver_id IS '接收人 ID(关联 pmis_employee.id)';
COMMENT ON COLUMN pmis_notification.biz_type IS '关联业务类型(如 contract/invoice/risk)';
COMMENT ON COLUMN pmis_notification.biz_id IS '关联业务单据 ID';
COMMENT ON COLUMN pmis_notification.read_status IS '已读状态: 0 未读 / 1 已读';
COMMENT ON COLUMN pmis_notification.read_time IS '阅读时间';
COMMENT ON COLUMN pmis_notification.expired_at IS '过期时间(过期后不再展示)';
COMMENT ON COLUMN pmis_notification.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_notification.created_at IS '发送时间';
COMMENT ON COLUMN pmis_notification.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_notification.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_notification.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX idx_pmis_notif_receiver ON pmis_notification (receiver_id, read_status) WHERE deleted = 0;
CREATE INDEX idx_pmis_notif_biz ON pmis_notification (biz_type, biz_id) WHERE deleted = 0;

-- ====================================================================
-- 6. 系统配置
-- ====================================================================

CREATE TABLE pmis_config (
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
COMMENT ON TABLE pmis_config IS '系统配置表: 业务可热更新的参数(预警阈值/费率/工作流引擎等),按 group 分组';
COMMENT ON COLUMN pmis_config.id IS '主键 ID';
COMMENT ON COLUMN pmis_config.config_group IS '配置分组(如 alert/rate/workflow/system)';
COMMENT ON COLUMN pmis_config.config_key IS '配置键(同组下唯一,如 alert.cpi.yellow)';
COMMENT ON COLUMN pmis_config.config_value IS '配置值';
COMMENT ON COLUMN pmis_config.value_type IS '值类型: STRING 字符串 / NUMBER 数值 / BOOLEAN 布尔 / JSON JSON 对象';
COMMENT ON COLUMN pmis_config.default_value IS '默认值(配置缺失时回退使用)';
COMMENT ON COLUMN pmis_config.description IS '配置项说明';
COMMENT ON COLUMN pmis_config.is_public IS '是否对前端公开: 1 公开 / 0 仅后端(避免敏感配置泄漏)';
COMMENT ON COLUMN pmis_config.sort_order IS '排序号';
COMMENT ON COLUMN pmis_config.status IS '启用状态: ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_config.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_config.created_at IS '创建时间';
COMMENT ON COLUMN pmis_config.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_config.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_config.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX idx_pmis_config_group ON pmis_config (config_group) WHERE deleted = 0;

-- ====================================================================
-- 7. 操作日志
-- ====================================================================

CREATE TABLE pmis_operation_log (
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
COMMENT ON TABLE pmis_operation_log IS '操作日志表: 用户关键操作全量留存(模块/动作/入参/出参/耗时/IP),用于审计与问题排查';
COMMENT ON COLUMN pmis_operation_log.id IS '主键 ID';
COMMENT ON COLUMN pmis_operation_log.user_id IS '操作用户 ID';
COMMENT ON COLUMN pmis_operation_log.username IS '操作用户名';
COMMENT ON COLUMN pmis_operation_log.module IS '操作模块(如 project/contract/finance)';
COMMENT ON COLUMN pmis_operation_log.action IS '操作动作(如 create/update/delete/approve)';
COMMENT ON COLUMN pmis_operation_log.method IS 'HTTP 方法(GET/POST/PUT/DELETE)';
COMMENT ON COLUMN pmis_operation_log.request_url IS '请求 URL';
COMMENT ON COLUMN pmis_operation_log.request_method IS '请求方法签名(如 ProjectController#create)';
COMMENT ON COLUMN pmis_operation_log.request_params IS '请求参数 JSON';
COMMENT ON COLUMN pmis_operation_log.response_data IS '响应数据 JSON(失败时为空)';
COMMENT ON COLUMN pmis_operation_log.ip IS '客户端 IP';
COMMENT ON COLUMN pmis_operation_log.user_agent IS '浏览器 User-Agent';
COMMENT ON COLUMN pmis_operation_log.cost_ms IS '接口耗时(毫秒)';
COMMENT ON COLUMN pmis_operation_log.status IS '操作状态: SUCCESS 成功 / FAILED 失败';
COMMENT ON COLUMN pmis_operation_log.error_message IS '错误信息(失败时填充)';
COMMENT ON COLUMN pmis_operation_log.created_at IS '操作时间';

CREATE INDEX idx_pmis_oplog_user ON pmis_operation_log (user_id);
CREATE INDEX idx_pmis_oplog_module ON pmis_operation_log (module, action);
CREATE INDEX idx_pmis_oplog_created ON pmis_operation_log (created_at);

-- ====================================================================
-- 8. 初始化数据
-- ====================================================================

-- 初始化超级管理员
-- 默认 admin 账号 (盐: pmis_salt_8, 密码: admin123, 哈希: MD5('admin123pmis_salt_8'))
INSERT INTO pmis_user_account (username, password, salt, status, created_by)
VALUES ('admin', MD5('admin123' || 'pmis_salt_8'), 'pmis_salt_8', 'ENABLED', 0)
ON CONFLICT (username, deleted) DO NOTHING;

-- 初始化职级 (L1-L18)
INSERT INTO pmis_job_level (level_code, level_name, level_segment, sort_order, description, created_by)
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
INSERT INTO pmis_job_level_rate
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
INSERT INTO pmis_department (dept_code, dept_name, parent_id, dept_path, sort_order, status, created_by)
VALUES ('ROOT', '南京云顶数字科技有限公司', 0, '/1', 0, 'ENABLED', 0)
ON CONFLICT DO NOTHING;

-- 初始化超级管理员角色
INSERT INTO pmis_role (role_code, role_name, data_scope, sort_order, status, created_by)
VALUES ('SUPER_ADMIN', '超级管理员', 'ALL', 0, 'ENABLED', 0)
ON CONFLICT DO NOTHING;

-- 初始化字典类型（PRD 2.3 节要求）
INSERT INTO pmis_dict_type (type_code, type_name, description, created_by) VALUES
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
INSERT INTO pmis_dict_item (type_code, item_code, item_value, sort_order, created_by) VALUES
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
INSERT INTO pmis_dict_item (type_code, item_code, item_value, sort_order, created_by) VALUES
    ('project_phase', 'REQUIREMENT', '需求调研', 1, 0),
    ('project_phase', 'DEVELOPMENT', '功能开发', 2, 0),
    ('project_phase', 'TESTING',     '测试阶段', 3, 0),
    ('project_phase', 'DEPLOYMENT',  '实施上线', 4, 0),
    ('project_phase', 'ACCEPTANCE',  '项目验收', 5, 0),
    ('project_phase', 'WARRANTY',    '质保运维', 6, 0)
ON CONFLICT DO NOTHING;

-- 初始化系统配置
INSERT INTO pmis_config (config_group, config_key, config_value, value_type, description, created_by) VALUES
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
