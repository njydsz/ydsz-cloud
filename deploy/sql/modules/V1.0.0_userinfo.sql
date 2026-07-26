-- ============================================================
-- YDSZ userinfo module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================
-- 本脚本 DDL 对应后端 userinfo 服务 (ydsz-userinfo) 的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign + NameAssembler(在 CommonAutoConfiguration 注册)。
-- ====================================================================
-- Schema 划分
-- ====================================================================
-- ====================================================================
-- 1. 字典/枚举值模块
-- ====================================================================

-- 字典类型表
CREATE TABLE IF NOT EXISTS ydsz_dict_type(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    type_code       VARCHAR(64)    NOT NULL,
    type_name       VARCHAR(128)   NOT NULL,
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    -- 数据完整性约束
    CONSTRAINT uk_ydsz_dict_type_code UNIQUE (type_code, deleted),
    CONSTRAINT ck_pdt_status_enum    CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pdt_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_dict_type IS '字典类型表: 业务字典分类定义(如项目类型、招采方式、计费方式)';

COMMENT ON COLUMN ydsz_dict_type.id IS '主键 ID';

COMMENT ON COLUMN ydsz_dict_type.type_code IS '字典类型编码(全局唯一,如 project_type/expense_category)';

COMMENT ON COLUMN ydsz_dict_type.type_name IS '字典类型名称(中文展示名)';

COMMENT ON COLUMN ydsz_dict_type.description IS '字典类型业务说明';

COMMENT ON COLUMN ydsz_dict_type.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN ydsz_dict_type.created_by IS '创建人 ID(SYSTEM=系统初始化)';

COMMENT ON COLUMN ydsz_dict_type.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_dict_type.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_dict_type.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_dict_type.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_dict_type.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_dict_type_status ON ydsz_dict_type (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_dict_type_tenant_created
    ON ydsz_dict_type (tenant_id, created_at DESC) WHERE deleted = 0;

-- 字典项表
CREATE TABLE IF NOT EXISTS ydsz_dict_item(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    type_code       VARCHAR(64)    NOT NULL,
    item_code       VARCHAR(64)    NOT NULL,
    item_value      VARCHAR(255)   NOT NULL,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    parent_id       VARCHAR(20)         NOT NULL DEFAULT 0,
    description     TEXT,
    ext_json        JSONB,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    -- 数据完整性约束
    CONSTRAINT uk_ydsz_dict_item UNIQUE (type_code, item_code, deleted),
    CONSTRAINT ck_pdi_status_enum   CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pdi_deleted_enum  CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_pdi_sort_nonneg   CHECK (sort_order >= 0)
);

COMMENT ON TABLE ydsz_dict_item IS '字典项表: 字典类型下的具体枚举值(如项目类型下的 SYSTEM_DEV/T_M 等)';

COMMENT ON COLUMN ydsz_dict_item.id IS '主键 ID';

COMMENT ON COLUMN ydsz_dict_item.type_code IS '所属字典类型编码(关联 ydsz_dict_type.type_code)';

COMMENT ON COLUMN ydsz_dict_item.item_code IS '字典项编码(type_code 下唯一,如 SYSTEM_DEV/T_M)';

COMMENT ON COLUMN ydsz_dict_item.item_value IS '字典项展示值(中文)';

COMMENT ON COLUMN ydsz_dict_item.sort_order IS '字典项排序号(升序)';

COMMENT ON COLUMN ydsz_dict_item.parent_id IS '父级字典项 ID(0=根,支持树形字典)';

COMMENT ON COLUMN ydsz_dict_item.description IS '字典项业务说明';

COMMENT ON COLUMN ydsz_dict_item.ext_json IS '扩展属性 JSONB(如颜色/图标/跳转链接)';

COMMENT ON COLUMN ydsz_dict_item.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN ydsz_dict_item.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_dict_item.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_dict_item.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_dict_item.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_dict_item.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX IF NOT EXISTS idx_ydsz_dict_item_type ON ydsz_dict_item (type_code) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_dict_item_status ON ydsz_dict_item (status) WHERE deleted = 0;

-- ====================================================================
-- 2. RBAC 权限模块
-- ====================================================================

-- 角色表
CREATE TABLE IF NOT EXISTS ydsz_role(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    role_code       VARCHAR(64)    NOT NULL,
    role_name       VARCHAR(64)    NOT NULL,
    description     TEXT,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    data_scope      VARCHAR(16)    NOT NULL DEFAULT 'SELF',
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_role_code UNIQUE (role_code, deleted),
    CONSTRAINT ck_pr_status_enum    CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pr_data_scope     CHECK (data_scope IN ('ALL', 'DEPT', 'DEPT_AND_CHILD', 'SELF', 'CUSTOM')),
    CONSTRAINT ck_pr_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_role IS '角色表: RBAC 角色定义,关联权限与数据范围';

COMMENT ON COLUMN ydsz_role.id IS '主键 ID';

COMMENT ON COLUMN ydsz_role.role_code IS '角色编码(全局唯一,如 SUPER_ADMIN/PM)';

COMMENT ON COLUMN ydsz_role.role_name IS '角色名称(中文展示名)';

COMMENT ON COLUMN ydsz_role.description IS '角色业务说明(职责、适用场景)';

COMMENT ON COLUMN ydsz_role.sort_order IS '角色排序号(升序)';

COMMENT ON COLUMN ydsz_role.data_scope IS '数据权限范围: ALL 全部 / DEPT 本部门 / DEPT_AND_CHILD 本部门及下级 / SELF 本人 / CUSTOM 自定义';

COMMENT ON COLUMN ydsz_role.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN ydsz_role.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_role.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_role.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_role.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_role.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_role.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_role_status ON ydsz_role (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_role_tenant_created
    ON ydsz_role (tenant_id, created_at DESC) WHERE deleted = 0;

-- 权限/菜单表
CREATE TABLE IF NOT EXISTS ydsz_permission(
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
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_permission_code UNIQUE (perm_code, deleted),
    CONSTRAINT ck_pp_perm_type    CHECK (perm_type IN ('MENU', 'BUTTON', 'API')),
    CONSTRAINT ck_pp_status_enum  CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pp_visible_enum CHECK (visible IN (0, 1)),
    CONSTRAINT ck_pp_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_permission IS '权限/菜单表: 树形结构,涵盖菜单/按钮/API 三类权限';

COMMENT ON COLUMN ydsz_permission.id IS '主键 ID';

COMMENT ON COLUMN ydsz_permission.parent_id IS '父级权限 ID(0=根,支持多级树)';

COMMENT ON COLUMN ydsz_permission.perm_code IS '权限编码(全局唯一,格式: module:entity:action,如 project:contract:create)';

COMMENT ON COLUMN ydsz_permission.perm_name IS '权限名称(中文展示名)';

COMMENT ON COLUMN ydsz_permission.perm_type IS '权限类型: MENU 菜单 / BUTTON 按钮 / API 接口';

COMMENT ON COLUMN ydsz_permission.path IS '前端路由路径(菜单/按钮可空)';

COMMENT ON COLUMN ydsz_permission.component IS '前端组件路径(对应 views 目录)';

COMMENT ON COLUMN ydsz_permission.icon IS '菜单图标(Element Plus 图标名)';

COMMENT ON COLUMN ydsz_permission.sort_order IS '排序号(同级升序)';

COMMENT ON COLUMN ydsz_permission.visible IS '是否显示: 1 显示 / 0 隐藏(按钮权限一般隐藏)';

COMMENT ON COLUMN ydsz_permission.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN ydsz_permission.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_permission.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_permission.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_permission.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_permission.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_permission.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_permission_parent ON ydsz_permission (parent_id);

CREATE INDEX IF NOT EXISTS idx_ydsz_permission_type ON ydsz_permission (perm_type) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_permission_tenant ON ydsz_permission(tenant_id);

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS ydsz_user_role(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    user_id         VARCHAR(20)         NOT NULL,
    role_id         VARCHAR(20)         NOT NULL,
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_user_role UNIQUE (user_id, role_id, deleted),
    CONSTRAINT ck_pur_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_user_role IS '用户-角色关联表: 多对多,用户可同时拥有多个角色';

COMMENT ON COLUMN ydsz_user_role.id IS '主键 ID';

COMMENT ON COLUMN ydsz_user_role.user_id IS '用户 ID(关联 ydsz_user_account.id)';

COMMENT ON COLUMN ydsz_user_role.role_id IS '角色 ID(关联 ydsz_role.id)';

COMMENT ON COLUMN ydsz_user_role.created_by IS '授权人 ID';

COMMENT ON COLUMN ydsz_user_role.created_at IS '授权时间';

COMMENT ON COLUMN ydsz_user_role.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_user_role.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_user_role_user ON ydsz_user_role (user_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_user_role_role ON ydsz_user_role (role_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_user_role_tenant ON ydsz_user_role(tenant_id);

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS ydsz_role_permission(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    role_id         VARCHAR(20)         NOT NULL,
    permission_id   VARCHAR(20)         NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_role_permission UNIQUE (role_id, permission_id, deleted),
    CONSTRAINT ck_prp_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_role_permission IS '角色-权限关联表: 多对多,角色绑定具体可访问的权限点';

COMMENT ON COLUMN ydsz_role_permission.id IS '主键 ID';

COMMENT ON COLUMN ydsz_role_permission.role_id IS '角色 ID(关联 ydsz_role.id)';

COMMENT ON COLUMN ydsz_role_permission.permission_id IS '权限 ID(关联 ydsz_permission.id)';

COMMENT ON COLUMN ydsz_role_permission.created_at IS '授权时间';

COMMENT ON COLUMN ydsz_role_permission.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_role_permission.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_role_permission_deleted ON ydsz_role_permission(deleted);

CREATE INDEX IF NOT EXISTS idx_role_permission_tenant ON ydsz_role_permission(tenant_id);

CREATE INDEX IF NOT EXISTS idx_role_permission_perm
    ON ydsz_role_permission(permission_id) WHERE deleted = 0;

-- ====================================================================
-- 3. 组织/人员模块
-- ====================================================================

-- 部门表
CREATE TABLE IF NOT EXISTS ydsz_department(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
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
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_department_code UNIQUE (dept_code, deleted),
    CONSTRAINT ck_pd_status_enum  CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pd_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_department IS '部门表: 树形组织架构,支持多级嵌套与路径检索';

COMMENT ON COLUMN ydsz_department.id IS '主键 ID';

COMMENT ON COLUMN ydsz_department.dept_code IS '部门编码(全局唯一,如 TECH/HR)';

COMMENT ON COLUMN ydsz_department.dept_name IS '部门名称';

COMMENT ON COLUMN ydsz_department.parent_id IS '父级部门 ID(0=根)';

COMMENT ON COLUMN ydsz_department.dept_path IS '部门路径(以斜杠分隔的祖先链路,如 /1/3/5,用于子树查询)';

COMMENT ON COLUMN ydsz_department.sort_order IS '部门排序号(同级升序)';

COMMENT ON COLUMN ydsz_department.leader_id IS '部门负责人 ID(关联 ydsz_employee.id)';

COMMENT ON COLUMN ydsz_department.phone IS '部门电话';

COMMENT ON COLUMN ydsz_department.email IS '部门邮箱';

COMMENT ON COLUMN ydsz_department.description IS '部门职责说明';

COMMENT ON COLUMN ydsz_department.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN ydsz_department.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_department.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_department.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_department.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_department.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_department.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_department_parent ON ydsz_department (parent_id);

CREATE INDEX IF NOT EXISTS idx_ydsz_department_status ON ydsz_department (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_department_tenant ON ydsz_department(tenant_id);

CREATE INDEX IF NOT EXISTS idx_department_tenant_created
    ON ydsz_department(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_dept_leader
    ON ydsz_department(leader_id) WHERE deleted = 0;

-- 岗位表
CREATE TABLE IF NOT EXISTS ydsz_post(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    position_code   VARCHAR(64)    NOT NULL,
    position_name   VARCHAR(128)   NOT NULL,
    department_id   VARCHAR(20)         NOT NULL,
    level_code      VARCHAR(8)     NOT NULL,
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_post_code UNIQUE (position_code, deleted),
    CONSTRAINT ck_pp_status_enum  CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pp_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_post IS '岗位表: 部门下的具体岗位定义(如开发工程师/PM/HRBP)';

COMMENT ON COLUMN ydsz_post.id IS '主键 ID';

COMMENT ON COLUMN ydsz_post.position_code IS '岗位编码(全局唯一)';

COMMENT ON COLUMN ydsz_post.position_name IS '岗位名称';

COMMENT ON COLUMN ydsz_post.department_id IS '所属部门 ID(关联 ydsz_department.id)';

COMMENT ON COLUMN ydsz_post.level_code IS '岗位职级(关联 ydsz_rank.level_code)';

COMMENT ON COLUMN ydsz_post.description IS '岗位职责说明';

COMMENT ON COLUMN ydsz_post.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN ydsz_post.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_post.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_post.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_post.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_post.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_post.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_post_dept ON ydsz_post (department_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_position_tenant ON ydsz_post(tenant_id);

CREATE INDEX IF NOT EXISTS idx_position_tenant_created
    ON ydsz_post(tenant_id, created_at DESC) WHERE deleted = 0;

-- 职级表 (L1-L18)
CREATE TABLE IF NOT EXISTS ydsz_rank(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    level_code      VARCHAR(8)     NOT NULL,
    level_name      VARCHAR(64)    NOT NULL,
    level_segment   VARCHAR(16)    NOT NULL,
    sort_order      INTEGER        NOT NULL,
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_rank_code UNIQUE (level_code, deleted),
    CONSTRAINT ck_pr_segment     CHECK (level_segment IN ('PRIMARY', 'MIDDLE', 'SENIOR', 'EXPERT', 'STRATEGIC')),
    CONSTRAINT ck_pr_status_enum CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pr_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_rank IS '职级表: L1-L18 共 18 级,定义能力晋升阶梯';

COMMENT ON COLUMN ydsz_rank.id IS '主键 ID';

COMMENT ON COLUMN ydsz_rank.level_code IS '职级编码(L1-L18)';

COMMENT ON COLUMN ydsz_rank.level_name IS '职级名称(如助理工程师/开发工程师/架构师)';

COMMENT ON COLUMN ydsz_rank.level_segment IS '职级段: PRIMARY 初级(L1-L3) / MIDDLE 中级(L4-L6) / SENIOR 高级(L7-L9) / EXPERT 专家(L10-L12) / STRATEGIC 战略(L13-L18)';

COMMENT ON COLUMN ydsz_rank.sort_order IS '职级排序号(升序,L1=1)';

COMMENT ON COLUMN ydsz_rank.description IS '职级能力要求说明';

COMMENT ON COLUMN ydsz_rank.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN ydsz_rank.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_rank.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_rank.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_rank.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_rank.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_rank.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_rank_tenant ON ydsz_rank(tenant_id);

CREATE INDEX IF NOT EXISTS idx_rank_tenant_sort
    ON ydsz_rank(tenant_id, sort_order) WHERE deleted = 0;

-- 职级费率表 (对外人天 / 对内人天)
CREATE TABLE IF NOT EXISTS ydsz_rank_rate(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
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
    created_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_rank_rate UNIQUE (level_code, version, deleted),
    CONSTRAINT ck_prr_external_nonneg CHECK (external_daily >= 0 AND internal_daily >= 0),
    CONSTRAINT ck_prr_billable_range  CHECK (billable_target >= 0 AND billable_target <= 1),
    CONSTRAINT ck_prr_dates_valid     CHECK (expire_date IS NULL OR expire_date >= effective_date),
    CONSTRAINT ck_prr_deleted_enum    CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_prr_cost_valid      CHECK (total_cost = base_salary + social_company + fund_company + travel_reimbursement + travel_allowance)
);

COMMENT ON TABLE ydsz_rank_rate IS '职级费率表(双费率): 对外报价人天 / 对内成本人天 / 五险一金+差旅成本拆解,支持版本化生效';

COMMENT ON COLUMN ydsz_rank_rate.id IS '主键 ID';

COMMENT ON COLUMN ydsz_rank_rate.level_code IS '职级编码(L1-L18,关联 ydsz_rank.level_code)';

COMMENT ON COLUMN ydsz_rank_rate.external_daily IS '对外人天单价(元/天,用于向客户报价)';

COMMENT ON COLUMN ydsz_rank_rate.internal_daily IS '对内人天成本(元/天,用于内部利润核算)';

COMMENT ON COLUMN ydsz_rank_rate.base_salary IS '月度基础工资(元)';

COMMENT ON COLUMN ydsz_rank_rate.social_company IS '公司社保部分(元/月)';

COMMENT ON COLUMN ydsz_rank_rate.social_personal IS '个人社保部分(元/月,从工资扣除)';

COMMENT ON COLUMN ydsz_rank_rate.fund_company IS '公司公积金部分(元/月)';

COMMENT ON COLUMN ydsz_rank_rate.fund_personal IS '个人公积金部分(元/月,从工资扣除)';

COMMENT ON COLUMN ydsz_rank_rate.take_home IS '税后到手工资(元/月)';

COMMENT ON COLUMN ydsz_rank_rate.travel_reimbursement IS '差旅报销-公司承担部分(元/月)';

COMMENT ON COLUMN ydsz_rank_rate.travel_allowance IS '差旅补贴-公司承担部分(元/月)';

COMMENT ON COLUMN ydsz_rank_rate.total_cost IS '公司总人力成本(元/月,=base_salary+social_company+fund_company+travel_reimbursement+travel_allowance)';

COMMENT ON COLUMN ydsz_rank_rate.billable_target IS '可计费利用率目标(0.0-1.0,如 0.78=78%)';

COMMENT ON COLUMN ydsz_rank_rate.effective_date IS '生效日期';

COMMENT ON COLUMN ydsz_rank_rate.expire_date IS '失效日期(NULL 表示长期有效)';

COMMENT ON COLUMN ydsz_rank_rate.version IS '版本号(同职级可有多版本,通过 effective_date 区分)';

COMMENT ON COLUMN ydsz_rank_rate.description IS '费率版本说明';

COMMENT ON COLUMN ydsz_rank_rate.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_rank_rate.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_rank_rate.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_rank_rate.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_rank_rate.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_rank_rate.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_rank_rate_code ON ydsz_rank_rate (level_code) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_rank_rate_effective ON ydsz_rank_rate (effective_date, expire_date);

CREATE INDEX IF NOT EXISTS idx_rank_rate_tenant ON ydsz_rank_rate(tenant_id);

-- 员工表
CREATE TABLE IF NOT EXISTS ydsz_employee(
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
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_emp_code UNIQUE (emp_code, deleted),
    CONSTRAINT ck_pe_gender_enum      CHECK (gender IN ('M', 'F', 'U')),
    CONSTRAINT ck_pe_employee_type   CHECK (employee_type IN ('FULL_TIME', 'PART_TIME', 'OUTSOURCE')),
    CONSTRAINT ck_pe_work_status     CHECK (work_status IN ('ACTIVE', 'LEAVE', 'SUSPEND', 'PROBATION')),
    CONSTRAINT ck_pe_bench_status     CHECK (bench_status IN ('YES', 'NO', 'TRAINING')),
    CONSTRAINT ck_pe_dates_valid      CHECK (leave_date IS NULL OR leave_date >= hire_date),
    CONSTRAINT ck_pe_deleted_enum     CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_employee IS '员工表: 员工主数据,关联用户账号/部门/岗位/职级,敏感字段加密存储';

COMMENT ON COLUMN ydsz_employee.id IS '主键 ID';

COMMENT ON COLUMN ydsz_employee.user_id IS '关联用户账号 ID(关联 ydsz_user_account.id)';

COMMENT ON COLUMN ydsz_employee.emp_code IS '工号(全局唯一,如 E20260001)';

COMMENT ON COLUMN ydsz_employee.emp_name IS '员工姓名';

COMMENT ON COLUMN ydsz_employee.id_card IS '身份证号(脱敏显示,完整数据见 id_card_enc)';

COMMENT ON COLUMN ydsz_employee.id_card_enc IS '身份证号 SM4 加密密文';

COMMENT ON COLUMN ydsz_employee.gender IS '性别: M 男 / F 女 / U 未知';

COMMENT ON COLUMN ydsz_employee.birth_date IS '出生日期';

COMMENT ON COLUMN ydsz_employee.phone IS '手机号(脱敏显示,完整数据见 phone_enc)';

COMMENT ON COLUMN ydsz_employee.phone_enc IS '手机号 SM4 加密密文';

COMMENT ON COLUMN ydsz_employee.email IS '企业邮箱';

COMMENT ON COLUMN ydsz_employee.department_id IS '所属部门 ID(关联 ydsz_department.id)';

COMMENT ON COLUMN ydsz_employee.position_id IS '岗位 ID(关联 ydsz_post.id)';

COMMENT ON COLUMN ydsz_employee.level_code IS '职级编码(全职 L1-L18 / 兼职 P1-P18,关联 ydsz_rank 或 ydsz_part_time_rate)';

COMMENT ON COLUMN ydsz_employee.employee_type IS '雇佣类型: FULL_TIME 全职 / PART_TIME 兼职 / OUTSOURCE 外包';

COMMENT ON COLUMN ydsz_employee.part_time_rate_id IS '兼职费率 ID(仅 PART_TIME 类型填写,关联 ydsz_part_time_rate.id)';

COMMENT ON COLUMN ydsz_employee.outsource_rate_id IS '外包费率 ID(仅 OUTSOURCE 类型填写,关联 ydsz_outsource_rate.id)';

COMMENT ON COLUMN ydsz_employee.hire_date IS '入职日期';

COMMENT ON COLUMN ydsz_employee.leave_date IS '离职日期(在职为 NULL)';

COMMENT ON COLUMN ydsz_employee.work_status IS '在职状态: ACTIVE 在职 / LEAVE 离职 / SUSPEND 停薪留职 / PROBATION 试用期';

COMMENT ON COLUMN ydsz_employee.bench_status IS 'Bench 状态: YES 闲置中 / NO 在项目中 / TRAINING 培训期';

COMMENT ON COLUMN ydsz_employee.bench_start IS '进入 Bench 的起始日期';

COMMENT ON COLUMN ydsz_employee.avatar IS '头像 URL';

COMMENT ON COLUMN ydsz_employee.address IS '家庭住址';

COMMENT ON COLUMN ydsz_employee.emergency_contact IS '紧急联系人姓名';

COMMENT ON COLUMN ydsz_employee.emergency_phone IS '紧急联系人电话';

COMMENT ON COLUMN ydsz_employee.description IS '备注';

COMMENT ON COLUMN ydsz_employee.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_employee.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_employee.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_employee.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_employee.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_employee.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_emp_user ON ydsz_employee (user_id);

CREATE INDEX IF NOT EXISTS idx_ydsz_emp_dept ON ydsz_employee (department_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_emp_level ON ydsz_employee (level_code) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_emp_type ON ydsz_employee (employee_type) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_emp_bench ON ydsz_employee (bench_status, bench_start) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_employee_tenant ON ydsz_employee(tenant_id);

CREATE INDEX IF NOT EXISTS idx_employee_tenant_created
    ON ydsz_employee(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_emp_position
    ON ydsz_employee(position_id) WHERE deleted = 0;

-- 兼职职级费率表 (P1-P18, 时薪核算月薪+商业保险+差旅)
CREATE TABLE IF NOT EXISTS ydsz_part_time_rate(
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
    created_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_part_time_rate UNIQUE (rate_code, version, deleted),
    CONSTRAINT ck_ptr_segment         CHECK (level_segment IN ('PRIMARY', 'MIDDLE', 'SENIOR', 'EXPERT', 'STRATEGIC')),
    CONSTRAINT ck_ptr_status_enum     CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_ptr_dates_valid     CHECK (expire_date IS NULL OR expire_date >= effective_date),
    CONSTRAINT ck_ptr_deleted_enum    CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_ptr_salary_valid    CHECK (monthly_salary = ROUND(hourly_rate * monthly_hours, 2)),
    CONSTRAINT ck_ptr_cost_valid      CHECK (total_cost = monthly_salary + commercial_insurance + travel_reimbursement + travel_allowance)
);

COMMENT ON TABLE ydsz_part_time_rate IS '兼职职级费率表(P1-P18): 时薪核算月薪+商业保险+差旅成本拆解,支持版本化生效';

COMMENT ON COLUMN ydsz_part_time_rate.id IS '主键 ID';

COMMENT ON COLUMN ydsz_part_time_rate.rate_code IS '兼职级别编码(P1-P18)';

COMMENT ON COLUMN ydsz_part_time_rate.rate_name IS '级别名称(如兼职初级工程师)';

COMMENT ON COLUMN ydsz_part_time_rate.level_segment IS '级别段: PRIMARY 初级(P1-P3) / MIDDLE 中级(P4-P6) / SENIOR 高级(P7-P9) / EXPERT 专家(P10-P12) / STRATEGIC 战略(P13-P18)';

COMMENT ON COLUMN ydsz_part_time_rate.hourly_rate IS '时薪(元/小时,兼职核心计价单元)';

COMMENT ON COLUMN ydsz_part_time_rate.monthly_hours IS '月工时数(默认176小时=22天×8小时)';

COMMENT ON COLUMN ydsz_part_time_rate.monthly_salary IS '月度薪资(元/月,= hourly_rate × monthly_hours)';

COMMENT ON COLUMN ydsz_part_time_rate.commercial_insurance IS '商业保险-公司承担部分(元/月)';

COMMENT ON COLUMN ydsz_part_time_rate.travel_reimbursement IS '差旅报销-公司承担部分(元/月)';

COMMENT ON COLUMN ydsz_part_time_rate.travel_allowance IS '差旅补贴-公司承担部分(元/月)';

COMMENT ON COLUMN ydsz_part_time_rate.total_cost IS '公司总人力成本(元/月,=monthly_salary+commercial_insurance+travel_reimbursement+travel_allowance)';

COMMENT ON COLUMN ydsz_part_time_rate.external_daily IS '对外人天单价(元/天,用于向客户报价)';

COMMENT ON COLUMN ydsz_part_time_rate.internal_daily IS '对内人天成本(元/天,用于内部利润核算)';

COMMENT ON COLUMN ydsz_part_time_rate.billable_target IS '可计费利用率目标(0.0-1.0)';

COMMENT ON COLUMN ydsz_part_time_rate.sort_order IS '排序序号';

COMMENT ON COLUMN ydsz_part_time_rate.effective_date IS '生效日期';

COMMENT ON COLUMN ydsz_part_time_rate.expire_date IS '失效日期(NULL 表示长期有效)';

COMMENT ON COLUMN ydsz_part_time_rate.version IS '版本号(同级别可有多版本,通过 effective_date 区分)';

COMMENT ON COLUMN ydsz_part_time_rate.description IS '费率版本说明';

COMMENT ON COLUMN ydsz_part_time_rate.status IS '状态: ACTIVE 启用 / INACTIVE 停用';

COMMENT ON COLUMN ydsz_part_time_rate.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_part_time_rate.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_part_time_rate.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_part_time_rate.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_part_time_rate.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_part_time_rate.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ptr_code ON ydsz_part_time_rate (rate_code) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ptr_effective ON ydsz_part_time_rate (effective_date, expire_date);

CREATE INDEX IF NOT EXISTS idx_ptr_tenant ON ydsz_part_time_rate(tenant_id);

CREATE INDEX IF NOT EXISTS idx_ptr_tenant_sort ON ydsz_part_time_rate(tenant_id, sort_order) WHERE deleted = 0;

-- 外包职级费率表 (V1-V18, 人天核算月薪+差旅报销+差旅补贴)
CREATE TABLE IF NOT EXISTS ydsz_outsource_rate(
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
    created_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_outsource_rate UNIQUE (rate_code, version, deleted),
    CONSTRAINT ck_por_segment         CHECK (level_segment IN ('PRIMARY', 'MIDDLE', 'SENIOR', 'EXPERT', 'STRATEGIC')),
    CONSTRAINT ck_por_status_enum     CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_por_dates_valid     CHECK (expire_date IS NULL OR expire_date >= effective_date),
    CONSTRAINT ck_por_deleted_enum    CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_por_salary_valid    CHECK (monthly_salary = ROUND(daily_rate * monthly_days, 2)),
    CONSTRAINT ck_por_cost_valid      CHECK (total_cost = monthly_salary + travel_reimbursement + travel_allowance)
);

COMMENT ON TABLE ydsz_outsource_rate IS '外包职级费率表(V1-V18): 人天核算月薪+差旅报销+差旅补贴成本拆解,支持版本化生效';

COMMENT ON COLUMN ydsz_outsource_rate.id IS '主键 ID';

COMMENT ON COLUMN ydsz_outsource_rate.rate_code IS '外包级别编码(V1-V18)';

COMMENT ON COLUMN ydsz_outsource_rate.rate_name IS '级别名称(如外包初级工程师)';

COMMENT ON COLUMN ydsz_outsource_rate.level_segment IS '级别段: PRIMARY 初级(V1-V3) / MIDDLE 中级(V4-V6) / SENIOR 高级(V7-V9) / EXPERT 专家(V10-V12) / STRATEGIC 战略(V13-V18)';

COMMENT ON COLUMN ydsz_outsource_rate.daily_rate IS '人天单价(元/天,外包核心计价单元)';

COMMENT ON COLUMN ydsz_outsource_rate.monthly_days IS '月工作天数(默认22天)';

COMMENT ON COLUMN ydsz_outsource_rate.monthly_salary IS '月度薪资(元/月,= daily_rate × monthly_days)';

COMMENT ON COLUMN ydsz_outsource_rate.travel_reimbursement IS '差旅报销-公司承担部分(元/月)';

COMMENT ON COLUMN ydsz_outsource_rate.travel_allowance IS '差旅补贴-公司承担部分(元/月)';

COMMENT ON COLUMN ydsz_outsource_rate.total_cost IS '公司总人力成本(元/月,=monthly_salary+travel_reimbursement+travel_allowance)';

COMMENT ON COLUMN ydsz_outsource_rate.external_daily IS '对外人天单价(元/天,用于向客户报价)';

COMMENT ON COLUMN ydsz_outsource_rate.internal_daily IS '对内人天成本(元/天,用于内部利润核算)';

COMMENT ON COLUMN ydsz_outsource_rate.billable_target IS '可计费利用率目标(0.0-1.0)';

COMMENT ON COLUMN ydsz_outsource_rate.sort_order IS '排序序号';

COMMENT ON COLUMN ydsz_outsource_rate.effective_date IS '生效日期';

COMMENT ON COLUMN ydsz_outsource_rate.expire_date IS '失效日期(NULL 表示长期有效)';

COMMENT ON COLUMN ydsz_outsource_rate.version IS '版本号(同级别可有多版本,通过 effective_date 区分)';

COMMENT ON COLUMN ydsz_outsource_rate.description IS '费率版本说明';

COMMENT ON COLUMN ydsz_outsource_rate.status IS '状态: ACTIVE 启用 / INACTIVE 停用';

COMMENT ON COLUMN ydsz_outsource_rate.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_outsource_rate.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_outsource_rate.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_outsource_rate.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_outsource_rate.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_outsource_rate.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_por_code ON ydsz_outsource_rate (rate_code) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_por_effective ON ydsz_outsource_rate (effective_date, expire_date);

CREATE INDEX IF NOT EXISTS idx_por_tenant ON ydsz_outsource_rate(tenant_id);

CREATE INDEX IF NOT EXISTS idx_por_tenant_sort ON ydsz_outsource_rate(tenant_id, sort_order) WHERE deleted = 0;

-- 员工标签表
CREATE TABLE IF NOT EXISTS ydsz_employee_tag(
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

COMMENT ON TABLE ydsz_employee_tag IS '员工标签表: 技能/行业/资质/认证,支持资源池精准匹配';

COMMENT ON COLUMN ydsz_employee_tag.id IS '主键 ID';

COMMENT ON COLUMN ydsz_employee_tag.employee_id IS '员工 ID(关联 ydsz_employee.id)';

COMMENT ON COLUMN ydsz_employee_tag.tag_type IS '标签类型: TECH_STACK 技术栈 / INDUSTRY 行业经验 / DOMAIN 业务领域 / CERTIFICATE 资质证书 / SKILL 软技能';

COMMENT ON COLUMN ydsz_employee_tag.tag_code IS '标签编码(同类型下唯一,如 Java/Python/FinTech)';

COMMENT ON COLUMN ydsz_employee_tag.tag_value IS '标签值(中文展示名)';

COMMENT ON COLUMN ydsz_employee_tag.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_employee_tag.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_employee_tag.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_emp_tag_emp ON ydsz_employee_tag (employee_id);

CREATE INDEX IF NOT EXISTS idx_ydsz_emp_tag_code ON ydsz_employee_tag (tag_code);

CREATE INDEX IF NOT EXISTS idx_emp_tag_tenant ON ydsz_employee_tag(tenant_id);

CREATE INDEX IF NOT EXISTS idx_ydsz_emp_tag_deleted ON ydsz_employee_tag(deleted);

-- ====================================================================
-- 4. 用户账号
-- ====================================================================

-- 用户账号表
CREATE TABLE IF NOT EXISTS ydsz_user_account(
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
    created_by         VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT       NOT NULL DEFAULT 0,
    tenant_id          VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_user_username UNIQUE (username, deleted),
    CONSTRAINT ck_pua_status_enum   CHECK (status IN ('ENABLED', 'DISABLED', 'LOCKED')),
    CONSTRAINT ck_pua_data_scope    CHECK (data_scope IN ('ALL', 'DEPT', 'DEPT_AND_CHILD', 'SELF', 'CUSTOM')),
    CONSTRAINT ck_pua_mfa_type      CHECK (mfa_type IN ('NONE', 'TOTP', 'SMS')),
    CONSTRAINT ck_pua_fail_nonneg   CHECK (login_fail_count >= 0 AND pwd_change_count >= 0),
    CONSTRAINT ck_pua_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_user_account IS '用户账号表: 登录凭证,存储密码哈希+盐值,支持登录失败锁定与 MFA';

COMMENT ON COLUMN ydsz_user_account.id IS '主键 ID';

COMMENT ON COLUMN ydsz_user_account.username IS '登录用户名(全局唯一)';

COMMENT ON COLUMN ydsz_user_account.password IS '密码哈希(BCrypt)';

COMMENT ON COLUMN ydsz_user_account.salt IS '密码盐值';

COMMENT ON COLUMN ydsz_user_account.employee_id IS '关联员工 ID(关联 ydsz_employee.id,1 个账号对应 1 个员工)';

COMMENT ON COLUMN ydsz_user_account.status IS '账号状态: ENABLED 启用 / DISABLED 停用 / LOCKED 锁定';

COMMENT ON COLUMN ydsz_user_account.last_login_time IS '最近登录时间';

COMMENT ON COLUMN ydsz_user_account.last_login_ip IS '最近登录 IP';

COMMENT ON COLUMN ydsz_user_account.login_fail_count IS '连续登录失败次数(达到阈值触发锁定)';

COMMENT ON COLUMN ydsz_user_account.locked_until IS '锁定截止时间(到期自动解锁)';

COMMENT ON COLUMN ydsz_user_account.dept_id IS '所属部门 ID(关联 ydsz_department.id,支持 dept: 审批人展开)';

COMMENT ON COLUMN ydsz_user_account.leader_id IS '直属上级用户 ID(关联 ydsz_user_account.id,支持 leader: 审批人展开)';

COMMENT ON COLUMN ydsz_user_account.position_code IS '岗位编码(如 PM/DEV/QA/SA,支持 position: 审批人展开)';

COMMENT ON COLUMN ydsz_user_account.data_scope IS '数据权限范围: ALL 全部 / DEPT 本部门 / DEPT_AND_CHILD 本部门及下级 / SELF 本人 / CUSTOM 自定义';

COMMENT ON COLUMN ydsz_user_account.custom_dept_ids IS '自定义数据权限部门 ID 列表(逗号分隔,data_scope=CUSTOM 时生效)';

COMMENT ON COLUMN ydsz_user_account.mfa_enabled IS '是否启用双因素认证';

COMMENT ON COLUMN ydsz_user_account.mfa_type IS '双因素认证类型: NONE 未启用 / TOTP 基于时间的一次性密码 / SMS 短信验证码';

COMMENT ON COLUMN ydsz_user_account.last_pwd_change_at IS '最近密码修改时间';

COMMENT ON COLUMN ydsz_user_account.pwd_change_count IS '密码修改次数(用于强制定期改密)';

COMMENT ON COLUMN ydsz_user_account.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_user_account.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_user_account.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_user_account.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_user_account.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_user_account.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_user_status ON ydsz_user_account (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_user_account_tenant ON ydsz_user_account(tenant_id);

CREATE INDEX IF NOT EXISTS idx_user_account_tenant_created
    ON ydsz_user_account(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_user_employee
    ON ydsz_user_account(employee_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pua_dept_id
    ON ydsz_user_account(dept_id) WHERE deleted = 0 AND dept_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pua_position_code
    ON ydsz_user_account(position_code) WHERE deleted = 0 AND position_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pua_leader_id
    ON ydsz_user_account(leader_id) WHERE deleted = 0 AND leader_id IS NOT NULL;

-- ====================================================================
-- 8. 初始化数据
-- ====================================================================

-- 初始化超级管理员
-- 默认 admin 账号 (密码: admin123, 哈希算法: BCrypt, 成本因子: 10)
-- BCrypt 自带盐,无需单独存储 salt 字段(此处置空字符串)。
-- 密码使用 BCrypt 哈希,首次登录建议强制修改。
INSERT INTO ydsz_user_account (username, password, salt, status, created_by)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMy.Mrq8BpVLqMDvQXEvCJ5DEmCJWP1tCaa', '', 'ENABLED', 0)
ON CONFLICT (username, deleted) DO NOTHING;

-- 初始化职级 (L1-L18)
INSERT INTO ydsz_rank (level_code, level_name, level_segment, sort_order, description, created_by)
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
INSERT INTO ydsz_rank_rate
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
INSERT INTO ydsz_part_time_rate
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
INSERT INTO ydsz_outsource_rate
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
    ('V12', '外包资深技术专家',   'EXPERT',    545.45, 22, 11999.90, 1000,  600, 13599.90, 1200,  600,  0.65, 12, '2026-01-01', 1, 'ACTIVE', 0),
    ('V13', '外包首席架构师',     'STRATEGIC', 590.91, 22, 12999.98, 1500,  800, 15299.98, 1300,  650,  0.60, 13, '2026-01-01', 1, 'ACTIVE', 0),
    ('V14', '外包技术总监',       'STRATEGIC', 636.36, 22, 13999.92, 1500,  800, 16299.92, 1400,  700,  0.55, 14, '2026-01-01', 1, 'ACTIVE', 0),
    ('V15', '外包CTO/事业部总经理', 'STRATEGIC',681.82, 22, 15000.04, 1500,  800, 17300.04, 1500,  750,  0.50, 15, '2026-01-01', 1, 'ACTIVE', 0),
    ('V16', '外包技术副总裁',     'STRATEGIC', 727.27, 22, 15999.94, 1500,  800, 18299.94, 1600,  800,  0.45, 16, '2026-01-01', 1, 'ACTIVE', 0),
    ('V17', '外包执行副总裁',     'STRATEGIC', 772.73, 22, 16999.94, 2000, 1000, 19999.94, 1700,  850,  0.40, 17, '2026-01-01', 1, 'ACTIVE', 0),
    ('V18', '外包首席科学家',     'STRATEGIC', 818.18, 22, 17999.96, 2000, 1000, 20999.96, 1800,  900,  0.40, 18, '2026-01-01', 1, 'ACTIVE', 0)
ON CONFLICT DO NOTHING;

-- 初始化根部门
INSERT INTO ydsz_department (dept_code, dept_name, parent_id, dept_path, sort_order, status, created_by)
VALUES ('ROOT', '南京云顶数字科技有限公司', 0, '/1', 0, 'ENABLED', 0)
ON CONFLICT DO NOTHING;

-- 初始化超级管理员角色
INSERT INTO ydsz_role (role_code, role_name, data_scope, sort_order, status, created_by)
VALUES ('SUPER_ADMIN', '超级管理员', 'ALL', 0, 'ENABLED', 0)
ON CONFLICT DO NOTHING;

-- 初始化字典类型（PRD 2.3 节要求）
INSERT INTO ydsz_dict_type (type_code, type_name, description, created_by) VALUES
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
INSERT INTO ydsz_dict_item (type_code, item_code, item_value, sort_order, created_by) VALUES
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
INSERT INTO ydsz_dict_item (type_code, item_code, item_value, sort_order, created_by) VALUES
    ('project_phase', 'REQUIREMENT', '需求调研', 1, 0),
    ('project_phase', 'DEVELOPMENT', '功能开发', 2, 0),
    ('project_phase', 'TESTING',     '测试阶段', 3, 0),
    ('project_phase', 'DEPLOYMENT',  '实施上线', 4, 0),
    ('project_phase', 'ACCEPTANCE',  '项目验收', 5, 0),
    ('project_phase', 'WARRANTY',    '质保运维', 6, 0)
ON CONFLICT DO NOTHING;

-- --------------------------------------------------------------------

-- ============================ [014_1] init ydsz resource bench schema ============================

-- =====================================================
-- YDSZ 批次11 DDL：资源池 + 人员标签 + 资源分配 + Bench 闲置
-- 版本: V1.0.0_014
-- 描述: 资源池(ydsz_resource_pool)、人员标签(ydsz_employee_tag)、
--       资源分配(ydsz_resource_assignment)、Bench 闲置(ydsz_bench_record)
-- =====================================================

-- P1-7 fix: ydsz_employee_tag 已在 [001] 章节以 tag_value 字段先建,本节扩展新字段
ALTER TABLE ydsz_employee_tag
    ADD COLUMN IF NOT EXISTS tag_name     VARCHAR(256) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS proficiency  INTEGER      NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS years_exp    INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS remark       TEXT,
    ADD COLUMN IF NOT EXISTS provider_trace_id VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS created_by   VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN IF NOT EXISTS updated_by   VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 放宽 tag_type 枚举约束(原[001]是 TECH_STACK/INDUSTRY/DOMAIN/CERTIFICATE/SKILL,本节扩展为含 CERT)
ALTER TABLE ydsz_employee_tag
    DROP CONSTRAINT IF EXISTS ck_pet_tag_type;

ALTER TABLE ydsz_employee_tag
    ADD CONSTRAINT ck_pet_tag_type CHECK (tag_type IN ('SKILL', 'INDUSTRY', 'DOMAIN', 'CERT', 'TECH_STACK', 'CERTIFICATE'));

COMMENT ON COLUMN ydsz_employee_tag.employee_id IS '员工 ID';

COMMENT ON COLUMN ydsz_employee_tag.tag_type IS '标签类型: SKILL 技能 / INDUSTRY 行业 / DOMAIN 领域 / CERT 资质';

COMMENT ON COLUMN ydsz_employee_tag.tag_code IS '标签编码: 业务唯一,如 JAVA / BANKING';

COMMENT ON COLUMN ydsz_employee_tag.tag_name IS '标签名称';

COMMENT ON COLUMN ydsz_employee_tag.proficiency IS '熟练度: 1=入门 2=了解 3=熟练 4=精通 5=专家';

COMMENT ON COLUMN ydsz_employee_tag.years_exp IS '相关经验年限';

COMMENT ON COLUMN ydsz_employee_tag.remark IS '备注';

COMMENT ON COLUMN ydsz_employee_tag.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_employee_tag.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN ydsz_employee_tag.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pet_emp / idx_pet_type)
CREATE INDEX IF NOT EXISTS idx_pet_tenant_emp
    ON ydsz_employee_tag(tenant_id, employee_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pet_tenant_type_code
    ON ydsz_employee_tag(tenant_id, tag_type, tag_code)
    WHERE deleted = 0;

-- =====================================================
-- 3. 资源分配主表 ydsz_resource_assignment
-- =====================================================
CREATE TABLE IF NOT EXISTS ydsz_resource_assignment(
    id                    VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    assignment_code       VARCHAR(64)  NOT NULL,
    employee_id           VARCHAR(20)       NOT NULL,
    employee_name         VARCHAR(64),
    level_code            VARCHAR(16),
    pool_id               VARCHAR(20),
    pool_type             VARCHAR(32),                         -- 冗余池类型
    initiation_id         VARCHAR(20),                              -- 关联项目
    initiation_name       VARCHAR(256),
    opportunity_id        VARCHAR(20),                              -- 关联商机
    status                VARCHAR(32)  NOT NULL DEFAULT 'RESERVED',
    allocation            NUMERIC(5,4) NOT NULL DEFAULT 1.0,   -- 0-1
    planned_start_date    DATE,
    planned_end_date      DATE,
    actual_start_date     DATE,
    actual_end_date       DATE,
    billable              SMALLINT     NOT NULL DEFAULT 1,
    daily_hours           NUMERIC(5,2) NOT NULL DEFAULT 8.0,
    tenant_id             VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id     VARCHAR(64)  NOT NULL DEFAULT '',
    created_by            VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pra_code              UNIQUE (assignment_code, deleted),
    CONSTRAINT ck_pra_status_enum       CHECK (status IN ('RESERVED','IN_PROGRESS','TRANSFERRED','RELEASED','CANCELLED')),
    CONSTRAINT ck_pra_pool_type         CHECK (pool_type IS NULL OR pool_type IN ('HQ','DIVISION','RESERVE')),
    CONSTRAINT ck_pra_allocation_range  CHECK (allocation > 0 AND allocation <= 1),
    CONSTRAINT ck_pra_billable          CHECK (billable IN (0, 1)),
    CONSTRAINT ck_pra_daily_hours       CHECK (daily_hours > 0 AND daily_hours <= 24),
    CONSTRAINT ck_pra_dates_order       CHECK (planned_end_date IS NULL OR planned_start_date IS NULL OR planned_end_date >= planned_start_date),
    CONSTRAINT ck_pra_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  ydsz_resource_assignment IS '资源分配表: 资源预占/入场/调岗/离场的全过程,act() 入口按 action 参数映射 AssignmentStatus';

COMMENT ON COLUMN ydsz_resource_assignment.assignment_code IS '分配单号: 业务唯一,如 RA-2026-001';

COMMENT ON COLUMN ydsz_resource_assignment.employee_id IS '员工 ID';

COMMENT ON COLUMN ydsz_resource_assignment.employee_name IS '员工姓名（冗余）';

COMMENT ON COLUMN ydsz_resource_assignment.level_code IS '职级: L1-L18';

COMMENT ON COLUMN ydsz_resource_assignment.pool_id IS '所属资源池 ID';

COMMENT ON COLUMN ydsz_resource_assignment.pool_type IS '资源池类型: HQ/DIVISION/RESERVE（冗余,便于查询）';

COMMENT ON COLUMN ydsz_resource_assignment.initiation_id IS '所属立项 ID: 分配到的项目';

COMMENT ON COLUMN ydsz_resource_assignment.initiation_name IS '立项名称（冗余）';

COMMENT ON COLUMN ydsz_resource_assignment.opportunity_id IS '所属商机 ID: 商机阶段预占时填写';

COMMENT ON COLUMN ydsz_resource_assignment.status IS '分配状态: RESERVED 已预占 / IN_PROGRESS 进行中 / TRANSFERRED 已调岗 / RELEASED 已释放 / CANCELLED 已取消';

COMMENT ON COLUMN ydsz_resource_assignment.allocation IS '占用比例: 0-1,例如 0.5=50% 投入';

COMMENT ON COLUMN ydsz_resource_assignment.planned_start_date IS '计划开始日期';

COMMENT ON COLUMN ydsz_resource_assignment.planned_end_date IS '计划结束日期';

COMMENT ON COLUMN ydsz_resource_assignment.actual_start_date IS '实际开始日期';

COMMENT ON COLUMN ydsz_resource_assignment.actual_end_date IS '实际结束日期';

COMMENT ON COLUMN ydsz_resource_assignment.billable IS '是否计费: 0=非计费,1=计费';

COMMENT ON COLUMN ydsz_resource_assignment.daily_hours IS '日均工时(小时): 默认 8';

COMMENT ON COLUMN ydsz_resource_assignment.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_resource_assignment.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN ydsz_resource_assignment.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pra_emp / idx_pra_initiation / idx_pra_status / idx_pra_pool)
CREATE INDEX IF NOT EXISTS idx_pra_tenant_emp_status
    ON ydsz_resource_assignment(tenant_id, employee_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pra_tenant_initiation_status
    ON ydsz_resource_assignment(tenant_id, initiation_id, status)
    WHERE deleted = 0 AND initiation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pra_tenant_pool_status
    ON ydsz_resource_assignment(tenant_id, pool_id, status)
    WHERE deleted = 0;

-- =====================================================
-- 4. Bench 闲置记录表 ydsz_bench_record
-- =====================================================
CREATE TABLE IF NOT EXISTS ydsz_bench_record(
    id                    VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    bench_code            VARCHAR(64)  NOT NULL,
    employee_id           VARCHAR(20)       NOT NULL,
    employee_name         VARCHAR(64),
    level_code            VARCHAR(16),
    pool_id               VARCHAR(20),
    bench_reason          VARCHAR(32)  NOT NULL DEFAULT 'ENTER', -- ENTER/EXIT
    reason_type            VARCHAR(32),                          -- PROJECT_END/RESERVE/TRAINING/LEAVE
    source_assignment     BIGINT,                               -- 触发本次 Bench 的分配记录
    bench_date            DATE         NOT NULL,
    exit_date             DATE,
    idle_days             INTEGER      NOT NULL DEFAULT 0,
    status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- BenchStatus
    daily_cost            NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_idle_cost       NUMERIC(15,2) NOT NULL DEFAULT 0,
    remark                TEXT,
    tenant_id             VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id     VARCHAR(64)  NOT NULL DEFAULT '',
    created_by            VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pbr_code              UNIQUE (bench_code, deleted),
    CONSTRAINT ck_pbr_bench_reason      CHECK (bench_reason IN ('ENTER','EXIT')),
    CONSTRAINT ck_pbr_reason_type       CHECK (reason_type IS NULL OR reason_type IN ('PROJECT_END','RESERVE','TRAINING','LEAVE','OTHER')),
    CONSTRAINT ck_pbr_status_enum       CHECK (status IN ('ACTIVE','CLOSED')),
    CONSTRAINT ck_pbr_cost_nonneg       CHECK (daily_cost >= 0 AND total_idle_cost >= 0),
    CONSTRAINT ck_pbr_idle_days_nonneg  CHECK (idle_days >= 0),
    CONSTRAINT ck_pbr_exit_after_bench  CHECK (exit_date IS NULL OR exit_date >= bench_date),
    CONSTRAINT ck_pbr_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  ydsz_bench_record IS 'Bench 闲置记录表: 资源闲置期间自动入池/出池,BenchCostCalculator 计算 idleDays + totalIdleCost';

COMMENT ON COLUMN ydsz_bench_record.bench_code IS 'Bench 单号: 业务唯一,如 BENCH-2026-001';

COMMENT ON COLUMN ydsz_bench_record.employee_id IS '员工 ID';

COMMENT ON COLUMN ydsz_bench_record.employee_name IS '员工姓名（冗余）';

COMMENT ON COLUMN ydsz_bench_record.level_code IS '职级: L1-L18';

COMMENT ON COLUMN ydsz_bench_record.pool_id IS '所属资源池 ID';

COMMENT ON COLUMN ydsz_bench_record.bench_reason IS 'Bench 类型: ENTER 入池 / EXIT 出池';

COMMENT ON COLUMN ydsz_bench_record.reason_type IS '原因类型: PROJECT_END 项目结束 / RESERVE 储备 / TRAINING 培训 / LEAVE 请假';

COMMENT ON COLUMN ydsz_bench_record.source_assignment IS '触发本次 Bench 的分配记录 ID: 引用 ydsz_resource_assignment.id(Long)';

COMMENT ON COLUMN ydsz_bench_record.bench_date IS '入池日期';

COMMENT ON COLUMN ydsz_bench_record.exit_date IS '出池日期: NULL 表示仍在 Bench';

COMMENT ON COLUMN ydsz_bench_record.idle_days IS '闲置天数: ChronoUnit.DAYS.between(benchDate, exitDate or now)';

COMMENT ON COLUMN ydsz_bench_record.status IS '状态: ACTIVE 闲置中 / CLOSED 已关闭';

COMMENT ON COLUMN ydsz_bench_record.daily_cost IS '日均闲置成本(元): 按职级内部费率计算';

COMMENT ON COLUMN ydsz_bench_record.total_idle_cost IS '累计闲置成本(元) = daily_cost * idle_days';

COMMENT ON COLUMN ydsz_bench_record.remark IS '备注';

COMMENT ON COLUMN ydsz_bench_record.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_bench_record.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN ydsz_bench_record.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pbr_emp / idx_pbr_status / idx_pbr_pool / idx_pbr_date)
CREATE INDEX IF NOT EXISTS idx_pbr_tenant_emp_status
    ON ydsz_bench_record(tenant_id, employee_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pbr_tenant_pool_status
    ON ydsz_bench_record(tenant_id, pool_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pbr_tenant_active_dates
    ON ydsz_bench_record(tenant_id, bench_date DESC)
    WHERE deleted = 0 AND status = 'ACTIVE';

-- 步骤 2a：插入系统管理子菜单
INSERT INTO ydsz_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system'), 'system:user',     '用户管理',     'MENU', '/system/user',     'system/user/index',     'user',     1, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system'), 'system:role',     '角色管理',     'MENU', '/system/role',     'system/role/index',     'avatar',   2, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system'), 'system:menu',     '菜单管理',     'MENU', '/system/menu',     'system/menu/index',     'menu',     3, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system'), 'system:dept',     '部门管理',     'MENU', '/system/dept',     'system/dept/index',     'office-building', 4, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system'), 'system:dict',     '数据字典',     'MENU', '/system/dict',     'system/dict/index',     'collection', 5, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system'), 'system:rank','职级管理',     'MENU', '/system/rank','system/rank/index', 'medal',    6, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system'), 'system:config',   '参数配置',     'MENU', '/system/config',   'system/config/index',   'tools',    7, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2b：插入业务根子菜单
INSERT INTO ydsz_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'business'), 'business:opportunity', '商机管理', 'MENU', '/business/opportunity', 'business/opportunity/index', 'lightbulb', 1, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'business'), 'business:initiation',  '立项管理', 'MENU', '/business/initiation',  'business/initiation/index',  'document', 2, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'business'), 'business:contract',     '合同管理', 'MENU', '/business/contract',     'business/contract/index',     'tickets', 3, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'business'), 'business:change',       '变更管理', 'MENU', '/business/change',       'business/change/index',       'refresh', 4, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2c：插入执行根子菜单
INSERT INTO ydsz_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'execution'), 'execution:wbs',         'WBS 任务',  'MENU', '/execution/wbs',         'execution/wbs/index',         'list',     1, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'execution'), 'execution:timesheet',   '工时管理',  'MENU', '/execution/timesheet',   'execution/timesheet/index',   'timer',    2, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'execution'), 'execution:attendance',  '考勤管理',  'MENU', '/execution/attendance',  'execution/attendance/index',  'calendar', 3, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'execution'), 'execution:cost',        '成本管理',  'MENU', '/execution/cost',        'execution/cost/index',        'money',    4, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'execution'), 'execution:revenue',     '收入管理',  'MENU', '/execution/revenue',     'execution/revenue/index',     'wallet',   5, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'execution'), 'execution:risk',        '风险登记',  'MENU', '/execution/risk',        'execution/risk/index',        'warning',  6, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'execution'), 'execution:profit',      '利润分析',  'MENU', '/execution/profit',      'execution/profit/index',      'data-line',7, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'execution'), 'execution:delivery',    '交付管理',  'MENU', '/execution/delivery',    'execution/delivery/index',    'box',      8, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2d：插入财务根子菜单
INSERT INTO ydsz_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'finance'), 'finance:invoice',  '发票管理', 'MENU', '/finance/invoice',  'finance/invoice/index',  'document-copy', 1, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'finance'), 'finance:payment',  '回款管理', 'MENU', '/finance/payment',  'finance/payment/index',  'bank-card',    2, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 2e：插入报表根子菜单
INSERT INTO ydsz_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'report'), 'report:profit',    '项目利润',   'MENU', '/report/profit',    'report/profit/index',    'pie-chart',    1, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'report'), 'report:cost',      '成本明细',   'MENU', '/report/cost',      'report/cost/index',      'data-board',   2, 1, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'report'), 'report:lifecycle', '生命周期台账', 'MENU', '/report/lifecycle', 'report/lifecycle/index', 'connection',   3, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 步骤 3：插入按钮级权限（依赖步骤 2 的二级菜单）
INSERT INTO ydsz_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system:user'), 'auth:user:create', '新增用户', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system:user'), 'auth:user:update', '编辑用户', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system:user'), 'auth:user:delete', '删除用户', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system:user'), 'auth:user:reset',  '重置密码', 'BUTTON', null, null, null, 4, 0, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system:role'), 'auth:role:create', '新增角色', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system:role'), 'auth:role:update', '编辑角色', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system:role'), 'auth:role:delete', '删除角色', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system:role'), 'auth:role:assign', '分配权限', 'BUTTON', null, null, null, 4, 0, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system:menu'), 'auth:perm:create', '新增菜单', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system:menu'), 'auth:perm:update', '编辑菜单', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
    ((SELECT id FROM ydsz_permission WHERE perm_code = 'system:menu'), 'auth:perm:delete', '删除菜单', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- 二. SUPER_ADMIN 角色绑定所有权限
INSERT INTO ydsz_role_permission (role_id, permission_id)
SELECT
    (SELECT id FROM ydsz_role WHERE role_code = 'SUPER_ADMIN'),
    p.id
FROM ydsz_permission p
WHERE p.deleted = 0
  AND p.status = 'ENABLED'
ON CONFLICT DO NOTHING;

-- 三. admin 用户绑定到 SUPER_ADMIN 角色
INSERT INTO ydsz_user_role (user_id, role_id, created_by)
SELECT
    (SELECT id FROM ydsz_user_account WHERE username = 'admin'),
    (SELECT id FROM ydsz_role WHERE role_code = 'SUPER_ADMIN'),
    0
ON CONFLICT DO NOTHING;

-- ----------------------------
-- 3) 双因素认证
-- ----------------------------
CREATE TABLE IF NOT EXISTS ydsz_user_2fa (
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

COMMENT ON TABLE  ydsz_user_2fa IS '用户双因素认证表: 基于 TOTP（Time-based OTP）的双因素认证,使用 constant-time 比对防时序攻击';

COMMENT ON COLUMN ydsz_user_2fa.user_id IS '用户 ID';

COMMENT ON COLUMN ydsz_user_2fa.mfa_type IS 'MFA 类型: TOTP 时间型 / SMS 短信 / EMAIL 邮件';

COMMENT ON COLUMN ydsz_user_2fa.secret IS 'TOTP 密钥: Base32 编码,扫描二维码';

COMMENT ON COLUMN ydsz_user_2fa.binding_at IS '绑定时间';

COMMENT ON COLUMN ydsz_user_2fa.last_used_at IS '最近使用时间';

COMMENT ON COLUMN ydsz_user_2fa.backup_codes IS '备份码（密文）: 一次性,小写 hex 存储,已使用标记为 _used_<timestamp>';

COMMENT ON COLUMN ydsz_user_2fa.enabled IS '是否启用: true=启用';

COMMENT ON COLUMN ydsz_user_2fa.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_user_2fa.deleted IS '逻辑删除: 0=未删除,1=已删除';

CREATE INDEX IF NOT EXISTS idx_user_2fa_tenant_user
    ON ydsz_user_2fa(tenant_id, user_id)
    WHERE deleted = 0;

-- ----------------------------
-- 6) 用户会话（单点登录/强制下线）
-- ----------------------------
CREATE TABLE IF NOT EXISTS ydsz_user_session (
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

COMMENT ON TABLE  ydsz_user_session IS '用户活跃会话表: 单点登录/强制下线管理,SessionService 维护生命周期';

COMMENT ON COLUMN ydsz_user_session.user_id IS '用户 ID';

COMMENT ON COLUMN ydsz_user_session.session_id IS '会话 ID: 唯一';

COMMENT ON COLUMN ydsz_user_session.token_jti IS 'JWT ID: 用于 token 失效';

COMMENT ON COLUMN ydsz_user_session.login_at IS '登录时间';

COMMENT ON COLUMN ydsz_user_session.last_active_at IS '最近活跃时间';

COMMENT ON COLUMN ydsz_user_session.expire_at IS '过期时间';

COMMENT ON COLUMN ydsz_user_session.client_ip IS '客户端 IP';

COMMENT ON COLUMN ydsz_user_session.user_agent IS '浏览器 UA';

COMMENT ON COLUMN ydsz_user_session.device_type IS '设备类型: WEB / IOS / ANDROID / DESKTOP';

COMMENT ON COLUMN ydsz_user_session.status IS '会话状态: ACTIVE 活跃 / EXPIRED 过期 / KICKED 踢出 / LOGOUT 主动登出';

COMMENT ON COLUMN ydsz_user_session.logout_at IS '登出时间';

COMMENT ON COLUMN ydsz_user_session.logout_reason IS '登出原因: USER_LOGOUT / ADMIN_KICK / EXPIRED';

COMMENT ON COLUMN ydsz_user_session.trace_id IS '链路追踪 ID';

COMMENT ON COLUMN ydsz_user_session.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_user_session.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_user_session_*)
CREATE INDEX IF NOT EXISTS idx_user_session_tenant_user_status
    ON ydsz_user_session(tenant_id, user_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_user_session_tenant_expire
    ON ydsz_user_session(tenant_id, expire_at)
    WHERE deleted = 0 AND status = 'ACTIVE';

-- --------------------------------------------------------------------

-- ============================ [039] init ydsz attendance schema ============================

-- =====================================================
-- YDSZ 批次12 DDL：考勤管理(出勤/加班/请假)
-- 版本: V1.0.0_015
-- 描述: 出勤(ydsz_attendance) + 加班(ydsz_overtime) + 请假(ydsz_leave)
-- =====================================================

-- =====================================================
-- 1. 出勤记录表 ydsz_attendance
-- =====================================================
CREATE TABLE IF NOT EXISTS ydsz_attendance (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    employee_id         VARCHAR(20)       NOT NULL,
    employee_name       VARCHAR(64),
    attendance_date     DATE         NOT NULL,
    check_in_time       TIMESTAMPTZ,
    check_out_time      TIMESTAMPTZ,
    work_hours          NUMERIC(5,2) NOT NULL DEFAULT 0.0,
    overtime_hours      NUMERIC(5,2) NOT NULL DEFAULT 0.0,
    status              VARCHAR(32)  NOT NULL DEFAULT 'NORMAL',  -- NORMAL/LATE/EARLY/ABSENT/LEAVE/OVERTIME
    work_type           VARCHAR(16)  NOT NULL DEFAULT 'WORKDAY',  -- WORKDAY/WEEKEND/HOLIDAY
    remark              TEXT,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_pa_emp_date         UNIQUE (employee_id, attendance_date, deleted),
    CONSTRAINT ck_pa_status           CHECK (status IN ('NORMAL','LATE','EARLY','ABSENT','LEAVE','OVERTIME')),
    CONSTRAINT ck_pa_work_type        CHECK (work_type IN ('WORKDAY','WEEKEND','HOLIDAY')),
    CONSTRAINT ck_pa_work_hours       CHECK (work_hours >= 0),
    CONSTRAINT ck_pa_overtime_hours   CHECK (overtime_hours >= 0),
    CONSTRAINT ck_pa_check_range      CHECK (check_in_time IS NULL OR check_out_time IS NULL OR check_out_time >= check_in_time),
    CONSTRAINT ck_pa_deleted          CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  ydsz_attendance IS '员工出勤记录表: 每日打卡 + 工作时长统计,支撑项目工时分配';

COMMENT ON COLUMN ydsz_attendance.employee_id IS '员工 ID';

COMMENT ON COLUMN ydsz_attendance.employee_name IS '员工姓名（冗余）';

COMMENT ON COLUMN ydsz_attendance.attendance_date IS '出勤日期';

COMMENT ON COLUMN ydsz_attendance.check_in_time IS '上班打卡时间';

COMMENT ON COLUMN ydsz_attendance.check_out_time IS '下班打卡时间';

COMMENT ON COLUMN ydsz_attendance.work_hours IS '工作时长(小时)';

COMMENT ON COLUMN ydsz_attendance.overtime_hours IS '加班时长(小时)';

COMMENT ON COLUMN ydsz_attendance.status IS '出勤状态: NORMAL 正常 / LATE 迟到 / EARLY 早退 / ABSENT 缺勤 / LEAVE 请假 / OVERTIME 加班';

COMMENT ON COLUMN ydsz_attendance.work_type IS '日期类型: WORKDAY 工作日 / WEEKEND 周末 / HOLIDAY 节假日';

COMMENT ON COLUMN ydsz_attendance.remark IS '备注';

COMMENT ON COLUMN ydsz_attendance.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_attendance.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN ydsz_attendance.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pa_tenant_emp_date
    ON ydsz_attendance(tenant_id, employee_id, attendance_date DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pa_tenant_date
    ON ydsz_attendance(tenant_id, attendance_date DESC);

CREATE INDEX IF NOT EXISTS idx_pa_tenant_status
    ON ydsz_attendance(tenant_id, status)
    WHERE deleted = 0;

-- =====================================================
-- 2. 加班申请表 ydsz_overtime
-- =====================================================
CREATE TABLE IF NOT EXISTS ydsz_overtime (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    overtime_code       VARCHAR(64)  NOT NULL,
    employee_id         VARCHAR(20)       NOT NULL,
    employee_name       VARCHAR(64),
    overtime_date       DATE         NOT NULL,
    start_time          TIMESTAMPTZ  NOT NULL,
    end_time            TIMESTAMPTZ  NOT NULL,
    overtime_hours      NUMERIC(5,2) NOT NULL,
    overtime_type       VARCHAR(32)  NOT NULL,                   -- WORKDAY/WEEKEND/HOLIDAY
    pay_rate            NUMERIC(5,2) NOT NULL DEFAULT 1.5,       -- 1.5/2.0/3.0 倍
    reason              TEXT,
    approval_id         VARCHAR(20),
    approval_status     VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED
    approver_id         VARCHAR(20),
    approver_name       VARCHAR(64),
    approval_time       TIMESTAMPTZ,
    approval_remark     TEXT,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_pot_code                UNIQUE (overtime_code, deleted),
    CONSTRAINT ck_pot_overtime_type       CHECK (overtime_type IN ('WORKDAY','WEEKEND','HOLIDAY')),
    CONSTRAINT ck_pot_approval_status     CHECK (approval_status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_pot_hours_positive      CHECK (overtime_hours > 0),
    CONSTRAINT ck_pot_pay_rate            CHECK (pay_rate IN (1.5, 2.0, 3.0)),
    CONSTRAINT ck_pot_time_range          CHECK (end_time > start_time),
    CONSTRAINT ck_pot_deleted             CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  ydsz_overtime IS '加班申请表: WORKDAY 1.5x / WEEKEND 2.0x / HOLIDAY 3.0x 法定倍数';

COMMENT ON COLUMN ydsz_overtime.overtime_code IS '加班单号: 业务唯一,如 OT-2026-001';

COMMENT ON COLUMN ydsz_overtime.employee_id IS '员工 ID';

COMMENT ON COLUMN ydsz_overtime.employee_name IS '员工姓名（冗余）';

COMMENT ON COLUMN ydsz_overtime.overtime_date IS '加班日期';

COMMENT ON COLUMN ydsz_overtime.start_time IS '加班开始时间';

COMMENT ON COLUMN ydsz_overtime.end_time IS '加班结束时间';

COMMENT ON COLUMN ydsz_overtime.overtime_hours IS '加班时长(小时)';

COMMENT ON COLUMN ydsz_overtime.overtime_type IS '加班类型: WORKDAY 工作日 / WEEKEND 周末 / HOLIDAY 节假日';

COMMENT ON COLUMN ydsz_overtime.pay_rate IS '加班倍数: 1.5/2.0/3.0 倍,用于薪资计算';

COMMENT ON COLUMN ydsz_overtime.reason IS '加班原因';

COMMENT ON COLUMN ydsz_overtime.approval_id IS '审批流实例 ID: 关联工作流引擎';

COMMENT ON COLUMN ydsz_overtime.approval_status IS '审批状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回 / CANCELLED 已取消';

COMMENT ON COLUMN ydsz_overtime.approver_id IS '审批人 ID';

COMMENT ON COLUMN ydsz_overtime.approver_name IS '审批人姓名（冗余）';

COMMENT ON COLUMN ydsz_overtime.approval_time IS '审批时间';

COMMENT ON COLUMN ydsz_overtime.approval_remark IS '审批意见';

COMMENT ON COLUMN ydsz_overtime.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_overtime.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN ydsz_overtime.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pot_tenant_emp_date
    ON ydsz_overtime(tenant_id, employee_id, overtime_date DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pot_tenant_date
    ON ydsz_overtime(tenant_id, overtime_date DESC);

CREATE INDEX IF NOT EXISTS idx_pot_tenant_status
    ON ydsz_overtime(tenant_id, approval_status)
    WHERE deleted = 0;

-- =====================================================
-- 3. 请假申请表 ydsz_leave
-- =====================================================
CREATE TABLE IF NOT EXISTS ydsz_leave (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    leave_code          VARCHAR(64)  NOT NULL,
    employee_id         VARCHAR(20)       NOT NULL,
    employee_name       VARCHAR(64),
    leave_type          VARCHAR(32)  NOT NULL,                   -- ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/BEREAVEMENT/OTHER
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    leave_days          NUMERIC(5,2) NOT NULL,
    reason              TEXT,
    attachment_url      VARCHAR(512),
    approval_id         VARCHAR(20),
    approval_status     VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED
    approver_id         VARCHAR(20),
    approver_name       VARCHAR(64),
    approval_time       TIMESTAMPTZ,
    approval_remark     TEXT,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_pl_code                UNIQUE (leave_code, deleted),
    CONSTRAINT ck_pl_leave_type          CHECK (leave_type IN ('ANNUAL','SICK','PERSONAL','MARRIAGE','MATERNITY','BEREAVEMENT','OTHER')),
    CONSTRAINT ck_pl_approval_status     CHECK (approval_status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_pl_days_positive       CHECK (leave_days > 0),
    CONSTRAINT ck_pl_date_range          CHECK (end_date >= start_date),
    CONSTRAINT ck_pl_deleted             CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  ydsz_leave IS '请假申请表: 7 种假期类型,自动算 leave_days';

COMMENT ON COLUMN ydsz_leave.leave_code IS '请假单号: 业务唯一,如 LV-2026-001';

COMMENT ON COLUMN ydsz_leave.employee_id IS '员工 ID';

COMMENT ON COLUMN ydsz_leave.employee_name IS '员工姓名（冗余）';

COMMENT ON COLUMN ydsz_leave.leave_type IS '假期类型: ANNUAL 年假 / SICK 病假 / PERSONAL 事假 / MARRIAGE 婚假 / MATERNITY 产假 / BEREAVEMENT 丧假 / OTHER 其他';

COMMENT ON COLUMN ydsz_leave.start_date IS '请假开始日期';

COMMENT ON COLUMN ydsz_leave.end_date IS '请假结束日期';

COMMENT ON COLUMN ydsz_leave.leave_days IS '请假天数(天)';

COMMENT ON COLUMN ydsz_leave.reason IS '请假原因';

COMMENT ON COLUMN ydsz_leave.attachment_url IS '证明附件 URL: 病假条/结婚证等';

COMMENT ON COLUMN ydsz_leave.approval_id IS '审批流实例 ID';

COMMENT ON COLUMN ydsz_leave.approval_status IS '审批状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回 / CANCELLED 已取消';

COMMENT ON COLUMN ydsz_leave.approver_id IS '审批人 ID';

COMMENT ON COLUMN ydsz_leave.approver_name IS '审批人姓名（冗余）';

COMMENT ON COLUMN ydsz_leave.approval_time IS '审批时间';

COMMENT ON COLUMN ydsz_leave.approval_remark IS '审批意见';

COMMENT ON COLUMN ydsz_leave.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_leave.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN ydsz_leave.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pl_tenant_emp
    ON ydsz_leave(tenant_id, employee_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pl_tenant_date_range
    ON ydsz_leave(tenant_id, start_date, end_date)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pl_tenant_type
    ON ydsz_leave(tenant_id, leave_type)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pl_tenant_status
    ON ydsz_leave(tenant_id, approval_status)
    WHERE deleted = 0;

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
ALTER TABLE ydsz_dict_type ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_dict_type_tenant ON ydsz_dict_type(tenant_id);

-- 2. 字典项
ALTER TABLE ydsz_dict_item ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_dict_item_tenant ON ydsz_dict_item(tenant_id);

-- 4. 角色
ALTER TABLE ydsz_role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_role_tenant ON ydsz_role(tenant_id);

-- 5. 权限
ALTER TABLE ydsz_permission ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_permission_tenant ON ydsz_permission(tenant_id);

-- 6. 用户-角色关联
ALTER TABLE ydsz_user_role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_user_role_tenant ON ydsz_user_role(tenant_id);

-- 7. 角色-权限关联
ALTER TABLE ydsz_role_permission ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_role_permission_tenant ON ydsz_role_permission(tenant_id);

-- 8. 部门
ALTER TABLE ydsz_department ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_department_tenant ON ydsz_department(tenant_id);

-- 9. 岗位
ALTER TABLE ydsz_post ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_position_tenant ON ydsz_post(tenant_id);

-- 10. 职级
ALTER TABLE ydsz_rank ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_rank_tenant ON ydsz_rank(tenant_id);

-- 11. 职级费率
ALTER TABLE ydsz_rank_rate ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_rank_rate_tenant ON ydsz_rank_rate(tenant_id);

-- 12. 员工
ALTER TABLE ydsz_employee ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_employee_tenant ON ydsz_employee(tenant_id);

-- 13. 员工标签
ALTER TABLE ydsz_employee_tag ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_emp_tag_tenant ON ydsz_employee_tag(tenant_id);

-- 14. 用户账号
ALTER TABLE ydsz_user_account ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_user_account_tenant ON ydsz_user_account(tenant_id);

-- ============================================================
-- 二、关键查询路径复合索引（H2.4）
--   覆盖分页查询 WHERE tenant_id = ? AND deleted = 0 ORDER BY created_at DESC
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_dict_type_tenant_created
    ON ydsz_dict_type(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_dict_item_tenant_created
    ON ydsz_dict_item(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_role_tenant_created
    ON ydsz_role(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_department_tenant_created
    ON ydsz_department(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_position_tenant_created
    ON ydsz_post(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_rank_tenant_created
    ON ydsz_rank(tenant_id, sort_order) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_employee_tenant_created
    ON ydsz_employee(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_user_account_tenant_created
    ON ydsz_user_account(tenant_id, created_at DESC) WHERE deleted = 0;

-- ============================================================
-- 三、外键关联列反向查询索引（H2.3）
-- ============================================================

-- 角色-权限：按 permission_id 反向查询"该权限被哪些角色引用"
CREATE INDEX IF NOT EXISTS idx_role_permission_perm
    ON ydsz_role_permission(permission_id) WHERE deleted = 0;

-- 员工-岗位：按 position_id 查询"该岗位下的员工"
CREATE INDEX IF NOT EXISTS idx_ydsz_emp_position
    ON ydsz_employee(position_id) WHERE deleted = 0;

-- 用户账号-员工：按 employee_id 反向查询
CREATE INDEX IF NOT EXISTS idx_ydsz_user_employee
    ON ydsz_user_account(employee_id) WHERE deleted = 0;

-- 部门-负责人：按 leader_id 反向查询
CREATE INDEX IF NOT EXISTS idx_ydsz_dept_leader
    ON ydsz_department(leader_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_role_permission_deleted ON ydsz_role_permission(deleted);

CREATE INDEX IF NOT EXISTS idx_ydsz_emp_tag_deleted ON ydsz_employee_tag(deleted);

ANALYZE ydsz_dict_type;

ANALYZE ydsz_dict_item;

ANALYZE ydsz_role;

ANALYZE ydsz_permission;

ANALYZE ydsz_user_role;

ANALYZE ydsz_role_permission;

ANALYZE ydsz_department;

ANALYZE ydsz_post;

ANALYZE ydsz_rank;
ANALYZE ydsz_rank_rate;

ANALYZE ydsz_employee;

ANALYZE ydsz_employee_tag;

ANALYZE ydsz_user_account;

CREATE INDEX IF NOT EXISTS idx_ydsz_attendance_trace
    ON ydsz_attendance (provider_trace_id)
    WHERE provider_trace_id <> '';

CREATE INDEX IF NOT EXISTS idx_ydsz_overtime_trace
    ON ydsz_overtime (provider_trace_id)
    WHERE provider_trace_id <> '';

CREATE INDEX IF NOT EXISTS idx_ydsz_leave_trace
    ON ydsz_leave (provider_trace_id)
    WHERE provider_trace_id <> '';

CREATE INDEX IF NOT EXISTS idx_ydsz_employee_tag_trace
    ON ydsz_employee_tag (provider_trace_id)
    WHERE provider_trace_id <> '';

CREATE INDEX IF NOT EXISTS idx_ydsz_resource_assignment_trace
    ON ydsz_resource_assignment (provider_trace_id)
    WHERE provider_trace_id <> '';

CREATE INDEX IF NOT EXISTS idx_ydsz_bench_record_trace
    ON ydsz_bench_record (provider_trace_id)
    WHERE provider_trace_id <> '';

-- P1-10: 从 V1.0.0_project.sql 迁移（ResourcePoolMapper 在 userinfo 模块）
-- =====================================================
-- 1. 资源池主表 ydsz_resource_pool
-- =====================================================
-- P1-6: 已废弃（无需 DROP），标记保留以记录历史。DROP TABLE IF EXISTS ydsz_resource_pool; -- 已废弃
CREATE TABLE IF NOT EXISTS ydsz_resource_pool(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    pool_code           VARCHAR(64)  NOT NULL,
    pool_name           VARCHAR(256) NOT NULL,
    pool_type           VARCHAR(32)  NOT NULL,                 -- HQ/DIVISION/RESERVE
    department_id       VARCHAR(20),                                -- 事业部/部门
    department_name     VARCHAR(256),
    level_range         VARCHAR(32),                           -- L1-L3 / L4-L12 / L13+
    headcount           INTEGER      NOT NULL DEFAULT 0,
    billable_target     INTEGER      NOT NULL DEFAULT 0,
    description         TEXT,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_prp_code              UNIQUE (pool_code, deleted),
    CONSTRAINT ck_prp_pool_type         CHECK (pool_type IN ('HQ','DIVISION','RESERVE')),
    CONSTRAINT ck_prp_status_enum       CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_prp_headcount_nonneg  CHECK (headcount >= 0),
    CONSTRAINT ck_prp_bill_target_nonneg CHECK (billable_target >= 0),
    CONSTRAINT ck_prp_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  ydsz_resource_pool IS '资源池表: 3 级资源池（HQ 总部 / DIVISION 事业部 / RESERVE 储备）,PoolType.inferByLevel 按职级自动分配';

COMMENT ON COLUMN ydsz_resource_pool.pool_code IS '资源池编码: 业务唯一,如 POOL-HQ-GLOBAL';

COMMENT ON COLUMN ydsz_resource_pool.pool_name IS '资源池名称';

COMMENT ON COLUMN ydsz_resource_pool.pool_type IS '资源池类型: HQ 总部 / DIVISION 事业部 / RESERVE 储备,按职级自动映射';

COMMENT ON COLUMN ydsz_resource_pool.department_id IS '所属部门 ID: 池归属的事业部/部门';

COMMENT ON COLUMN ydsz_resource_pool.department_name IS '所属部门名称（冗余）';

COMMENT ON COLUMN ydsz_resource_pool.level_range IS '职级范围: L1-L3 / L4-L12 / L13+';

COMMENT ON COLUMN ydsz_resource_pool.headcount IS '当前人数';

COMMENT ON COLUMN ydsz_resource_pool.billable_target IS '计费人头目标: 期望投入计费项目的人数';

COMMENT ON COLUMN ydsz_resource_pool.description IS '资源池描述';

COMMENT ON COLUMN ydsz_resource_pool.status IS '状态: ACTIVE 启用 / INACTIVE 停用';

COMMENT ON COLUMN ydsz_resource_pool.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_resource_pool.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN ydsz_resource_pool.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_prp_type_status / idx_prp_dept)
CREATE INDEX IF NOT EXISTS idx_prp_tenant_type_status
    ON ydsz_resource_pool(tenant_id, pool_type, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_prp_tenant_dept
    ON ydsz_resource_pool(tenant_id, department_id)
    WHERE deleted = 0 AND department_id IS NOT NULL;

-- =====================================================
-- 5. 初始化三级资源池（HQ/DIVISION/RESERVE）
-- =====================================================
INSERT INTO ydsz_resource_pool
    (pool_code, pool_name, pool_type, department_id, department_name, level_range, headcount, billable_target, status, tenant_id, provider_trace_id)
VALUES
    ('POOL-HQ-GLOBAL',        '总部高级资源池',   'HQ',       1, '总部',  'L13+', 0, 0, 'ACTIVE', 1, 'init'),
    ('POOL-DIV-CONSULTING',   '咨询事业部池',    'DIVISION', 2, '咨询事业部', 'L4-L12', 0, 0, 'ACTIVE', 1, 'init'),
    ('POOL-DIV-IMPL',         '实施事业部池',    'DIVISION', 3, '实施事业部', 'L4-L12', 0, 0, 'ACTIVE', 1, 'init'),
        ('POOL-RESERVE-TRAINING', '储备培训池',      'RESERVE',  1, '总部',  'L1-L3', 0, 0, 'ACTIVE', 1, 'init') ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_ydsz_resource_pool_trace
    ON ydsz_resource_pool (provider_trace_id)
    WHERE provider_trace_id <> '';


-- ============================================================
-- 7. 菜单与权限（sdt-ids + sdt-mps 合并）
-- ============================================================

-- 菜单表（sdt-ids.Menu + sdt-mps.Menu 合并）
CREATE TABLE IF NOT EXISTS ydsz_menu(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    parent_id       VARCHAR(20)    NOT NULL DEFAULT '0',
    menu_name       VARCHAR(128)   NOT NULL,
    menu_code       VARCHAR(64)    NOT NULL,
    menu_type       VARCHAR(16)    NOT NULL DEFAULT 'MENU',
    path            VARCHAR(256),
    component       VARCHAR(256),
    icon             VARCHAR(64),
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    permission_code VARCHAR(128),
    visible         SMALLINT       NOT NULL DEFAULT 1,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_menu_code UNIQUE (menu_code, deleted),
    CONSTRAINT ck_menu_type_enum  CHECK (menu_type IN ('MENU', 'BUTTON', 'API')),
    CONSTRAINT ck_menu_status_enum CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_menu_visible_enum CHECK (visible IN (0, 1)),
    CONSTRAINT ck_menu_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_menu IS '菜单表: 菜单/按钮/API 三级权限(sdt-ids + sdt-mps 合并),对接 common-auth @AuthMenuPermission';
COMMENT ON COLUMN ydsz_menu.id IS '主键 ID';
COMMENT ON COLUMN ydsz_menu.parent_id IS '父菜单 ID(根菜单为 0)';
COMMENT ON COLUMN ydsz_menu.menu_name IS '菜单名称';
COMMENT ON COLUMN ydsz_menu.menu_code IS '菜单编码(全局唯一)';
COMMENT ON COLUMN ydsz_menu.menu_type IS '菜单类型: MENU 目录/菜单 / BUTTON 按钮 / API 接口';
COMMENT ON COLUMN ydsz_menu.path IS '前端路由路径';
COMMENT ON COLUMN ydsz_menu.component IS '前端组件路径';
COMMENT ON COLUMN ydsz_menu.icon IS '菜单图标';
COMMENT ON COLUMN ydsz_menu.sort_order IS '排序号';
COMMENT ON COLUMN ydsz_menu.permission_code IS '权限码(如 system:user:add)';
COMMENT ON COLUMN ydsz_menu.visible IS '是否可见: 1 可见 / 0 隐藏';
COMMENT ON COLUMN ydsz_menu.status IS '启用状态: ENABLED / DISABLED';
COMMENT ON COLUMN ydsz_menu.deleted IS '逻辑删除: 0 未删除 / 1 已删除';
COMMENT ON COLUMN ydsz_menu.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_ydsz_menu_parent ON ydsz_menu(parent_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ydsz_menu_tenant ON ydsz_menu(tenant_id) WHERE deleted = 0;

-- ============================================================
-- 8. 公司与组织架构（sdt-ids.Company/CompanyDept/UserDept/UserPost/UserField 迁移）
-- ============================================================

-- 公司表
CREATE TABLE IF NOT EXISTS ydsz_company(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    company_name    VARCHAR(128)   NOT NULL,
    company_code    VARCHAR(64)    NOT NULL,
    parent_id       VARCHAR(20)    NOT NULL DEFAULT '0',
    contact_person  VARCHAR(64),
    contact_phone   VARCHAR(32),
    address         VARCHAR(512),
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_company_code UNIQUE (company_code, deleted),
    CONSTRAINT ck_company_status_enum CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_company_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_company IS '公司表: 公司体系映射为租户上下文(TenantContext)';
COMMENT ON COLUMN ydsz_company.id IS '主键 ID';
COMMENT ON COLUMN ydsz_company.company_name IS '公司名称';
COMMENT ON COLUMN ydsz_company.company_code IS '公司编码(全局唯一)';
COMMENT ON COLUMN ydsz_company.parent_id IS '父公司 ID(根公司为 0)';
COMMENT ON COLUMN ydsz_company.contact_person IS '联系人';
COMMENT ON COLUMN ydsz_company.contact_phone IS '联系电话';
COMMENT ON COLUMN ydsz_company.address IS '公司地址';
COMMENT ON COLUMN ydsz_company.status IS '启用状态';
COMMENT ON COLUMN ydsz_company.deleted IS '逻辑删除';
COMMENT ON COLUMN ydsz_company.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_ydsz_company_parent ON ydsz_company(parent_id) WHERE deleted = 0;

-- 公司-部门关联表
CREATE TABLE IF NOT EXISTS ydsz_company_dept(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    company_id      VARCHAR(20)    NOT NULL,
    dept_id         VARCHAR(20)    NOT NULL,
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_company_dept UNIQUE (company_id, dept_id, deleted),
    CONSTRAINT ck_cd_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_company_dept IS '公司-部门关联表: 公司与部门的多对多关联';
COMMENT ON COLUMN ydsz_company_dept.id IS '主键 ID';
COMMENT ON COLUMN ydsz_company_dept.company_id IS '公司 ID';
COMMENT ON COLUMN ydsz_company_dept.dept_id IS '部门 ID';
COMMENT ON COLUMN ydsz_company_dept.deleted IS '逻辑删除';
COMMENT ON COLUMN ydsz_company_dept.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_ydsz_cd_company ON ydsz_company_dept(company_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ydsz_cd_dept ON ydsz_company_dept(dept_id) WHERE deleted = 0;

-- 用户-部门关联表
CREATE TABLE IF NOT EXISTS ydsz_user_dept(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    user_id         VARCHAR(20)    NOT NULL,
    dept_id         VARCHAR(20)    NOT NULL,
    is_primary      SMALLINT       NOT NULL DEFAULT 0,
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_user_dept UNIQUE (user_id, dept_id, deleted),
    CONSTRAINT ck_ud_is_primary CHECK (is_primary IN (0, 1)),
    CONSTRAINT ck_ud_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_user_dept IS '用户-部门关联表: 用户可归属多个部门,is_primary=1 为主部门';
COMMENT ON COLUMN ydsz_user_dept.user_id IS '用户 ID';
COMMENT ON COLUMN ydsz_user_dept.dept_id IS '部门 ID';
COMMENT ON COLUMN ydsz_user_dept.is_primary IS '是否主部门: 1 是 / 0 否';
COMMENT ON COLUMN ydsz_user_dept.deleted IS '逻辑删除';
COMMENT ON COLUMN ydsz_user_dept.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_ydsz_ud_user ON ydsz_user_dept(user_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ydsz_ud_dept ON ydsz_user_dept(dept_id) WHERE deleted = 0;

-- 用户-岗位关联表
CREATE TABLE IF NOT EXISTS ydsz_user_post(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    user_id         VARCHAR(20)    NOT NULL,
    post_id         VARCHAR(20)    NOT NULL,
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_user_post UNIQUE (user_id, post_id, deleted),
    CONSTRAINT ck_up_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_user_post IS '用户-岗位关联表: 用户可担任多个岗位';
COMMENT ON COLUMN ydsz_user_post.user_id IS '用户 ID';
COMMENT ON COLUMN ydsz_user_post.post_id IS '岗位 ID(ydsz_post)';
COMMENT ON COLUMN ydsz_user_post.deleted IS '逻辑删除';
COMMENT ON COLUMN ydsz_user_post.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_ydsz_up_user ON ydsz_user_post(user_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ydsz_up_post ON ydsz_user_post(post_id) WHERE deleted = 0;

-- 用户自定义字段表
CREATE TABLE IF NOT EXISTS ydsz_user_field(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    user_id         VARCHAR(20)    NOT NULL,
    field_key       VARCHAR(64)    NOT NULL,
    field_value     TEXT,
    field_type      VARCHAR(16)    NOT NULL DEFAULT 'STRING',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_user_field UNIQUE (user_id, field_key, deleted),
    CONSTRAINT ck_uf_field_type CHECK (field_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON', 'DATE')),
    CONSTRAINT ck_uf_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_user_field IS '用户自定义字段表: 用户扩展属性(来自 sdt-ids.UserField)';
COMMENT ON COLUMN ydsz_user_field.user_id IS '用户 ID';
COMMENT ON COLUMN ydsz_user_field.field_key IS '字段键名';
COMMENT ON COLUMN ydsz_user_field.field_value IS '字段值';
COMMENT ON COLUMN ydsz_user_field.field_type IS '值类型: STRING/NUMBER/BOOLEAN/JSON/DATE';
COMMENT ON COLUMN ydsz_user_field.deleted IS '逻辑删除';
COMMENT ON COLUMN ydsz_user_field.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_ydsz_uf_user ON ydsz_user_field(user_id) WHERE deleted = 0;

-- ============================================================
-- 9. 国际化（sdt-ids.Language 迁移）
-- ============================================================

-- 语言偏好表
CREATE TABLE IF NOT EXISTS ydsz_language(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    language_code   VARCHAR(16)    NOT NULL,
    language_name   VARCHAR(64)    NOT NULL,
    is_default      SMALLINT       NOT NULL DEFAULT 0,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_lang_code UNIQUE (language_code, deleted),
    CONSTRAINT ck_lang_status_enum CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_lang_default_enum CHECK (is_default IN (0, 1)),
    CONSTRAINT ck_lang_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_language IS '语言偏好表: 支持的语言列表(对接 common-base i18n)';
COMMENT ON COLUMN ydsz_language.language_code IS '语言编码(如 ZH/EN)';
COMMENT ON COLUMN ydsz_language.language_name IS '语言名称';
COMMENT ON COLUMN ydsz_language.is_default IS '是否默认语言: 1 是 / 0 否';
COMMENT ON COLUMN ydsz_language.sort_order IS '排序号';
COMMENT ON COLUMN ydsz_language.status IS '启用状态';
COMMENT ON COLUMN ydsz_language.deleted IS '逻辑删除';
COMMENT ON COLUMN ydsz_language.tenant_id IS '租户 ID';
