-- ============================================================
-- PMIS Full Database Initialization Script
-- Executes all module scripts in dependency order
-- ============================================================

\i V1.0.0_system.sql
\i V1.0.0_userinfo.sql
\i V1.0.0_project.sql
\i V1.0.0_cronjob.sql
\i V1.0.0_message.sql
\i V1.0.0_workflow.sql
\i V1.0.0_agent.sql
\i V1.0.0_literule.sql

-- V1.1.0 架构优化：废弃重复表清理与数据迁移
\i V1.1.0_refactor_deprecated_tables.sql

-- V1.1.0 架构优化：统一 DAG 引擎表
\i V1.1.0_unified_dag.sql

-- V1.1.0 架构优化：规则表归口迁移 (project → literule)
\i V1.1.0_rule_table_migration.sql
