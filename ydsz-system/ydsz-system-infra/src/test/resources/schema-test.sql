-- =====================================================================
--  集成测试专用 Schema（最小化 DDL，仅包含字典相关表）
-- =====================================================================
-- 对应生产 DDL 位置：data/sqls/postgre/ydsz-system.sql
-- 手动执行：由 DictRepositoryIT 通过 @Sql 注解加载，或TestBase.executeSqlScript() 触发

-- 字典类型表
CREATE TABLE IF NOT EXISTS ydsz_sys_dict_type (
    id                       VARCHAR(36)              NOT NULL,
    type_code                VARCHAR(64)              NOT NULL,
    type_name                VARCHAR(128)             NOT NULL,
    description              VARCHAR(512),
    status                   VARCHAR(32),
    sort                     INTEGER                  NOT NULL DEFAULT 0,
    revision                 INTEGER                  NOT NULL DEFAULT 0,
    tenant_id                VARCHAR(36)              NOT NULL DEFAULT '0',
    is_deleted               SMALLINT                 NOT NULL DEFAULT 0,
    created_by               VARCHAR(36),
    created_at               TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               VARCHAR(36),
    updated_at               TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ydsz_sys_dict_type PRIMARY KEY (id),
    CONSTRAINT uk_ydsz_sys_dict_type_type_code UNIQUE (type_code)
);

CREATE INDEX IF NOT EXISTS idx_ydsz_sys_dict_type_tenant_deleted ON ydsz_sys_dict_type (tenant_id, is_deleted);

-- 字典项表
CREATE TABLE IF NOT EXISTS ydsz_sys_dict_item (
    id                       VARCHAR(36)              NOT NULL,
    type_code                VARCHAR(64)              NOT NULL,
    item_code                VARCHAR(64)              NOT NULL,
    item_value               VARCHAR(128)             NOT NULL,
    parent_id                VARCHAR(36),
    description              VARCHAR(512),
    ext_json                 JSONB,
    status                   VARCHAR(32),
    sort                     INTEGER                  NOT NULL DEFAULT 0,
    revision                 INTEGER                  NOT NULL DEFAULT 0,
    tenant_id                VARCHAR(36)              NOT NULL DEFAULT '0',
    is_deleted               SMALLINT                 NOT NULL DEFAULT 0,
    created_by               VARCHAR(36),
    created_at               TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               VARCHAR(36),
    updated_at               TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ydsz_sys_dict_item PRIMARY KEY (id),
    CONSTRAINT uk_ydsz_sys_dict_item_type_item_code UNIQUE (type_code, item_code)
);

CREATE INDEX IF NOT EXISTS idx_ydsz_sys_dict_item_tenant_deleted ON ydsz_sys_dict_item (tenant_id, is_deleted);
