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
--   - pg_hint_plan / pg_stat_statements 在某些环境（如 PG18 或未配置 preload）
--     不可用，使用 DO 块容错，避免阻断主流程
-- ============================================================

-- pg_stat_statements: 需 shared_preload_libraries 预加载, 未加载时跳过
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_stat_statements 不可用, 跳过: %', SQLERRM;
END $$;

-- pg_hint_plan: 仅 PG 12-16 可用, PG18 不支持, 跳过
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_hint_plan;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_hint_plan 不可用, 跳过: %', SQLERRM;
END $$;

-- uuid-ossp: 无需 preload, 标准扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- pgcrypto: 无需 preload, 标准扩展
CREATE EXTENSION IF NOT EXISTS pgcrypto;
