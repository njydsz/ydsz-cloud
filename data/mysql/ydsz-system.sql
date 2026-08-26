-- ----------------------------------------------------------------------------
-- 模块名   : ydsz-system（系统管理模块）
-- 说明     : 基于 ydsz-system-infra 实体类整理的完整建表脚本
--            （租户/套餐、字典、配置、应用、变量、实体版本）
-- 日期     : 2026-08-25
-- @author  : ydsz-team
-- ----------------------------------------------------------------------------

-- ============================================================================
-- 租户 / 套餐
-- ============================================================================

-- 租户主表（SaaS 多租户核心元数据）
CREATE TABLE IF NOT EXISTS ydsz_sys_tenant (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    tenant_code     VARCHAR(64)     NOT NULL COMMENT '租户编码（唯一业务标识，租户登录/调用使用）',
    tenant_name     VARCHAR(128)    NOT NULL COMMENT '租户名称（展示用）',
    contact_name    VARCHAR(64)     DEFAULT NULL COMMENT '联系人姓名',
    contact_phone   VARCHAR(32)     DEFAULT NULL COMMENT '联系电话（脱敏返回）',
    contact_email   VARCHAR(128)    DEFAULT NULL COMMENT '联系邮箱（脱敏返回）',
    plan_id         VARCHAR(32)     DEFAULT NULL COMMENT '关联套餐 ID（ydsz_sys_tenant_plan.id）',
    expire_at       DATETIME        DEFAULT NULL COMMENT '订阅到期时间（到期后租户被自动锁定/降级）',
    datasource_key  VARCHAR(64)     DEFAULT NULL COMMENT '独立数据源标识（ISOLATE_DB 模式下使用）',
    remark          VARCHAR(512)    DEFAULT NULL COMMENT '备注',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识（ENABLED/DISABLED，启用状态值）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_tenant_code UNIQUE (tenant_code),
    INDEX idx_plan_id (plan_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户主表';

-- 租户套餐表（套餐/订阅计划：功能菜单 + 资源配额 + 计费规则）
CREATE TABLE IF NOT EXISTS ydsz_sys_tenant_plan (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    plan_code       VARCHAR(64)     NOT NULL COMMENT '套餐编码（唯一标识，如 TRIAL/STANDARD/ENTERPRISE）',
    plan_name       VARCHAR(128)    NOT NULL COMMENT '套餐名称（展示用，如「试用版」「企业版」）',
    description     TEXT            DEFAULT NULL COMMENT '套餐描述（包含价格、功能清单、配额上限）',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序号（升序，影响前端套餐选择器顺序）',
    quota_json      JSON            DEFAULT NULL COMMENT '资源配额 JSON（如 {"maxUsers":50,"maxProjects":10,"storageGb":100}）',
    feature_json    JSON            DEFAULT NULL COMMENT '功能开关 JSON（如 {"workflow":true,"dataAnalytics":false}）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_plan_code UNIQUE (plan_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户套餐表';

-- 租户套餐菜单关联表（套餐与菜单权限多对多关联）
CREATE TABLE IF NOT EXISTS ydsz_sys_tenant_plan_menu (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    plan_id         VARCHAR(32)     NOT NULL COMMENT '套餐 ID（ydsz_sys_tenant_plan.id）',
    menu_id         VARCHAR(64)     NOT NULL COMMENT '菜单 ID（ydsz_menu.id 或权限码 ydsz:xxx）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_plan_menu UNIQUE (plan_id, menu_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户套餐菜单关联表';

-- ============================================================================
-- 数据字典
-- ============================================================================

-- 字典类型表（数据字典分类信息）
CREATE TABLE IF NOT EXISTS ydsz_sys_dict_type (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    type_code       VARCHAR(64)     NOT NULL COMMENT '类型编码（唯一标识，用于业务引用）',
    type_name       VARCHAR(128)    NOT NULL COMMENT '类型名称（展示用）',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '类型描述',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_type_code UNIQUE (type_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

-- 字典项表（字典类型的具体枚举值）
CREATE TABLE IF NOT EXISTS ydsz_sys_dict_item (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    type_code       VARCHAR(64)     NOT NULL COMMENT '所属字典类型编码（逻辑外键 → ydsz_sys_dict_type.type_code）',
    item_code       VARCHAR(64)     NOT NULL COMMENT '字典项编码（同 typeCode 内唯一）',
    item_value      VARCHAR(128)    NOT NULL COMMENT '字典项真实值（业务代码引用的枚举值，如 "PAID"）',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '展示排序序号（升序）',
    parent_id       VARCHAR(32)     DEFAULT NULL COMMENT '父级字典项 ID（支持树形字典，如行政区划）',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '字典项描述',
    ext_json        JSON            DEFAULT NULL COMMENT '扩展属性 JSON（承载自定义属性，如色值、图标、URL 等）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_type_item_code UNIQUE (type_code, item_code),
    INDEX idx_parent_id (parent_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项表';

-- ============================================================================
-- 系统配置 / 系统变量
-- ============================================================================

-- 系统配置表（系统级配置项，面向后端）
CREATE TABLE IF NOT EXISTS ydsz_sys_config (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    config_group    VARCHAR(64)     NOT NULL COMMENT '配置分组（按业务域分类管理配置）',
    config_key      VARCHAR(128)    NOT NULL COMMENT '配置键（同组内唯一标识）',
    config_value    TEXT            DEFAULT NULL COMMENT '配置值',
    value_type      VARCHAR(32)     NOT NULL COMMENT '值类型（STRING/NUMBER/BOOLEAN/JSON）',
    default_value   TEXT            DEFAULT NULL COMMENT '默认值（配置未设置时使用）',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '配置描述',
    is_public       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否公开配置（1=公开，前端可查；0=私有，仅后端可查）',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序序号',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_config_group_key UNIQUE (config_group, config_key),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 系统变量表（系统级动态变量，面向业务侧，按 key 高频查询）
CREATE TABLE IF NOT EXISTS ydsz_sys_variable (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    variable_key    VARCHAR(128)    NOT NULL COMMENT '变量键（唯一标识，全局唯一）',
    variable_value  TEXT            DEFAULT NULL COMMENT '变量值（按 valueType 反序列化为 String/Number/Boolean/JSON）',
    value_type      VARCHAR(32)     NOT NULL COMMENT '值类型（STRING/NUMBER/BOOLEAN/JSON）',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '变量描述（业务含义说明）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识（ENABLED/DISABLED）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_variable_key UNIQUE (variable_key),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统变量表';

-- ============================================================================
-- 应用信息 / 实体版本
-- ============================================================================

-- 应用信息表（OAuth2 客户端应用注册信息）
CREATE TABLE IF NOT EXISTS ydsz_sys_app_info (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    app_code        VARCHAR(64)     NOT NULL COMMENT '应用业务编码（对外展示/业务标识，全局唯一）',
    app_name        VARCHAR(128)    NOT NULL COMMENT '应用名称',
    app_key         VARCHAR(64)     NOT NULL COMMENT '应用唯一标识（认证查询入口，语义等价 OAuth2 client_id，租户内唯一）',
    app_secret      VARCHAR(128)    NOT NULL COMMENT '应用安全密钥（BCrypt 哈希存储，语义等价 OAuth2 client_secret）',
    redirect_url    VARCHAR(1024)   DEFAULT NULL COMMENT 'OAuth2 授权回调地址',
    scopes          VARCHAR(512)    DEFAULT NULL COMMENT 'OAuth2 授权范围（CSV 格式，如 "user.read,order.write"）',
    bound_ips       VARCHAR(512)    DEFAULT NULL COMMENT 'IP 绑定白名单（CSV 格式；为空表示不限制 IP）',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '应用描述',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_app_code UNIQUE (app_code),
    CONSTRAINT uk_tenant_app_key UNIQUE (tenant_id, app_key),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用信息表';

-- 统一实体版本表（Config/Dict/Variable 变更历史快照）
CREATE TABLE IF NOT EXISTS ydsz_sys_entity_version (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    resource_type   VARCHAR(32)     NOT NULL COMMENT '资源类型（CONFIG/DICT/VARIABLE）',
    resource_key    VARCHAR(128)    NOT NULL COMMENT '资源唯一标识（configKey/typeCode/variableKey）',
    resource_group  VARCHAR(64)     DEFAULT NULL COMMENT '资源分组（仅 CONFIG 类型使用，其他为 null）',
    version         VARCHAR(32)     NOT NULL COMMENT '版本号字符串',
    change_log      VARCHAR(512)    DEFAULT NULL COMMENT '变更说明',
    snapshot_json   JSON            DEFAULT NULL COMMENT '变更前 JSON 快照（用于回滚）',
    effective_date  DATETIME        DEFAULT NULL COMMENT '生效时间',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_resource_type_key_version (resource_type, resource_key, version),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一实体版本表';
