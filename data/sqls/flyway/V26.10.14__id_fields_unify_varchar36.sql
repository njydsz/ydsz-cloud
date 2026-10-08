-- =============================================================================
-- Flyway Migration: V26.10.14
-- Description: ID 类字段类型长度统一 VARCHAR(36)（YDIZ-DB-008 P0）
-- Author:      ydsz-team
-- Created:     2026-10-08
-- =============================================================================
--
-- 背景:
--   Java 层已将全部业务实体从 MpBaseEntity<Long> 迁移至 MpBaseEntity<String>，
--   雪花算法（SnowflakeIdGenerator）生成的 long 值由 MyBatis-Plus 自动转换为 String 存储。
--   本轮 DDL 迁移将数据库列类型统一为 VARCHAR(36)，与 Java 实体层对齐。
--
--   YDIZ-DB-008 规范:
--   - id 列: VARCHAR(36)（兼容 UUID 带连接符 36 字符 + 雪花数字 19 位）
--   - tenant_id: VARCHAR(36)（兼容 UUID + 雪花 String，从 V26.10.07 的 varchar(64) 和基线 varchar(32) 统一收敛）
--   - created_by / updated_by: VARCHAR(36)（与 id 同宽）
--
-- 影响范围:
--   - G1: Generator 模块 7 张表 BIGINT 自增 → VARCHAR(36)（需删序列）+ BOOLEAN → SMALLINT
--   - G2: Agent 模块 ydsz_agt_insight_report BIGSERIAL → VARCHAR(36)（需删序列）
--   - G3: Userinfo 模块 2 张表 BIGINT 自增 → VARCHAR(36)
--   - G4: 全平台 tenant_id 统一为 VARCHAR(36)（135+ 表动态扫描）
--   - G5: 全平台 created_by / updated_by 统一为 VARCHAR(36)（动态扫描）
--
-- 执行策略:
--   - ALTER COLUMN TYPE 仅改元数据，MVCC 不触发全表重写（PG 11+）
--   - BIGINT → VARCHAR(36) 使用 USING id::text 实现安全转型
--   - 建议在低峰期执行；如遇大表，可使用 pg_recopy 替代
-- =============================================================================

BEGIN;

-- ===========================================================================
-- 第 1 步：Generator 模块 BIGINT 自增 id → VARCHAR(36) + BOOLEAN → SMALLINT
-- ===========================================================================
-- 说明: 7 张 Generator 表原使用 IdType.AUTO（数据库自增），改为 IdType.ASSIGN_ID
--       雪花 ID 由应用层生成。需先删默认值/序列，再改类型。

-- 1.1 ydsz_gen_datasource
ALTER TABLE ydsz_gen_datasource ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_datasource ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_datasource ALTER COLUMN is_deleted TYPE SMALLINT USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END;
ALTER TABLE ydsz_gen_datasource ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_datasource ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_datasource ADD CONSTRAINT pk_ydsz_gen_datasource PRIMARY KEY (id);
COMMENT ON COLUMN ydsz_gen_datasource.id IS '主键 ID（雪花算法 String）';

-- 1.2 ydsz_gen_template_group
ALTER TABLE ydsz_gen_template_group ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_template_group ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_template_group ALTER COLUMN is_deleted TYPE SMALLINT USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END;
ALTER TABLE ydsz_gen_template_group ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_template_group ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_template_group ADD CONSTRAINT pk_ydsz_gen_template_group PRIMARY KEY (id);
COMMENT ON COLUMN ydsz_gen_template_group.id IS '主键 ID（雪花算法 String）';

-- 1.3 ydsz_gen_history
ALTER TABLE ydsz_gen_history ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_history ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_history ALTER COLUMN is_deleted TYPE SMALLINT USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END;
ALTER TABLE ydsz_gen_history ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_history ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_history ADD CONSTRAINT pk_ydsz_gen_history PRIMARY KEY (id);
COMMENT ON COLUMN ydsz_gen_history.id IS '主键 ID（雪花算法 String）';

-- 1.4 ydsz_gen_history_file
ALTER TABLE ydsz_gen_history_file ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_history_file ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_history_file ALTER COLUMN is_deleted TYPE SMALLINT USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END;
ALTER TABLE ydsz_gen_history_file ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_history_file ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_history_file ADD CONSTRAINT pk_ydsz_gen_history_file PRIMARY KEY (id);
COMMENT ON COLUMN ydsz_gen_history_file.id IS '主键 ID（雪花算法 String）';

-- 1.5 ydsz_gen_table_meta
ALTER TABLE ydsz_gen_table_meta ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_table_meta ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_table_meta ALTER COLUMN is_deleted TYPE SMALLINT USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END;
ALTER TABLE ydsz_gen_table_meta ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_table_meta ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_table_meta ADD CONSTRAINT pk_ydsz_gen_table_meta PRIMARY KEY (id);
COMMENT ON COLUMN ydsz_gen_table_meta.id IS '主键 ID（雪花算法 String）';

-- 1.6 ydsz_gen_template
ALTER TABLE ydsz_gen_template ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_template ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_template ALTER COLUMN is_deleted TYPE SMALLINT USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END;
ALTER TABLE ydsz_gen_template ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_template ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_template ADD CONSTRAINT pk_ydsz_gen_template PRIMARY KEY (id);
COMMENT ON COLUMN ydsz_gen_template.id IS '主键 ID（雪花算法 String）';

-- 1.7 ydsz_gen_column_meta
ALTER TABLE ydsz_gen_column_meta ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_column_meta ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_column_meta ALTER COLUMN is_deleted TYPE SMALLINT USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END;
ALTER TABLE ydsz_gen_column_meta ADD CONSTRAINT pk_ydsz_gen_column_meta PRIMARY KEY (id);
COMMENT ON COLUMN ydsz_gen_column_meta.id IS '主键 ID（雪花算法 String）';

-- 删除 Generator 模块已废弃的自增序列（DROP DEFAULT 后序列仍存在需手动清理）
DROP SEQUENCE IF EXISTS ydsz_gen_datasource_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_template_group_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_history_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_history_file_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_table_meta_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_template_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_column_meta_id_seq;

-- ===========================================================================
-- 第 2 步：Agent 模块 ydzs_agt_insight_report BIGSERIAL → VARCHAR(36)
-- ===========================================================================
ALTER TABLE ydzs_agt_insight_report ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydzs_agt_insight_report ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydzs_agt_insight_report ALTER COLUMN tenant_id TYPE VARCHAR(36);
ALTER TABLE ydzs_agt_insight_report ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydzs_agt_insight_report ALTER COLUMN updated_by TYPE VARCHAR(36);
COMMENT ON COLUMN ydzs_agt_insight_report.id IS '主键 ID（雪花算法 String）';
DROP SEQUENCE IF EXISTS ydzs_agt_insight_report_id_seq;

-- ===========================================================================
-- 第 3 步：Userinfo 模块 BIGINT 自增 id → VARCHAR(36)
-- ===========================================================================
-- 3.1 ydzs_idm_auth_credential
ALTER TABLE ydzs_idm_auth_credential ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydzs_idm_auth_credential ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydzs_idm_auth_credential ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydzs_idm_auth_credential ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydzs_idm_auth_credential ADD CONSTRAINT pk_ydsz_idm_auth_credential PRIMARY KEY (id);
COMMENT ON COLUMN ydzs_idm_auth_credential.id IS '主键 ID（雪花算法 String）';

-- 3.2 ydzs_idm_auth_apikey
ALTER TABLE ydzs_idm_auth_apikey ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydzs_idm_auth_apikey ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydzs_idm_auth_apikey ALTER COLUMN user_id TYPE VARCHAR(36);
ALTER TABLE ydzs_idm_auth_apikey ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydzs_idm_auth_apikey ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydzs_idm_auth_apikey ADD CONSTRAINT pk_ydsz_idm_auth_apikey PRIMARY KEY (id);
COMMENT ON COLUMN ydzs_idm_auth_apikey.id IS '主键 ID（雪花算法 String）';

-- 删除 Userinfo 模块已废弃的自增序列
DROP SEQUENCE IF EXISTS ydzs_idm_auth_credential_id_seq;
DROP SEQUENCE IF EXISTS ydzs_idm_auth_apikey_id_seq;

-- ===========================================================================
-- 第 4 步：全平台 tenant_id 统一为 VARCHAR(36)（动态扫描 + 条件执行）
-- ===========================================================================
-- 说明: 跳过已为 varchar(36) 的表，避免无效 DDL。
--       涵盖 V26.10.07 升级的 135 张 varchar(64) 表 + 旧基线 varchar(32) 表。
DO $$
DECLARE
    col_record  RECORD;
    current_ty  information_schema.data_type;
    current_max information_schema.character_maximum_length;
BEGIN
    FOR col_record IN
        SELECT table_schema, table_name, column_name
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND column_name = 'tenant_id'
           AND table_name LIKE 'ydsz\\_%'
           AND data_type = 'character varying'
    LOOP
        SELECT character_maximum_length INTO current_max
          FROM information_schema.columns
         WHERE table_schema = col_record.table_schema
           AND table_name = col_record.table_name
           AND column_name = 'tenant_id';

        IF current_max IS NULL OR current_max != 36 THEN
            EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I TYPE VARCHAR(36)',
                           col_record.table_schema, col_record.table_name, col_record.column_name);
            RAISE NOTICE 'Altered tenant_id to VARCHAR(36): %.%',
                         col_record.table_schema, col_record.table_name;
        END IF;
    END LOOP;
END$$;

-- ===========================================================================
-- 第 5 步：全平台 created_by / updated_by 统一为 VARCHAR(36)（动态扫描）
-- ===========================================================================
DO $$
DECLARE
    col_record  RECORD;
    current_max information_schema.character_maximum_length;
BEGIN
    FOR col_record IN
        SELECT table_schema, table_name, column_name
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND column_name IN ('created_by', 'updated_by')
           AND table_name LIKE 'ydsz\\_%'
           AND data_type = 'character varying'
    LOOP
        SELECT character_maximum_length INTO current_max
          FROM information_schema.columns
         WHERE table_schema = col_record.table_schema
           AND table_name = col_record.table_name
           AND column_name = col_record.column_name;

        IF current_max IS NULL OR current_max != 36 THEN
            EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I TYPE VARCHAR(36)',
                           col_record.table_schema, col_record.table_name, col_record.column_name);
            RAISE NOTICE 'Altered % to VARCHAR(36): %.%',
                         col_record.column_name, col_record.table_schema, col_record.table_name;
        END IF;
    END LOOP;
END$$;

-- ===========================================================================
-- 第 6 步：刷新所有受影响表的统计信息（确保 planner 使用正确索引）
-- ===========================================================================
ANALYZE ydsz_gen_datasource;
ANALYZE ydsz_gen_template_group;
ANALYZE ydsz_gen_history;
ANALYZE ydsz_gen_history_file;
ANALYZE ydsz_gen_table_meta;
ANALYZE ydsz_gen_template;
ANALYZE ydsz_gen_column_meta;
ANALYZE ydzs_agt_insight_report;
ANALYZE ydzs_idm_auth_credential;
ANALYZE ydzs_idm_auth_apikey;

COMMIT;
