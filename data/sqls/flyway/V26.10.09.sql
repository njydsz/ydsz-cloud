-- =============================================================================
-- Flyway Migration: V26.10.09
-- Description: 高增长表按月 RANGE 分区（审计日志、通用OutBox、Agent追踪）
-- Author:      ydsz-team
-- Created:     2026-10-10
-- =============================================================================
--
-- 背景: V26.10.08 已对 ydzs_msg_log / ydzs_msg_outbox 完成按月分区。
--   本脚本继续对以下三张高增长表实施分区：
--   A) ydzz_comm_audit_log    — 审计日志，日增量 10 万+，按 operation_time RANGE
--   B) ydzs_comm_outbox      — 领域事件投递，高并发写入，按 created_at RANGE
--   C) ydzs_agt_trace_step   — Agent 执行步骤追踪，按 created_at RANGE
--
-- 各 Section 变更一览：
--   Section 1: ydzs_comm_audit_log 按月 RANGE 分区 + 分区维护函数
--   Section 2: ydzs_comm_outbox 按月 RANGE 分区
--   Section 3: ydzs_agt_trace_step 按月 RANGE 分区
--
-- 执行建议:
--   - 建议在低峰期执行，分区迁移涉及数据拷贝
--   - PostgreSQL 17 支持原生声明式分区，迁移时可自动将现有数据归入对应分区
-- =============================================================================

-- =============================================================================
-- Section 1: ydzsz_comm_audit_log 按月 RANGE 分区（日增量 10 万+）
-- =============================================================================

-- 1.1 仅当表未分区时执行（幂等检测）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = 'ydsz_comm_audit_log'
          AND c.relkind = 'p'  -- 已是分区表
    ) THEN
        RAISE NOTICE 'ydsz_comm_audit_log 已是分区表，跳过';
    ELSE
        RAISE NOTICE 'ydsz_comm_audit_log 开始分区改造...';
    END IF;
END$$;

-- 1.2 重命名旧表
ALTER TABLE IF EXISTS "ydsz_comm_audit_log" RENAME TO "ydsz_comm_audit_log_old";

-- 1.3 创建分区表（operation_time 作为分区键，与核心查询对齐）
CREATE TABLE "ydsz_comm_audit_log" (
  "id"                      character varying(36)  NOT NULL,
  "app_key"                 character varying(64)  NOT NULL DEFAULT ''::character varying,
  "operator_id"             character varying(36)  DEFAULT NULL::character varying,
  "operator_name"           character varying(64)  DEFAULT NULL::character varying,
  "audit_type"              smallint               NOT NULL DEFAULT 1,
  "action"                  smallint               NOT NULL DEFAULT 99,
  "status"                  smallint               NOT NULL DEFAULT 1,
  "module"                  character varying(128) DEFAULT NULL::character varying,
  "content"                 character varying(1024) DEFAULT NULL::character varying,
  "business_no"             character varying(128) DEFAULT NULL::character varying,
  "ip_address"              character varying(64)  DEFAULT NULL::character varying,
  "request_params"          text,
  "response_result"         text,
  "diff_before_snapshot"    text,
  "diff_after_snapshot"     text,
  "error_message"           character varying(512) DEFAULT NULL::character varying,
  "cost_time"               bigint                 DEFAULT 0,
  "trace_id"                character varying(36)  DEFAULT NULL::character varying,
  "tenant_id"               character varying(36)  DEFAULT NULL::character varying,
  "is_deleted"              smallint               NOT NULL DEFAULT 0,
  "sort"                    integer                DEFAULT 0,
  "revision"                integer                DEFAULT 0,
  "created_by"              character varying(36)  DEFAULT NULL::character varying,
  "created_at"              timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_by"              character varying(36)  DEFAULT NULL::character varying,
  "updated_at"              timestamp without time zone DEFAULT now(),
  "operation_time"          timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_ydsz_comm_audit_log" PRIMARY KEY (id, operation_time)
) PARTITION BY RANGE (operation_time);

-- 1.4 创建月份分区（2026-10 ~ 2027-09，与 ydzs_msg_log 对齐）
CREATE TABLE "ydsz_comm_audit_log_2026_10" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE "ydsz_comm_audit_log_2026_11" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE "ydsz_comm_audit_log_2026_12" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE "ydsz_comm_audit_log_2027_01" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE "ydsz_comm_audit_log_2027_02" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE "ydsz_comm_audit_log_2027_03" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE "ydsz_comm_audit_log_2027_04" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE "ydsz_comm_audit_log_2027_05" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE "ydsz_comm_audit_log_2027_06" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE "ydsz_comm_audit_log_2027_07" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE "ydsz_comm_audit_log_2027_08" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE "ydsz_comm_audit_log_2027_09" PARTITION OF "ydsz_comm_audit_log"
    FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');
CREATE TABLE "ydsz_comm_audit_log_default" PARTITION OF "ydsz_comm_audit_log" DEFAULT;

-- 1.5 重建索引
CREATE INDEX "idx_ydsz_comm_audit_log_operation_time"   ON "ydsz_comm_audit_log" ("operation_time" DESC);
CREATE INDEX "idx_ydsz_comm_audit_log_operator_id"      ON "ydsz_comm_audit_log" ("operator_id", "operation_time" DESC);
CREATE INDEX "idx_ydsz_comm_audit_log_action"           ON "ydsz_comm_audit_log" ("action", "operation_time" DESC);
CREATE INDEX "idx_ydsz_comm_audit_log_module_action"    ON "ydsz_comm_audit_log" ("module", "action", "operation_time" DESC);
CREATE INDEX "idx_ydsz_comm_audit_log_app_key"          ON "ydsz_comm_audit_log" ("app_key", "operation_time" DESC);
CREATE INDEX "idx_ydsz_comm_audit_log_trace_id"         ON "ydsz_comm_audit_log" ("trace_id");
CREATE INDEX "idx_ydsz_comm_audit_log_tenant_time"      ON "ydsz_comm_audit_log" ("tenant_id", "operation_time" DESC);
CREATE INDEX "idx_ydsz_comm_audit_log_status"           ON "ydsz_comm_audit_log" ("status", "operation_time" DESC);

-- 1.6 数据迁移（按月分批提交）
DO $$
DECLARE
    v_min_date date;
    v_max_date date;
    v_cursor  date;
    v_end     date;
    v_count   bigint;
BEGIN
    SELECT MIN(operation_time)::date, MAX(operation_time)::date
      INTO v_min_date, v_max_date
      FROM "ydsz_comm_audit_log_old";
    IF v_min_date IS NULL THEN
        RAISE NOTICE 'ydsz_comm_audit_log_old 为空，跳过数据迁移';
        RETURN;
    END IF;
    v_cursor := date_trunc('month', v_min_date)::date;
    v_end    := (date_trunc('month', v_max_date) + interval '1 month')::date;
    WHILE v_cursor < v_end LOOP
        INSERT INTO "ydsz_comm_audit_log"
            SELECT * FROM "ydsz_comm_audit_log_old"
             WHERE operation_time >= v_cursor
               AND operation_time <  v_cursor + interval '1 month';
        GET DIAGNOSTICS v_count = ROW_COUNT;
        RAISE NOTICE '迁移 audit_log % 数据: % 行', v_cursor, v_count;
        COMMIT;
        v_cursor := v_cursor + interval '1 month';
    END LOOP;
END$$;

-- 1.7 数据校验
DO $$
DECLARE
    v_old_count bigint;
    v_new_count bigint;
BEGIN
    SELECT COUNT(*) INTO v_old_count FROM "ydsz_comm_audit_log_old";
    SELECT COUNT(*) INTO v_new_count FROM "ydsz_comm_audit_log";
    IF v_old_count != v_new_count THEN
        RAISE EXCEPTION 'audit_log 行数不一致! old=%, new=%', v_old_count, v_new_count;
    END IF;
    RAISE NOTICE 'audit_log 行数校验通过: % 行', v_new_count;
END$$;

-- 1.8 删除旧表（校验通过后手动执行，或注释掉由 DBA 确认后执行）
-- DROP TABLE IF EXISTS "ydsz_comm_audit_log_old";


-- =============================================================================
-- Section 2: ydzs_comm_outbox 按月 RANGE 分区（领域事件投递，高并发写入）
-- =============================================================================

-- 2.1 重命名旧表
ALTER TABLE IF EXISTS "ydsz_comm_outbox" RENAME TO "ydsz_comm_outbox_old";

-- 2.2 创建分区表
CREATE TABLE "ydsz_comm_outbox" (
  "id"              character varying(36)  NOT NULL,
  "aggregate_type"  character varying(128) NOT NULL,
  "aggregate_id"    character varying(36)  NOT NULL,
  "event_type"      character varying(128) NOT NULL,
  "payload"         text                     NOT NULL,
  "status"          character varying(15)  NOT NULL DEFAULT 'PENDING'::character varying,
  "retry_count"     bigint                   NOT NULL DEFAULT 0,
  "max_retries"     bigint                   NOT NULL DEFAULT 5,
  "next_retry_at"   timestamp without time zone,
  "error_message"   text,
  "schema_version"  integer                  NOT NULL DEFAULT 1,
  "compressed"      smallint                 NOT NULL DEFAULT 0,
  "trace_id"        character varying(36)    DEFAULT NULL::character varying,
  "idempotency_key" character varying(64)    DEFAULT NULL::character varying,
  "sent_at"         timestamp without time zone,
  "sort"            integer                  DEFAULT 0,
  "revision"        integer                  DEFAULT 0,
  "tenant_id"       character varying(36)    DEFAULT NULL::character varying,
  "is_deleted"      smallint                 NOT NULL DEFAULT 0,
  "created_by"      character varying(36)    DEFAULT NULL::character varying,
  "created_at"      timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_by"      character varying(36)    DEFAULT NULL::character varying,
  "updated_at"      timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_ydsz_comm_outbox" PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 2.3 创建月份分区
CREATE TABLE "ydsz_comm_outbox_2026_10" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE "ydsz_comm_outbox_2026_11" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE "ydsz_comm_outbox_2026_12" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE "ydsz_comm_outbox_2027_01" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE "ydsz_comm_outbox_2027_02" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE "ydsz_comm_outbox_2027_03" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE "ydsz_comm_outbox_2027_04" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE "ydsz_comm_outbox_2027_05" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE "ydsz_comm_outbox_2027_06" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE "ydsz_comm_outbox_2027_07" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE "ydsz_comm_outbox_2027_08" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE "ydsz_comm_outbox_2027_09" PARTITION OF "ydsz_comm_outbox"
    FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');
CREATE TABLE "ydsz_comm_outbox_default" PARTITION OF "ydsz_comm_outbox" DEFAULT;

-- 2.4 重建索引
CREATE INDEX "idx_ydsz_comm_outbox_pending"   ON "ydsz_comm_outbox" ("status", "created_at" ASC);
CREATE INDEX "idx_ydsz_comm_outbox_retry"     ON "ydsz_comm_outbox" ("status", "next_retry_at");
CREATE INDEX "idx_ydsz_comm_outbox_processing" ON "ydsz_comm_outbox" ("status", "updated_at");
CREATE INDEX "idx_ydsz_comm_outbox_sent_at"   ON "ydsz_comm_outbox" ("status", "sent_at");
CREATE INDEX "idx_ydsz_comm_outbox_tenant"    ON "ydsz_comm_outbox" ("tenant_id", "status");
CREATE INDEX "idx_ydsz_comm_outbox_dedup"     ON "ydsz_comm_outbox" ("idempotency_key", "status");
CREATE INDEX "idx_ydsz_comm_outbox_aggregate" ON "ydsz_comm_outbox" ("aggregate_type", "aggregate_id", "created_at" DESC);

-- 2.5 数据迁移
DO $$
DECLARE
    v_min_date date;
    v_max_date date;
    v_cursor  date;
    v_end     date;
    v_count   bigint;
BEGIN
    SELECT MIN(created_at)::date, MAX(created_at)::date
      INTO v_min_date, v_max_date
      FROM "ydsz_comm_outbox_old";
    IF v_min_date IS NULL THEN
        RAISE NOTICE 'ydsz_comm_outbox_old 为空，跳过数据迁移';
        RETURN;
    END IF;
    v_cursor := date_trunc('month', v_min_date)::date;
    v_end    := (date_trunc('month', v_max_date) + interval '1 month')::date;
    WHILE v_cursor < v_end LOOP
        INSERT INTO "ydsz_comm_outbox"
            SELECT * FROM "ydsz_comm_outbox_old"
             WHERE created_at >= v_cursor
               AND created_at <  v_cursor + interval '1 month';
        GET DIAGNOSTICS v_count = ROW_COUNT;
        RAISE NOTICE '迁移 outbox % 数据: % 行', v_cursor, v_count;
        COMMIT;
        v_cursor := v_cursor + interval '1 month';
    END LOOP;
END$$;

-- 2.6 数据校验
DO $$
DECLARE
    v_old_count bigint;
    v_new_count bigint;
BEGIN
    SELECT COUNT(*) INTO v_old_count FROM "ydsz_comm_outbox_old";
    SELECT COUNT(*) INTO v_new_count FROM "ydsz_comm_outbox";
    IF v_old_count != v_new_count THEN
        RAISE EXCEPTION 'outbox 行数不一致! old=%, new=%', v_old_count, v_new_count;
    END IF;
    RAISE NOTICE 'outbox 行数校验通过: % 行', v_new_count;
END$$;


-- =============================================================================
-- Section 3: ydzs_agt_trace_step 按月 RANGE 分区（Agent 执行步骤追踪）
-- =============================================================================

-- 3.1 重命名旧表
ALTER TABLE IF EXISTS "ydsz_agt_trace_step" RENAME TO "ydsz_agt_trace_step_old";

-- 3.2 创建分区表（含 created_at 时间列，分区键为 created_at）
CREATE TABLE "ydsz_agt_trace_step" (
  "trace_id"       character varying(36)  NOT NULL,
  "step_index"     integer               NOT NULL,
  "step_type"      character varying(32)  NOT NULL,
  "step_name"      character varying(128) DEFAULT NULL::character varying,
  "agent_id"       character varying(36)  DEFAULT NULL::character varying,
  "session_id"     character varying(36)  DEFAULT NULL::character varying,
  "parent_step_id" character varying(36)  DEFAULT NULL::character varying,
  "content"        text,
  "input_json"     jsonb,
  "output_json"    jsonb,
  "metadata"       jsonb,
  "cost_ms"        integer               DEFAULT 0,
  "status"         character varying(32)  NOT NULL DEFAULT 'SUCCESS'::character varying,
  "error_message"  text,
  "tenant_id"      character varying(36)  NOT NULL DEFAULT '0'::character varying,
  "is_deleted"     smallint              NOT NULL DEFAULT 0,
  "sort"           integer               DEFAULT 0,
  "revision"       integer               DEFAULT 0,
  "created_by"     character varying(36)  DEFAULT NULL::character varying,
  "created_at"     timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_by"     character varying(36)  DEFAULT NULL::character varying,
  "updated_at"     timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_ydsz_agt_trace_step" PRIMARY KEY (trace_id, step_index, created_at)
) PARTITION BY RANGE (created_at);

-- 3.3 创建月份分区
CREATE TABLE "ydsz_agt_trace_step_2026_10" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE "ydsz_agt_trace_step_2026_11" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE "ydsz_agt_trace_step_2026_12" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE "ydsz_agt_trace_step_2027_01" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE "ydsz_agt_trace_step_2027_02" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE "ydsz_agt_trace_step_2027_03" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE "ydsz_agt_trace_step_2027_04" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE "ydsz_agt_trace_step_2027_05" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE "ydsz_agt_trace_step_2027_06" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE "ydsz_agt_trace_step_2027_07" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE "ydsz_agt_trace_step_2027_08" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE "ydsz_agt_trace_step_2027_09" PARTITION OF "ydsz_agt_trace_step"
    FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');
CREATE TABLE "ydsz_agt_trace_step_default" PARTITION OF "ydsz_agt_trace_step" DEFAULT;

-- 3.4 重建索引
CREATE INDEX "idx_ydsz_agt_trace_step_agent"     ON "ydsz_agt_trace_step" ("agent_id", "created_at" DESC);
CREATE INDEX "idx_ydsz_agt_trace_step_session"   ON "ydsz_agt_trace_step" ("session_id", "created_at" DESC);
CREATE INDEX "idx_ydsz_agt_trace_step_tenant"    ON "ydsz_agt_trace_step" ("tenant_id", "is_deleted");
CREATE INDEX "idx_ydsz_agt_trace_step_type_time" ON "ydsz_agt_trace_step" ("step_type", "created_at" DESC);

-- 3.5 数据迁移
DO $$
DECLARE
    v_min_date date;
    v_max_date date;
    v_cursor  date;
    v_end     date;
    v_count   bigint;
BEGIN
    SELECT MIN(created_at)::date, MAX(created_at)::date
      INTO v_min_date, v_max_date
      FROM "ydsz_agt_trace_step_old";
    IF v_min_date IS NULL THEN
        RAISE NOTICE 'ydsz_agt_trace_step_old 为空，跳过数据迁移';
        RETURN;
    END IF;
    v_cursor := date_trunc('month', v_min_date)::date;
    v_end    := (date_trunc('month', v_max_date) + interval '1 month')::date;
    WHILE v_cursor < v_end LOOP
        INSERT INTO "ydsz_agt_trace_step"
            SELECT * FROM "ydsz_agt_trace_step_old"
             WHERE created_at >= v_cursor
               AND created_at <  v_cursor + interval '1 month';
        GET DIAGNOSTICS v_count = ROW_COUNT;
        RAISE NOTICE '迁移 trace_step % 数据: % 行', v_cursor, v_count;
        COMMIT;
        v_cursor := v_cursor + interval '1 month';
    END LOOP;
END$$;

-- 3.6 数据校验
DO $$
DECLARE
    v_old_count bigint;
    v_new_count bigint;
BEGIN
    SELECT COUNT(*) INTO v_old_count FROM "ydsz_agt_trace_step_old";
    SELECT COUNT(*) INTO v_new_count FROM "ydsz_agt_trace_step";
    IF v_old_count != v_new_count THEN
        RAISE EXCEPTION 'trace_step 行数不一致! old=%, new=%', v_old_count, v_new_count;
    END IF;
    RAISE NOTICE 'trace_step 行数校验通过: % 行', v_new_count;
END$$;


-- =============================================================================
-- Section 4: 分区维护函数（自动创建未来月份分区）
-- =============================================================================
CREATE OR REPLACE FUNCTION create_monthly_partition(
    p_table_name TEXT,
    p_date      DATE
) RETURNS VOID AS $$
DECLARE
    v_partition TEXT;
    v_start     DATE;
    v_end       DATE;
    v_sql       TEXT;
BEGIN
    v_start     := date_trunc('month', p_date);
    v_end       := v_start + interval '1 month';
    v_partition := format('%s_%s_%s',
                          p_table_name,
                          to_char(v_start, 'YYYY'),
                          to_char(v_start, 'MM'));

    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = v_partition
    ) THEN
        v_sql := format('CREATE TABLE "%s" PARTITION OF "%s" FOR VALUES FROM (%L) TO (%L)',
                        v_partition, p_table_name, v_start, v_end);
        EXECUTE v_sql;
        RAISE NOTICE '创建分区: %', v_partition;
    END IF;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION create_monthly_partition(TEXT, DATE) IS '自动创建指定表的月份分区（幂等，已存在则跳过）';

-- =============================================================================
-- 使用说明:
-- 1. 预创建下月分区（调用维护函数）:
--    SELECT create_monthly_partition('ydsz_comm_audit_log', CURRENT_DATE + interval '1 month');
--    SELECT create_monthly_partition('ydsz_comm_outbox', CURRENT_DATE + interval '1 month');
--    SELECT create_months_agt_trace_step', CURRENT_DATE + interval '1 month');
--
-- 2. 删除旧表（确认校验通过后执行）:
--    DROP TABLE IF EXISTS "ydsz_comm_audit_log_old";
--    DROP TABLE IF EXISTS "ydsz_comm_outbox_old";
--    DROP TABLE IF EXISTS "ydsz_agt_trace_step_old";
--
-- 3. 旧分区归档（超过 6 个月的数据可 DROP PARTITION 秒级清理）:
--    ALTER TABLE "ydsz_comm_audit_log" DETACH PARTITION "ydsz_comm_audit_log_2026_10";
-- =============================================================================
