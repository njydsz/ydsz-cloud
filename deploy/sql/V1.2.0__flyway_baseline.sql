-- ============================================================
-- PMIS 数据库版本管理 — Flyway Schema History 表
-- 
-- 说明:
--   本表由 Flyway 自动创建和管理,此处提供手动初始化脚本
--   适用于首次接入 Flyway 或手动维护场景。
--
-- 版本命名规范:
--   V{major}.{minor}.{patch}__{description}.sql
--   例: V1.0.0__init_schema.sql
--       V1.1.0__add_dag_tables.sql
--       V1.1.1__fix_index_name.sql
--
-- 注意:
--   1. 已执行过的迁移文件不可修改（Flyway checksum 校验）
--   2. 需要修改历史变更时,新建一个 V{version}__{fix_description}.sql
--   3. 回滚脚本放在 R__{description}.sql 中（Flyway Repeatable）
--   4. 所有 DDL 必须幂等（IF NOT EXISTS / IF EXISTS）
-- ============================================================

-- Flyway 元数据表（与 Flyway 10.x 默认结构一致）
CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank    INTEGER       NOT NULL,
    version           VARCHAR(50),
    description       VARCHAR(200)  NOT NULL,
    type              VARCHAR(20)   NOT NULL,
    script            VARCHAR(1000) NOT NULL,
    checksum          INTEGER,
    installed_by      VARCHAR(100)  NOT NULL,
    installed_on      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time    INTEGER       NOT NULL,
    success           BOOLEAN       NOT NULL,
    CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)
);

-- 为 success 字段创建索引（Flyway 查询用）
CREATE INDEX IF NOT EXISTS flyway_schema_history_s_idx
    ON flyway_schema_history (success);

-- ============================================================
-- 基线设定: 将 V1.0.0 ~ V1.1.0 的已有脚本标记为已执行
-- 这样 Flyway 不会重复执行这些脚本
-- ============================================================
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
SELECT
    ROW_NUMBER() OVER (ORDER BY filename) AS installed_rank,
    -- 从文件名解析版本号: V1.0.0_system.sql → 1.0.0
    SUBSTRING(filename FROM 'V([0-9]+\.[0-9]+\.[0-9]+)') AS version,
    -- 从文件名解析描述: V1.0.0_system.sql → system
    SUBSTRING(filename FROM 'V[0-9]+\.[0-9]+\.[0-9]+_?([a-z_]+)\.sql') AS description,
    'SQL' AS type,
    filename AS script,
    NULL AS checksum,
    'baseline' AS installed_by,
    0 AS execution_time,
    TRUE AS success
FROM (
    VALUES
        ('V1.0.0_agent.sql'),
        ('V1.0.0_cronjob.sql'),
        ('V1.0.0_literule.sql'),
        ('V1.0.0_message.sql'),
        ('V1.0.0_project.sql'),
        ('V1.0.0_system.sql'),
        ('V1.0.0_userinfo.sql'),
        ('V1.0.0_workflow.sql'),
        ('V1.1.0_refactor_deprecated_tables.sql'),
        ('V1.1.0_rule_table_migration.sql'),
        ('V1.1.0_unified_dag.sql')
) AS t(filename)
WHERE NOT EXISTS (
    SELECT 1 FROM flyway_schema_history fsh
    WHERE fsh.script = filename
);

-- ============================================================
-- 变更审计日志表（DBA 操作记录,与 Flyway 互补）
-- ============================================================
CREATE TABLE IF NOT EXISTS pmis_db_changelog (
    id                BIGSERIAL         PRIMARY KEY,
    version           VARCHAR(50)       NOT NULL,
    description       VARCHAR(500)      NOT NULL,
    script_filename   VARCHAR(1000)     NOT NULL,
    applied_by        VARCHAR(100)      NOT NULL,
    applied_at        TIMESTAMPTZ       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_ms      INTEGER,
    success           BOOLEAN           NOT NULL DEFAULT TRUE,
    rollback_script   VARCHAR(1000),
    notes             TEXT,
    CONSTRAINT uq_db_changelog_version_script UNIQUE (version, script_filename)
);

COMMENT ON TABLE pmis_db_changelog IS 'PMIS 数据库变更审计日志';
COMMENT ON COLUMN pmis_db_changelog.version IS '版本号,如 1.0.0';
COMMENT ON COLUMN pmis_db_changelog.description IS '变更描述';
COMMENT ON COLUMN pmis_db_changelog.script_filename IS 'SQL 脚本文件名';
COMMENT ON COLUMN pmis_db_changelog.applied_by IS '执行人(数据库用户)';
COMMENT ON COLUMN pmis_db_changelog.applied_at IS '执行时间';
COMMENT ON COLUMN pmis_db_changelog.execution_ms IS '执行耗时(毫秒)';
COMMENT ON COLUMN pmis_db_changelog.success IS '是否成功';
COMMENT ON COLUMN pmis_db_changelog.rollback_script IS '回滚脚本文件名';
COMMENT ON COLUMN pmis_db_changelog.notes IS '备注';
