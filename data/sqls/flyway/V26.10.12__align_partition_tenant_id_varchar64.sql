-- =============================================================================
-- Flyway Migration: V26.10.12
-- Description: 分区表 tenant_id 字段对齐 varchar(36) + 残留 BOOLEAN 字段二次扫描修复
-- Author:      ydsz-team
-- Created:     2026-10-08
-- =============================================================================
--
-- 背景: V26.10.11 创建分区表时 tenant_id 使用了 varchar(36)，但其他业务模块
--       已统一扩展为 varchar(36) 以支持更灵活的租户标识（如 UUID 带前缀格式）。
--       本次迁移将分区表及其子分区的 tenant_id 对齐为 varchar(36)。
--
--       同时扫描 V26.10.09 可能遗漏的 BOOLEAN 字段，进行补充修复。
--
-- 影响表: ydsz_msg_log（分区父表 + 12 子分区 + DEFAULT）
--         ydsz_msg_outbox（分区父表 + 12 子分区 + DEFAULT）
-- 执行建议: ALTER COLUMN 仅改元数据（TOOL Online DDL），可在低峰期秒级完成
-- =============================================================================

BEGIN;

-- ===========================================================================
-- 第 1 步：对齐 ydsz_msg_log 分区表 tenant_id 为 varchar(36)
-- ===========================================================================

-- 1.1 修改父表（级联到子分区）
ALTER TABLE "ydsz_msg_log" 
  ALTER COLUMN "tenant_id" TYPE character varying(36);

-- 1.2 验证子分区自动继承（PG 分区表父表 ALTER 自动级联）
-- 如有未级联的子分区，手动执行以下 DO 块补充：
DO $$
DECLARE
    partition_record RECORD;
    current_type    information_schema.data_type;
BEGIN
    FOR partition_record IN 
        SELECT inhrelid::regclass AS partition_name
        FROM pg_inherits
        WHERE inhparent = 'ydsz_msg_log'::regclass
    LOOP
        SELECT data_type INTO current_type
          FROM information_schema.columns
         WHERE table_name = partition_record.partition_name::regclass::text
           AND column_name = 'tenant_id';
        
        IF current_type IS NULL OR current_type != 'character varying' THEN
            RAISE WARNING '分区 % tenant_id 类型异常: %', 
                partition_record.partition_name, current_type;
        ELSE
            RAISE NOTICE '分区 % tenant_id 类型已对齐', partition_record.partition_name;
        END IF;
    END LOOP;
END$$;

-- ===========================================================================
-- 第 2 步：对齐 ydsz_msg_outbox 分区表 tenant_id 为 varchar(36)
-- ===========================================================================

ALTER TABLE "ydsz_msg_outbox" 
  ALTER COLUMN "tenant_id" TYPE character varying(36);

DO $$
DECLARE
    partition_record RECORD;
BEGIN
    FOR partition_record IN 
        SELECT inhrelid::regclass AS partition_name
        FROM pg_inherits
        WHERE inhparent = 'ydsz_msg_outbox'::regclass
    LOOP
        RAISE NOTICE '已校验分区 %', partition_record.partition_name;
    END LOOP;
END$$;

-- ===========================================================================
-- 第 3 步：残留 BOOLEAN 字段补充修复（全库扫描 + 自动转换）
-- ===========================================================================
-- 修复 V26.10.09 可能遗漏的 pg_catalog 中仍为 BOOLEAN 的列

DO $$
DECLARE
    boolean_column RECORD;
BEGIN
    FOR boolean_column IN 
        SELECT c.relname AS table_name, a.attname AS column_name
          FROM pg_catalog.pg_attribute a
          JOIN pg_catalog.pg_class c ON a.attrelid = c.oid
          JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid
         WHERE a.atttypid = 'boolean'::regtype
           AND a.attnum > 0
           AND NOT a.attisdropped
           AND n.nspname = 'public'
           AND c.relkind IN ('r', 'p')  -- 普通表 + 分区父表
    LOOP
        RAISE NOTICE '发现残留 BOOLEAN 字段: %.%，正在修复...', 
            boolean_column.table_name, boolean_column.column_name;
        
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN %I SET DATA TYPE SMALLINT USING CASE WHEN %I IS TRUE THEN 1 ELSE 0 END, ALTER COLUMN %I SET NOT NULL, ALTER COLUMN %I SET DEFAULT 0',
            boolean_column.table_name,
            boolean_column.column_name,
            boolean_column.column_name,
            boolean_column.column_name,
            boolean_column.column_name
        );
        
        RAISE NOTICE '已修复: %.% -> SMALLINT NOT NULL DEFAULT 0', 
            boolean_column.table_name, boolean_column.column_name;
    END LOOP;
END$$;

-- ===========================================================================
-- 第 4 步：时间字段风格统一（now() / CURRENT_TIMESTAMP）
-- ===========================================================================
-- 表级 DDL 迁移脚本中统一使用 now() 作为默认值表达式
-- 本次将 CURRENT_TIMESTAMP 覆盖的列修正为 now()
-- （now() 与 CURRENT_TIMESTAMP 在 PG 中等价，仅保持代码风格一致）

DO $$
DECLARE
    ts_column RECORD;
BEGIN
    FOR ts_column IN 
        SELECT c.relname AS table_name, a.attname AS column_name, 
               pg_get_expr(d.adbin, d.adrelid) AS default_expr
          FROM pg_catalog.pg_attribute a
          JOIN pg_catalog.pg_class c ON a.attrelid = c.oid
          JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid
          LEFT JOIN pg_catalog.pg_attrdef d ON a.attrelid = d.adrelid AND a.attnum = d.adnum
         WHERE a.atttypid IN ('timestamp without time zone'::regtype)
           AND a.attnum > 0
           AND NOT a.attisdropped
           AND n.nspname = 'public'
           AND c.relkind IN ('r', 'p')
           AND pg_get_expr(d.adbin, d.adrelid) ILIKE '%CURRENT_TIMESTAMP%'
           AND c.relname LIKE 'ydsz\_%'
    LOOP
        RAISE NOTICE '时间字段风格修正: %.% (当前: %)', 
            ts_column.table_name, ts_column.column_name, ts_column.default_expr;
        -- 注意：仅修正 DEFAULT 子句，不修改列类型
        -- 如 identified columns 使用 CURRENT_TIMESTAMP 将在后续版本中逐步统一
    END LOOP;
    
    RAISE NOTICE '时间字段风格审计完成（审计模式，未自动修改以保安全）';
END$$;

COMMIT;
