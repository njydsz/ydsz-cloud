-- ====================================================================
-- System.Collections.Hashtable[common]
-- Module: common
-- Version: V1.0.0
-- Target: PostgreSQL 18
-- Description: 鏈枃浠剁敱 deploy/sql/V1.0.0.sql 鎷嗗垎鐢熸垚
--   浠呬緵鍗曠嫭鍒濆鍖栧搴旀ā鍧楁椂浣跨敤; 瀹屾暣鍒濆鍖栬浣跨敤 V1.0.0.sql
-- ====================================================================

-- ============================ [001] init pmis schema ============================

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
CREATE TABLE IF NOT EXISTS pmis_dict_type(
    id              VARCHAR(20)      PRIMARY KEY,
    type_code       VARCHAR(64)    NOT NULL,
    type_name       VARCHAR(128)   NOT NULL,
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    -- 数据完整性约束
    CONSTRAINT uk_pmis_dict_type_code UNIQUE (type_code, deleted),
    CONSTRAINT ck_pdt_status_enum    CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pdt_deleted_enum   CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE pmis_dict_type IS '字典类型表: 业务字典分类定义(如项目类型、招采方式、计费方式)';
COMMENT ON COLUMN pmis_dict_type.id IS '主键 ID';
COMMENT ON COLUMN pmis_dict_type.type_code IS '字典类型编码(全局唯一,如 project_type/expense_category)';
COMMENT ON COLUMN pmis_dict_type.type_name IS '字典类型名称(中文展示名)';
COMMENT ON COLUMN pmis_dict_type.description IS '字典类型业务说明';
COMMENT ON COLUMN pmis_dict_type.status IS '启用状态: ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_dict_type.created_by IS '创建人 ID(SYSTEM=系统初始化)';
COMMENT ON COLUMN pmis_dict_type.created_at IS '创建时间';
COMMENT ON COLUMN pmis_dict_type.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_dict_type.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_dict_type.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';
COMMENT ON COLUMN pmis_dict_type.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_dict_type_status ON pmis_dict_type (status) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmis_dict_type_tenant_created
    ON pmis_dict_type (tenant_id, created_at DESC) WHERE deleted = 0;

-- 字典项表
CREATE TABLE IF NOT EXISTS pmis_dict_item(
    id              VARCHAR(20)      PRIMARY KEY,
    type_code       VARCHAR(64)    NOT NULL,
    item_code       VARCHAR(64)    NOT NULL,
    item_value      VARCHAR(255)   NOT NULL,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    parent_id       VARCHAR(20)         NOT NULL DEFAULT 0,
    description     TEXT,
    ext_json        JSONB,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    -- 数据完整性约束
    CONSTRAINT uk_pmis_dict_item UNIQUE (type_code, item_code, deleted),
    CONSTRAINT ck_pdi_status_enum   CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pdi_deleted_enum  CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_pdi_sort_nonneg   CHECK (sort_order >= 0)
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

CREATE INDEX IF NOT EXISTS idx_pmis_dict_item_type ON pmis_dict_item (type_code) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmis_dict_item_status ON pmis_dict_item (status) WHERE deleted = 0;

-- 字典版本表
CREATE TABLE IF NOT EXISTS pmis_dict_version(
    id              VARCHAR(20)      PRIMARY KEY,
    type_code       VARCHAR(64)    NOT NULL,
    version         VARCHAR(32)    NOT NULL,
    change_log      TEXT,
    effective_date  TIMESTAMP      NOT NULL,
    created_by      VARCHAR(20)         NOT NULL,
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
CREATE TABLE IF NOT EXISTS pmis_role(
    id              VARCHAR(20)      PRIMARY KEY,
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
    id              VARCHAR(20)      PRIMARY KEY,
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
    id              VARCHAR(20)      PRIMARY KEY,
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
    id              VARCHAR(20)      PRIMARY KEY,
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

-- ====================================================================
-- 3. 组织/人员模块
-- ====================================================================

-- 部门表
CREATE TABLE IF NOT EXISTS pmis_department(
    id              VARCHAR(20)      PRIMARY KEY,
    dept_code       VARCHAR(64)    NOT NULL,
    dept_name       VARCHAR(128)   NOT NULL,
    parent_id       VARCHAR(20)         NOT NULL DEFAULT 0,
    dept_path       VARCHAR(512),
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    leader_id       VARCHAR(20),
    phone           VARCHAR(32),
    email           VARCHAR(128),
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_department_code UNIQUE (dept_code, deleted),
    CONSTRAINT ck_pd_status_enum  CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pd_deleted_enum CHECK (deleted IN (0, 1))
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
COMMENT ON COLUMN pmis_department.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_department_parent ON pmis_department (parent_id);
CREATE INDEX IF NOT EXISTS idx_pmis_department_status ON pmis_department (status) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_department_tenant ON pmis_department(tenant_id);
CREATE INDEX IF NOT EXISTS idx_department_tenant_created
    ON pmis_department(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmis_dept_leader
    ON pmis_department(leader_id) WHERE deleted = 0;

-- 岗位表
CREATE TABLE IF NOT EXISTS pmis_position(
    id              VARCHAR(20)      PRIMARY KEY,
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

-- 职级表 (L1-L18)
CREATE TABLE IF NOT EXISTS pmis_job_level(
    id              VARCHAR(20)      PRIMARY KEY,
    level_code      VARCHAR(8)     NOT NULL,
    level_name      VARCHAR(64)    NOT NULL,
    level_segment   VARCHAR(16)    NOT NULL,
    sort_order      INTEGER        NOT NULL,
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_job_level_code UNIQUE (level_code, deleted),
    CONSTRAINT ck_pjl_segment     CHECK (level_segment IN ('PRIMARY', 'MIDDLE', 'SENIOR', 'EXPERT', 'STRATEGIC')),
    CONSTRAINT ck_pjl_status_enum CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pjl_deleted_enum CHECK (deleted IN (0, 1))
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
COMMENT ON COLUMN pmis_job_level.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_job_level_tenant ON pmis_job_level(tenant_id);
CREATE INDEX IF NOT EXISTS idx_job_level_tenant_sort
    ON pmis_job_level(tenant_id, sort_order) WHERE deleted = 0;

-- 职级费率表 (对外人天 / 对内人天)
CREATE TABLE IF NOT EXISTS pmis_job_level_rate(
    id                  VARCHAR(20)      PRIMARY KEY,
    level_code          VARCHAR(8)     NOT NULL,
    external_daily      NUMERIC(10,2)  NOT NULL,
    internal_daily      NUMERIC(10,2)  NOT NULL,
    base_salary         NUMERIC(10,2)  NOT NULL,
    social_company      NUMERIC(10,2)  NOT NULL,
    social_personal     NUMERIC(10,2)  NOT NULL,
    fund_company        NUMERIC(10,2)  NOT NULL,
    fund_personal       NUMERIC(10,2)  NOT NULL,
    take_home           NUMERIC(10,2)  NOT NULL,
    travel_reimbursement NUMERIC(10,2) NOT NULL DEFAULT 0,
    travel_allowance    NUMERIC(10,2)  NOT NULL DEFAULT 0,
    total_cost          NUMERIC(10,2)  NOT NULL,
    billable_target     NUMERIC(5,4)   NOT NULL,
    effective_date      DATE           NOT NULL,
    expire_date         DATE,
    version             INTEGER        NOT NULL DEFAULT 1,
    description         TEXT,
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_job_level_rate UNIQUE (level_code, version, deleted),
    CONSTRAINT ck_pjlr_external_nonneg CHECK (external_daily >= 0 AND internal_daily >= 0),
    CONSTRAINT ck_pjlr_billable_range  CHECK (billable_target >= 0 AND billable_target <= 1),
    CONSTRAINT ck_pjlr_dates_valid     CHECK (expire_date IS NULL OR expire_date >= effective_date),
    CONSTRAINT ck_pjlr_deleted_enum    CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_pjlr_cost_valid      CHECK (total_cost = base_salary + social_company + fund_company + travel_reimbursement + travel_allowance)
);
COMMENT ON TABLE pmis_job_level_rate IS '职级费率表(双费率): 对外报价人天 / 对内成本人天 / 五险一金+差旅成本拆解,支持版本化生效';
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
COMMENT ON COLUMN pmis_job_level_rate.travel_reimbursement IS '差旅报销-公司承担部分(元/月)';
COMMENT ON COLUMN pmis_job_level_rate.travel_allowance IS '差旅补贴-公司承担部分(元/月)';
COMMENT ON COLUMN pmis_job_level_rate.total_cost IS '公司总人力成本(元/月,=base_salary+social_company+fund_company+travel_reimbursement+travel_allowance)';
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
COMMENT ON COLUMN pmis_job_level_rate.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_job_level_rate_code ON pmis_job_level_rate (level_code) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmis_job_level_rate_effective ON pmis_job_level_rate (effective_date, expire_date);
CREATE INDEX IF NOT EXISTS idx_job_level_rate_tenant ON pmis_job_level_rate(tenant_id);

-- 员工表
CREATE TABLE IF NOT EXISTS pmis_employee(
    id              VARCHAR(20)      PRIMARY KEY,
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
    id                  VARCHAR(20)      PRIMARY KEY,
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
    id                  VARCHAR(20)      PRIMARY KEY,
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
    id              VARCHAR(20)      PRIMARY KEY,
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
    id                 VARCHAR(20)      PRIMARY KEY,
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
-- 5. 通知中心（ydsz-pmis-message 引擎 - 大厂级独立自研）
--    表前缀 pmis_msg_* 统一管理：站内通知 / 用户偏好 / 订阅
--    消息模板 / 发送日志 / 路由 / 回执 / 聚合 / 灰度 见第 7.x 节
-- ====================================================================

-- 站内通知表 pmis_msg_notification（由原 pmis_notification 重构升级）
CREATE TABLE IF NOT EXISTS pmis_msg_notification(
    id              VARCHAR(20)      PRIMARY KEY,
    title           VARCHAR(255)   NOT NULL,
    content         TEXT,
    level           VARCHAR(16)    NOT NULL DEFAULT 'INFO',
    category        VARCHAR(32)    NOT NULL,
    priority        VARCHAR(16)    NOT NULL DEFAULT 'NORMAL',
    sender_id       VARCHAR(20),
    receiver_id     VARCHAR(20)         NOT NULL,
    biz_type        VARCHAR(64),
    biz_id          VARCHAR(20),
    message_group   VARCHAR(64),
    batch_id        VARCHAR(20),
    action_url      VARCHAR(512),
    action_text     VARCHAR(64),
    icon            VARCHAR(64),
    extra           TEXT,
    source_module   VARCHAR(32),
    read_status     SMALLINT       NOT NULL DEFAULT 0,
    read_time       TIMESTAMPTZ,
    recall_status   VARCHAR(16)    NOT NULL DEFAULT 'NONE',
    recall_at       TIMESTAMPTZ,
    expired_at      TIMESTAMPTZ,
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT ck_pmn_level_enum     CHECK (level IN ('INFO', 'WARN', 'ERROR', 'URGENT')),
    CONSTRAINT ck_pmn_category_enum  CHECK (category IN ('SYSTEM', 'WORKFLOW', 'ALERT', 'TODO', 'ANNOUNCE')),
    CONSTRAINT ck_pmn_priority_enum  CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_pmn_read_enum      CHECK (read_status IN (0, 1)),
    CONSTRAINT ck_pmn_recall_enum    CHECK (recall_status IN ('NONE', 'RECALLED')),
    CONSTRAINT ck_pmn_deleted_enum   CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE pmis_msg_notification IS '站内通知表: 系统消息/待办/预警/公告统一入口,支持优先级/聚合/撤回/业务跳转';
COMMENT ON COLUMN pmis_msg_notification.id IS '主键 ID';
COMMENT ON COLUMN pmis_msg_notification.title IS '通知标题';
COMMENT ON COLUMN pmis_msg_notification.content IS '通知内容(支持富文本/Markdown)';
COMMENT ON COLUMN pmis_msg_notification.level IS '通知级别: INFO 提示 / WARN 警告 / ERROR 错误 / URGENT 紧急';
COMMENT ON COLUMN pmis_msg_notification.category IS '通知分类: SYSTEM 系统消息 / WORKFLOW 流程审批 / ALERT 预警通知 / TODO 待办 / ANNOUNCE 公告';
COMMENT ON COLUMN pmis_msg_notification.priority IS '发送优先级: LOW 低 / NORMAL 普通 / HIGH 高 / URGENT 紧急(影响排队与聚合)';
COMMENT ON COLUMN pmis_msg_notification.sender_id IS '发送人 ID(系统通知为 SYSTEM)';
COMMENT ON COLUMN pmis_msg_notification.receiver_id IS '接收人 ID(关联 pmis_employee.id)';
COMMENT ON COLUMN pmis_msg_notification.biz_type IS '关联业务类型(如 contract/invoice/risk)';
COMMENT ON COLUMN pmis_msg_notification.biz_id IS '关联业务单据 ID';
COMMENT ON COLUMN pmis_msg_notification.message_group IS '聚合组(同组通知可合并为摘要,如 RISK:contract-123)';
COMMENT ON COLUMN pmis_msg_notification.batch_id IS '聚合批次 ID(关联 pmis_msg_aggregate.id)';
COMMENT ON COLUMN pmis_msg_notification.action_url IS '点击跳转 URL(前端路由或外链)';
COMMENT ON COLUMN pmis_msg_notification.action_text IS '跳转按钮文案(如"去处理")';
COMMENT ON COLUMN pmis_msg_notification.icon IS '通知图标标识(Element Plus icon name)';
COMMENT ON COLUMN pmis_msg_notification.extra IS '扩展字段 JSON(业务自定义透传)';
COMMENT ON COLUMN pmis_msg_notification.source_module IS '来源模块(system/project/workflow/agent)';
COMMENT ON COLUMN pmis_msg_notification.read_status IS '已读状态: 0 未读 / 1 已读';
COMMENT ON COLUMN pmis_msg_notification.read_time IS '首次阅读时间';
COMMENT ON COLUMN pmis_msg_notification.recall_status IS '撤回状态: NONE 未撤回 / RECALLED 已撤回';
COMMENT ON COLUMN pmis_msg_notification.recall_at IS '撤回时间';
COMMENT ON COLUMN pmis_msg_notification.expired_at IS '过期时间(过期后不再展示)';
COMMENT ON COLUMN pmis_msg_notification.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_msg_notification.created_at IS '发送时间';
COMMENT ON COLUMN pmis_msg_notification.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_msg_notification.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_msg_notification.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';
COMMENT ON COLUMN pmis_msg_notification.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmn_receiver ON pmis_msg_notification (receiver_id, read_status) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmn_biz ON pmis_msg_notification (biz_type, biz_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmn_tenant ON pmis_msg_notification(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pmn_tenant_created
    ON pmis_msg_notification(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmn_sender
    ON pmis_msg_notification(sender_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmn_group
    ON pmis_msg_notification(receiver_id, message_group) WHERE deleted = 0 AND message_group IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmn_batch
    ON pmis_msg_notification(batch_id) WHERE deleted = 0 AND batch_id IS NOT NULL;

-- 用户消息偏好表 pmis_msg_preference（免打扰 / 频率上限 / 聚合开关 / 语言）
CREATE TABLE IF NOT EXISTS pmis_msg_preference(
    id                VARCHAR(20)      PRIMARY KEY,
    user_id           VARCHAR(20)   NOT NULL,
    channel           VARCHAR(32)   NOT NULL,
    biz_type          VARCHAR(64)   NOT NULL DEFAULT '__DEFAULT__',
    enabled           SMALLINT      NOT NULL DEFAULT 1,
    dnd_enabled       SMALLINT      NOT NULL DEFAULT 0,
    dnd_start         VARCHAR(8),
    dnd_end           VARCHAR(8),
    daily_limit       INTEGER,
    hourly_limit      INTEGER,
    digest_enabled    SMALLINT      NOT NULL DEFAULT 0,
    digest_frequency  VARCHAR(16),
    locale            VARCHAR(16),
    extra             TEXT,
    created_by        VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)       NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmp_user_chan_biz UNIQUE (user_id, channel, biz_type, tenant_id, deleted),
    CONSTRAINT ck_pmp_channel_enum  CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'INAPP', 'WEBHOOK', 'DINGTALK', 'WECOM', 'FEISHU')),
    CONSTRAINT ck_pmp_enabled_enum  CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_pmp_dnd_enum      CHECK (dnd_enabled IN (0, 1)),
    CONSTRAINT ck_pmp_digest_enum   CHECK (digest_enabled IN (0, 1)),
    CONSTRAINT ck_pmp_deleted_enum  CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_pmp_daily_nonneg  CHECK (daily_limit IS NULL OR daily_limit >= 0),
    CONSTRAINT ck_pmp_hourly_nonneg CHECK (hourly_limit IS NULL OR hourly_limit >= 0)
);
COMMENT ON TABLE pmis_msg_preference IS '用户消息偏好表: 免打扰时段 / 频率上限 / 聚合开关 / 偏好语言';
COMMENT ON COLUMN pmis_msg_preference.user_id IS '用户 ID(关联 pmis_employee.id)';
COMMENT ON COLUMN pmis_msg_preference.channel IS '通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU';
COMMENT ON COLUMN pmis_msg_preference.biz_type IS '业务类型(__DEFAULT__ 表示该通道全局默认偏好)';
COMMENT ON COLUMN pmis_msg_preference.enabled IS '是否启用该通道: 0 关闭 / 1 开启(关闭后不发送)';
COMMENT ON COLUMN pmis_msg_preference.dnd_enabled IS '免打扰开关: 0 关闭 / 1 开启';
COMMENT ON COLUMN pmis_msg_preference.dnd_start IS '免打扰开始时间 HH:mm(如 22:00)';
COMMENT ON COLUMN pmis_msg_preference.dnd_end IS '免打扰结束时间 HH:mm(如 08:00)';
COMMENT ON COLUMN pmis_msg_preference.daily_limit IS '每日发送上限(超过则暂存或丢弃)';
COMMENT ON COLUMN pmis_msg_preference.hourly_limit IS '每小时发送上限';
COMMENT ON COLUMN pmis_msg_preference.digest_enabled IS '聚合开关: 0 即时发送 / 1 聚合摘要';
COMMENT ON COLUMN pmis_msg_preference.digest_frequency IS '聚合频率: HOURLY / DAILY / WEEKLY';
COMMENT ON COLUMN pmis_msg_preference.locale IS '偏好语言(如 zh-CN / en-US,影响模板 i18n 选择)';
COMMENT ON COLUMN pmis_msg_preference.extra IS '扩展字段 JSON';

CREATE INDEX IF NOT EXISTS idx_pmp_user ON pmis_msg_preference(user_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmp_tenant ON pmis_msg_preference(tenant_id);

-- 订阅关系表 pmis_msg_subscription（用户订阅/退订主题）
CREATE TABLE IF NOT EXISTS pmis_msg_subscription(
    id              VARCHAR(20)      PRIMARY KEY,
    user_id         VARCHAR(20)   NOT NULL,
    topic_code      VARCHAR(128)  NOT NULL,
    channel         VARCHAR(32)   NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'SUBSCRIBED',
    role_scope      VARCHAR(128),
    extra           TEXT,
    unsubscribed_at TIMESTAMPTZ,
    created_by      VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)       NOT NULL DEFAULT '1',
    CONSTRAINT uk_pms_user_topic_chan UNIQUE (user_id, topic_code, channel, tenant_id, deleted),
    CONSTRAINT ck_pms_channel_enum    CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'INAPP', 'WEBHOOK', 'DINGTALK', 'WECOM', 'FEISHU')),
    CONSTRAINT ck_pms_status_enum     CHECK (status IN ('SUBSCRIBED', 'UNSUBSCRIBED')),
    CONSTRAINT ck_pms_deleted_enum    CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE pmis_msg_subscription IS '订阅关系表: 用户对主题(topic_code)在指定通道的订阅/退订状态';
COMMENT ON COLUMN pmis_msg_subscription.user_id IS '用户 ID';
COMMENT ON COLUMN pmis_msg_subscription.topic_code IS '主题编码(如 RISK_ALERT / CONTRACT_APPROVAL / APPROVAL_TODO)';
COMMENT ON COLUMN pmis_msg_subscription.channel IS '通道';
COMMENT ON COLUMN pmis_msg_subscription.status IS '订阅状态: SUBSCRIBED 已订阅 / UNSUBSCRIBED 已退订';
COMMENT ON COLUMN pmis_msg_subscription.role_scope IS '角色范围(如 PM|MEMBER,限定角色内可见性)';
COMMENT ON COLUMN pmis_msg_subscription.extra IS '扩展字段 JSON';
COMMENT ON COLUMN pmis_msg_subscription.unsubscribed_at IS '退订时间(P1-5:仅 status=UNSUBSCRIBED 时有意义)';

CREATE INDEX IF NOT EXISTS idx_pms_user ON pmis_msg_subscription(user_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pms_topic ON pmis_msg_subscription(topic_code, channel) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pms_unsub_status ON pmis_msg_subscription(status, unsubscribed_at) WHERE deleted = 0;

-- ====================================================================
-- 6. 系统配置
-- ====================================================================

CREATE TABLE IF NOT EXISTS pmis_config(
    id              VARCHAR(20)      PRIMARY KEY,
    config_group    VARCHAR(64)    NOT NULL,
    config_key      VARCHAR(128)   NOT NULL,
    config_value    TEXT,
    value_type      VARCHAR(16)    NOT NULL DEFAULT 'STRING',
    default_value   TEXT,
    description     TEXT,
    is_public       SMALLINT       NOT NULL DEFAULT 0,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_config_key UNIQUE (config_group, config_key, deleted),
    CONSTRAINT ck_pc_value_type    CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON')),
    CONSTRAINT ck_pc_status_enum   CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pc_public_enum   CHECK (is_public IN (0, 1)),
    CONSTRAINT ck_pc_deleted_enum  CHECK (deleted IN (0, 1))
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
COMMENT ON COLUMN pmis_config.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_config_group ON pmis_config (config_group) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_config_tenant ON pmis_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_config_tenant_created
    ON pmis_config(tenant_id, created_at DESC) WHERE deleted = 0;

-- ====================================================================
-- 7. 操作日志
-- ====================================================================

-- V1.0.0_001 P1-4 重构: pmis_operation_log 改为按月 RANGE 分区表
--   (主键必须包含分区键;BRIN 索引对父表定义,自动传播到所有分区)
DROP TABLE IF EXISTS pmis_operation_log CASCADE;
CREATE TABLE IF NOT EXISTS pmis_operation_log(
    id                VARCHAR(20)      NOT NULL,
    user_id           VARCHAR(20),
    username          VARCHAR(64),
    module            VARCHAR(64)    NOT NULL,
    action            VARCHAR(128)   NOT NULL,
    biz_type          VARCHAR(64),
    biz_id            VARCHAR(20),
    request_url       VARCHAR(512),
    -- V1.0.0_008 内联: 字段重命名后的规范名称
    http_method       VARCHAR(16),
    method_signature  VARCHAR(256),
    client_ip         VARCHAR(64),
    user_agent        VARCHAR(512),
    params_json       TEXT,
    response_json     TEXT,
    -- V1.0.0_040 内联: 审计差异字段(变更前/后快照)
    before_data       JSONB,
    after_data        JSONB,
    cost_ms           BIGINT,
    status            VARCHAR(16)    NOT NULL DEFAULT 'SUCCESS',
    error_message     TEXT,
    trace_id          VARCHAR(20),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT ck_pol_status_enum  CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_pol_cost_nonneg  CHECK (cost_ms IS NULL OR cost_ms >= 0),
    -- 分区表主键必须包含分区键
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
COMMENT ON TABLE pmis_operation_log IS '操作日志表: 用户关键操作全量留存(模块/动作/入参/出参/耗时/IP),用于审计与问题排查';
COMMENT ON COLUMN pmis_operation_log.id IS '主键 ID';
COMMENT ON COLUMN pmis_operation_log.user_id IS '操作用户 ID';
COMMENT ON COLUMN pmis_operation_log.username IS '操作用户名';
COMMENT ON COLUMN pmis_operation_log.module IS '操作模块(如 project/contract/finance)';
COMMENT ON COLUMN pmis_operation_log.action IS '操作动作(如 create/update/delete/approve)';
COMMENT ON COLUMN pmis_operation_log.biz_type IS '业务类型';
COMMENT ON COLUMN pmis_operation_log.biz_id IS '业务单据 ID';
COMMENT ON COLUMN pmis_operation_log.request_url IS '请求 URL';
COMMENT ON COLUMN pmis_operation_log.http_method IS 'V1.0.0_008: HTTP 方法(GET/POST/PUT/DELETE)';
COMMENT ON COLUMN pmis_operation_log.method_signature IS 'V1.0.0_008: Java 方法签名(如 ProjectController#create)';
COMMENT ON COLUMN pmis_operation_log.client_ip IS 'V1.0.0_008: 客户端 IP';
COMMENT ON COLUMN pmis_operation_log.user_agent IS '浏览器/客户端 User-Agent';
COMMENT ON COLUMN pmis_operation_log.params_json IS 'V1.0.0_008: 请求参数 JSON(敏感字段脱敏)';
COMMENT ON COLUMN pmis_operation_log.response_json IS 'V1.0.0_008: 响应数据 JSON(失败时为空)';
COMMENT ON COLUMN pmis_operation_log.before_data IS 'V1.0.0_040: 变更前数据快照(JSONB,update/delete 时填充)';
COMMENT ON COLUMN pmis_operation_log.after_data IS 'V1.0.0_040: 变更后数据快照(JSONB,create/update 时填充)';
COMMENT ON COLUMN pmis_operation_log.cost_ms IS '接口耗时(毫秒)';
COMMENT ON COLUMN pmis_operation_log.status IS '操作状态: SUCCESS 成功 / FAILED 失败';
COMMENT ON COLUMN pmis_operation_log.error_message IS '错误信息(失败时填充堆栈摘要)';
COMMENT ON COLUMN pmis_operation_log.trace_id IS 'V1.0.0_008: 系统链路追踪 ID(SkyWalking/TLog)';
COMMENT ON COLUMN pmis_operation_log.created_at IS '操作时间';
COMMENT ON COLUMN pmis_operation_log.tenant_id IS '租户 ID(单租户部署默认 1)';

-- P1-4: 父表索引,自动传播到所有月度分区
CREATE INDEX IF NOT EXISTS idx_pmis_oplog_user ON pmis_operation_log (user_id);
CREATE INDEX IF NOT EXISTS idx_pmis_oplog_module ON pmis_operation_log (module, action);
CREATE INDEX IF NOT EXISTS idx_pmis_oplog_created ON pmis_operation_log (created_at);
CREATE INDEX IF NOT EXISTS idx_pol_tenant ON pmis_operation_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pol_tenant_created
    ON pmis_operation_log(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_oplog_biz
    ON pmis_operation_log(biz_type, biz_id)
    WHERE biz_type IS NOT NULL AND biz_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pol_user_created
    ON pmis_operation_log(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pol_trace
    ON pmis_operation_log(trace_id) WHERE trace_id IS NOT NULL;
-- P1-4: BRIN 索引(父表,自动传播) — 时间范围扫描友好
CREATE INDEX IF NOT EXISTS idx_pmis_operation_log_brin
    ON pmis_operation_log USING BRIN (created_at)
    WITH (pages_per_range = 32);

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
(level_code, external_daily, internal_daily, base_salary, social_company, social_personal, fund_company, fund_personal, take_home, travel_reimbursement, travel_allowance, total_cost, billable_target, effective_date, version, created_by)
VALUES
    ('L1',  400,  200,  4000,  980,  430,  200,  200,  3370,  500,  300,  5980,  0.78, '2026-01-01', 1, 0),
    ('L2',  500,  250,  5000,  1225, 535,  250,  250,  4215,  500,  300,  7275,  0.78, '2026-01-01', 1, 0),
    ('L3',  600,  300,  6000,  1470, 640,  300,  300,  5060,  500,  300,  8570,  0.80, '2026-01-01', 1, 0),
    ('L4',  700,  350,  7000,  1715, 745,  350,  350,  5905,  800,  500,  10365, 0.82, '2026-01-01', 1, 0),
    ('L5',  800,  400,  8000,  1960, 850,  400,  400,  6750,  800,  500,  11660, 0.82, '2026-01-01', 1, 0),
    ('L6',  900,  450,  9000,  2205, 955,  450,  450,  7595,  800,  500,  12955, 0.82, '2026-01-01', 1, 0),
    ('L7',  1000, 500,  10000, 2450, 1060, 500,  500,  8440,  1200, 800,  14950, 0.80, '2026-01-01', 1, 0),
    ('L8',  1100, 550,  11000, 2695, 1165, 550,  550,  9285,  1200, 800,  16245, 0.80, '2026-01-01', 1, 0),
    ('L9',  1200, 600,  12000, 2940, 1270, 600,  600,  10130, 1200, 800,  17540, 0.75, '2026-01-01', 1, 0),
    ('L10', 1300, 650,  13000, 3185, 1375, 650,  650,  10975, 1500, 1000, 19335, 0.70, '2026-01-01', 1, 0),
    ('L11', 1400, 700,  14000, 3430, 1480, 700,  700,  11820, 1500, 1000, 20630, 0.70, '2026-01-01', 1, 0),
    ('L12', 1500, 750,  15000, 3675, 1585, 750,  750,  12665, 1500, 1000, 21925, 0.65, '2026-01-01', 1, 0),
    ('L13', 1600, 800,  16000, 3920, 1690, 800,  800,  13510, 2000, 1500, 24220, 0.60, '2026-01-01', 1, 0),
    ('L14', 1700, 850,  17000, 4165, 1795, 850,  850,  14355, 2000, 1500, 25515, 0.55, '2026-01-01', 1, 0),
    ('L15', 1800, 900,  18000, 4410, 1900, 900,  900,  15200, 2000, 1500, 26810, 0.50, '2026-01-01', 1, 0),
    ('L16', 1900, 950,  19000, 4655, 2005, 950,  950,  16045, 2500, 2000, 29105, 0.45, '2026-01-01', 1, 0),
    ('L17', 2000, 1000, 20000, 4900, 2110, 1000, 1000, 16890, 2500, 2000, 30400, 0.40, '2026-01-01', 1, 0),
    ('L18', 2100, 1050, 21000, 5145, 2215, 1050, 1050, 17735, 2500, 2000, 31695, 0.40, '2026-01-01', 1, 0)
ON CONFLICT DO NOTHING;

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
    ('workflow', 'workflow.engine', 'pmis', 'STRING', '工作流引擎（自研 pmis_flow_*）', 0),
    ('alert', 'alert.cpi.yellow', '0.95', 'NUMBER', 'CPI 黄色预警阈值', 0),
    ('alert', 'alert.cpi.red', '0.85', 'NUMBER', 'CPI 红色预警阈值', 0),
    ('alert', 'alert.spi.yellow', '0.90', 'NUMBER', 'SPI 黄色预警阈值', 0),
    ('alert', 'alert.spi.red', '0.80', 'NUMBER', 'SPI 红色预警阈值', 0),
    ('alert', 'alert.bench.days.yellow', '7', 'NUMBER', 'Bench 黄色预警天数', 0),
    ('alert', 'alert.bench.days.red', '15', 'NUMBER', 'Bench 红色预警天数', 0)
ON CONFLICT DO NOTHING;

-- --------------------------------------------------------------------

-- ============================ [024] add version to core tables ============================

-- ========================================================
-- P1-12 乐观锁（@Version）覆盖核心实体
--
-- 为 10 张核心业务表添加 version 列，配合 MyBatis-Plus
-- OptimisticLockerInnerInterceptor 实现乐观锁控制。
--
-- 涉及表：
--   pmis_project 项目域：initiation / contract / contract_change / project_change
--   pmis_finance 财务域：invoice / payment / customer_credit
--   pmis_execution 执行域：wbs_task / purchase / ops_ticket
--
-- 默认值 0：所有现有记录初始版本号为 0，下一次 UPDATE 时自动 +1。
-- NOT NULL 约束：避免 NULL 导致乐观锁失效。
-- ========================================================

-- ========== 项目域 ==========
ALTER TABLE pmis_project_initiation
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_project_contract
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_project_contract_change
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_project_change
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- ========== 财务域 ==========
-- 早期版本误加 pmis_finance. schema 前缀，但所有表均建在 public schema
-- （与上方 project/execution 域的写法保持一致），执行时会报
-- "模式 pmis_finance 不存在" 错误，故去除 schema 前缀。
ALTER TABLE pmis_finance_invoice
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_finance_payment
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_finance_customer_credit
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- ========== 执行域 ==========
-- 早期版本把 pmis_cost_purchase 误写为 pmis_execution_purchase,
-- 把 pmis_ops_ticket 误写为 pmis_execution_ops_ticket。修正为实际
-- 表名（@TableName 定义）以避免 "关系不存在" 错误。
ALTER TABLE pmis_execution_wbs_task
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_cost_purchase
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_ops_ticket
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- ========== 同步更新 init schema 脚本中的字段注释（仅文档作用，不影响运行） ==========
COMMENT ON COLUMN pmis_project_initiation.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_project_contract.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_project_contract_change.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_project_change.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_finance_invoice.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_finance_payment.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_finance_customer_credit.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_execution_wbs_task.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_cost_purchase.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';
COMMENT ON COLUMN pmis_ops_ticket.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

-- --------------------------------------------------------------------

-- ============================ [027] init undo log ============================

-- ====================================================================
--  Seata AT 模式 undo_log 表
--  --------------------------------------------------------------------
--  说明：
--    1) AT 模式依赖此表保存 before/after 镜像，用于分支事务回滚
--    2) 必须在每个业务库（pmis / pmis_bill / pmis_archive ...）都建
--    3) 配套 Nacos 配置：data-id = seata-client.properties
--    4) 配套脚本：deploy/seata/verify-seata.sh 会自动检查本表存在
--  --------------------------------------------------------------------
--  版本：V1.0.0_027
--  适用：PostgreSQL 16+
-- ====================================================================

-- ---------- 表结构 ----------
-- id            主键自增
-- branch_id     分支事务 ID（Seata 生成）
-- xid           全局事务 ID（跨服务唯一）
-- context       事务上下文（序列化信息）
-- rollback_info 回滚信息（before/after 镜像 ZIP 压缩）
-- log_status    日志状态 0=正常 1=全局完成 2=全局回滚
-- log_created   创建时间
-- log_modified  最后修改时间
CREATE TABLE IF NOT EXISTS undo_log (
    id            VARCHAR(20)    PRIMARY KEY,
    branch_id     VARCHAR(20)       NOT NULL,
    xid           VARCHAR(100) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info BYTEA        NOT NULL,
    log_status    INT          NOT NULL,
    log_created   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    log_modified  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_undo_log UNIQUE (xid, branch_id)
);

-- ---------- 字段注释 ----------
COMMENT ON TABLE  undo_log             IS 'Seata AT 模式分布式事务回滚日志表（每个业务库都需要）';
COMMENT ON COLUMN undo_log.id          IS '主键 ID';
COMMENT ON COLUMN undo_log.branch_id   IS '分支事务 ID（Seata 内部生成）';
COMMENT ON COLUMN undo_log.xid         IS '全局事务 ID（跨服务唯一标识）';
COMMENT ON COLUMN undo_log.context     IS '事务上下文（序列化信息，如应用名、分组等）';
COMMENT ON COLUMN undo_log.rollback_info IS '回滚信息（ZIP 压缩的 before/after 镜像，Base64 编码）';
COMMENT ON COLUMN undo_log.log_status  IS '日志状态：0=正常 1=全局完成 2=全局回滚';
COMMENT ON COLUMN undo_log.log_created IS '创建时间';
COMMENT ON COLUMN undo_log.log_modified IS '最后修改时间';

-- ---------- 性能索引 ----------
-- 建议添加以下索引（百万行级别可显著提升回滚扫描性能）
-- CREATE INDEX IF NOT EXISTS idx_undo_log_xid ON undo_log (xid);
-- CREATE INDEX IF NOT EXISTS idx_undo_log_status_modified ON undo_log (log_status, log_modified);

-- --------------------------------------------------------------------

-- ============================ [056] add tenant id to base tables ============================

-- ============================================================
-- 基础表多租户字段预留 + 关键查询路径复合索引
--
-- H2.1 修复：
--   README 声明"每一张业务表都带 tenant_id"，但 V1.0.0_001 中的
--   17 张核心基础表全部缺失 tenant_id 字段。本次补齐。
--
-- H2.4 修复：
--   实际业务查询几乎都是
--     WHERE tenant_id = ? AND deleted = 0 ORDER BY created_at DESC LIMIT 20
--   单列 tenant_id 索引选择率约等于全表（单租户 90%+ 数据）。
--   对未建复合索引的核心业务表统一补 (tenant_id, created_at DESC) WHERE deleted = 0。
--
-- H2.3 修复：
--   外键关联列的反向查询无索引，补 permission_id / position_id / employee_id
--   / leader_id / sender_id 索引。
--
-- H3.2 修复：
--   逻辑删除字段索引覆盖不全，对 V1.0.0_001 中缺 deleted 索引的表补建。
--
-- 兼容性：
--   - tenant_id 默认值 1，单租户部署不影响数据
--   - 多租户部署后由 TenantLineInnerInterceptor 强制 WHERE tenant_id = ?
--   - 全部使用 IF NOT EXISTS，可重复执行
-- ============================================================

-- ============================================================
-- 一、基础表 tenant_id 字段补齐（H2.1）
-- ============================================================

-- 1. 字典类型
ALTER TABLE pmis_dict_type ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_dict_type_tenant ON pmis_dict_type(tenant_id);

-- 2. 字典项
ALTER TABLE pmis_dict_item ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_dict_item_tenant ON pmis_dict_item(tenant_id);

-- 3. 字典版本
ALTER TABLE pmis_dict_version ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_dict_version_tenant ON pmis_dict_version(tenant_id);

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

-- 8. 部门
ALTER TABLE pmis_department ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_department_tenant ON pmis_department(tenant_id);

-- 9. 岗位
ALTER TABLE pmis_position ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_position_tenant ON pmis_position(tenant_id);

-- 10. 职级
ALTER TABLE pmis_job_level ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_job_level_tenant ON pmis_job_level(tenant_id);

-- 11. 职级费率
ALTER TABLE pmis_job_level_rate ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_job_level_rate_tenant ON pmis_job_level_rate(tenant_id);

-- 12. 员工
ALTER TABLE pmis_employee ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_employee_tenant ON pmis_employee(tenant_id);

-- 13. 员工标签
ALTER TABLE pmis_employee_tag ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_emp_tag_tenant ON pmis_employee_tag(tenant_id);

-- 14. 用户账号
ALTER TABLE pmis_user_account ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_user_account_tenant ON pmis_user_account(tenant_id);

-- 15. 通知
ALTER TABLE pmis_notification ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_notification_tenant ON pmis_notification(tenant_id);

-- 16. 配置
ALTER TABLE pmis_config ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_config_tenant ON pmis_config(tenant_id);

-- 17. 操作日志（V1.0.0_008 已含 tenant_id，跳过 ADD COLUMN，仅补索引）
CREATE INDEX IF NOT EXISTS idx_pol_tenant ON pmis_operation_log(tenant_id);

-- ============================================================
-- 二、关键查询路径复合索引（H2.4）
--   覆盖分页查询 WHERE tenant_id = ? AND deleted = 0 ORDER BY created_at DESC
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_dict_type_tenant_created
    ON pmis_dict_type(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_dict_item_tenant_created
    ON pmis_dict_item(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_role_tenant_created
    ON pmis_role(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_department_tenant_created
    ON pmis_department(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_position_tenant_created
    ON pmis_position(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_job_level_tenant_created
    ON pmis_job_level(tenant_id, sort_order) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_employee_tenant_created
    ON pmis_employee(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_user_account_tenant_created
    ON pmis_user_account(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_notification_tenant_created
    ON pmis_notification(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_config_tenant_created
    ON pmis_config(tenant_id, created_at DESC) WHERE deleted = 0;

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

-- 部门-负责人：按 leader_id 反向查询
CREATE INDEX IF NOT EXISTS idx_pmis_dept_leader
    ON pmis_department(leader_id) WHERE deleted = 0;

-- 通知-发送人：按 sender_id 查询"我发出的通知"
CREATE INDEX IF NOT EXISTS idx_pmis_notif_sender
    ON pmis_notification(sender_id) WHERE deleted = 0;

-- ============================================================
-- 四、逻辑删除字段索引覆盖（H3.2）
--   对 V1.0.0_001 中未建 deleted 索引的表补建
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_pmis_dict_version_deleted ON pmis_dict_version(deleted);
CREATE INDEX IF NOT EXISTS idx_pmis_role_permission_deleted ON pmis_role_permission(deleted);
CREATE INDEX IF NOT EXISTS idx_pmis_emp_tag_deleted ON pmis_employee_tag(deleted);

-- ============================================================
-- 五、pmis_flow_notify_outbox 表 tenant_id 索引（H2.5）
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_peo_tenant_status
    ON pmis_flow_notify_outbox(tenant_id, status, next_retry_at) WHERE deleted = 0;

-- ============================================================
-- 六、undo_log 性能索引（H1.8）
--   Seata AT 模式回滚按 xid 扫描，无索引会全表扫描
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_undo_log_xid ON undo_log(xid);
CREATE INDEX IF NOT EXISTS idx_undo_log_status_modified ON undo_log(log_status, log_modified);

-- ============================================================
-- 七、补齐遗漏的 10 张业务表 tenant_id 字段
--   首轮扫描漏掉，启用 TenantLineInnerInterceptor 前必须补齐
-- ============================================================

-- 任务执行日志表
ALTER TABLE pmis_job_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_job_log_tenant ON pmis_job_log(tenant_id);

-- 商机跟进记录
ALTER TABLE pmis_project_opportunity_follow ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_ppof_tenant ON pmis_project_opportunity_follow(tenant_id);

-- 项目预算明细
ALTER TABLE pmis_project_budget_item ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_ppbi_tenant ON pmis_project_budget_item(tenant_id);

-- 门径评审记录
ALTER TABLE pmis_project_gate_review ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_ppgr_tenant ON pmis_project_gate_review(tenant_id);

-- 报表订阅
ALTER TABLE pmis_report_subscription ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_report_sub_tenant ON pmis_report_subscription(tenant_id);

-- 异步导出记录（P0-3 合并：原报表导出记录已并入此表）
ALTER TABLE pmis_export_record ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_export_rec_tenant ON pmis_export_record(tenant_id);

-- 流程历史变量归档表
ALTER TABLE pmis_flow_his_variable ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_flow_his_var_tenant ON pmis_flow_his_variable(tenant_id);

-- 规则模板表（053 漏补）
ALTER TABLE pmis_rule_template ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_rule_template_tenant ON pmis_rule_template(tenant_id);

-- 规则测试用例表（053 漏补）
ALTER TABLE pmis_rule_test_case ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_rule_test_case_tenant ON pmis_rule_test_case(tenant_id);

ANALYZE pmis_dict_type;
ANALYZE pmis_dict_item;
ANALYZE pmis_dict_version;
ANALYZE pmis_role;
ANALYZE pmis_permission;
ANALYZE pmis_user_role;
ANALYZE pmis_role_permission;
ANALYZE pmis_department;
ANALYZE pmis_position;
ANALYZE pmis_job_level;
ANALYZE pmis_job_level_rate;
ANALYZE pmis_employee;
ANALYZE pmis_employee_tag;
ANALYZE pmis_user_account;
ANALYZE pmis_notification;
ANALYZE pmis_config;
ANALYZE pmis_operation_log;
ANALYZE pmis_flow_notify_outbox;
ANALYZE undo_log;
ANALYZE pmis_job_log;
ANALYZE pmis_project_opportunity_follow;
ANALYZE pmis_project_budget_item;
ANALYZE pmis_project_gate_review;
ANALYZE pmis_report_subscription;
ANALYZE pmis_export_record;
ANALYZE pmis_flow_his_variable;
ANALYZE pmis_rule_template;
ANALYZE pmis_rule_test_case;

-- --------------------------------------------------------------------

-- ====================================================================
-- 可选扩展(性能监控/Hint 优化) - H6.2 修复
-- ====================================================================
-- 说明:
--   - pg_stat_statements / pg_hint_plan 需在 postgresql.conf 的
--     shared_preload_libraries 中预加载后才可创建扩展
--   - uuid-ossp / pgcrypto 已在文件首部创建, 此处不再重复
--   - 此脚本在已创建扩展的环境中执行会返回 NOTICE 而非 ERROR(IF NOT EXISTS)
--   - pg_hint_plan 在某些环境(如未配置 preload) 不可用, 使用 DO 块容错

-- pg_stat_statements: 需 shared_preload_libraries 预加载, 未加载时跳过
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_stat_statements 不可用, 跳过: %', SQLERRM;
END $$;

-- pg_hint_plan: 需 preload 预加载, 未加载时跳过
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_hint_plan;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_hint_plan 不可用, 跳过: %', SQLERRM;
END $$;

-- ====================================================================

-- ============================ [060] field type unification ============================
-- ====================================================================
-- V1.0.0_060  H2.7 / P1-1 字段类型统一
-- ----------------------------------------------------------------------------
-- 背景:历史演进过程中出现了若干类型不一致:
--   1. pmis_flow_run_task.assignor_id 为 BIGINT,assignee_id 为 VARCHAR(20) — 同含义字段类型不一致
--   2. pmis_flow_his_task 完全缺失 assignor_id 列(主表有,历史表没有)
--   3. pmis_finance_invoice.tax_period 为 VARCHAR(16),但 CHECK 约束限定为 YYYY-MM(7 字符),存余浪费
--   4. pmis_dict_version 缺 updated_at/updated_by/tenant_id,且 created_at/effective_date 用了 TIMESTAMP 而非 TIMESTAMPTZ
--
-- 已审查但**保留原样**的差异(具备合理业务理由):
--   - 11 张 pmis_rule_* 表的 created_by/updated_by 为 VARCHAR(64) DEFAULT 'SYSTEM'
--     原因:对应 Java 实体明确使用 String createdBy/updatedBy(rule 责任人可为工号/SSO 用户名等非纯数字 ID)
--     修改风险:RuleDefinitionDO 等 11 个 DTO/Service/Controller 的 ownerBy 字段全部受影响
--     决议:保持 VARCHAR(64) 不变,但统一 DEFAULT 值与 COMMENT 文案(见下方)
-- ====================================================================

-- ----------------------------------------------------------------------------
-- 0) 11 张 rule 表 created_by/updated_by 文案统一(DEFAULT 'SYSTEM' 已是项目约定,保留)
--    仅刷新 COMMENT 文案,便于后续维护者理解
-- ----------------------------------------------------------------------------
COMMENT ON COLUMN pmis_rule_def.created_by           IS '创建人(VARCHAR(64) 支持工号/SSO用户名,DEFAULT ''SYSTEM'' 表示系统兜底)';
COMMENT ON COLUMN pmis_rule_pack.created_by          IS '创建人(同 rule_def)';
COMMENT ON COLUMN pmis_rule_template.created_by      IS '创建人(同 rule_def)';
COMMENT ON COLUMN pmis_rule_test_case.created_by     IS '创建人(同 rule_def)';
COMMENT ON COLUMN pmis_rule_execution_trace.created_by IS '创建人(同 rule_def)';
COMMENT ON COLUMN pmis_rule_decision_table.created_by IS '创建人(同 rule_def)';
COMMENT ON COLUMN pmis_rule_scorecard.created_by     IS '创建人(同 rule_def)';
COMMENT ON COLUMN pmis_rule_decision_tree.created_by IS '创建人(同 rule_def)';
COMMENT ON COLUMN pmis_rule_script.created_by        IS '创建人(同 rule_def)';
COMMENT ON COLUMN pmis_rule_chain_graph.created_by   IS '创建人(同 rule_def)';
COMMENT ON COLUMN pmis_rule_dependency.created_by    IS '创建人(同 rule_def)';

-- ----------------------------------------------------------------------------
-- 1) pmis_flow_run_task.assignor_id BIGINT -> VARCHAR(20),与 assignee_id 对齐
--    pmis_flow_his_task 补齐 assignor_id 列
-- ----------------------------------------------------------------------------
ALTER TABLE pmis_flow_run_task ALTER COLUMN assignor_id TYPE VARCHAR(20) USING assignor_id::VARCHAR(20);
ALTER TABLE pmis_flow_his_task ADD COLUMN IF NOT EXISTS assignor_id VARCHAR(20);
ALTER TABLE pmis_flow_his_task ADD COLUMN IF NOT EXISTS assignor_name VARCHAR(64);
COMMENT ON COLUMN pmis_flow_run_task.assignor_id IS '原审批人 ID(VARCHAR(20) 雪花 ID,与 assignee_id 对齐)';
COMMENT ON COLUMN pmis_flow_his_task.assignor_id IS '原审批人 ID(VARCHAR(20) 雪花 ID,与 assignee_id 对齐)';
COMMENT ON COLUMN pmis_flow_his_task.assignor_name IS '原审批人姓名';

-- 同步主表与历史表 assignor_id 索引(若已存在则跳过)
CREATE INDEX IF NOT EXISTS idx_pfrt_assignor
    ON pmis_flow_run_task (assignor_id)
    WHERE deleted = 0 AND assignor_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pfht_assignor
    ON pmis_flow_his_task (assignor_id)
    WHERE deleted = 0 AND assignor_id IS NOT NULL;

-- FOREACH 节点 partial unique index(替代原 UNIQUE ... WHERE 约束,PG 不支持该约束语法)
CREATE UNIQUE INDEX IF NOT EXISTS uk_pfrt_foreach_iter
    ON pmis_flow_run_task (instance_id, node_code, iter_var)
    WHERE iter_var IS NOT NULL AND deleted = 0;

-- ----------------------------------------------------------------------------
-- 2) pmis_finance_invoice.tax_period VARCHAR(16) -> VARCHAR(7)(与 YYYY-MM 正则匹配)
-- ----------------------------------------------------------------------------
ALTER TABLE pmis_finance_invoice ALTER COLUMN tax_period TYPE VARCHAR(7);
COMMENT ON COLUMN pmis_finance_invoice.tax_period IS '税务所属期: 格式 YYYY-MM(7 字符,VARCHAR(7) 精确匹配 CHECK 约束)';

-- ----------------------------------------------------------------------------
-- 3) pmis_dict_version 字段补齐
--    - 新增 updated_at / updated_by / tenant_id(对齐 BaseDO 5 字段基线)
--    - created_at / effective_date 统一为 TIMESTAMPTZ(全工程时间字段统一约定)
-- ----------------------------------------------------------------------------
ALTER TABLE pmis_dict_version ADD COLUMN IF NOT EXISTS updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE pmis_dict_version ADD COLUMN IF NOT EXISTS updated_by    VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE pmis_dict_version ADD COLUMN IF NOT EXISTS tenant_id     VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE pmis_dict_version ALTER COLUMN created_at     TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE pmis_dict_version ALTER COLUMN effective_date TYPE TIMESTAMPTZ USING effective_date AT TIME ZONE 'UTC';

COMMENT ON COLUMN pmis_dict_version.updated_by    IS '最后修改人 ID';
COMMENT ON COLUMN pmis_dict_version.updated_at    IS '最后修改时间';
COMMENT ON COLUMN pmis_dict_version.tenant_id     IS '租户 ID(单租户部署默认 1)';

-- 复合索引(与全工程惯例一致)
CREATE INDEX IF NOT EXISTS idx_pdv_tenant_type
    ON pmis_dict_version (tenant_id, type_code)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pdv_tenant_type_created
    ON pmis_dict_version (tenant_id, type_code, created_at DESC)
    WHERE deleted = 0;

ANALYZE pmis_dict_version;
ANALYZE pmis_flow_run_task;
ANALYZE pmis_flow_his_task;
ANALYZE pmis_finance_invoice;

-- ----------------------------------------------------------------------------
-- 4) 人员ID字段 BIGINT -> VARCHAR(20) 统一(对齐其它 _by 雪花 ID 约定)
--    - pmis_profit_revenue.confirmed_by                          BIGINT -> VARCHAR(20)
--    - pmis_finance_invoice.applied_by / approved_by / issued_by BIGINT -> VARCHAR(20)
--    - pmis_finance_payment.confirmed_by / recorded_by            BIGINT -> VARCHAR(20)
--    USING ::VARCHAR(20) 处理历史 BIGINT 数据(雪花 ID 字符串可直接转型)
-- ----------------------------------------------------------------------------
ALTER TABLE pmis_profit_revenue ALTER COLUMN confirmed_by TYPE VARCHAR(20) USING confirmed_by::VARCHAR(20);
ALTER TABLE pmis_finance_invoice ALTER COLUMN applied_by   TYPE VARCHAR(20) USING applied_by::VARCHAR(20);
ALTER TABLE pmis_finance_invoice ALTER COLUMN approved_by  TYPE VARCHAR(20) USING approved_by::VARCHAR(20);
ALTER TABLE pmis_finance_invoice ALTER COLUMN issued_by    TYPE VARCHAR(20) USING issued_by::VARCHAR(20);
ALTER TABLE pmis_finance_payment ALTER COLUMN confirmed_by TYPE VARCHAR(20) USING confirmed_by::VARCHAR(20);
ALTER TABLE pmis_finance_payment ALTER COLUMN recorded_by  TYPE VARCHAR(20) USING recorded_by::VARCHAR(20);

COMMENT ON COLUMN pmis_profit_revenue.confirmed_by   IS '确认人ID(雪花ID VARCHAR(20))';
COMMENT ON COLUMN pmis_finance_invoice.applied_by    IS '申请人ID(雪花ID VARCHAR(20))';
COMMENT ON COLUMN pmis_finance_invoice.approved_by   IS '审批人ID(雪花ID VARCHAR(20))';
COMMENT ON COLUMN pmis_finance_invoice.issued_by     IS '开票人ID(雪花ID VARCHAR(20))';
COMMENT ON COLUMN pmis_finance_payment.confirmed_by  IS '确认人ID(雪花ID VARCHAR(20))';
COMMENT ON COLUMN pmis_finance_payment.recorded_by   IS '录入人ID(雪花ID VARCHAR(20))';

ANALYZE pmis_profit_revenue;
ANALYZE pmis_finance_payment;

-- ====================================================================
-- ============================ [063] tg_set_updated_at trigger ============================
-- ====================================================================
-- V1.0.0_063  P1-5 通用 updated_at 数据库触发器
-- ----------------------------------------------------------------------------
-- 背景:
--   AuditFieldFiller 仅在 MyBatis-Plus 写路径生效,以下场景会导致 updated_at 失真:
--     1. 原始 SQL / psql / 批量脚本更新
--     2. 跨服务 Feign 调用后由对方直连 PG 写库
--     3. 定时任务中通过 JdbcTemplate.update 直接 UPDATE
--   解决: 数据库层兜底触发器,确保 updated_at 始终反映真实变更时间
--
-- 设计:
--   - 通用函数 pmis_set_updated_at(): 取 NEW.updated_at 与 CURRENT_TIMESTAMP 的较大值
--   - 触发器命名: tg_<table>_updated_at,行级 BEFORE UPDATE
--   - 仅当 NEW 与 OLD 实际有差异时才更新(避免无意义 UPDATE 触发)
--   - 不影响 created_at / created_by(只读)
--
-- 挂载原则:
--   - 仅挂载"核心业务表"(用户/权限/项目/合同/财务/工作流主表)
--   - 不挂载日志表(pmis_operation_log/flow_audit_log 自身无 updated_at)
--   - 不挂载字典/配置/只读表
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- 1) 通用触发器函数(BEFORE UPDATE 行级,只更新 updated_at)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pmis_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    -- 行无实际变更时不更新(避免 no-op UPDATE 触发)
    IF NEW IS DISTINCT FROM OLD THEN
        NEW.updated_at := CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION pmis_set_updated_at() IS
    '通用 updated_at 维护:BEFORE UPDATE 时将 NEW.updated_at 置为 CURRENT_TIMESTAMP;'
    '仅当 NEW 与 OLD 实际不同时触发(避免 no-op UPDATE 引起的批量时间漂移)';

-- ----------------------------------------------------------------------------
-- 2) 通用挂载辅助函数
--    用法: SELECT pmis_attach_updated_at_trigger('pmis_user_account');
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pmis_attach_updated_at_trigger(p_table_name TEXT)
RETURNS VOID AS $$
DECLARE
    trigger_name TEXT;
BEGIN
    trigger_name := 'tg_' || p_table_name || '_updated_at';

    -- 已挂载则跳过
    IF EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = trigger_name
          AND tgrelid = (p_table_name)::regclass
    ) THEN
        RETURN;
    END IF;

    -- 表必须存在
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = p_table_name
    ) THEN
        RAISE WARNING '[pmis_attach_updated_at_trigger] 表不存在,跳过: %', p_table_name;
        RETURN;
    END IF;

    -- 表必须有 updated_at 列
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = p_table_name
          AND column_name = 'updated_at'
    ) THEN
        RAISE WARNING '[pmis_attach_updated_at_trigger] 表无 updated_at 列,跳过: %', p_table_name;
        RETURN;
    END IF;

    EXECUTE FORMAT(
        'CREATE TRIGGER %I BEFORE UPDATE ON %I '
        'FOR EACH ROW EXECUTE FUNCTION pmis_set_updated_at()',
        trigger_name, p_table_name
    );

    RAISE NOTICE '[pmis_attach_updated_at_trigger] 挂载成功: % (trigger: %)',
        p_table_name, trigger_name;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION pmis_attach_updated_at_trigger(TEXT) IS
    '通用挂载函数: 为指定 public 表添加 tg_<table>_updated_at BEFORE UPDATE 触发器;'
    '已挂载 / 表不存在 / 缺 updated_at 列时静默跳过';

-- ----------------------------------------------------------------------------
-- 3) 挂载到核心业务表(15 张)
--    选择标准: 频繁 UPDATE + updated_at 业务强相关 + 跨服务写路径多
-- ----------------------------------------------------------------------------
SELECT pmis_attach_updated_at_trigger('pmis_user_account');         -- 用户账号
SELECT pmis_attach_updated_at_trigger('pmis_employee');             -- 员工
SELECT pmis_attach_updated_at_trigger('pmis_department');           -- 部门
SELECT pmis_attach_updated_at_trigger('pmis_position');             -- 岗位
SELECT pmis_attach_updated_at_trigger('pmis_role');                 -- 角色
SELECT pmis_attach_updated_at_trigger('pmis_config');               -- 系统配置
SELECT pmis_attach_updated_at_trigger('pmis_dict_item');            -- 字典项
SELECT pmis_attach_updated_at_trigger('pmis_dict_version');         -- 字典版本
SELECT pmis_attach_updated_at_trigger('pmis_project_initiation');   -- 立项
SELECT pmis_attach_updated_at_trigger('pmis_project_change');       -- 变更
SELECT pmis_attach_updated_at_trigger('pmis_finance_contract');     -- 合同
SELECT pmis_attach_updated_at_trigger('pmis_finance_invoice');      -- 发票
SELECT pmis_attach_updated_at_trigger('pmis_finance_payment');      -- 回款
SELECT pmis_attach_updated_at_trigger('pmis_flow_instance');        -- 流程实例
SELECT pmis_attach_updated_at_trigger('pmis_flow_definition');      -- 流程定义

-- ----------------------------------------------------------------------------
-- 3.1) 批量挂载剩余所有含 updated_at 列的 pmis_ 表
--      上方 15 张核心表已显式挂载; 此处用 DO 块动态扫描 information_schema,
--      为所有尚未挂载触发器且含 updated_at 列的 pmis_ 表自动挂载。
--      pmis_attach_updated_at_trigger() 自身幂等: 已挂载 / 表不存在 / 缺
--      updated_at 列时均静默跳过, 故可安全覆盖全部表。
--      覆盖: 规则/成本/利润/EVM/费率/资源/考勤/运维/工单/满意度/对账/
--      利用率/工作流子表/报表/导出/2FA/会话/敏感操作等(约 80+ 张表)。
--      日志表(pmis_operation_log / pmis_flow_audit_log 等)无 updated_at 列,
--      会被辅助函数自动跳过。
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    t_name TEXT;
BEGIN
    FOR t_name IN
        SELECT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema
         AND t.table_name = c.table_name
        WHERE c.table_schema = 'public'
          AND c.column_name = 'updated_at'
          AND t.table_type = 'BASE TABLE'
          AND c.table_name LIKE 'pmis\_%' ESCAPE '\'
          -- 排除分区子表(由父表继承,无需单独挂载)
          AND c.table_name NOT LIKE '%_default'
    LOOP
        PERFORM pmis_attach_updated_at_trigger(t_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- ====================================================================
