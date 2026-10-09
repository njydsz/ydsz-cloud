-- ============================================================================
-- Flyway Migration: V26.10.10 - 高增长表按月 RANGE 分区
-- Date: 2026-10-10
-- Author: ydsz-team
-- ============================================================================
--
-- 背景:
--   对 ydzs_comm_audit_log（审计日志表）和 ydzs_msg_log（消息发送日志表）两张高增长表实施按月 RANGE 分区，
--   提升大表查询性能，优化数据归档和清理效率。采用 PostgreSQL 原生声明式分区方案，
--   分区键为 created_at 字段，保留历史数据分区，同时预创建未来 3 个月的分区。
--
-- 执行建议:
--   1. 必须在测试环境验证通过后方可在生产环境执行
--   2.建议在业务低峰期执行，数据迁移步骤会根据原始数据量消耗一定时间
--   3. 执行期间会有短暂的排它锁，建议预留足够的执行窗口
--   4. 原始表重命名为 *_old 后缀保留，DBA 确认新表运行稳定后可手动清理旧表
--   5. 每月建议通过定时任务自动创建下个月的分区，避免数据写入默认分区
--
-- 注意事项:
--   - PostgreSQL 分区表主键必须包含分区键，因此新表主键为 (id, created_at)
--   - 仅创建常用查询所需的全局索引，分区表会自动将索引应用到所有分区
--   - 默认分区作为兜底，数据超出预创建分区范围时会写入默认分区，需定期检查默认分区大小
-- ============================================================================

-- ============================================================================
-- Section 1: ydzs_comm_audit_log 按月 RANGE 分区改造
-- ============================================================================

-- 1.1 重命名原始表
ALTER TABLE IF EXISTS ydsz_comm_audit_log RENAME TO ydzs_comm_audit_log_old;

-- 1.2 创建分区表
CREATE TABLE IF NOT EXISTS ydsz_comm_audit_log (
    id              VARCHAR(36) NOT NULL,
    audit_type      VARCHAR(64) DEFAULT NULL,
    biz_type        VARCHAR(64) DEFAULT NULL,
    biz_id          VARCHAR(36) DEFAULT NULL,
    content         TEXT        DEFAULT NULL,
    operator_id     VARCHAR(36) DEFAULT NULL,
    operator_name   VARCHAR(128) DEFAULT NULL,
    operator_ip     VARCHAR(64)  DEFAULT NULL,
    tenant_id       VARCHAR(36) NOT NULL DEFAULT '0',
    is_deleted      SMALLINT    NOT NULL DEFAULT 0,
    created_by      VARCHAR(36) DEFAULT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(36) DEFAULT NULL,
    updated_at      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    revision        INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_ydsz_comm_audit_log PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 1.3 创建分区（当前月份+未来3个月+默认分区，可动态扩展）
-- 2026年10月分区
CREATE TABLE IF NOT EXISTS ydzs_comm_audit_log_2026_10 PARTITION OF ydsz_comm_audit_log
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
-- 2026年11月分区
CREATE TABLE IF NOT EXISTS ydzs_comm_audit_log_2026_11 PARTITION OF ydsz_comm_audit_log
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
-- 2026年12月分区
CREATE TABLE IF NOT EXISTS ydzs_comm_audit_log_2026_12 PARTITION OF ydsz_comm_audit_log
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
-- 默认分区兜底
CREATE TABLE IF NOT EXISTS ydzs_comm_audit_log_default PARTITION OF ydsz_comm_audit_log DEFAULT;

-- 1.4 重建索引
CREATE INDEX IF NOT EXISTS idx_ydsz_comm_audit_log_biz ON ydsz_comm_audit_log (biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_ydsz_comm_audit_log_created_at ON ydsz_comm_audit_log (created_at);
CREATE INDEX IF NOT EXISTS idx_ydsz_comm_audit_log_operator ON ydsz_comm_audit_log (operator_id);
CREATE INDEX IF NOT EXISTS idx_ydsz_comm_audit_log_tenant_deleted ON ydsz_comm_audit_log (tenant_id, is_deleted);

-- 1.5 数据迁移（按月分批）
DO $$
DECLARE
    v_min_date DATE;
    v_max_date DATE;
    v_cursor  DATE;
    v_end     DATE;
    v_count   BIGINT;
BEGIN
    SELECT MIN(created_at)::DATE, MAX(created_at)::DATE
      INTO v_min_date, v_max_date
      FROM ydzs_comm_audit_log_old;
    IF v_min_date IS NULL THEN
        RAISE NOTICE 'ydzs_comm_audit_log_old 为空，跳过数据迁移';
        RETURN;
    END IF;
    v_cursor := DATE_TRUNC('MONTH', v_min_date)::DATE;
    v_end    := (DATE_TRUNC('MONTH', v_max_date) + INTERVAL '1 MONTH')::DATE;
    WHILE v_cursor < v_end LOOP
        INSERT INTO ydsz_comm_audit_log
            SELECT * FROM ydzs_comm_audit_log_old
             WHERE created_at >= v_cursor
               AND created_at < v_cursor + INTERVAL '1 MONTH';
        GET DIAGNOSTICS v_count = ROW_COUNT;
        RAISE NOTICE '迁移审计日志 % 数据: % 行', v_cursor, v_count;
        COMMIT;
        v_cursor := v_cursor + INTERVAL '1 MONTH';
    END LOOP;
END$$;

-- 1.6 数据校验
DO $$
DECLARE
    v_old_count BIGINT;
    v_new_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_old_count FROM ydzs_comm_audit_log_old;
    SELECT COUNT(*) INTO v_new_count FROM ydsz_comm_audit_log;
    IF v_old_count != v_new_count THEN
        RAISE EXCEPTION '审计日志表行数不一致! old=%, new=%', v_old_count, v_new_count;
    END IF;
    RAISE NOTICE '审计日志表行数校验通过: % 行', v_new_count;
END$$;


-- ============================================================================
-- Section 2: ydsz_msg_log 按月 RANGE 分区改造（覆盖现有分区策略，优化分区范围）
-- ============================================================================

-- 2.1 重命名原始表
ALTER TABLE IF EXISTS ydsz_msg_log RENAME TO ydsz_msg_log_old;

-- 2.2 创建分区表
CREATE TABLE IF NOT EXISTS ydsz_msg_log (
    id              VARCHAR(36)   NOT NULL,
    channel         VARCHAR(32)   NOT NULL,
    biz_type        VARCHAR(36)   DEFAULT NULL,
    biz_id          VARCHAR(36)   DEFAULT NULL,
    receiver        VARCHAR(128)  DEFAULT NULL,
    template_code   VARCHAR(36)   DEFAULT NULL,
    template_params JSONB         DEFAULT NULL,
    content         TEXT          DEFAULT NULL,
    status          VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    error_message   TEXT          DEFAULT NULL,
    priority        VARCHAR(32)   NOT NULL DEFAULT 'NORMAL',
    sender_id       VARCHAR(32)   DEFAULT NULL,
    message_group   VARCHAR(64)   DEFAULT NULL,
    batch_id        VARCHAR(64)   DEFAULT NULL,
    route_rule_id   VARCHAR(32)   DEFAULT NULL,
    canary          SMALLINT      DEFAULT 0,
    canary_key      VARCHAR(128)  DEFAULT NULL,
    dedup_key       VARCHAR(128)  DEFAULT NULL,
    recall_status   VARCHAR(32)   NOT NULL DEFAULT 'NONE',
    recall_at       TIMESTAMP     DEFAULT NULL,
    receipt_status  VARCHAR(32)   NOT NULL DEFAULT 'NONE',
    receipt_at      TIMESTAMP     DEFAULT NULL,
    retry_count     INT           NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMP     DEFAULT NULL,
    provider_trace_id VARCHAR(128) DEFAULT NULL,
    cost_ms         BIGINT        DEFAULT NULL,
    cost            NUMERIC(20,6) DEFAULT NULL,
    trace_id        VARCHAR(64)   DEFAULT NULL,
    msg_id          VARCHAR(36)   DEFAULT NULL,
    topic           VARCHAR(128)  DEFAULT NULL,
    reconsume_times INT           DEFAULT NULL,
    parent_msg_id   VARCHAR(36)   DEFAULT NULL,
    scheduled_at    TIMESTAMP     DEFAULT NULL,
    sort            INT           DEFAULT 0,
    revision        INT           DEFAULT 0,
    tenant_id       VARCHAR(36)   NOT NULL DEFAULT '0',
    is_deleted      SMALLINT      NOT NULL DEFAULT 0,
    created_by      VARCHAR(36)   DEFAULT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(36)   DEFAULT NULL,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ydsz_msg_log PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 2.3 创建分区（当前月份+未来3个月+默认分区，可动态扩展）
-- 2026年10月分区
CREATE TABLE IF NOT EXISTS ydzs_msg_log_2026_10 PARTITION OF ydsz_msg_log
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
-- 2026年11月分区
CREATE TABLE IF NOT EXISTS ydzs_msg_log_2026_11 PARTITION OF ydsz_msg_log
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
-- 2026年12月分区
CREATE TABLE IF NOT EXISTS ydzs_msg_log_2026_12 PARTITION OF ydsz_msg_log
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
-- 默认分区兜底
CREATE TABLE IF NOT EXISTS ydzs_msg_log_default PARTITION OF ydsz_msg_log DEFAULT;

-- 2.4 重建索引
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_batch_id ON ydsz_msg_log (batch_id);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_biz ON ydsz_msg_log (biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_created_at ON ydsz_msg_log (created_at);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_dedup_key ON ydsz_msg_log (dedup_key);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_msg_id ON ydsz_msg_log (msg_id);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_provider_trace_id ON ydsz_msg_log (provider_trace_id);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_receiver ON ydsz_msg_log (receiver);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_status_created ON ydsz_msg_log (status, created_at);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_template_code ON ydsz_msg_log (template_code);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_tenant_channel_time ON ydsz_msg_log (tenant_id, channel, created_at);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_tenant_is_deleted ON ydsz_msg_log (tenant_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_trace_id ON ydsz_msg_log (trace_id);

-- 2.5 数据迁移（按月分批）
DO $$
DECLARE
    v_min_date DATE;
    v_max_date DATE;
    v_cursor  DATE;
    v_end     DATE;
    v_count   BIGINT;
BEGIN
    SELECT MIN(created_at)::DATE, MAX(created_at)::DATE
      INTO v_min_date, v_max_date
      FROM ydsz_msg_log_old;
    IF v_min_date IS NULL THEN
        RAISE NOTICE 'ydsz_msg_log_old 为空，跳过数据迁移';
        RETURN;
    END IF;
    v_cursor := DATE_TRUNC('MONTH', v_min_date)::DATE;
    v_end    := (DATE_TRUNC('MONTH', v_max_date) + INTERVAL '1 MONTH')::DATE;
    WHILE v_cursor < v_end LOOP
        INSERT INTO ydsz_msg_log
            SELECT * FROM ydsz_msg_log_old
             WHERE created_at >= v_cursor
               AND created_at < v_cursor + INTERVAL '1 MONTH';
        GET DIAGNOSTICS v_count = ROW_COUNT;
        RAISE NOTICE '迁移消息日志 % 数据: % 行', v_cursor, v_count;
        COMMIT;
        v_cursor := v_cursor + INTERVAL '1 MONTH';
    END LOOP;
END$$;

-- 2.6 数据校验
DO $$
DECLARE
    v_old_count BIGINT;
    v_new_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_old_count FROM ydsz_msg_log_old;
    SELECT COUNT(*) INTO v_new_count FROM ydsz_msg_log;
    IF v_old_count != v_new_count THEN
        RAISE EXCEPTION '消息日志表行数不一致! old=%, new=%', v_old_count, v_new_count;
    END IF;
    RAISE NOTICE '消息日志表行数校验通过: % 行', v_new_count;
END$$;


-- ============================================================================
-- 运维备注（供DBA参考）
-- ============================================================================
-- 1. 每月自动创建下个月分区的示例SQL：
-- CREATE TABLE IF NOT EXISTS ydsz_comm_audit_log_YYYY_MM PARTITION OF ydsz_comm_audit_log
--     FOR VALUES FROM ('YYYY-MM-01') TO ('YYYY-MM-01'::DATE + INTERVAL '1 MONTH');
-- CREATE TABLE IF NOT EXISTS ydsz_msg_log_YYYY_MM PARTITION OF ydsz_msg_log
--     FOR VALUES FROM ('YYYY-MM-01') TO ('YYYY-MM-01'::DATE + INTERVAL '1 MONTH');
--
-- 2. 查询分区大小：
-- SELECT partition_name, pg_size_pretty(pg_total_relation_size(partition_name)) AS size
-- FROM information_schema.table_partitions
-- WHERE table_name IN ('ydsz_comm_audit_log', 'ydsz_msg_log')
-- ORDER BY partition_name;
--
-- 3. 确认新表运行稳定后，清理旧表的SQL（仅DBA执行）：
-- DROP TABLE IF EXISTS ydzs_comm_audit_log_old;
-- DROP TABLE IF EXISTS ydsz_msg_log_old;
--
-- 4. 分区表添加新列/索引时，MySQL会自动同步到所有分区，无需单独维护分区结构。
-- ============================================================================

-- ============================================================================
-- 完成
-- ============================================================================
