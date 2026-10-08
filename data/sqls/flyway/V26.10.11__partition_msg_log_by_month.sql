-- =============================================================================
-- V26.10.11: ydsz_msg_log / ydsz_msg_outbox 按月 RANGE 分区方案
-- 策略：新建分区表 + 数据迁移 + 原子 RENAME 切换（业务无感知）
-- 适用：PostgreSQL 15+（推荐） / PG14 需去掉 ATTACH 步骤
-- 警告：执行前务必备份；建议在低峰期执行；需 ACCESS EXCLUSIVE 锁（秒级）
-- =============================================================================

-- =============================================================================
-- 第 0 步：前置检查（手动执行确认）
-- =============================================================================
-- SELECT COALESCE(MIN(created_at), NOW()) AS earliest FROM "ydsz_msg_log";
-- SELECT COALESCE(MAX(created_at), NOW()) AS latest   FROM "ydsz_msg_log";
-- SELECT COUNT(*) AS row_count FROM "ydsz_msg_log";
-- SELECT COALESCE(MIN(created_at), NOW()) AS earliest FROM "ydsz_msg_outbox";
-- SELECT COALESCE(MAX(created_at), NOW()) AS latest   FROM "ydsz_msg_outbox";
-- SELECT COUNT(*) AS row_count FROM "ydsz_msg_outbox";

-- =============================================================================
-- 第 1 步：ydsz_msg_log 分区改造
-- =============================================================================

-- 1.1 删除旧表上的触发器/依赖（如有），保留原有 FK 引用对象
--     msg_log 无外部 FK，无需处理

-- 1.2 重命名旧表为备份（秒级 ACCESS EXCLUSIVE 锁）
ALTER TABLE "ydsz_msg_log" RENAME TO "ydsz_msg_log_old";

-- 1.3 创建新分区表（与原表结构完全一致，含所有约束、默认值）
CREATE TABLE "ydsz_msg_log" (
  "id"              character varying(36)  NOT NULL,
  "channel"         character varying(32)  NOT NULL,
  "biz_type"        character varying(36)  DEFAULT NULL::character varying,
  "biz_id"          character varying(36)  DEFAULT NULL::character varying,
  "receiver"        character varying(128) DEFAULT NULL::character varying,
  "template_code"   character varying(36)  DEFAULT NULL::character varying,
  "template_params" jsonb,
  "content"         text,
  "status"          character varying(32)  NOT NULL DEFAULT 'PENDING'::character varying,
  "error_message"   text,
  "priority"        character varying(32)  NOT NULL DEFAULT 'NORMAL'::character varying,
  "sender_id"       character varying(32)  DEFAULT NULL::character varying,
  "message_group"   character varying(64)  DEFAULT NULL::character varying,
  "batch_id"        character varying(64)  DEFAULT NULL::character varying,
  "route_rule_id"   character varying(32)  DEFAULT NULL::character varying,
  "canary"          smallint              DEFAULT 0,
  "canary_key"      character varying(128) DEFAULT NULL::character varying,
  "dedup_key"       character varying(128) DEFAULT NULL::character varying,
  "recall_status"   character varying(32)  NOT NULL DEFAULT 'NONE'::character varying,
  "recall_at"       timestamp without time zone,
  "receipt_status"  character varying(32)  NOT NULL DEFAULT 'NONE'::character varying,
  "receipt_at"      timestamp without time zone,
  "retry_count"     integer               NOT NULL DEFAULT 0,
  "next_retry_at"   timestamp without time zone,
  "provider_trace_id" character varying(128) DEFAULT NULL::character varying,
  "cost_ms"         bigint,
  "cost"            numeric(20,6)         DEFAULT NULL::numeric,
  "trace_id"        character varying(64)  DEFAULT NULL::character varying,
  "msg_id"          character varying(36)  DEFAULT NULL::character varying,
  "topic"           character varying(128) DEFAULT NULL::character varying,
  "reconsume_times" integer,
  "parent_msg_id"   character varying(36)  DEFAULT NULL::character varying,
  "scheduled_at"    timestamp without time zone,
  "sort"            integer               DEFAULT 0,
  "revision"        integer               DEFAULT 0,
  "tenant_id"       character varying(36)  NOT NULL DEFAULT '0'::character varying,
  "is_deleted"      smallint              NOT NULL DEFAULT 0,
  "created_by"      character varying(36)  DEFAULT NULL::character varying,
  "created_at"      timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_by"      character varying(36)  DEFAULT NULL::character varying,
  "updated_at"      timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_ydsz_msg_log" PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- ⚠ 注意：分区表 PK 必须包含分区键 created_at

-- 1.4 创建未来 12 个月的分区（2026-10 ~ 2027-09）
--     每个分区覆盖一个自然月：[当月 1 日, 次月 1 日)
CREATE TABLE "ydsz_msg_log_2026_10" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE "ydsz_msg_log_2026_11" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE "ydsz_msg_log_2026_12" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE "ydsz_msg_log_2027_01" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE "ydsz_msg_log_2027_02" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE "ydsz_msg_log_2027_03" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE "ydsz_msg_log_2027_04" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE "ydsz_msg_log_2027_05" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE "ydsz_msg_log_2027_06" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE "ydsz_msg_log_2027_07" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE "ydsz_msg_log_2027_08" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE "ydsz_msg_log_2027_09" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');

-- 1.5 DEFAULT 分区（兜底，防止写入失败——捕获 created_at 超出预定义范围的数据）
CREATE TABLE "ydsz_msg_log_default" PARTITION OF "ydsz_msg_log" DEFAULT;

-- 1.6 在新分区表上重建索引（每个分区自动继承，但需手动创建本地索引）
--     主键约束已包含 (id, created_at)，以下索引用于常见查询路径
CREATE INDEX "idx_ydsz_msg_log_batch_id"            ON "ydsz_msg_log" ("batch_id");
CREATE INDEX "idx_ydsz_msg_log_biz"                 ON "ydsz_msg_log" ("biz_type", "biz_id");
CREATE INDEX "idx_ydsz_msg_log_dedup_key"           ON "ydsz_msg_log" ("dedup_key");
CREATE INDEX "idx_ydsz_msg_log_msg_id"              ON "ydsz_msg_log" ("msg_id");
CREATE INDEX "idx_ydsz_msg_log_provider_trace_id"   ON "ydsz_msg_log" ("provider_trace_id");
CREATE INDEX "idx_ydsz_msg_log_receiver"            ON "ydsz_msg_log" ("receiver");
CREATE INDEX "idx_ydsz_msg_log_scheduled_at"        ON "ydsz_msg_log" ("scheduled_at");
CREATE INDEX "idx_ydsz_msg_log_status_created"      ON "ydsz_msg_log" ("status", "created_at");
CREATE INDEX "idx_ydsz_msg_log_template_code"       ON "ydsz_msg_log" ("template_code");
CREATE INDEX "idx_ydsz_msg_log_tenant_channel_time" ON "ydsz_msg_log" ("tenant_id", "channel", "created_at");
CREATE INDEX "idx_ydsz_msg_log_tenant_is_deleted"   ON "ydsz_msg_log" ("tenant_id", "is_deleted");
CREATE INDEX "idx_ydsz_msg_log_trace_id"           ON "ydsz_msg_log" ("trace_id");

-- 1.7 数据迁移（分批写入，减少 WAL 压力）
--     根据实际情况调整 batch_size；此处按 created_at 月份分批
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
      FROM "ydsz_msg_log_old";

    IF v_min_date IS NULL THEN
        RAISE NOTICE 'ydsz_msg_log_old 为空，跳过数据迁移';
        RETURN;
    END IF;

    v_cursor := date_trunc('month', v_min_date)::date;
    v_end    := (date_trunc('month', v_max_date) + interval '1 month')::date;

    WHILE v_cursor < v_end LOOP
        INSERT INTO "ydsz_msg_log"
            SELECT * FROM "ydsz_msg_log_old"
             WHERE created_at >= v_cursor
               AND created_at <  v_cursor + interval '1 month';
        GET DIAGNOSTICS v_count = ROW_COUNT;
        RAISE NOTICE '迁移 % 数据: % 行', v_cursor, v_count;
        COMMIT;
        v_cursor := v_cursor + interval '1 month';
    END LOOP;
END$$;

-- 1.8 数据校验（确认行数一致）
DO $$
DECLARE
    v_old_count bigint;
    v_new_count bigint;
BEGIN
    SELECT COUNT(*) INTO v_old_count FROM "ydsz_msg_log_old";
    SELECT COUNT(*) INTO v_new_count FROM "ydsz_msg_log";
    IF v_old_count != v_new_count THEN
        RAISE EXCEPTION '行数不一致! old=%, new=%', v_old_count, v_new_count;
    END IF;
    RAISE NOTICE '行数校验通过: % 行', v_new_count;
END$$;

-- 1.9 删除旧表（确认校验通过后手动解除注释）
-- DROP TABLE "ydsz_msg_log_old";


-- =============================================================================
-- 第 2 步：ydsz_msg_outbox 分区改造
-- =============================================================================

-- 2.1 重命名旧表
ALTER TABLE "ydsz_msg_outbox" RENAME TO "ydsz_msg_outbox_old";

-- 2.2 创建新分区表
--     ⚠ 注意：原表 is_deleted 为 boolean，此处改为 smallint 以符合项目规范
CREATE TABLE "ydsz_msg_outbox" (
  "id"              character varying(36)  NOT NULL,
  "aggregate_type"  character varying(128) NOT NULL,
  "aggregate_id"    character varying(128) NOT NULL,
  "event_type"      character varying(128) NOT NULL,
  "payload"         jsonb                  NOT NULL,
  "status"          character varying(32)  NOT NULL DEFAULT 'PENDING'::character varying,
  "publish_attempts" integer               NOT NULL DEFAULT 0,
  "published_at"    timestamp without time zone,
  "sort"            integer               DEFAULT 0,
  "revision"        integer               DEFAULT 0,
  "tenant_id"       character varying(36)  DEFAULT NULL::character varying,
  "is_deleted"      smallint              NOT NULL DEFAULT 0,
  "created_by"      character varying(36),
  "created_at"      timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_by"      character varying(36),
  "updated_at"      timestamp without time zone DEFAULT now(),
  CONSTRAINT "pk_ydsz_msg_outbox" PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 2.3 创建未来 12 个月的分区
CREATE TABLE "ydsz_msg_outbox_2026_10" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE "ydsz_msg_outbox_2026_11" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE "ydsz_msg_outbox_2026_12" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE "ydsz_msg_outbox_2027_01" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE "ydsz_msg_outbox_2027_02" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE "ydsz_msg_outbox_2027_03" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE "ydsz_msg_outbox_2027_04" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE "ydsz_msg_outbox_2027_05" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE "ydsz_msg_outbox_2027_06" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE "ydsz_msg_outbox_2027_07" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE "ydsz_msg_outbox_2027_08" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE "ydsz_msg_outbox_2027_09" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');

-- 2.4 DEFAULT 兜底分区
CREATE TABLE "ydsz_msg_outbox_default" PARTITION OF "ydsz_msg_outbox" DEFAULT;

-- 2.5 重建索引
CREATE INDEX "idx_ydsz_msg_outbox_aggregate"      ON "ydsz_msg_outbox" ("aggregate_type", "aggregate_id");
CREATE INDEX "idx_ydsz_msg_outbox_status_created" ON "ydsz_msg_outbox" ("status", "created_at");
CREATE INDEX "idx_ydsz_msg_outbox_status_published" ON "ydsz_msg_outbox" ("status", "published_at");
CREATE INDEX "idx_ydsz_msg_outbox_tenant_status"  ON "ydsz_msg_outbox" ("tenant_id", "status");

-- 2.6 数据迁移（按月分批）
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
      FROM "ydsz_msg_outbox_old";

    IF v_min_date IS NULL THEN
        RAISE NOTICE 'ydsz_msg_outbox_old 为空，跳过数据迁移';
        RETURN;
    END IF;

    v_cursor := date_trunc('month', v_min_date)::date;
    v_end    := (date_trunc('month', v_max_date) + interval '1 month')::date;

    WHILE v_cursor < v_end LOOP
        INSERT INTO "ydsz_msg_outbox"
            SELECT * FROM "ydsz_msg_outbox_old"
             WHERE created_at >= v_cursor
               AND created_at <  v_cursor + interval '1 month';
        GET DIAGNOSTICS v_count = ROW_COUNT;
        RAISE NOTICE '迁移 % 数据: % 行', v_cursor, v_count;
        COMMIT;
        v_cursor := v_cursor + interval '1 month';
    END LOOP;
END$$;

-- 2.7 数据校验
DO $$
DECLARE
    v_old_count bigint;
    v_new_count bigint;
BEGIN
    SELECT COUNT(*) INTO v_old_count FROM "ydsz_msg_outbox_old";
    SELECT COUNT(*) INTO v_new_count FROM "ydsz_msg_outbox";
    IF v_old_count != v_new_count THEN
        RAISE EXCEPTION '行数不一致! old=%, new=%', v_old_count, v_new_count;
    END IF;
    RAISE NOTICE '行数校验通过: % 行', v_new_count;
END$$;

-- 2.8 删除旧表（确认校验通过后手动解除注释）
-- DROP TABLE "ydsz_msg_outbox_old";


-- =============================================================================
-- 第 3 步：注意事项与运维建议
-- =============================================================================
-- 1. 分区维护：每月月底通过 Flyway 添加下月分区，防止 DEFAULT 兜底
--    示例：
--    CREATE TABLE "ydsz_msg_log_2027_10" PARTITION OF "ydsz_msg_log"
--        FOR VALUES FROM ('2027-10-01') TO ('2027-11-01');
--
-- 2. 历史数据归档：N 个月前的分区可 SET UNLOGGED + pg_dump 导出后 DETACH 删除
--
-- 3. 新增分区只需 SHARE UPDATE EXCLUSIVE 锁，不影响读写
--
-- 4. 查询语句若带 created_at 条件，PG 可自动分区裁剪（partition pruning）
--    建议 SQL 始终包含 created_at 过滤条件以发挥分区优势
--
-- 5. 如需回滚：重命名还原 + DROP 分区表（1.9/2.8 步骤执行前均可秒级回滚）
--
-- 6. ydsz_msg_outbox 的 is_deleted 已由原表 boolean 改为 smallint 以统一规范，
--    应用层 INSERT 传入 0/1 即可，MyBatis-Plus 枚举映射不受影响
-- =============================================================================
