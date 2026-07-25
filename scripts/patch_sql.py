#!/usr/bin/env python3
"""
Patch SQL DDL files:
1. Rename ydsz_position -> ydsz_post in V1.0.0_userinfo.sql
2. Append new tables to V1.0.0_userinfo.sql (menu, company, company_dept, user_dept, user_post, user_field, language)
3. Append new tables to V1.0.0_system.sql (app_info, variable)
"""
import re

SQL_DIR = r'd:\Code\ydsz\ydsz-pmis\deploy\sql\modules'

# ---- 1. Rename ydsz_position -> ydsz_post in userinfo SQL ----
userinfo_path = f'{SQL_DIR}/V1.0.0_userinfo.sql'
with open(userinfo_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace ydsz_position with ydsz_post (table name and all references)
content = content.replace('ydsz_position', 'ydsz_post')
print('Renamed ydsz_position -> ydsz_post in V1.0.0_userinfo.sql')

# ---- 2. Append new userinfo tables ----
NEW_USERINFO_TABLES = r'''

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
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
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
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
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
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
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
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
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
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
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
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
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
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
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
'''

content += NEW_USERINFO_TABLES
with open(userinfo_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Appended 7 new tables to V1.0.0_userinfo.sql')

# ---- 3. Append new system tables ----
system_path = f'{SQL_DIR}/V1.0.0_system.sql'
with open(system_path, 'r', encoding='utf-8') as f:
    sys_content = f.read()

NEW_SYSTEM_TABLES = r'''

-- ============================================================
-- 10. 应用注册（sdt-app.AppInfo 迁移，OAuth2 client_id 校验）
-- ============================================================

-- 应用注册表
CREATE TABLE IF NOT EXISTS ydsz_app_info(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    app_code        VARCHAR(64)    NOT NULL,
    app_name        VARCHAR(128)   NOT NULL,
    app_key         VARCHAR(128)   NOT NULL,
    app_secret      VARCHAR(256)   NOT NULL,
    redirect_url    VARCHAR(512),
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_app_code UNIQUE (app_code, deleted),
    CONSTRAINT uk_ydsz_app_key UNIQUE (app_key, deleted),
    CONSTRAINT ck_app_status_enum CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_app_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_app_info IS '应用注册表: OAuth2 client_id/client_secret 校验数据源(来自 sdt-app.AppInfo)';
COMMENT ON COLUMN ydsz_app_info.id IS '主键 ID';
COMMENT ON COLUMN ydsz_app_info.app_code IS '应用编码(全局唯一)';
COMMENT ON COLUMN ydsz_app_info.app_name IS '应用名称';
COMMENT ON COLUMN ydsz_app_info.app_key IS '应用 Key(client_id)';
COMMENT ON COLUMN ydsz_app_info.app_secret IS '应用密钥(client_secret,BCrypt 加密存储)';
COMMENT ON COLUMN ydsz_app_info.redirect_url IS '授权回调地址';
COMMENT ON COLUMN ydsz_app_info.description IS '应用描述';
COMMENT ON COLUMN ydsz_app_info.status IS '启用状态: ENABLED / DISABLED';
COMMENT ON COLUMN ydsz_app_info.deleted IS '逻辑删除';
COMMENT ON COLUMN ydsz_app_info.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_ydsz_app_status ON ydsz_app_info(status) WHERE deleted = 0;

-- ============================================================
-- 11. 系统变量（sdt-ids.Variable 迁移，业务级变量管理）
-- ============================================================

-- 系统变量表
CREATE TABLE IF NOT EXISTS ydsz_variable(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    variable_key    VARCHAR(128)   NOT NULL,
    variable_value  TEXT,
    value_type      VARCHAR(16)    NOT NULL DEFAULT 'STRING',
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_var_key UNIQUE (variable_key, deleted),
    CONSTRAINT ck_var_value_type CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON')),
    CONSTRAINT ck_var_status_enum CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_var_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_variable IS '系统变量表: 业务级变量管理(来自 sdt-ids.Variable,可对接 common-config 热加载)';
COMMENT ON COLUMN ydsz_variable.id IS '主键 ID';
COMMENT ON COLUMN ydsz_variable.variable_key IS '变量键(全局唯一)';
COMMENT ON COLUMN ydsz_variable.variable_value IS '变量值';
COMMENT ON COLUMN ydsz_variable.value_type IS '值类型: STRING/NUMBER/BOOLEAN/JSON';
COMMENT ON COLUMN ydsz_variable.description IS '变量说明';
COMMENT ON COLUMN ydsz_variable.status IS '启用状态';
COMMENT ON COLUMN ydsz_variable.deleted IS '逻辑删除';
COMMENT ON COLUMN ydsz_variable.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_ydsz_var_status ON ydsz_variable(status) WHERE deleted = 0;
'''

sys_content += NEW_SYSTEM_TABLES
with open(system_path, 'w', encoding='utf-8') as f:
    f.write(sys_content)
print('Appended 2 new tables to V1.0.0_system.sql')
print('\nDone: SQL DDL patched successfully.')
