-- =====================================================================
-- ydsz-system 初始化脚本（PostgreSQL 15+）
-- 说明：本模块禁止使用 Flyway/Liquibase，DDL 统一以 SQL 脚本形式管理。
-- 执行：psql -U postgres -d ydsz_cloud -f ydsz-system/deploy/sql/init.sql
-- 版本：1.0.0
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 系统配置表 ydsz_config
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_config (
    id            VARCHAR(64)  NOT NULL PRIMARY KEY,
    config_group  VARCHAR(64)  NOT NULL,
    config_key    VARCHAR(128) NOT NULL,
    config_value  TEXT,
    value_type    VARCHAR(16)  NOT NULL DEFAULT 'STRING',
    default_value TEXT,
    description   VARCHAR(512),
    is_public     SMALLINT     NOT NULL DEFAULT 0,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    deleted       SMALLINT     NOT NULL DEFAULT 0,
    status        VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    revision      INTEGER      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 唯一索引：同分组下配置键唯一（配合代码层 checkDuplicateKey 兜底）
CREATE UNIQUE INDEX IF NOT EXISTS uk_config_group_key ON ydsz_config (tenant_id, config_group, config_key);
CREATE INDEX IF NOT EXISTS idx_config_tenant_status ON ydsz_config (tenant_id, status);

-- ---------------------------------------------------------------------
-- 2. 字典类型表 ydsz_dict_type
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_dict_type (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    type_code   VARCHAR(64)  NOT NULL,
    type_name   VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    tenant_id   VARCHAR(64)  NOT NULL DEFAULT 'default',
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    status      VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    revision    INTEGER      NOT NULL DEFAULT 0,
    created_by  VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 唯一索引：租户内 typeCode 唯一（业务主键，前端可见）
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_type_code ON ydsz_dict_type (tenant_id, type_code);

-- ---------------------------------------------------------------------
-- 3. 字典项表 ydsz_dict_item
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_dict_item (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    type_code   VARCHAR(64)  NOT NULL,
    item_code   VARCHAR(64)  NOT NULL,
    item_value  VARCHAR(255) NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    parent_id   VARCHAR(64),
    description VARCHAR(512),
    ext_json    TEXT,
    tenant_id   VARCHAR(64)  NOT NULL DEFAULT 'default',
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    status      VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    revision    INTEGER      NOT NULL DEFAULT 0,
    created_by  VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 唯一索引：(typeCode, itemCode) 租户内唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_type_item ON ydsz_dict_item (tenant_id, type_code, item_code);
-- 树形查询索引
CREATE INDEX IF NOT EXISTS idx_dict_item_parent ON ydsz_dict_item (parent_id);
CREATE INDEX IF NOT EXISTS idx_dict_item_type_status ON ydsz_dict_item (tenant_id, type_code, status, sort_order);

-- ---------------------------------------------------------------------
-- 4. 实体版本表 ydsz_entity_version（Config/Dict/Variable 统一变更历史）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_entity_version (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    resource_type  VARCHAR(32)  NOT NULL,
    resource_key   VARCHAR(128) NOT NULL,
    resource_group VARCHAR(64),
    version        VARCHAR(64)  NOT NULL,
    change_log     VARCHAR(255),
    snapshot_json  TEXT,
    effective_date TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id      VARCHAR(64)  NOT NULL DEFAULT 'default',
    deleted        SMALLINT     NOT NULL DEFAULT 0,
    status         VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    revision       INTEGER      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 版本历史查询索引
CREATE INDEX IF NOT EXISTS idx_entity_version_type_key ON ydsz_entity_version (resource_type, resource_key, version);

-- ---------------------------------------------------------------------
-- 5. 应用注册表 ydsz_app_info（OAuth2 client 注册）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_app_info (
    id           VARCHAR(64)  NOT NULL PRIMARY KEY,
    app_code     VARCHAR(64)  NOT NULL,
    app_name     VARCHAR(128) NOT NULL,
    app_key      VARCHAR(128) NOT NULL,
    app_secret   VARCHAR(255),
    redirect_url VARCHAR(512),
    scopes       VARCHAR(1024),
    bound_ips    VARCHAR(1024),
    description  VARCHAR(512),
    tenant_id    VARCHAR(64)  NOT NULL DEFAULT 'default',
    deleted      SMALLINT     NOT NULL DEFAULT 0,
    status       VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    revision     INTEGER      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 唯一索引：租户内 appKey 唯一（校验入口）
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_app_key ON ydsz_app_info (tenant_id, app_key);

-- ---------------------------------------------------------------------
-- 6. 系统变量表 ydsz_variable
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_variable (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    variable_key   VARCHAR(128) NOT NULL,
    variable_value TEXT,
    value_type     VARCHAR(16)  NOT NULL DEFAULT 'STRING',
    description    VARCHAR(512),
    tenant_id      VARCHAR(64)  NOT NULL DEFAULT 'default',
    deleted        SMALLINT     NOT NULL DEFAULT 0,
    status         VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    revision       INTEGER      NOT NULL DEFAULT 0,
    created_by     VARCHAR(64),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 唯一索引：租户内 variableKey 唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_variable_key ON ydsz_variable (tenant_id, variable_key);

-- ---------------------------------------------------------------------
-- 7. 租户主表 ydsz_tenant
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_tenant (
    id            VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_code   VARCHAR(64)  NOT NULL,
    tenant_name   VARCHAR(128) NOT NULL,
    contact_name  VARCHAR(64),
    contact_phone VARCHAR(32),
    contact_email VARCHAR(128),
    plan_id       VARCHAR(64),
    expire_at     TIMESTAMP,
    datasource_key VARCHAR(64),
    remark        VARCHAR(512),
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    deleted       SMALLINT     NOT NULL DEFAULT 0,
    status        VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    revision      INTEGER      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 唯一索引：租户编码全局唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_code ON ydsz_tenant (tenant_code);
CREATE INDEX IF NOT EXISTS idx_tenant_plan ON ydsz_tenant (plan_id);
-- 到期自动锁定调度查询索引（TenantExpireScheduler）
CREATE INDEX IF NOT EXISTS idx_tenant_status_expire ON ydsz_tenant (status, expire_at);

-- ---------------------------------------------------------------------
-- 8. 租户套餐表 ydsz_tenant_plan
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_tenant_plan (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    plan_code   VARCHAR(64)  NOT NULL,
    plan_name   VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    quota_json  TEXT,
    feature_json TEXT,
    tenant_id   VARCHAR(64)  NOT NULL DEFAULT 'default',
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    status      VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    revision    INTEGER      NOT NULL DEFAULT 0,
    created_by  VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 唯一索引：套餐编码全局唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_plan_code ON ydsz_tenant_plan (plan_code);

-- ---------------------------------------------------------------------
-- 9. 套餐-菜单关联表 ydsz_tenant_plan_menu
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_tenant_plan_menu (
    id         VARCHAR(64) NOT NULL PRIMARY KEY,
    plan_id    VARCHAR(64) NOT NULL,
    menu_id    VARCHAR(64) NOT NULL,
    tenant_id  VARCHAR(64) NOT NULL DEFAULT 'default',
    deleted    SMALLINT    NOT NULL DEFAULT 0,
    status     VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    revision   INTEGER     NOT NULL DEFAULT 0,
    created_by VARCHAR(64),
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 唯一索引：(plan_id, menu_id) 关联唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_plan_menu ON ydsz_tenant_plan_menu (plan_id, menu_id);
CREATE INDEX IF NOT EXISTS idx_plan_menu_plan ON ydsz_tenant_plan_menu (plan_id);

-- =====================================================================
-- 初始数据
-- =====================================================================

-- 平台内置租户（禁止删除）
INSERT INTO ydsz_tenant (id, tenant_code, tenant_name, tenant_id, status, created_by)
VALUES ('1000000000000000001', 'DEFAULT', '平台默认租户', '1000000000000000001', 'ENABLED', 'system')
ON CONFLICT DO NOTHING;

INSERT INTO ydsz_tenant (id, tenant_code, tenant_name, tenant_id, status, created_by)
VALUES ('1000000000000000002', 'MASTER', '平台管理租户', '1000000000000000002', 'ENABLED', 'system')
ON CONFLICT DO NOTHING;

-- 默认套餐（TRIAL / STANDARD / ENTERPRISE）
INSERT INTO ydsz_tenant_plan (id, plan_code, plan_name, description, sort_order, quota_json, feature_json, tenant_id, created_by)
VALUES
    ('2000000000000000001', 'TRIAL', '试用版', '7 天试用，5 用户 / 3 项目 / 10GB 存储', 10,
     '{"maxUsers":5,"maxProjects":3,"storageGb":10}', '{"workflow":true,"dataAnalytics":false}', '1000000000000000001', 'system'),
    ('2000000000000000002', 'STANDARD', '标准版', '50 用户，不限项目 / 100GB 存储', 20,
     '{"maxUsers":50,"maxProjects":-1,"storageGb":100}', '{"workflow":true,"dataAnalytics":true}', '1000000000000000001', 'system'),
    ('2000000000000000003', 'ENTERPRISE', '企业版', '定制配额 + 独立数据库隔离', 30,
     '{"maxUsers":-1,"maxProjects":-1,"storageGb":-1}', '{"workflow":true,"dataAnalytics":true}', '1000000000000000001', 'system')
ON CONFLICT DO NOTHING;

-- 示例字典类型：用户状态
INSERT INTO ydsz_dict_type (id, type_code, type_name, description, tenant_id, created_by)
VALUES ('3000000000000000001', 'user_status', '用户状态', '在职 / 离职 / 休假等状态枚举', '1000000000000000001', 'system')
ON CONFLICT DO NOTHING;

INSERT INTO ydsz_dict_item (id, type_code, item_code, item_value, sort_order, tenant_id, created_by)
VALUES
    ('3000000000000000011', 'user_status', 'ACTIVE', '在职', 10, '1000000000000000001', 'system'),
    ('3000000000000000012', 'user_status', 'RESIGNED', '离职', 20, '1000000000000000001', 'system'),
    ('3000000000000000013', 'user_status', 'ON_LEAVE', '休假', 30, '1000000000000000001', 'system')
ON CONFLICT DO NOTHING;

-- 示例字典类型：订单状态
INSERT INTO ydsz_dict_type (id, type_code, type_name, description, tenant_id, created_by)
VALUES ('3000000000000000002', 'order_status', '订单状态', '订单全生命周期状态枚举', '1000000000000000001', 'system')
ON CONFLICT DO NOTHING;

INSERT INTO ydsz_dict_item (id, type_code, item_code, item_value, sort_order, tenant_id, created_by)
VALUES
    ('3000000000000000021', 'order_status', 'PENDING', '待支付', 10, '1000000000000000001', 'system'),
    ('3000000000000000022', 'order_status', 'PAID', '已支付', 20, '1000000000000000001', 'system'),
    ('3000000000000000023', 'order_status', 'SHIPPED', '已发货', 30, '1000000000000000001', 'system'),
    ('3000000000000000024', 'order_status', 'COMPLETED', '已完成', 40, '1000000000000000001', 'system'),
    ('3000000000000000025', 'order_status', 'CANCELLED', '已取消', 50, '1000000000000000001', 'system')
ON CONFLICT DO NOTHING;

-- 示例系统配置（工作流 SLA 默认时长）
INSERT INTO ydsz_config (id, config_group, config_key, config_value, value_type, description, is_public, sort_order, tenant_id, created_by)
VALUES
    ('4000000000000000001', 'workflow', 'ydsz.workflow.sla-default-hours', '24', 'NUMBER', '工作流 SLA 默认时长（小时）', 0, 10, '1000000000000000001', 'system'),
    ('4000000000000000002', 'feature', 'ydsz.feature.gray-release-enabled', 'false', 'BOOLEAN', '灰度发布功能开关', 1, 20, '1000000000000000001', 'system')
ON CONFLICT DO NOTHING;

-- 示例系统变量（当前会计年度）
INSERT INTO ydsz_variable (id, variable_key, variable_value, value_type, description, tenant_id, created_by)
VALUES ('5000000000000000001', 'finance.current_fiscal_year', '2026', 'NUMBER', '当前生效的会计年度', '1000000000000000001', 'system')
ON CONFLICT DO NOTHING;
