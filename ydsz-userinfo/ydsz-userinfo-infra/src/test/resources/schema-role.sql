-- ============================================================
-- 集成测试用：角色表 PostgreSQL 16 DDL
-- 来源：sqls/legacy-dialects/mysql/ydsz-userinfo.sql 转换为 PG 语法
-- 使用方式：@Sql(scripts = "/schema-role.sql") 在 IT 方法/类上触发
-- ============================================================

DROP TABLE IF EXISTS ydsz_rbac_role CASCADE;

CREATE TABLE ydsz_rbac_role (
    id              VARCHAR(32)     PRIMARY KEY,
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0',
    role_code       VARCHAR(64)     NOT NULL,
    role_name       VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    sort INTEGER    NOT NULL DEFAULT 0,
    is_built_in     SMALLINT        NOT NULL DEFAULT 0,
    data_scope      VARCHAR(32),
    status          VARCHAR(32)     NOT NULL DEFAULT 'ENABLED',
    is_deleted      SMALLINT        NOT NULL DEFAULT 0,
    revision        INTEGER         NOT NULL DEFAULT 0,
    created_by      VARCHAR(64),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tenant_role_code UNIQUE (tenant_id, role_code)
);

CREATE INDEX IF NOT EXISTS idx_role_tenant_deleted ON ydsz_rbac_role (tenant_id, is_deleted);
