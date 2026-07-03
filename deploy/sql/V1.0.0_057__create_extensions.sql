-- ============================================================
-- V1.0.0_057__create_extensions.sql
-- 创建 PostgreSQL 扩展（H6.2 修复）
--
-- 问题：原 deploy/sql/README.md:142 仅文档化要求手动执行
--   psql -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
--   未纳入 Flyway 管理，新环境部署易遗漏，导致查询 pg_stat_statements
--   视图报"relation does not exist"。
--
-- 注意：
--   - pg_stat_statements / pg_hint_plan 需在 postgresql.conf 的
--     shared_preload_libraries 中预加载后才可创建扩展
--   - uuid-ossp / pgcrypto 无需 preload
--   - 此脚本在已创建扩展的环境中执行会返回 NOTICE 而非 ERROR（IF NOT EXISTS）
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
CREATE EXTENSION IF NOT EXISTS pg_hint_plan;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
