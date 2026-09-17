-- ============================================================
-- 集成测试用：系统配置表 PostgreSQL 16 DDL
-- 来源：sqls/legacy-dialects/mysql/ydsz-system.sql 转换为 PG 语法
-- 使用方式：@Sql(scripts = "/schema-config.sql") 在 IT 方法/类上触发
-- ============================================================

DROP TABLE IF EXISTS ydsz_sys_config CASCADE;

CREATE TABLE ydsz_sys_config (
    id              VARCHAR(32)     PRIMARY KEY,
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0',
    config_group    VARCHAR(64)     NOT NULL,
    config_key      VARCHAR(128)    NOT NULL,
    config_value    TEXT,
    value_type      VARCHAR(32)     NOT NULL DEFAULT 'STRING',
    default_value   TEXT,
    description     VARCHAR(512),
    is_public       SMALLINT        NOT NULL DEFAULT 0,
    "sort"          INTEGER         NOT NULL DEFAULT 0,
    status          VARCHAR(32),
    is_deleted      SMALLINT        NOT NULL DEFAULT 0,
    revision        INTEGER         NOT NULL DEFAULT 0,
    created_by      VARCHAR(64),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tenant_group_key UNIQUE (tenant_id, config_group, config_key)
);

CREATE INDEX IF NOT EXISTS idx_config_tenant_deleted ON ydsz_sys_config (tenant_id, is_deleted);
