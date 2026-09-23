-- ============================================================================
-- YDSZ Cloud 26.09.23 - 工作流引擎优化：审计分区 + 幂等表 + 索引审视
--
-- 功能：
--   1. ydsz_flow_audit_log 按月 RANGE 分区（PostgreSQL 原生分区）
--   2. ydsz_flow_idempotent 全链路幂等表
--   3. 索引审视与补全
--   4. 运营审计表（断点续传归档游标持久化）
-- 依赖：无（独立执行）
-- 日期：2026-09-23
-- @author ydsz-team
-- ============================================================================

-- ============================================================================
-- 一、ydsz_flow_audit_log 改造为按月 RANGE 分区表
-- ============================================================================
-- 当前表为普通单表，全生命周期审计只追加，按月分区可显著提升查询性能（operator_id / operated_at）
-- 同时便于历史分区 detach 归档到冷存储。
--
-- 步骤：
--   1. 备份原表为 ydsz_flow_audit_log_old（如存在）
--   2. 重命名原表 → ydsz_flow_audit_log_old
--   3. 创建分区表 ydsz_flow_audit_log
--   4. 把旧数据 ATTACH 为新分区
--   4. 建立未来 12 个月分区 + 定时创建下月分区（pg_cron 或应用启动时检测）

DO $$
BEGIN
    -- 检查原表是否存在
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ydsz_flow_audit_log') THEN
        -- 重命名原表
        ALTER TABLE ydsz_flow_audit_log RENAME TO ydsz_flow_audit_log_old;
    END IF;
END $$;

-- 创建分区表（按月 RANGE 分区，分区键 operated_at）
CREATE TABLE IF NOT EXISTS ydsz_flow_audit_log (
    id                       VARCHAR(32)     NOT NULL,
    tenant_id                VARCHAR(32)      NOT NULL DEFAULT '0',
    instance_id              VARCHAR(32)      NOT NULL,
    task_id                  VARCHAR(32)      DEFAULT NULL,
    flow_code                VARCHAR(64)      NOT NULL,
    business_type            VARCHAR(64)      DEFAULT NULL,
    business_id              VARCHAR(64)      DEFAULT NULL,
    node_code                VARCHAR(64)      DEFAULT NULL,
    node_name                VARCHAR(128)     DEFAULT NULL,
    action                   VARCHAR(32)      NOT NULL,
    operator_id              VARCHAR(32)      NOT NULL,
    operator_name            VARCHAR(64)      DEFAULT NULL,
    target_id                VARCHAR(32)      DEFAULT NULL,
    target_name              VARCHAR(64)      DEFAULT NULL,
    comment                  VARCHAR(512)     DEFAULT NULL,
    comment_type             VARCHAR(32)      DEFAULT NULL,
    operated_at              TIMESTAMP        NOT NULL,
    provider_trace_id        VARCHAR(64)      DEFAULT NULL,
    status                   VARCHAR(32)      DEFAULT NULL,
    is_deleted               SMALLINT         NOT NULL DEFAULT 0,
    revision                 INTEGER          NOT NULL DEFAULT 0,
    created_by               VARCHAR(64)      DEFAULT NULL,
    created_at               TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               VARCHAR(64)      DEFAULT NULL,
    updated_at               TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ydsz_flow_audit_log PRIMARY KEY (id, operated_at)
) PARTITION BY RANGE (operated_at);

COMMENT ON TABLE ydsz_flow_audit_log IS '流程审计日志表（按月分区，只追加）';
COMMENT ON COLUMN ydsz_flow_audit_log.id IS '主键 ID（Snowflake）';
COMMENT ON COLUMN ydsz_flow_audit_log.tenant_id IS '租户 ID（多租户隔离）';
COMMENT ON COLUMN ydsz_flow_audit_log.instance_id IS '流程实例 ID';
COMMENT ON COLUMN ydsz_flow_audit_log.task_id IS '任务 ID（实例级操作可为空）';
COMMENT ON COLUMN ydsz_flow_audit_log.flow_code IS '流程编码';
COMMENT ON COLUMN ydsz_flow_audit_log.action IS '操作类型（START/PASS/REJECT/TRANSFER/DELEGATE/COUNTERSIGN/RECALL/URGE/TERMINATE/SUSPEND/ACTIVATE/CLAIM）';
COMMENT ON COLUMN ydsz_flow_audit_log.operator_id IS '操作人 ID';
COMMENT ON COLUMN ydsz_flow_audit_log.operated_at IS '操作时间（精确到毫秒，分区键）';
COMMENT ON COLUMN ydsz_flow_audit_log.is_deleted IS '逻辑删除标识（0=未删除，1=已删除）';

-- 将原表作为 2026-09 前的数据分区（如原表有数据）
-- 注意：需要手动确认原表最小 operated_at 来创建精确的历史分区
DO $$
DECLARE
    min_date DATE;
    max_date DATE;
    partition_start DATE;
    partition_end DATE;
    partition_name TEXT;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ydsz_flow_audit_log_old') THEN
        -- 查询原表的时间范围
        SELECT MIN(operated_at::date), MAX(operated_at::date)
          INTO min_date, max_date
          FROM ydsz_flow_audit_log_old;

        IF min_date IS NOT NULL THEN
            -- 创建从最小日期到最大日期+1个月的逐月分区
            partition_start := date_trunc('month', min_date)::date;
            partition_end := (date_trunc('month', max_date) + INTERVAL '1 month')::date;

            WHILE partition_start < partition_end LOOP
                partition_name := 'ydsz_flow_audit_log_' || to_char(partition_start, 'YYYY_MM');

                -- 检查分区是否已存在
                IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = partition_name) THEN
                    EXECUTE format(
                        'CREATE TABLE IF NOT EXISTS %I PARTITION OF ydsz_flow_audit_log
                         FOR VALUES FROM (%L) TO (%L)',
                        partition_name,
                        partition_start,
                        partition_start + INTERVAL '1 month'
                    );
                END IF;

                partition_start := partition_start + INTERVAL '1 month';
            END LOOP;

            -- 将数据从旧表迁移到新分区表
            INSERT INTO ydsz_flow_audit_log
            SELECT * FROM ydsz_flow_audit_log_old
            ON CONFLICT (id, operated_at) DO NOTHING;

            -- 为历史分区建立索引
            partition_start := date_trunc('month', min_date)::date;
            partition_end := (date_trunc('month', max_date) + INTERVAL '1 month')::date;
            WHILE partition_start < partition_end LOOP
                partition_name := 'ydsz_flow_audit_log_' || to_char(partition_start, 'YYYY_MM');

                EXECUTE format(
                    'CREATE INDEX IF NOT EXISTS %I ON %I (instance_id)',
                    'idx_' || lower(partition_name) || '_instance_id',
                    partition_name
                );
                EXECUTE format(
                    'CREATE INDEX IF NOT EXISTS %I ON %I (operator_id, operated_at)',
                    'idx_' || lower(partition_name) || '_operator',
                    partition_name
                );
                EXECUTE format(
                    'CREATE INDEX IF NOT EXISTS %I ON %I (tenant_id, is_deleted, operated_at)',
                    'idx_' || lower(partition_name) => '_tenant_del_at',
                    partition_name
                );

                partition_start := partition_start + INTERVAL '1 month';
            END LOOP;
        END IF;

        -- 删除旧表（数据已迁移）
        -- DROP TABLE IF EXISTS ydsz_flow_audit_log_old;
        RAISE NOTICE '原 ydsz_flow_audit_log_old 数据已迁移，请确认后手动 DROP';
    END IF;
END $$;

-- 创建未来 12 个月分区
DO $$
DECLARE
    i INT := 0;
    partition_start DATE;
    partition_end DATE;
    partition_name TEXT;
BEGIN
    FOR i IN 0..11 LOOP
        partition_start := (date_trunc('month', CURRENT_DATE) + (i || ' months')::interval)::date;
        partition_end := partition_start + INTERVAL '1 month';
        partition_name := 'ydsz_flow_audit_log_' || to_char(partition_start, 'YYYY_MM');

        IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = partition_name) THEN
            EXECUTE format(
                'CREATE TABLE IF NOT EXISTS %I PARTITION OF ydsz_flow_audit_log
                 FOR VALUES FROM (%L) TO (%L)',
                partition_name,
                partition_start,
                partition_end
            );
        END IF;

        -- 为每个新分区建立索引
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON %I (instance_id)',
            'idx_' || lower(partition_name) || '_instance_id',
            partition_name
        );
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON %I (operator_id, operated_at)',
            'idx_' || lower(partition_name) || '_operator',
            partition_name
        );
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON %I (tenant_id, is_deleted, operated_at)',
            'idx_' || lower(partition_name) || '_tenant_del_at',
            partition_name
        );
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON %I (business_type, business_id)',
            'idx_' || lower(partition_name) || '_business',
            partition_name
        );
    END LOOP;
END $$;

-- ============================================================================
-- 二、ydsz_flow_idempotent 全链路幂等表
-- ============================================================================
-- 用途：防止 MQ 重复消费、网络超时重试、用户双击提交等场景导致的操作重复
-- 幂等键：scope + key_hash（SHA-256 of scope+key），过期时间由 ttl_at 控制

CREATE TABLE IF NOT EXISTS ydsz_flow_idempotent (
    id              VARCHAR(32)     PRIMARY KEY,
    scope           VARCHAR(64)      NOT NULL,
    key_hash        VARCHAR(64)      NOT NULL,
    key_raw         VARCHAR(512)     DEFAULT NULL,
    result_data     TEXT             DEFAULT NULL,
    status          VARCHAR(16)      NOT NULL DEFAULT 'PROCESSING',
    -- PROCESSING=处理中, SUCCESS=已成功, FAILED=处理失败可重试
    retry_count     INTEGER          NOT NULL DEFAULT 0,
    error_message   VARCHAR(512)     DEFAULT NULL,
    tenant_id       VARCHAR(32)      DEFAULT '0',
    created_at      TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ttl_at          TIMESTAMP        NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '7 days')
);

COMMENT ON TABLE ydsz_flow_idempotent IS '工作流全链路幂等记录表';
COMMENT ON COLUMN ydsz_flow_idempotent.id IS '主键 ID（Snowflake）';
COMMENT ON COLUMN ydsz_flow_idempotent.scope IS '幂等作用域（如 workflow.advance / workflow.start / workflow.reject）';
COMMENT ON COLUMN ydsz_flow_idempotent.key_hash IS '幂等键哈希（SHA-256），唯一约束';
COMMENT ON COLUMN ydsz_flow_idempotent.key_raw IS '幂等键明文（便于排查）';
COMMENT ON COLUMN ydsz_flow_idempotent.result_data IS '成功结果 JSON（供重复请求直接返回）';
COMMENT ON COLUMN ydsz_flow_idempotent.status IS '处理状态（PROCESSING / SUCCESS / FAILED）';
COMMENT ON COLUMN ydsz_flow_idempotent.retry_count IS '重试次数';
COMMENT ON COLUMN ydsz_flow_idempotent.error_message IS '最后一次错误信息';
COMMENT ON COLUMN ydsz_flow_idempotent.ttl_at IS '过期时间（自动清理依据）';

-- 唯一约束：同一 scope 下同一 key_hash 只有一条活跃记录
CREATE UNIQUE INDEX IF NOT EXISTS uk_ydsz_flow_idempotent_scope_hash
    ON ydsz_flow_idempotent (scope, key_hash)
    WHERE status IN ('PROCESSING', 'SUCCESS');

-- 清理索引（供 purge job 按 ttl_at 批量删除）
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_idempotent_ttl_at
    ON ydsz_flow_idempotent (ttl_at)
    WHERE status = 'SUCCESS';

-- 租户维度查询索引
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_idempotent_tenant_status
    ON ydsz_flow_idempotent (tenant_id, status);

-- ============================================================================
-- 三、ydsz_flow_archive_cursor 归档断点续传游标表
-- ============================================================================
-- 用途：FlowHistoryArchiveService 断点续传，记录上次归档的最大 end_time
CREATE TABLE IF NOT EXISTS ydsz_flow_archive_cursor (
    id              VARCHAR(32)     PRIMARY KEY,
    archive_type    VARCHAR(32)     NOT NULL,
    -- INSTANCE / PURGE
    cursor_value    VARCHAR(64)     NOT NULL,
    -- 归档游标值（如最大 end_time 的 ISO-8601）
    cursor_data     JSONB            DEFAULT NULL,
    -- 附加数据（如上次归档统计、最后处理的实例数）
    tenant_id       VARCHAR(32)      DEFAULT '0',
    created_at      TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ydsz_flow_archive_cursor IS '流程归档断点续传游标表';
COMMENT ON COLUMN ydsz_flow_archive_cursor.archive_type IS '归档类型（INSTANCE / PURGE）';
COMMENT ON COLUMN ydsz_flow_archive_cursor.cursor_value IS '游标值（如上次归档的最大 end_time）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ydsz_flow_archive_cursor_type_tenant
    ON ydsz_flow_archive_cursor (archive_type, tenant_id);

-- ============================================================================
-- 四、索引审视与补全
-- ============================================================================

-- 4.1 ydsz_flow_run_task 复合覆盖索引（待办列表高频查询）
-- 场景：某审批人的待办列表（按 assignee + status 查询 + 排序）
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_run_task_assignee_status_due
    ON ydsz_flow_run_task (tenant_id, assignee, status, due_date DESC, created_at DESC);

-- 4.2 ydsz_flow_instance 状态监控复合索引
-- 场景：按状态 + 流程定义统计在途实例数（发布前影响分析 / 监控仪表盘）
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_instance_status_def_end
    ON ydsz_flow_instance (tenant_id, status, definition_id, end_time);

-- 4.3 ydsz_flow_his_instance 历史查询复合索引
-- 场景：发起人按时间段查询已结束实例
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_his_instance_initiator_end
    ON ydsz_flow_his_instance (tenant_id, initiator, end_time DESC);

-- 4.4 ydsz_flow_his_task 历史审批记录查询
-- 场景：某审批人按时间段查询已办任务
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_his_task_assignee_operated
    ON ydsz_flow_his_task (tenant_id, assignee, operated_at DESC);

-- 4.5 ydsz_flow_event_subscription 事件分发索引
-- 场景：按事件类型 + 关联键查找 WAITING 订阅
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_event_sub_type_status
    ON ydsz_flow_event_subscription (tenant_id, event_type, status, correlation_key);

-- ============================================================================
-- 五、pg_cron 定时分区创建（可选，需安装 pg_cron 扩展）
-- ============================================================================
-- 启用方式：CREATE EXTENSION IF NOT EXISTS pg_cron;
-- 每月 1 日 00:00 创建下月分区
/*
SELECT cron.schedule(
    'flow_audit_partition_next_month',
    '0 0 1 * *',
    $$
    DO $$
    DECLARE
        next_month_start DATE := date_trunc('month', CURRENT_DATE + INTERVAL '1 month');
        next_month_end   DATE := next_month_start + INTERVAL '1 month';
        partition_name   TEXT := 'ydsz_flow_audit_log_' || to_char(next_month_start, 'YYYY_MM');
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = partition_name) THEN
            EXECUTE format(
                'CREATE TABLE IF NOT EXISTS %I PARTITION OF ydsz_flow_audit_log
                 FOR VALUES FROM (%L) TO (%L)',
                partition_name, next_month_start, next_month_end
            );
        END IF;
    END $$;
    $$
);
*/

-- ============================================================================
-- 回滚脚本
-- ============================================================================
/*
-- DROP TABLE IF EXISTS ydsz_flow_idempotent;
-- DROP TABLE IF EXISTS ydsz_flow_archive_cursor;
-- DROP INDEX IF EXISTS idx_ydsz_flow_run_task_assignee_status_due;
-- DROP INDEX IF EXISTS idx_ydsz_flow_instance_status_def_end;
-- DROP INDEX IF EXISTS idx_ydsz_flow_his_instance_initiator_end;
-- DROP INDEX IF EXISTS idx_ydsz_flow_his_task_assignee_operated;
-- DROP INDEX IF EXISTS idx_ydsz_flow_event_sub_type_status;
*/
