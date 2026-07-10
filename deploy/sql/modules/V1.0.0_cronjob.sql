-- ============================================================
-- PMIS cronjob module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================
-- 本脚本 DDL 对应后端 cronjob 服务 (ydsz-pmis-cronjob) 的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign + NameAssembler(在 CommonAutoConfiguration 注册)。
-- --------------------------------------------------------------------

-- ============================ [006] init pmis job schema ============================
-- [INLINE-OPT] 已统一为单文件 V1.0.0.sql 的最终形态:
--   1) 时间字段 TIMESTAMP → TIMESTAMPTZ
--   2) 审计字段 create_by/create_time → created_by/created_at 规范命名
--   3) tenant_id 改为 NOT NULL DEFAULT 1
--   4) 内联 status/deleted CHECK 约束
--   5) 内联复合部分索引 (tenant_id, created_at DESC) WHERE deleted = 0
--   6) 计数器类字段 (fire_count/success_count/fail_count/login_fail_count 等) 添加非负 CHECK
-- =====================================================
-- PMIS 任务调度模块 DDL
-- 版本: V1.0.0_006 (merged into V1.0.0.sql)
-- 描述: 动态定时任务定义 + 执行日志(自研调度引擎)
-- =====================================================

-- 任务定义表 pmis_job
CREATE TABLE IF NOT EXISTS pmis_job(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_name        VARCHAR(128)   NOT NULL,
    job_group       VARCHAR(64)    NOT NULL DEFAULT 'DEFAULT',
    job_key         VARCHAR(128)   NOT NULL,
    handler         VARCHAR(256)   NOT NULL,
    cron_expression VARCHAR(128),
    -- [P0-3] 调度类型: CRON(Cron表达式, 默认) / FIXED_RATE(固定频率) / FIXED_DELAY(固定延迟) / API(仅手动触发)
    schedule_type   VARCHAR(32)    NOT NULL DEFAULT 'CRON',
    -- [P0-3] 固定频率间隔(毫秒): schedule_type=FIXED_RATE 时生效
    fixed_rate_ms   BIGINT,
    -- [P0-3] 固定延迟间隔(毫秒): schedule_type=FIXED_DELAY 时生效
    fixed_delay_ms  BIGINT,
    params_json     TEXT,
    status          VARCHAR(32)    NOT NULL DEFAULT 'NORMAL',
    remark          VARCHAR(512),
    next_fire_time  TIMESTAMPTZ,
    last_fire_time  TIMESTAMPTZ,
    fire_count      BIGINT         NOT NULL DEFAULT 0,
    success_count   BIGINT         NOT NULL DEFAULT 0,
    fail_count      BIGINT         NOT NULL DEFAULT 0,
    -- [P0-4] 任务级锁 TTL（毫秒, NULL 使用全局默认值）和任务超时（毫秒, NULL 不限）
    lock_ttl_ms     BIGINT,
    timeout_ms      BIGINT,
    -- [P6-3] 慢任务阈值（毫秒, NULL 不检测慢任务; 超过此值记入 pmis_job_slow_log）
    slow_threshold_ms BIGINT,
    -- [P2-1] Misfire 策略: FIRE_NOW 立即执行(默认) / SKIP 跳过 / COALESCE 合并执行并标记 MISFIRED
    misfire_policy  VARCHAR(32)    NOT NULL DEFAULT 'FIRE_NOW',
    -- [P3-3] 分片总数: 1=非分片任务(默认), >1 时按 ShardingStrategy 分配到在线节点并行执行
    shard_total     INTEGER        NOT NULL DEFAULT 1,
    -- [P1-5] 任务类型: BEAN(Spring Bean, 默认) / HTTP(HTTP 调用) / SHELL(脚本) / GLUE(在线代码)
    job_type        VARCHAR(32)    NOT NULL DEFAULT 'BEAN',
    -- [P1-1] 失败重试: 最大重试次数(0=不重试) / 重试间隔(毫秒, NULL=立即) / 退避策略
    max_retries     INTEGER        NOT NULL DEFAULT 0,
    retry_interval_ms BIGINT,
    retry_backoff   VARCHAR(32)    NOT NULL DEFAULT 'FIXED',
    -- [P1-2] 阻塞策略: SERIAL(排队, 默认) / COVER(中断+执行新) / DISCARD(丢弃新) / CONCURRENT(并行)
    block_strategy  VARCHAR(32)    NOT NULL DEFAULT 'SERIAL',
    -- [P1-6] 任务级熔断: 连续失败次数 / 最大连续失败次数(达到后自动暂停) / 自动恢复时间(分钟)
    consecutive_fail_count INTEGER NOT NULL DEFAULT 0,
    max_consecutive_fails INTEGER,
    auto_resume_after_minutes INTEGER,
    -- [P4-7] 优先级: 1-10, 越小越高(默认 5)
    priority        INTEGER        NOT NULL DEFAULT 5,
    -- [P4-8] 版本号: 每次修改 +1, 用于乐观锁和版本追溯
    version         INTEGER        NOT NULL DEFAULT 1,
    -- [P2-8] 任务级时区: 如 Asia/Shanghai / America/New_York / UTC, NULL 使用系统默认时区
    timezone        VARCHAR(64),
    -- [P3-12] 目标集群名称: NULL=本地集群(默认), 非 NULL 时通过 CrossClusterDispatcher 派发到指定集群
    cluster         VARCHAR(64),
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    -- 数据完整性约束
    CONSTRAINT uk_pmis_job_key UNIQUE (job_key, deleted),
    -- [P1-6] status 增加 AUTO_PAUSED: 连续失败熔断后自动暂停
    CONSTRAINT ck_pj_status_enum    CHECK (status IN ('NORMAL', 'PAUSED', 'ERROR', 'COMPLETE', 'AUTO_PAUSED')),
    CONSTRAINT ck_pj_counts_nonneg  CHECK (fire_count >= 0 AND success_count >= 0 AND fail_count >= 0),
    CONSTRAINT ck_pj_success_le     CHECK (success_count <= fire_count),
    -- [P0-3] 调度类型与固定间隔校验
    CONSTRAINT ck_pj_schedule_type_enum CHECK (schedule_type IN ('CRON', 'FIXED_RATE', 'FIXED_DELAY', 'API')),
    CONSTRAINT ck_pj_fixed_rate_pos CHECK (fixed_rate_ms IS NULL OR fixed_rate_ms > 0),
    CONSTRAINT ck_pj_fixed_delay_pos CHECK (fixed_delay_ms IS NULL OR fixed_delay_ms > 0),
    CONSTRAINT ck_pj_lock_ttl_nonneg CHECK (lock_ttl_ms IS NULL OR lock_ttl_ms > 0),
    CONSTRAINT ck_pj_timeout_nonneg  CHECK (timeout_ms IS NULL OR timeout_ms > 0),
    CONSTRAINT ck_pj_slow_threshold_nonneg CHECK (slow_threshold_ms IS NULL OR slow_threshold_ms > 0),
    CONSTRAINT ck_pj_misfire_enum   CHECK (misfire_policy IN ('FIRE_NOW', 'SKIP', 'COALESCE')),
    CONSTRAINT ck_pj_shard_total_pos CHECK (shard_total >= 1),
    CONSTRAINT ck_pj_job_type_enum  CHECK (job_type IN ('BEAN', 'HTTP', 'SHELL', 'GLUE', 'MAP', 'MAP_REDUCE')),
    CONSTRAINT ck_pj_max_retries_nonneg CHECK (max_retries >= 0),
    CONSTRAINT ck_pj_retry_backoff_enum CHECK (retry_backoff IN ('FIXED', 'EXPONENTIAL')),
    CONSTRAINT ck_pj_block_strategy_enum CHECK (block_strategy IN ('SERIAL', 'COVER', 'DISCARD', 'CONCURRENT')),
    CONSTRAINT ck_pj_consecutive_fail_nonneg CHECK (consecutive_fail_count >= 0),
    CONSTRAINT ck_pj_max_consecutive_fails_pos CHECK (max_consecutive_fails IS NULL OR max_consecutive_fails > 0),
    CONSTRAINT ck_pj_auto_resume_pos CHECK (auto_resume_after_minutes IS NULL OR auto_resume_after_minutes > 0),
    CONSTRAINT ck_pj_priority_range CHECK (priority >= 1 AND priority <= 10),
    CONSTRAINT ck_pj_version_pos    CHECK (version >= 1),
    CONSTRAINT ck_pj_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job IS '动态定时任务定义表: 支持运行时增删改触发频率的定时任务(自研调度引擎)';

COMMENT ON COLUMN pmis_job.id IS '主键 ID';

COMMENT ON COLUMN pmis_job.job_name IS '任务名称(展示用)';

COMMENT ON COLUMN pmis_job.job_group IS '任务分组(如 DEFAULT/RECONCILE/ALERT)';

COMMENT ON COLUMN pmis_job.job_key IS '任务唯一 KEY(调度器使用)';

COMMENT ON COLUMN pmis_job.handler IS '任务处理器 Bean 名称(Spring Bean)';

COMMENT ON COLUMN pmis_job.cron_expression IS 'Cron 表达式(如 0 0 2 * * ? = 每日 02:00)';

COMMENT ON COLUMN pmis_job.schedule_type IS '调度类型: CRON(Cron表达式, 默认) / FIXED_RATE(固定频率) / FIXED_DELAY(固定延迟) / API(仅手动触发)';

COMMENT ON COLUMN pmis_job.fixed_rate_ms IS '固定频率间隔(毫秒): schedule_type=FIXED_RATE 时生效';

COMMENT ON COLUMN pmis_job.fixed_delay_ms IS '固定延迟间隔(毫秒): schedule_type=FIXED_DELAY 时生效';

COMMENT ON COLUMN pmis_job.params_json IS '任务参数 JSON';

COMMENT ON COLUMN pmis_job.status IS '任务状态: NORMAL 正常 / PAUSED 暂停 / ERROR 异常 / COMPLETE 一次性任务完成';

COMMENT ON COLUMN pmis_job.remark IS '任务说明';

COMMENT ON COLUMN pmis_job.next_fire_time IS '下次触发时间';

COMMENT ON COLUMN pmis_job.last_fire_time IS '上次触发时间';

COMMENT ON COLUMN pmis_job.fire_count IS '累计触发次数';

COMMENT ON COLUMN pmis_job.success_count IS '成功执行次数';

COMMENT ON COLUMN pmis_job.fail_count IS '失败次数(超过阈值告警)';

COMMENT ON COLUMN pmis_job.lock_ttl_ms IS '任务级分布式锁 TTL(毫秒, NULL 使用全局默认 pmis.cronjob.job-lock-ttl)';

COMMENT ON COLUMN pmis_job.timeout_ms IS '任务超时时间(毫秒, NULL 表示不限超时; 超时后 Leader 标记 FAILED 并重派)';

COMMENT ON COLUMN pmis_job.slow_threshold_ms IS '慢任务阈值(毫秒, NULL 不检测慢任务; 执行耗时超过此值记入 pmis_job_slow_log)';

COMMENT ON COLUMN pmis_job.misfire_policy IS 'Misfire 策略: FIRE_NOW 立即执行(默认) / SKIP 跳过推进 next_fire_time / COALESCE 合并执行并日志标记 MISFIRED';

COMMENT ON COLUMN pmis_job.shard_total IS '分片总数: 1=非分片任务(默认), >1 时按 ShardingStrategy 分配到在线节点并行执行';

COMMENT ON COLUMN pmis_job.job_type IS '任务类型: BEAN(Spring Bean, 默认) / HTTP(HTTP 调用) / SHELL(脚本) / GLUE(在线代码) / MAP(Map 动态子任务) / MAP_REDUCE(MapReduce 动态子任务+汇总)';

COMMENT ON COLUMN pmis_job.max_retries IS '最大重试次数: 0=不重试(默认), >0 时失败后自动重试';

COMMENT ON COLUMN pmis_job.retry_interval_ms IS '重试间隔(毫秒): NULL=立即重试, >0 时按 retry_backoff 策略计算间隔';

COMMENT ON COLUMN pmis_job.retry_backoff IS '重试退避策略: FIXED 固定间隔(默认) / EXPONENTIAL 指数退避(间隔*2^retryCount)';

COMMENT ON COLUMN pmis_job.block_strategy IS '阻塞策略: SERIAL 排队(默认) / COVER 中断+执行新 / DISCARD 丢弃新 / CONCURRENT 并行';

COMMENT ON COLUMN pmis_job.consecutive_fail_count IS '连续失败次数: 成功时归零, 失败时+1, 达到 max_consecutive_fails 时自动暂停';

COMMENT ON COLUMN pmis_job.max_consecutive_fails IS '最大连续失败次数: NULL=不熔断, >0 时达到阈值后 status 改为 AUTO_PAUSED';

COMMENT ON COLUMN pmis_job.auto_resume_after_minutes IS '自动恢复时间(分钟): NULL=不自动恢复, >0 时 AUTO_PAUSED 后定时检查恢复';

COMMENT ON COLUMN pmis_job.priority IS '优先级: 1-10, 越小越高(默认 5), 扫描器按优先级排序派发';

COMMENT ON COLUMN pmis_job.version IS '版本号: 每次修改 +1, 用于乐观锁和版本追溯';

COMMENT ON COLUMN pmis_job.timezone IS '任务级时区: 如 Asia/Shanghai / America/New_York / UTC, NULL 使用系统默认时区';

COMMENT ON COLUMN pmis_job.cluster IS '目标集群名称: NULL=本地集群(默认), 非 NULL 时通过 CrossClusterDispatcher 派发到指定集群';

COMMENT ON COLUMN pmis_job.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_job.created_at IS '创建时间';

COMMENT ON COLUMN pmis_job.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_job.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_job.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_job.tenant_id IS '租户 ID(单租户部署默认 1)';

-- [INLINE-OPT] status/group 走部分索引(逻辑删除过滤)
CREATE INDEX IF NOT EXISTS idx_pmis_job_status
    ON pmis_job (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_job_group
    ON pmis_job (job_group) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:按租户 + 创建时间倒序(任务中心列表)
CREATE INDEX IF NOT EXISTS idx_pmis_job_tenant_created
    ON pmis_job (tenant_id, created_at DESC) WHERE deleted = 0;

-- [INLINE-OPT] 下次触发时间:调度器扫描待触发任务
CREATE INDEX IF NOT EXISTS idx_pmis_job_next_fire
    ON pmis_job (next_fire_time) WHERE deleted = 0 AND status = 'NORMAL' AND next_fire_time IS NOT NULL;

-- ============================================================================
-- [P1-3] 调度节点心跳表 pmis_job_node
-- ----------------------------------------------------------------------------
-- 每个 cronjob 实例启动时注册一条记录，定时（默认 10s）更新 last_heartbeat。
-- Leader 节点通过 last_heartbeat 判断节点是否在线，用于任务派发选择执行节点。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_node(
    node_id         VARCHAR(128)  PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    app_name        VARCHAR(128)  NOT NULL,
    host            VARCHAR(128)  NOT NULL,
    port            INTEGER,
    last_heartbeat  TIMESTAMPTZ   NOT NULL,
    status          VARCHAR(32)   NOT NULL DEFAULT 'ONLINE',
    cpu_usage       NUMERIC(5,2),
    mem_usage_pct   NUMERIC(5,2),
    running_count   INTEGER       NOT NULL DEFAULT 0,
    tags            JSONB,
    CONSTRAINT ck_pjn_status_enum CHECK (status IN ('ONLINE', 'OFFLINE', 'DRAINING')),
    CONSTRAINT ck_pjn_cpu_range   CHECK (cpu_usage IS NULL OR (cpu_usage >= 0 AND cpu_usage <= 100)),
    CONSTRAINT ck_pjn_mem_range   CHECK (mem_usage_pct IS NULL OR (mem_usage_pct >= 0 AND mem_usage_pct <= 100)),
    CONSTRAINT ck_pjn_running_nonneg CHECK (running_count >= 0)
);

-- 心跳时间索引：Leader 扫描在线节点
CREATE INDEX IF NOT EXISTS idx_pjn_last_hb ON pmis_job_node(last_heartbeat);

-- 状态索引：筛选 ONLINE 节点
CREATE INDEX IF NOT EXISTS idx_pjn_status ON pmis_job_node(status);

COMMENT ON TABLE pmis_job_node IS '调度节点心跳表（P1-3）';

COMMENT ON COLUMN pmis_job_node.node_id IS '节点 ID（hostname:port 或 hostname:pid）';

COMMENT ON COLUMN pmis_job_node.app_name IS '应用名称';

COMMENT ON COLUMN pmis_job_node.host IS '主机名';

COMMENT ON COLUMN pmis_job_node.port IS '服务端口';

COMMENT ON COLUMN pmis_job_node.last_heartbeat IS '最后心跳时间（Leader 据此判断节点是否在线）';

COMMENT ON COLUMN pmis_job_node.status IS '节点状态: ONLINE 在线 / OFFLINE 离线 / DRAINING 排空退出中';

COMMENT ON COLUMN pmis_job_node.cpu_usage IS 'CPU 使用率（百分比 0-100）';

COMMENT ON COLUMN pmis_job_node.mem_usage_pct IS '内存使用率（百分比 0-100）';

COMMENT ON COLUMN pmis_job_node.running_count IS '当前正在执行的任务数（用于负载均衡选择）';

COMMENT ON COLUMN pmis_job_node.tags IS '节点标签（JSON，用于任务亲和性选择）';

-- 任务执行日志表 pmis_job_log
CREATE TABLE IF NOT EXISTS pmis_job_log(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id          VARCHAR(20)         NOT NULL,
    job_key         VARCHAR(128)   NOT NULL,
    start_time      TIMESTAMPTZ    NOT NULL,
    end_time        TIMESTAMPTZ,
    duration_ms     BIGINT,
    status          VARCHAR(32)    NOT NULL,
    error_message   TEXT,
    params_json     TEXT,
    result_json     TEXT,
    trace_id        VARCHAR(20),
    -- [P2-2] 触发类型: CRON 定时 / MANUAL 手动 / RETRY 重试 / MISFIRED Misfire 触发 / DEPENDENT 依赖触发 / API 接口触发 / FAILOVER 故障转移
    trigger_type    VARCHAR(32)    NOT NULL DEFAULT 'CRON',
    -- [P0-1] 持锁者标识(hostname:pid): 任务派发抢占分布式锁时记录锁的 value,
    --        供 TimeoutMonitor 超时后通过 Lua 脚本安全释放锁(仅当 value 匹配时才 delete)
    lock_holder     VARCHAR(64),
    -- [P0-2] 执行节点 ID(hostname:port): 用于故障转移时定位任务所在节点
    exec_node_id    VARCHAR(64),
    -- [P0-2] 执行线程 ID: 用于超时强制中断时定位执行线程
    exec_thread_id  BIGINT,
    -- [P1-4] 分片索引: 非分片任务为 NULL; 分片任务为 0-based 索引
    --        供 JobNodeReaper 故障转移时重建分片锁 key (pmis:job:lock:{jobKey}:shard:{shardIndex})
    shard_index     INTEGER,
    -- [P1-4] 分片总数: 非分片任务为 NULL; 分片任务为 shardTotal 值
    shard_total     INTEGER,
    -- [INLINE-OPT] P0-D3 内联:MQ 投递元信息字段
    msg_id          VARCHAR(20),
    topic           VARCHAR(128),
    reconsume_times INTEGER        NOT NULL DEFAULT 0,
    -- [INLINE-OPT] 审计字段统一为 created_at,deleted 字段保留以便系统级清理
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    -- 数据完整性约束
    -- [P0-2] status 增加 ZOMBIE: 超时后线程无法中断,标记为僵尸任务由下次扫描清理
    CONSTRAINT ck_pjl_status_enum   CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'TIMEOUT', 'ZOMBIE')),
    CONSTRAINT ck_pjl_duration_nonneg CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_pjl_times_valid   CHECK (end_time IS NULL OR end_time >= start_time),
    CONSTRAINT ck_pjl_reconsume_nonneg CHECK (reconsume_times >= 0),
    CONSTRAINT ck_pjl_trigger_type_enum CHECK (trigger_type IN ('CRON', 'MANUAL', 'RETRY', 'MISFIRED', 'DEPENDENT', 'API', 'FAILOVER')),
    CONSTRAINT ck_pjl_deleted_enum  CHECK (deleted IN (0, 1))
);

-- [INLINE-OPT] 任务日志不需要 tenant_id 维度(系统全局资源)
-- 注:此处不携带 tenant_id,以避免与任务中心分页逻辑耦合

COMMENT ON TABLE pmis_job_log IS '任务执行日志: 每次任务执行的耗时/入参/出参/异常,用于排障与审计';

COMMENT ON COLUMN pmis_job_log.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_log.job_id IS '任务 ID(关联 pmis_job.id)';

COMMENT ON COLUMN pmis_job_log.job_key IS '任务 KEY(冗余,避免连表)';

COMMENT ON COLUMN pmis_job_log.start_time IS '任务开始时间';

COMMENT ON COLUMN pmis_job_log.end_time IS '任务结束时间';

COMMENT ON COLUMN pmis_job_log.duration_ms IS '任务执行耗时(毫秒)';

COMMENT ON COLUMN pmis_job_log.status IS '执行状态: RUNNING 进行中 / SUCCESS 成功 / FAILED 失败 / TIMEOUT 超时';

COMMENT ON COLUMN pmis_job_log.error_message IS '异常堆栈(失败时填充)';

COMMENT ON COLUMN pmis_job_log.params_json IS '执行参数 JSON';

COMMENT ON COLUMN pmis_job_log.result_json IS '执行结果 JSON';

COMMENT ON COLUMN pmis_job_log.trace_id IS '链路追踪 ID(SkyWalking/TLog)';

COMMENT ON COLUMN pmis_job_log.trigger_type IS '触发类型: CRON 定时 / MANUAL 手动 / RETRY 重试 / MISFIRED Misfire 触发 / DEPENDENT 依赖触发 / API 接口触发 / FAILOVER 故障转移';

COMMENT ON COLUMN pmis_job_log.lock_holder IS '持锁者标识(hostname:pid): 分布式锁的 value,超时后通过 Lua 脚本安全释放锁';

COMMENT ON COLUMN pmis_job_log.exec_node_id IS '执行节点 ID(hostname:port): 用于故障转移时定位任务所在节点';

COMMENT ON COLUMN pmis_job_log.exec_thread_id IS '执行线程 ID: 用于超时强制中断时定位执行线程';

COMMENT ON COLUMN pmis_job_log.shard_index IS '分片索引: 非分片任务为 NULL; 分片任务为 0-based, 供故障转移重建分片锁 key';

COMMENT ON COLUMN pmis_job_log.shard_total IS '分片总数: 非分片任务为 NULL; 分片任务为 shardTotal 值';

COMMENT ON COLUMN pmis_job_log.msg_id IS 'RocketMQ 消息 ID(关联 MQ 投递链路)';

COMMENT ON COLUMN pmis_job_log.topic IS 'RocketMQ Topic(标识消息来源 Topic,DLQ 消息填充原 Topic)';

COMMENT ON COLUMN pmis_job_log.reconsume_times IS 'RocketMQ 重试次数(死信消息填充实际重试次数)';

COMMENT ON COLUMN pmis_job_log.created_at IS '日志写入时间';

COMMENT ON COLUMN pmis_job_log.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- [INLINE-OPT] status 走部分索引(高频过滤)
CREATE INDEX IF NOT EXISTS idx_pjl_job_id
    ON pmis_job_log (job_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pjl_job_key
    ON pmis_job_log (job_key) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pjl_status
    ON pmis_job_log (status) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:按 job_id + 开始时间倒序(任务执行历史分页)
CREATE INDEX IF NOT EXISTS idx_pjl_job_start
    ON pmis_job_log (job_id, start_time DESC) WHERE deleted = 0;

-- [INLINE-OPT] 链路追踪 ID 索引(分布式排障)
CREATE INDEX IF NOT EXISTS idx_pjl_trace_id
    ON pmis_job_log (trace_id) WHERE deleted = 0 AND trace_id IS NOT NULL;

-- ============================================================================
-- [P0-2] 任务执行日志明细表 pmis_job_log_content
-- ----------------------------------------------------------------------------
-- 存储任务执行过程中业务侧通过 JobLogger 写入的逐行日志，
-- 供前端 SSE 实时滚动展示（在线日志白屏化）。
-- 与 pmis_job_log（执行级汇总）互补，本表为行级明细。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_log_content(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    log_id          VARCHAR(20)         NOT NULL,
    job_key         VARCHAR(128)   NOT NULL,
    line_no         INTEGER        NOT NULL,
    log_level       VARCHAR(16)    NOT NULL DEFAULT 'INFO',
    content         VARCHAR(4000)  NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pjlc_level_enum CHECK (log_level IN ('DEBUG', 'INFO', 'WARN', 'ERROR')),
    CONSTRAINT ck_pjlc_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_log_content IS '任务执行日志明细: 业务侧通过 JobLogger 写入的逐行日志, 供前端 SSE 实时滚动展示';

COMMENT ON COLUMN pmis_job_log_content.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_log_content.log_id IS '执行日志 ID(关联 pmis_job_log.id)';

COMMENT ON COLUMN pmis_job_log_content.job_key IS '任务 KEY(冗余, 避免连表)';

COMMENT ON COLUMN pmis_job_log_content.line_no IS '行号(从 1 递增)';

COMMENT ON COLUMN pmis_job_log_content.log_level IS '日志级别: DEBUG / INFO / WARN / ERROR';

COMMENT ON COLUMN pmis_job_log_content.content IS '日志内容(单行, 最长 4000 字符)';

COMMENT ON COLUMN pmis_job_log_content.created_at IS '写入时间';

COMMENT ON COLUMN pmis_job_log_content.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX IF NOT EXISTS idx_pjlc_log_id
    ON pmis_job_log_content (log_id, line_no) WHERE deleted = 0;

-- ============================================================================
-- [P0-4] MapReduce 子任务表 pmis_job_task
-- ----------------------------------------------------------------------------
-- 存储动态产生的子任务，一个 JobInstance（logId）对应多个子任务。
-- 由 MapTaskExecutor 管理：root task 调用 map() 产生子任务，框架执行后记录结果。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_task(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id          VARCHAR(20)         NOT NULL,
    log_id          VARCHAR(20)         NOT NULL,
    job_key         VARCHAR(128)   NOT NULL,
    task_name       VARCHAR(128)   NOT NULL,
    task_params     TEXT,
    task_type       VARCHAR(16)    NOT NULL DEFAULT 'SUB_TASK',
    status          VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    result          TEXT,
    error_message   TEXT,
    exec_node_id    VARCHAR(64),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pjt_type_enum CHECK (task_type IN ('ROOT', 'SUB_TASK')),
    CONSTRAINT ck_pjt_status_enum CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_pjt_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_task IS 'MapReduce 子任务表: 动态产生的子任务及其执行结果';

COMMENT ON COLUMN pmis_job_task.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_task.job_id IS '任务 ID';

COMMENT ON COLUMN pmis_job_task.log_id IS '执行日志 ID(关联 pmis_job_log.id)';

COMMENT ON COLUMN pmis_job_task.job_key IS '任务 KEY(冗余)';

COMMENT ON COLUMN pmis_job_task.task_name IS '子任务名称';

COMMENT ON COLUMN pmis_job_task.task_params IS '子任务参数 JSON';

COMMENT ON COLUMN pmis_job_task.task_type IS '子任务类型: ROOT 根任务 / SUB_TASK 子任务';

COMMENT ON COLUMN pmis_job_task.status IS '执行状态: PENDING / RUNNING / SUCCESS / FAILED';

COMMENT ON COLUMN pmis_job_task.result IS '执行结果 JSON';

COMMENT ON COLUMN pmis_job_task.error_message IS '错误信息';

COMMENT ON COLUMN pmis_job_task.exec_node_id IS '执行节点 ID';

COMMENT ON COLUMN pmis_job_task.created_at IS '创建时间';

COMMENT ON COLUMN pmis_job_task.updated_at IS '更新时间';

COMMENT ON COLUMN pmis_job_task.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX IF NOT EXISTS idx_pjt_log_id
    ON pmis_job_task (log_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pjt_status
    ON pmis_job_task (status) WHERE deleted = 0;

-- ============================================================================
-- [P1-2] GLUE 在线编码表 pmis_job_glue
-- ----------------------------------------------------------------------------
-- 存储 GLUE 类型任务的在线代码，支持版本管理和回滚。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_glue(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id          VARCHAR(20)         NOT NULL,
    source_code     TEXT           NOT NULL,
    language        VARCHAR(16)    NOT NULL DEFAULT 'GROOVY',
    version         INTEGER        NOT NULL DEFAULT 1,
    remark          VARCHAR(512),
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pjg_language_enum CHECK (language IN ('GROOVY', 'JAVA')),
    CONSTRAINT ck_pjg_version_pos CHECK (version >= 1),
    CONSTRAINT ck_pjg_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_glue IS 'GLUE 在线编码表: 存储在线编辑的代码及版本历史';

COMMENT ON COLUMN pmis_job_glue.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_glue.job_id IS '任务 ID';

COMMENT ON COLUMN pmis_job_glue.source_code IS '源代码';

COMMENT ON COLUMN pmis_job_glue.language IS '语言: GROOVY(默认) / JAVA';

COMMENT ON COLUMN pmis_job_glue.version IS '版本号(从 1 递增)';

COMMENT ON COLUMN pmis_job_glue.remark IS '版本备注';

COMMENT ON COLUMN pmis_job_glue.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_job_glue.created_at IS '创建时间';

COMMENT ON COLUMN pmis_job_glue.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX IF NOT EXISTS idx_pjg_job_id
    ON pmis_job_glue (job_id, version DESC) WHERE deleted = 0;

-- ============================================================================
-- [P1-6] 任务配置历史版本表 pmis_job_history
-- ----------------------------------------------------------------------------
-- 每次任务配置更新时自动保存历史快照，支持版本对比和一键回滚。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_history(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id          VARCHAR(20)         NOT NULL,
    version         INTEGER        NOT NULL,
    snapshot        TEXT           NOT NULL,
    job_name        VARCHAR(128),
    job_key         VARCHAR(128)   NOT NULL,
    handler         VARCHAR(256),
    cron_expression VARCHAR(128),
    params_json     TEXT,
    remark          VARCHAR(512),
    changed_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    changed_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pjh_version_pos CHECK (version >= 1),
    CONSTRAINT ck_pjh_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_history IS '任务配置历史版本表: 每次更新自动保存快照, 支持回滚';

COMMENT ON COLUMN pmis_job_history.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_history.job_id IS '任务 ID';

COMMENT ON COLUMN pmis_job_history.version IS '版本号(对应更新前的 job.version)';

COMMENT ON COLUMN pmis_job_history.snapshot IS '完整 JobDO JSON 快照';

COMMENT ON COLUMN pmis_job_history.job_name IS '任务名称(冗余, 便于列表展示)';

COMMENT ON COLUMN pmis_job_history.job_key IS '任务 KEY(冗余)';

COMMENT ON COLUMN pmis_job_history.handler IS '处理器(冗余)';

COMMENT ON COLUMN pmis_job_history.cron_expression IS 'Cron 表达式(冗余)';

COMMENT ON COLUMN pmis_job_history.params_json IS '参数 JSON(冗余)';

COMMENT ON COLUMN pmis_job_history.remark IS '备注(冗余)';

COMMENT ON COLUMN pmis_job_history.changed_by IS '修改人 ID';

COMMENT ON COLUMN pmis_job_history.changed_at IS '修改时间';

COMMENT ON COLUMN pmis_job_history.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX IF NOT EXISTS idx_pjh_job_id
    ON pmis_job_history (job_id, version DESC) WHERE deleted = 0;

-- ============================================================================
-- [P6-3] 慢任务诊断日志表 pmis_job_slow_log
-- ----------------------------------------------------------------------------
-- 当任务执行耗时超过 pmis_job.slow_threshold_ms 时，自动记录到本表。
-- 与 pmis_job_log 的区别：
--   - job_log 记录全部执行（RUNNING/SUCCESS/FAILED/TIMEOUT），用于审计
--   - slow_log 仅记录慢执行，用于性能趋势分析与优化决策
-- 由 SlowTaskDetector 定期扫描 job_log 并写入，不影响任务执行主流程。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_slow_log(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id            VARCHAR(20)         NOT NULL,
    job_key           VARCHAR(128)   NOT NULL,
    log_id            VARCHAR(20)         NOT NULL,
    duration_ms       BIGINT         NOT NULL,
    slow_threshold_ms BIGINT         NOT NULL,
    params_json       TEXT,
    error_message     TEXT,
    trace_id          VARCHAR(20),
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pjsl_duration_pos    CHECK (duration_ms > 0),
    CONSTRAINT ck_pjsl_threshold_pos   CHECK (slow_threshold_ms > 0),
    CONSTRAINT ck_pjsl_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_slow_log IS '慢任务诊断日志（P6-3）: 仅记录执行耗时超过 slow_threshold_ms 的任务，用于性能分析';

COMMENT ON COLUMN pmis_job_slow_log.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_slow_log.job_id IS '任务 ID（关联 pmis_job.id）';

COMMENT ON COLUMN pmis_job_slow_log.job_key IS '任务 KEY（冗余,避免连表）';

COMMENT ON COLUMN pmis_job_slow_log.log_id IS '关联 pmis_job_log.id（原始终端执行日志）';

COMMENT ON COLUMN pmis_job_slow_log.duration_ms IS '本次执行耗时（毫秒）';

COMMENT ON COLUMN pmis_job_slow_log.slow_threshold_ms IS '慢任务阈值（毫秒，来自 pmis_job.slow_threshold_ms）';

COMMENT ON COLUMN pmis_job_slow_log.params_json IS '执行参数 JSON（冗余自 job_log,便于独立分析）';

COMMENT ON COLUMN pmis_job_slow_log.error_message IS '异常信息（如慢且有异常,冗余自 job_log）';

COMMENT ON COLUMN pmis_job_slow_log.trace_id IS '链路追踪 ID（关联分布式链路）';

COMMENT ON COLUMN pmis_job_slow_log.tenant_id IS '租户 ID（单租户部署默认 1）';

COMMENT ON COLUMN pmis_job_slow_log.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_job_slow_log.created_at IS '记录时间';

COMMENT ON COLUMN pmis_job_slow_log.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_job_slow_log.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_job_slow_log.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- [INLINE-OPT] job_id 索引（按任务查慢日志）
CREATE INDEX IF NOT EXISTS idx_pjsl_job_id
    ON pmis_job_slow_log (job_id) WHERE deleted = 0;

-- [INLINE-OPT] 创建时间索引（按时间范围查慢日志趋势）
CREATE INDEX IF NOT EXISTS idx_pjsl_created
    ON pmis_job_slow_log (created_at DESC) WHERE deleted = 0;

-- ============================================================================
-- [P4-1] 任务依赖关系表 pmis_job_relation
-- ----------------------------------------------------------------------------
-- 存储 DAG 工作流中任务之间的依赖边（parent_job → child_job）。
-- 当 parent_job 执行成功后，根据 fail_strategy 决定是否触发 child_job。
-- 对标 XXL-Job 子任务依赖 / PowerJob 工作流实例。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_relation(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    parent_job_id   VARCHAR(20)      NOT NULL,
    child_job_id    VARCHAR(20)      NOT NULL,
    -- [P4-3] 失败传播策略: FAIL_FAST 前置失败不触发后继(默认) / CONTINUE_ON_FAIL 前置失败仍触发
    fail_strategy   VARCHAR(32)    NOT NULL DEFAULT 'FAIL_FAST',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_pmis_job_relation UNIQUE (parent_job_id, child_job_id, deleted),
    CONSTRAINT ck_pjr_fail_strategy  CHECK (fail_strategy IN ('FAIL_FAST', 'CONTINUE_ON_FAIL')),
    CONSTRAINT ck_pjr_no_self_ref   CHECK (parent_job_id != child_job_id),
    CONSTRAINT ck_pjr_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_relation IS '任务依赖关系表（P4 DAG 工作流）';

COMMENT ON COLUMN pmis_job_relation.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_relation.parent_job_id IS '前置任务 ID（执行成功后触发后继）';

COMMENT ON COLUMN pmis_job_relation.child_job_id IS '后继任务 ID（被前置任务触发）';

COMMENT ON COLUMN pmis_job_relation.fail_strategy IS '失败传播策略: FAIL_FAST 前置失败不触发后继(默认) / CONTINUE_ON_FAIL 前置失败仍触发后继';

COMMENT ON COLUMN pmis_job_relation.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_job_relation.created_at IS '创建时间';

COMMENT ON COLUMN pmis_job_relation.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_job_relation.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_job_relation.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- 索引: 按前置任务查询后继列表（高频，任务成功后触发）
CREATE INDEX IF NOT EXISTS idx_pjr_parent
    ON pmis_job_relation (parent_job_id) WHERE deleted = 0;

-- 索引: 按后继任务查询前置列表（DAG 解析时使用）
CREATE INDEX IF NOT EXISTS idx_pjr_child
    ON pmis_job_relation (child_job_id) WHERE deleted = 0;

-- ============================================================================
-- [P2-1] DAG 工作流定义表 pmis_job_dag
-- ----------------------------------------------------------------------------
-- 将 DAG 提升为一等公民：一个 DAG 定义包含若干任务节点和依赖边，
-- 支持手动触发或 Cron 定时触发整个工作流。
-- dag_definition(JSON) 存储节点与边及前端可视化位置信息，格式：
-- {
--   "nodes": [{"jobKey":"a","jobId":"1","label":"抽取","x":100,"y":200,"paramsJson":"{}"}],
--   "edges": [{"from":"a","to":"b","failStrategy":"FAIL_FAST","condition":null}]
-- }
-- 对标 PowerJob workflow / XXL-Job 子任务链。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_dag(
    id                      VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    dag_key                 VARCHAR(128)  NOT NULL,
    dag_name                VARCHAR(128)  NOT NULL,
    dag_definition          TEXT          NOT NULL,
    -- DAG 状态: DRAFT 草稿 / ENABLED 启用 / DISABLED 禁用
    status                  VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    -- 触发类型: MANUAL 手动 / CRON 定时
    trigger_type            VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    cron_expression         VARCHAR(128),
    -- 最大并发实例数: 0=不限制(默认 1)，防止同一 DAG 并发执行过多
    max_concurrent_instances INTEGER      NOT NULL DEFAULT 1,
    -- DAG 级失败策略: FAIL_FAST 任一节点失败则中止整个 DAG / CONTINUE_ON_FAIL 继续执行无关分支
    fail_strategy           VARCHAR(32)   NOT NULL DEFAULT 'FAIL_FAST',
    description             VARCHAR(512),
    next_fire_time          TIMESTAMPTZ,
    last_fire_time          TIMESTAMPTZ,
    fire_count              BIGINT        NOT NULL DEFAULT 0,
    success_count           BIGINT        NOT NULL DEFAULT 0,
    fail_count              BIGINT        NOT NULL DEFAULT 0,
    -- 版本号: 每次修改 +1，用于乐观锁
    version                 INTEGER       NOT NULL DEFAULT 1,
    created_by              VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT      NOT NULL DEFAULT 0,
    tenant_id               VARCHAR(20)       NOT NULL DEFAULT '1',
    -- 数据完整性约束
    CONSTRAINT uk_pmis_job_dag_key UNIQUE (dag_key, deleted),
    CONSTRAINT ck_pjd_status_enum  CHECK (status IN ('DRAFT', 'ENABLED', 'DISABLED')),
    CONSTRAINT ck_pjd_trigger_enum CHECK (trigger_type IN ('MANUAL', 'CRON')),
    CONSTRAINT ck_pjd_fail_strategy_enum CHECK (fail_strategy IN ('FAIL_FAST', 'CONTINUE_ON_FAIL', 'RETRY', 'SKIP_SUBSEQUENT')),
    CONSTRAINT ck_pjd_max_concurrent_nonneg CHECK (max_concurrent_instances >= 0),
    CONSTRAINT ck_pjd_counts_nonneg CHECK (fire_count >= 0 AND success_count >= 0 AND fail_count >= 0),
    CONSTRAINT ck_pjd_version_pos  CHECK (version >= 1),
    CONSTRAINT ck_pjd_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_dag IS 'DAG 工作流定义表（P2 DAG 增强）';

COMMENT ON COLUMN pmis_job_dag.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_dag.dag_key IS 'DAG 唯一 KEY（调度与触发使用）';

COMMENT ON COLUMN pmis_job_dag.dag_name IS 'DAG 名称（展示用）';

COMMENT ON COLUMN pmis_job_dag.dag_definition IS 'DAG 定义 JSON（nodes + edges + 可视化坐标）';

COMMENT ON COLUMN pmis_job_dag.status IS 'DAG 状态: DRAFT 草稿 / ENABLED 启用 / DISABLED 禁用';

COMMENT ON COLUMN pmis_job_dag.trigger_type IS '触发类型: MANUAL 手动 / CRON 定时';

COMMENT ON COLUMN pmis_job_dag.cron_expression IS 'Cron 表达式（trigger_type=CRON 时必填）';

COMMENT ON COLUMN pmis_job_dag.max_concurrent_instances IS '最大并发实例数(0=不限制, 默认1)';

COMMENT ON COLUMN pmis_job_dag.fail_strategy IS 'DAG 级失败策略: FAIL_FAST 中止 / CONTINUE_ON_FAIL 继续';

COMMENT ON COLUMN pmis_job_dag.description IS 'DAG 描述';

COMMENT ON COLUMN pmis_job_dag.next_fire_time IS '下次触发时间（CRON 模式）';

COMMENT ON COLUMN pmis_job_dag.last_fire_time IS '上次触发时间';

COMMENT ON COLUMN pmis_job_dag.fire_count IS '总触发次数';

COMMENT ON COLUMN pmis_job_dag.success_count IS '成功次数';

COMMENT ON COLUMN pmis_job_dag.fail_count IS '失败次数';

COMMENT ON COLUMN pmis_job_dag.version IS '版本号(乐观锁)';

COMMENT ON COLUMN pmis_job_dag.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_pjd_status
    ON pmis_job_dag (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pjd_tenant
    ON pmis_job_dag (tenant_id) WHERE deleted = 0;

-- ============================================================================
-- [P2-2] DAG 工作流实例表 pmis_job_dag_instance
-- ----------------------------------------------------------------------------
-- 记录每次 DAG 执行的整体状态，对标 PowerJob workflow_instance。
-- 一个 DAG 定义可对应多次实例（每次触发/每次定时调度）。
-- context_json 存储 DAG 实例级上下文，支持跨节点传参。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_dag_instance(
    id                      VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    dag_id                  VARCHAR(20)   NOT NULL,
    dag_key                 VARCHAR(128)  NOT NULL,
    -- 实例状态: PENDING 待执行 / RUNNING 执行中 / SUCCESS 成功 / FAILED 失败
    --          PARTIAL_SUCCESS 部分成功 / PAUSED 暂停 / CANCELED 取消
    status                  VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    trigger_type            VARCHAR(32)   NOT NULL,
    trigger_by              VARCHAR(20),
    trigger_trace_id        VARCHAR(64),
    -- DAG 实例级上下文 JSON：上游节点输出可写入此上下文，下游节点读取
    context_json            TEXT,
    started_at              TIMESTAMPTZ,
    finished_at             TIMESTAMPTZ,
    duration_ms             BIGINT,
    error_message           VARCHAR(1024),
    -- 节点统计: 便于前端展示进度
    total_nodes             INTEGER       NOT NULL DEFAULT 0,
    success_nodes           INTEGER       NOT NULL DEFAULT 0,
    failed_nodes            INTEGER       NOT NULL DEFAULT 0,
    skipped_nodes           INTEGER       NOT NULL DEFAULT 0,
    created_by              VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT      NOT NULL DEFAULT 0,
    tenant_id               VARCHAR(20)       NOT NULL DEFAULT '1',
    -- 数据完整性约束
    CONSTRAINT ck_pjdi_status_enum CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL_SUCCESS', 'PAUSED', 'CANCELED')),
    CONSTRAINT ck_pjdi_trigger_enum CHECK (trigger_type IN ('MANUAL', 'CRON', 'DEPENDENT')),
    CONSTRAINT ck_pjdi_counts_nonneg CHECK (total_nodes >= 0 AND success_nodes >= 0 AND failed_nodes >= 0 AND skipped_nodes >= 0),
    CONSTRAINT ck_pjdi_nodes_consistent CHECK (success_nodes + failed_nodes + skipped_nodes <= total_nodes),
    CONSTRAINT ck_pjdi_duration_nonneg CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_pjdi_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_dag_instance IS 'DAG 工作流实例表（P2 DAG 增强）';

COMMENT ON COLUMN pmis_job_dag_instance.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_dag_instance.dag_id IS 'DAG 定义 ID';

COMMENT ON COLUMN pmis_job_dag_instance.dag_key IS 'DAG KEY（冗余，便于查询）';

COMMENT ON COLUMN pmis_job_dag_instance.status IS '实例状态: PENDING/RUNNING/SUCCESS/FAILED/PARTIAL_SUCCESS/PAUSED/CANCELED';

COMMENT ON COLUMN pmis_job_dag_instance.trigger_type IS '触发类型: MANUAL/CRON/DEPENDENT';

COMMENT ON COLUMN pmis_job_dag_instance.trigger_by IS '触发人（MANUAL 时为用户 ID）';

COMMENT ON COLUMN pmis_job_dag_instance.trigger_trace_id IS '触发 traceId（用于链路追踪）';

COMMENT ON COLUMN pmis_job_dag_instance.context_json IS 'DAG 实例级上下文 JSON（跨节点传参）';

COMMENT ON COLUMN pmis_job_dag_instance.started_at IS '开始时间';

COMMENT ON COLUMN pmis_job_dag_instance.finished_at IS '结束时间';

COMMENT ON COLUMN pmis_job_dag_instance.duration_ms IS '执行耗时（毫秒）';

COMMENT ON COLUMN pmis_job_dag_instance.error_message IS '错误信息（FAILED 时填充）';

COMMENT ON COLUMN pmis_job_dag_instance.total_nodes IS '总节点数';

COMMENT ON COLUMN pmis_job_dag_instance.success_nodes IS '成功节点数';

COMMENT ON COLUMN pmis_job_dag_instance.failed_nodes IS '失败节点数';

COMMENT ON COLUMN pmis_job_dag_instance.skipped_nodes IS '跳过节点数';

COMMENT ON COLUMN pmis_job_dag_instance.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_pjdi_dag_id
    ON pmis_job_dag_instance (dag_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pjdi_status
    ON pmis_job_dag_instance (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pjdi_created_at
    ON pmis_job_dag_instance (created_at DESC) WHERE deleted = 0;

-- ============================================================================
-- [P2-3] DAG 节点实例表 pmis_job_dag_node_instance
-- ----------------------------------------------------------------------------
-- 记录 DAG 实例中每个任务节点的执行状态，对标 PowerJob node_instance。
-- 一个 DAG 实例包含若干节点实例，每个节点实例关联一个任务执行日志（pmis_job_log）。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_dag_node_instance(
    id                      VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    dag_instance_id         VARCHAR(20)   NOT NULL,
    dag_id                  VARCHAR(20)   NOT NULL,
    job_id                  VARCHAR(20)   NOT NULL,
    job_key                 VARCHAR(128)  NOT NULL,
    -- 节点状态: PENDING 待执行 / RUNNING 执行中 / SUCCESS 成功 / FAILED 失败 / SKIPPED 跳过 / RETRYING 重试中
    node_status             VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    -- 关联的任务执行日志 ID（pmis_job_log.id）
    log_id                  VARCHAR(20),
    -- 节点级重试次数（独立于任务级 maxRetries，由 DAG 失败策略控制）
    retry_count             INTEGER       NOT NULL DEFAULT 0,
    max_retries             INTEGER       NOT NULL DEFAULT 0,
    started_at              TIMESTAMPTZ,
    finished_at             TIMESTAMPTZ,
    duration_ms             BIGINT,
    result_json             TEXT,
    error_message           VARCHAR(1024),
    created_by              VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT      NOT NULL DEFAULT 0,
    tenant_id               VARCHAR(20)       NOT NULL DEFAULT '1',
    -- 数据完整性约束
    CONSTRAINT ck_pjdni_status_enum CHECK (node_status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED', 'RETRYING')),
    CONSTRAINT ck_pjdni_retry_nonneg CHECK (retry_count >= 0 AND max_retries >= 0),
    CONSTRAINT ck_pjdni_duration_nonneg CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_pjdni_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_dag_node_instance IS 'DAG 节点实例表（P2 DAG 增强）';

COMMENT ON COLUMN pmis_job_dag_node_instance.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_dag_node_instance.dag_instance_id IS 'DAG 实例 ID';

COMMENT ON COLUMN pmis_job_dag_node_instance.dag_id IS 'DAG 定义 ID';

COMMENT ON COLUMN pmis_job_dag_node_instance.job_id IS '任务 ID';

COMMENT ON COLUMN pmis_job_dag_node_instance.job_key IS '任务 KEY（冗余）';

COMMENT ON COLUMN pmis_job_dag_node_instance.node_status IS '节点状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/RETRYING';

COMMENT ON COLUMN pmis_job_dag_node_instance.log_id IS '关联的任务执行日志 ID（pmis_job_log.id）';

COMMENT ON COLUMN pmis_job_dag_node_instance.retry_count IS '节点级重试次数';

COMMENT ON COLUMN pmis_job_dag_node_instance.max_retries IS '节点级最大重试次数';

COMMENT ON COLUMN pmis_job_dag_node_instance.started_at IS '节点开始时间';

COMMENT ON COLUMN pmis_job_dag_node_instance.finished_at IS '节点结束时间';

COMMENT ON COLUMN pmis_job_dag_node_instance.duration_ms IS '节点执行耗时（毫秒）';

COMMENT ON COLUMN pmis_job_dag_node_instance.result_json IS '节点执行结果 JSON';

COMMENT ON COLUMN pmis_job_dag_node_instance.error_message IS '节点错误信息';

COMMENT ON COLUMN pmis_job_dag_node_instance.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_pjdni_dag_instance
    ON pmis_job_dag_node_instance (dag_instance_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pjdni_job_id
    ON pmis_job_dag_node_instance (job_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pjdni_status
    ON pmis_job_dag_node_instance (node_status) WHERE deleted = 0;

-- ============================================================================
-- [P5-1] 任务告警规则表 pmis_job_alert_rule
-- ----------------------------------------------------------------------------
-- 定义告警触发条件、级别、通知通道与去重策略。
-- 规则可绑定到具体任务（job_id 非空），也可作为全局规则（job_id 为 NULL）应用于所有任务。
-- 对标 XXL-Job 告警中心 / PowerJob 告警配置。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_alert_rule(
    id                    VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    rule_name             VARCHAR(128)  NOT NULL,
    job_id                VARCHAR(20),
    job_key               VARCHAR(128),
    -- [P5-2] 告警类型: FAIL 任务失败 / TIMEOUT 任务超时 / SLOW 任务慢 / FAIL_RATE 失败率 / DURATION_P95 P95耗时
    alert_type            VARCHAR(32)   NOT NULL,
    -- [P5-2] 告警级别: INFO 提示 / WARN 警告 / ERROR 错误 / CRITICAL 严重
    alert_level           VARCHAR(32)   NOT NULL DEFAULT 'WARN',
    -- 阈值: 按 alert_type 解释 (FAIL_RATE 百分比 0-100 / SLOW+DURATION_P95 毫秒数)
    threshold             BIGINT,
    -- 统计时间窗口 (分钟), 仅 FAIL_RATE / DURATION_P95 生效
    time_window_minutes   INTEGER,
    -- 通知通道 (JSON 数组: ["EMAIL","DINGTALK","WECOM","WEBHOOK"])
    channels              TEXT          NOT NULL,
    -- 接收人 (JSON 数组: 邮箱/手机号/userId 列表)
    receivers             TEXT,
    -- 冷却时间 (分钟), 同一规则在冷却期内不重复告警
    cooldown_minutes      INTEGER       NOT NULL DEFAULT 10,
    -- 是否启用: 0 禁用 / 1 启用
    enabled               SMALLINT      NOT NULL DEFAULT 1,
    -- 最后告警时间 (用于冷却判断)
    last_alert_at         TIMESTAMPTZ,
    created_by            VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT      NOT NULL DEFAULT 0,
    tenant_id             VARCHAR(20)         NOT NULL DEFAULT '1',
    -- 数据完整性约束
    CONSTRAINT ck_pjar_alert_type_enum   CHECK (alert_type IN ('FAIL', 'TIMEOUT', 'SLOW', 'FAIL_RATE', 'DURATION_P95')),
    CONSTRAINT ck_pjar_alert_level_enum  CHECK (alert_level IN ('INFO', 'WARN', 'ERROR', 'CRITICAL')),
    CONSTRAINT ck_pjar_threshold_nonneg  CHECK (threshold IS NULL OR threshold >= 0),
    CONSTRAINT ck_pjar_window_nonneg     CHECK (time_window_minutes IS NULL OR time_window_minutes > 0),
    CONSTRAINT ck_pjar_cooldown_nonneg   CHECK (cooldown_minutes >= 0),
    CONSTRAINT ck_pjar_enabled_enum      CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_pjar_deleted_enum      CHECK (deleted IN (0, 1)),
    -- [P5-2] 阈值约束: FAIL_RATE / SLOW / DURATION_P95 必须配置阈值
    CONSTRAINT ck_pjar_threshold_required CHECK (
        alert_type IN ('FAIL', 'TIMEOUT') OR threshold IS NOT NULL
    ),
    -- [P5-2] 时间窗口约束: FAIL_RATE / DURATION_P95 必须配置时间窗口
    CONSTRAINT ck_pjar_window_required   CHECK (
        alert_type NOT IN ('FAIL_RATE', 'DURATION_P95') OR time_window_minutes IS NOT NULL
    )
);

COMMENT ON TABLE pmis_job_alert_rule IS '任务告警规则表（P5 告警 + 监控）';

COMMENT ON COLUMN pmis_job_alert_rule.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_alert_rule.rule_name IS '规则名称（展示用）';

COMMENT ON COLUMN pmis_job_alert_rule.job_id IS '关联任务 ID（NULL 表示全局规则）';

COMMENT ON COLUMN pmis_job_alert_rule.job_key IS '任务 KEY 冗余（NULL 表示全局规则）';

COMMENT ON COLUMN pmis_job_alert_rule.alert_type IS '告警类型: FAIL 任务失败 / TIMEOUT 任务超时 / SLOW 任务慢 / FAIL_RATE 失败率 / DURATION_P95 P95耗时';

COMMENT ON COLUMN pmis_job_alert_rule.alert_level IS '告警级别: INFO 提示 / WARN 警告 / ERROR 错误 / CRITICAL 严重';

COMMENT ON COLUMN pmis_job_alert_rule.threshold IS '阈值（按 alert_type 解释: FAIL_RATE 百分比 0-100 / SLOW+DURATION_P95 毫秒数）';

COMMENT ON COLUMN pmis_job_alert_rule.time_window_minutes IS '统计时间窗口（分钟），仅 FAIL_RATE / DURATION_P95 生效';

COMMENT ON COLUMN pmis_job_alert_rule.channels IS '通知通道（JSON 数组: ["EMAIL","DINGTALK","WECOM","WEBHOOK"]）';

COMMENT ON COLUMN pmis_job_alert_rule.receivers IS '接收人（JSON 数组: 邮箱/手机号/userId 列表）';

COMMENT ON COLUMN pmis_job_alert_rule.cooldown_minutes IS '冷却时间（分钟），同一规则在冷却期内不重复告警';

COMMENT ON COLUMN pmis_job_alert_rule.enabled IS '是否启用: 0 禁用 / 1 启用';

COMMENT ON COLUMN pmis_job_alert_rule.last_alert_at IS '最后告警时间（用于冷却判断，CAS 更新）';

COMMENT ON COLUMN pmis_job_alert_rule.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_job_alert_rule.created_at IS '创建时间';

COMMENT ON COLUMN pmis_job_alert_rule.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_job_alert_rule.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_job_alert_rule.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_job_alert_rule.tenant_id IS '租户 ID（单租户部署默认 1）';

-- 索引: 按 job_id 查询任务专属规则（任务告警触发时使用）
CREATE INDEX IF NOT EXISTS idx_pjar_job_id
    ON pmis_job_alert_rule (job_id) WHERE deleted = 0 AND enabled = 1;

-- 索引: 按告警类型筛选（批量加载同类规则）
CREATE INDEX IF NOT EXISTS idx_pjar_alert_type
    ON pmis_job_alert_rule (alert_type) WHERE deleted = 0 AND enabled = 1;

-- 索引: 按启用状态加载全部启用规则（启动时缓存）
CREATE INDEX IF NOT EXISTS idx_pjar_enabled
    ON pmis_job_alert_rule (enabled) WHERE deleted = 0;

-- 索引: 按租户 + 创建时间倒序（告警中心列表）
CREATE INDEX IF NOT EXISTS idx_pjar_tenant_created
    ON pmis_job_alert_rule (tenant_id, created_at DESC) WHERE deleted = 0;

-- ============================================================================
-- [P5-1] 任务告警日志表 pmis_job_alert_log
-- ----------------------------------------------------------------------------
-- 记录每次告警派发的实际情况，用于审计、去重判断和告警效果统计。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_alert_log(
    id                    VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    rule_id               VARCHAR(20)   NOT NULL,
    rule_name             VARCHAR(128)  NOT NULL,
    job_id                VARCHAR(20),
    job_key               VARCHAR(128),
    alert_type            VARCHAR(32)   NOT NULL,
    alert_level           VARCHAR(32)   NOT NULL,
    -- 触发时的实际值（如失败率 85.5、耗时 5000）
    trigger_value         VARCHAR(64),
    threshold             BIGINT,
    -- 实际发送通道（JSON 数组）
    channels              TEXT          NOT NULL,
    -- 告警状态: PENDING 派发中 / SUCCESS 全部成功 / PARTIAL 部分成功 / FAILED 全部失败
    status                VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    error_message         TEXT,
    trace_id              VARCHAR(20),
    -- 触发该告警的任务日志 ID（关联 pmis_job_log.id）
    trigger_log_id        VARCHAR(20),
    created_by            VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT      NOT NULL DEFAULT 0,
    tenant_id             VARCHAR(20)         NOT NULL DEFAULT '1',
    -- 数据完整性约束
    CONSTRAINT ck_pjal_alert_type_enum   CHECK (alert_type IN ('FAIL', 'TIMEOUT', 'SLOW', 'FAIL_RATE', 'DURATION_P95')),
    CONSTRAINT ck_pjal_alert_level_enum  CHECK (alert_level IN ('INFO', 'WARN', 'ERROR', 'CRITICAL')),
    CONSTRAINT ck_pjal_threshold_nonneg  CHECK (threshold IS NULL OR threshold >= 0),
    CONSTRAINT ck_pjal_status_enum       CHECK (status IN ('PENDING', 'SUCCESS', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_pjal_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_alert_log IS '任务告警日志表（P5 告警 + 监控）';

COMMENT ON COLUMN pmis_job_alert_log.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_alert_log.rule_id IS '告警规则 ID（关联 pmis_job_alert_rule.id）';

COMMENT ON COLUMN pmis_job_alert_log.rule_name IS '规则名称（冗余，避免连表）';

COMMENT ON COLUMN pmis_job_alert_log.job_id IS '任务 ID（NULL 表示全局告警）';

COMMENT ON COLUMN pmis_job_alert_log.job_key IS '任务 KEY（冗余）';

COMMENT ON COLUMN pmis_job_alert_log.alert_type IS '告警类型: FAIL / TIMEOUT / SLOW / FAIL_RATE / DURATION_P95';

COMMENT ON COLUMN pmis_job_alert_log.alert_level IS '告警级别: INFO / WARN / ERROR / CRITICAL';

COMMENT ON COLUMN pmis_job_alert_log.trigger_value IS '触发时的实际值（如失败率 85.5、耗时 5000）';

COMMENT ON COLUMN pmis_job_alert_log.threshold IS '规则阈值（冗余）';

COMMENT ON COLUMN pmis_job_alert_log.channels IS '实际发送通道（JSON 数组）';

COMMENT ON COLUMN pmis_job_alert_log.status IS '告警状态: PENDING 派发中 / SUCCESS 全部成功 / PARTIAL 部分成功 / FAILED 全部失败';

COMMENT ON COLUMN pmis_job_alert_log.error_message IS '错误信息（部分通道失败时记录）';

COMMENT ON COLUMN pmis_job_alert_log.trace_id IS '链路追踪 ID（分布式排障）';

COMMENT ON COLUMN pmis_job_alert_log.trigger_log_id IS '触发该告警的任务日志 ID（关联 pmis_job_log.id）';

COMMENT ON COLUMN pmis_job_alert_log.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_job_alert_log.created_at IS '告警发送时间';

COMMENT ON COLUMN pmis_job_alert_log.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_job_alert_log.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_job_alert_log.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_job_alert_log.tenant_id IS '租户 ID';

-- 索引: 按规则 ID 查询去重判断（冷却期内是否已告警）
CREATE INDEX IF NOT EXISTS idx_pjal_rule_id
    ON pmis_job_alert_log (rule_id, created_at DESC) WHERE deleted = 0;

-- 索引: 按任务 ID 查询告警历史（任务详情页）
CREATE INDEX IF NOT EXISTS idx_pjal_job_id
    ON pmis_job_alert_log (job_id, created_at DESC) WHERE deleted = 0;

-- 索引: 按告警级别筛选（告警中心）
CREATE INDEX IF NOT EXISTS idx_pjal_alert_level
    ON pmis_job_alert_log (alert_level, created_at DESC) WHERE deleted = 0;

-- 索引: 按租户 + 创建时间倒序（告警中心列表）
CREATE INDEX IF NOT EXISTS idx_pjal_tenant_created
    ON pmis_job_alert_log (tenant_id, created_at DESC) WHERE deleted = 0;

-- 索引: 链路追踪 ID（分布式排障）
CREATE INDEX IF NOT EXISTS idx_pjal_trace_id
    ON pmis_job_alert_log (trace_id) WHERE deleted = 0 AND trace_id IS NOT NULL;

-- ============================================================================
-- [P2-3] 任务执行每日统计表 pmis_job_daily_stats
-- ----------------------------------------------------------------------------
-- 每天凌晨聚合 pmis_job_log 的执行统计，供前端趋势图展示。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_daily_stats(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id          VARCHAR(20)         NOT NULL,
    job_key         VARCHAR(128)   NOT NULL,
    stats_date      DATE           NOT NULL,
    fire_count      BIGINT         NOT NULL DEFAULT 0,
    success_count   BIGINT         NOT NULL DEFAULT 0,
    fail_count      BIGINT         NOT NULL DEFAULT 0,
    timeout_count   BIGINT         NOT NULL DEFAULT 0,
    avg_duration_ms BIGINT,
    max_duration_ms BIGINT,
    min_duration_ms BIGINT,
    p95_duration_ms BIGINT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pjds_counts_nonneg CHECK (fire_count >= 0 AND success_count >= 0 AND fail_count >= 0 AND timeout_count >= 0),
    CONSTRAINT ck_pjds_deleted_enum CHECK (deleted IN (0, 1)),
    CONSTRAINT uk_pjds_job_date UNIQUE (job_id, stats_date, deleted)
);

COMMENT ON TABLE pmis_job_daily_stats IS '任务执行每日统计: 聚合 pmis_job_log 按日统计, 供趋势图展示';

COMMENT ON COLUMN pmis_job_daily_stats.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_daily_stats.job_id IS '任务 ID';

COMMENT ON COLUMN pmis_job_daily_stats.job_key IS '任务 KEY(冗余)';

COMMENT ON COLUMN pmis_job_daily_stats.stats_date IS '统计日期';

COMMENT ON COLUMN pmis_job_daily_stats.fire_count IS '当日触发次数';

COMMENT ON COLUMN pmis_job_daily_stats.success_count IS '当日成功次数';

COMMENT ON COLUMN pmis_job_daily_stats.fail_count IS '当日失败次数';

COMMENT ON COLUMN pmis_job_daily_stats.timeout_count IS '当日超时次数';

COMMENT ON COLUMN pmis_job_daily_stats.avg_duration_ms IS '平均耗时(毫秒)';

COMMENT ON COLUMN pmis_job_daily_stats.max_duration_ms IS '最大耗时(毫秒)';

COMMENT ON COLUMN pmis_job_daily_stats.min_duration_ms IS '最小耗时(毫秒)';

COMMENT ON COLUMN pmis_job_daily_stats.p95_duration_ms IS 'P95 耗时(毫秒)';

COMMENT ON COLUMN pmis_job_daily_stats.created_at IS '创建时间';

COMMENT ON COLUMN pmis_job_daily_stats.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX IF NOT EXISTS idx_pjds_job_date
    ON pmis_job_daily_stats (job_id, stats_date DESC) WHERE deleted = 0;

-- ============================================================================
-- [P2-7] 任务 SLA 管理表 pmis_job_sla
-- ----------------------------------------------------------------------------
-- 定义任务的 SLA 约束（最大执行时长/最大失败率/最小成功率），
-- 由 AlertScanner 定期检查，违约时触发告警。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_sla(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id          VARCHAR(20)         NOT NULL,
    job_key         VARCHAR(128)   NOT NULL,
    max_duration_ms BIGINT,
    max_fail_rate   DECIMAL(5,2),
    min_success_rate DECIMAL(5,2),
    alert_level     VARCHAR(16)    NOT NULL DEFAULT 'WARNING',
    enabled         SMALLINT       NOT NULL DEFAULT 1,
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pjs_max_duration_pos CHECK (max_duration_ms IS NULL OR max_duration_ms > 0),
    CONSTRAINT ck_pjs_fail_rate_range CHECK (max_fail_rate IS NULL OR (max_fail_rate >= 0 AND max_fail_rate <= 100)),
    CONSTRAINT ck_pjs_success_rate_range CHECK (min_success_rate IS NULL OR (min_success_rate >= 0 AND min_success_rate <= 100)),
    CONSTRAINT ck_pjs_alert_level_enum CHECK (alert_level IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT ck_pjs_enabled_enum CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_pjs_deleted_enum CHECK (deleted IN (0, 1)),
    CONSTRAINT uk_pjs_job_id UNIQUE (job_id, deleted)
);

COMMENT ON TABLE pmis_job_sla IS '任务 SLA 管理表: 定义最大执行时长/失败率/成功率约束, 违约时告警';

COMMENT ON COLUMN pmis_job_sla.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_sla.job_id IS '任务 ID';

COMMENT ON COLUMN pmis_job_sla.job_key IS '任务 KEY(冗余)';

COMMENT ON COLUMN pmis_job_sla.max_duration_ms IS '最大执行时长(毫秒), 超过则违约';

COMMENT ON COLUMN pmis_job_sla.max_fail_rate IS '最大失败率(%), 超过则违约';

COMMENT ON COLUMN pmis_job_sla.min_success_rate IS '最小成功率(%), 低于则违约';

COMMENT ON COLUMN pmis_job_sla.alert_level IS '告警级别: INFO / WARNING / CRITICAL';

COMMENT ON COLUMN pmis_job_sla.enabled IS '是否启用: 0 禁用 / 1 启用';

COMMENT ON COLUMN pmis_job_sla.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_job_sla.created_at IS '创建时间';

COMMENT ON COLUMN pmis_job_sla.updated_by IS '修改人 ID';

COMMENT ON COLUMN pmis_job_sla.updated_at IS '修改时间';

COMMENT ON COLUMN pmis_job_sla.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- ============================================================================

-- ============================================================================
-- [P2-7] 任务版本历史表 pmis_job_version_history
-- ----------------------------------------------------------------------------
-- 每次任务定义变更时记录一条版本快照，支持版本回溯和差异对比。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_version_history(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id            VARCHAR(20)         NOT NULL,
    job_key           VARCHAR(128)   NOT NULL,
    version           INTEGER        NOT NULL,
    change_type       VARCHAR(32)    NOT NULL,
    before_snapshot   TEXT,
    after_snapshot    TEXT,
    change_remark     VARCHAR(512),
    changed_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    changed_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pjvh_change_type_enum CHECK (change_type IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT ck_pjvh_version_pos CHECK (version >= 1)
);

COMMENT ON TABLE pmis_job_version_history IS '任务版本历史表: 每次任务定义变更时记录版本快照, 支持回溯和差异对比';

COMMENT ON COLUMN pmis_job_version_history.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_version_history.job_id IS '任务 ID';

COMMENT ON COLUMN pmis_job_version_history.job_key IS '任务 KEY(冗余)';

COMMENT ON COLUMN pmis_job_version_history.version IS '版本号';

COMMENT ON COLUMN pmis_job_version_history.change_type IS '变更类型: CREATE / UPDATE / DELETE';

COMMENT ON COLUMN pmis_job_version_history.before_snapshot IS '变更前快照 JSON';

COMMENT ON COLUMN pmis_job_version_history.after_snapshot IS '变更后快照 JSON';

COMMENT ON COLUMN pmis_job_version_history.change_remark IS '变更说明';

COMMENT ON COLUMN pmis_job_version_history.changed_by IS '变更人 ID';

COMMENT ON COLUMN pmis_job_version_history.changed_at IS '变更时间';

CREATE INDEX IF NOT EXISTS idx_pjvh_job_id
    ON pmis_job_version_history (job_id, version DESC);

-- ============================================================================
-- [P2-8] 执行产物管理表 pmis_job_artifact
-- ----------------------------------------------------------------------------
-- 记录任务执行产生的文件/数据产物，支持产物查询、下载和过期清理。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_artifact(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id          VARCHAR(20)         NOT NULL,
    log_id          VARCHAR(20)         NOT NULL,
    job_key         VARCHAR(128)   NOT NULL,
    artifact_name   VARCHAR(256)   NOT NULL,
    artifact_type   VARCHAR(32)    NOT NULL DEFAULT 'FILE',
    storage_path    VARCHAR(1024)  NOT NULL,
    size_bytes      BIGINT,
    content_type    VARCHAR(128),
    metadata        TEXT,
    expire_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pja_artifact_type_enum CHECK (artifact_type IN ('FILE', 'REPORT', 'DATA', 'LOG')),
    CONSTRAINT ck_pja_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_artifact IS '执行产物管理表: 记录任务执行产生的文件/数据产物, 支持查询/下载/清理';

COMMENT ON COLUMN pmis_job_artifact.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_artifact.job_id IS '任务 ID';

COMMENT ON COLUMN pmis_job_artifact.log_id IS '执行日志 ID';

COMMENT ON COLUMN pmis_job_artifact.job_key IS '任务 KEY(冗余)';

COMMENT ON COLUMN pmis_job_artifact.artifact_name IS '产物名称';

COMMENT ON COLUMN pmis_job_artifact.artifact_type IS '产物类型: FILE / REPORT / DATA / LOG';

COMMENT ON COLUMN pmis_job_artifact.storage_path IS '存储路径(文件系统路径或对象存储 URL)';

COMMENT ON COLUMN pmis_job_artifact.size_bytes IS '产物大小(字节)';

COMMENT ON COLUMN pmis_job_artifact.content_type IS '内容类型(MIME type)';

COMMENT ON COLUMN pmis_job_artifact.metadata IS '产物元数据 JSON';

COMMENT ON COLUMN pmis_job_artifact.expire_at IS '过期时间(NULL=不过期, 过期后由清理任务删除)';

COMMENT ON COLUMN pmis_job_artifact.created_at IS '创建时间';

COMMENT ON COLUMN pmis_job_artifact.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX IF NOT EXISTS idx_pja_log_id
    ON pmis_job_artifact (log_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pja_job_key
    ON pmis_job_artifact (job_key, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pja_expire_at
    ON pmis_job_artifact (expire_at) WHERE deleted = 0 AND expire_at IS NOT NULL;

-- ============================================================================
-- [P3-13] WebHook 事件订阅表 pmis_job_webhook
-- ----------------------------------------------------------------------------
-- 记录用户配置的 WebHook 订阅，在任务生命周期事件发生时推送通知。
-- ============================================================================
CREATE TABLE IF NOT EXISTS pmis_job_webhook(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    name            VARCHAR(128)   NOT NULL,
    event_type      VARCHAR(64)    NOT NULL,
    job_key         VARCHAR(128),
    job_group       VARCHAR(64),
    callback_url    VARCHAR(1024)  NOT NULL,
    http_method     VARCHAR(16)    NOT NULL DEFAULT 'POST',
    headers         TEXT,
    secret          VARCHAR(256),
    status          VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pjw_event_type_enum CHECK (event_type IN (
        'TASK_STARTED', 'TASK_SUCCESS', 'TASK_FAILED', 'TASK_TIMEOUT', 'DAG_COMPLETED')),
    CONSTRAINT ck_pjw_http_method_enum CHECK (http_method IN ('POST', 'PUT')),
    CONSTRAINT ck_pjw_status_enum CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_pjw_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_job_webhook IS 'WebHook 事件订阅表: 配置任务生命周期事件的通知推送';

COMMENT ON COLUMN pmis_job_webhook.id IS '主键 ID';

COMMENT ON COLUMN pmis_job_webhook.name IS 'WebHook 名称';

COMMENT ON COLUMN pmis_job_webhook.event_type IS '订阅事件类型: TASK_STARTED / TASK_SUCCESS / TASK_FAILED / TASK_TIMEOUT / DAG_COMPLETED';

COMMENT ON COLUMN pmis_job_webhook.job_key IS '订阅任务 KEY(NULL=所有任务)';

COMMENT ON COLUMN pmis_job_webhook.job_group IS '订阅任务组(NULL=所有分组)';

COMMENT ON COLUMN pmis_job_webhook.callback_url IS 'WebHook 回调 URL';

COMMENT ON COLUMN pmis_job_webhook.http_method IS '请求方法: POST / PUT';

COMMENT ON COLUMN pmis_job_webhook.headers IS '请求头 JSON';

COMMENT ON COLUMN pmis_job_webhook.secret IS '密钥(用于 HMAC-SHA256 签名验证)';

COMMENT ON COLUMN pmis_job_webhook.status IS '状态: ACTIVE / INACTIVE';

COMMENT ON COLUMN pmis_job_webhook.created_at IS '创建时间';

COMMENT ON COLUMN pmis_job_webhook.updated_at IS '更新时间';

COMMENT ON COLUMN pmis_job_webhook.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX IF NOT EXISTS idx_pjw_event_job
    ON pmis_job_webhook (event_type, job_key) WHERE deleted = 0 AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_pjw_status
    ON pmis_job_webhook (status) WHERE deleted = 0;

-- ----------------------------
-- 2) 预警分级推送表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_alert_dispatch (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    alert_code          VARCHAR(64)  NOT NULL UNIQUE,
    alert_type          VARCHAR(32)  NOT NULL,
    alert_level         VARCHAR(8)   NOT NULL,
    source_type         VARCHAR(32)  NOT NULL,
    source_id           VARCHAR(20),
    title               VARCHAR(256) NOT NULL,
    content             TEXT,
    target_role         VARCHAR(64)  NOT NULL,
    target_user_ids     VARCHAR(1024),
    push_channels       VARCHAR(64)  NOT NULL DEFAULT 'INAPP',
    dispatched_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_by       VARCHAR(64),
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    sent_at             TIMESTAMPTZ,
    fail_reason         VARCHAR(512),
    retry_count         INT          NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    -- 枚举约束
    CONSTRAINT ck_pad_alert_type       CHECK (alert_type  IN ('BUDGET','EVM','SLA','RISK','PROFIT','BENCH','UTILIZATION','OTHER')),
    CONSTRAINT ck_pad_alert_level      CHECK (alert_level IN ('YELLOW','RED')),
    CONSTRAINT ck_pad_source_type      CHECK (source_type IN ('PROJECT','EVM','TICKET','BENCH','CONFIG','OTHER')),
    CONSTRAINT ck_pad_push_channels    CHECK (push_channels ~ '^(INAPP|EMAIL|SMS|WECHAT)(,(INAPP|EMAIL|SMS|WECHAT))*$'),
    CONSTRAINT ck_pad_status_enum      CHECK (status IN ('PENDING','SENT','FAILED','RETRYING')),
    CONSTRAINT ck_pad_retry_count      CHECK (retry_count >= 0 AND retry_count <= 10),
    CONSTRAINT ck_pad_deleted          CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_alert_dispatch IS '预警分级推送表: 黄/红不同层级触达,失败自动重试（最大 3 次,硬上限 10 次）';

COMMENT ON COLUMN pmis_alert_dispatch.alert_code IS '预警编码: 业务唯一,如 ALERT-2026-001';

COMMENT ON COLUMN pmis_alert_dispatch.alert_type IS '预警类型: BUDGET 预算 / EVM 挣值 / SLA 工单 / RISK 风险 / PROFIT 利润 / BENCH 闲置 / UTILIZATION 利用率 / OTHER 其他';

COMMENT ON COLUMN pmis_alert_dispatch.alert_level IS '预警等级: YELLOW 黄色 / RED 红色';

COMMENT ON COLUMN pmis_alert_dispatch.source_type IS '触发源类型: PROJECT/EVM/TICKET/BENCH/CONFIG/OTHER';

COMMENT ON COLUMN pmis_alert_dispatch.source_id IS '触发源业务 ID';

COMMENT ON COLUMN pmis_alert_dispatch.title IS '预警标题';

COMMENT ON COLUMN pmis_alert_dispatch.content IS '预警内容（已渲染的模板）';

COMMENT ON COLUMN pmis_alert_dispatch.target_role IS '目标角色: PM/PMO/CFO 等';

COMMENT ON COLUMN pmis_alert_dispatch.target_user_ids IS '目标用户 ID 列表: 逗号分隔,精确触达';

COMMENT ON COLUMN pmis_alert_dispatch.push_channels IS '推送渠道: INAPP 站内信 / EMAIL 邮件 / SMS 短信 / WECHAT 微信,逗号分隔';

COMMENT ON COLUMN pmis_alert_dispatch.dispatched_at IS '派发时间';

COMMENT ON COLUMN pmis_alert_dispatch.dispatched_by IS '派发人: 定时任务 / 系统 / 用户';

COMMENT ON COLUMN pmis_alert_dispatch.status IS '发送状态: PENDING 待发送 / SENT 已发送 / FAILED 失败 / RETRYING 重试中';

COMMENT ON COLUMN pmis_alert_dispatch.sent_at IS '发送成功时间';

COMMENT ON COLUMN pmis_alert_dispatch.fail_reason IS '失败原因';

COMMENT ON COLUMN pmis_alert_dispatch.retry_count IS '重试次数: 业务最大 3 次,硬上限 10 次';

COMMENT ON COLUMN pmis_alert_dispatch.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_alert_dispatch.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_alert_dispatch.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的单列索引)
CREATE INDEX IF NOT EXISTS idx_pad_tenant_level_status
    ON pmis_alert_dispatch(tenant_id, alert_level, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pad_tenant_type_dispatched
    ON pmis_alert_dispatch(tenant_id, alert_type, dispatched_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pad_tenant_source
    ON pmis_alert_dispatch(tenant_id, source_type, source_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pad_tenant_target
    ON pmis_alert_dispatch(tenant_id, target_role)
    WHERE deleted = 0;

-- --------------------------------------------------------------------

-- ============================ [021] register pmis smart jobs ============================

-- ============================================================
-- V1.0.0_021  智能化升级 P5/P6/P7  定时任务注册
-- ============================================================
-- 说明：批次 16 智能化升级-系统内部数据管理（PRD 4.2）
--   P5-2 预警重试补偿：每 5 分钟扫描 PENDING/FAILED 预警重发
--   P6-1 每日自动对账：每日 02:00 跑成本/收入/回款/开票/工时/利润 对账
--   P7-3 售后巡检    ：每日 03:00 扫质保期 + 运维工单 SLA
-- 表 pmis_job 已在 V1.0.0_006 创建。
-- ============================================================

-- 清理旧记录（保证可重跑）


-- ---------- P5-2 预警重试补偿 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, status, remark, tenant_id)
VALUES (
    '预警重试补偿任务',
    'ALERT',
    'alertDispatchRetryJob',
    'alertDispatchRetryJobHandler',
    '0 0/5 * * * ?',
    'NORMAL',
    '每 5 分钟扫描 PENDING/FAILED 预警并重发，超过 maxRetry 后保持 FAILED',
    1
) ON CONFLICT DO NOTHING;

-- ---------- P6-1 每日自动对账 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, status, remark, tenant_id)
VALUES (
    '每日对账任务',
    'RECONCILE',
    'dailyReconcileJob',
    'dailyReconcileJobHandler',
    '0 0 2 * * ?',
    'NORMAL',
    '每日 02:00 校验成本/收入/开票/回款/工时/利润 6 维度双向一致性，落库 pmis_reconcile_daily',
    1
) ON CONFLICT DO NOTHING;

-- ---------- P7-3 售后巡检 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, status, remark, tenant_id)
VALUES (
    '售后巡检任务',
    'AFTERSALES',
    'afterSalesScanJob',
    'afterSalesScanJobHandler',
    '0 0 3 * * ?',
    'NORMAL',
    '每日 03:00 扫描即将到期/已过期质保期 + 运维工单 SLA 违约',
    1
) ON CONFLICT DO NOTHING;

-- -------------------------------------------
-- 2. FlowNodeDO 扩展字段（流程设计时存到 ext 即可，无需新加列）
-- -------------------------------------------
-- 节点定时器配置由前端设计器写入 FlowNodeDO.ext JSON，格式：
--   {
--     "timerCycle": "PT5M",     // ISO 8601 duration（5 分钟）
--     "timerDate": "2026-07-02T10:00:00",  // 绝对时间
--     "isBoundary": true,       // 是否边界定时器
--     "attachedToUserTask": "node_xxx",  // 边界定时器挂接的 userTask
--     "boundaryAction": "INTERRUPT|CONTINUE"  // 边界触发后行为
--   }
--
-- 解析逻辑由 BpmnXmlParser.parseExtensionElements + FlowNodeDO.ext 处理，
-- 本 SQL 不增加新列，复用 ext JSON。

-- -------------------------------------------
-- 3. 注册定时器扫描器调度任务（PMIS Cronjob）
-- -------------------------------------------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, status, remark, tenant_id)
VALUES (
    '工作流定时器扫描',
    'FLOW',
    'flowTimerScannerJob',
    'flowTimerScannerHandler',
    '0/30 * * * * ?',
    'NORMAL',
    'P1-2: 每 30s 扫描到点定时器，触发中间/边界定时器',
    1
) ON CONFLICT (job_key, deleted) WHERE deleted = 0 DO NOTHING;

-- --------------------------------------------------------------------
-- P0-3 合并：原 pmis_report_export_record 已并入 pmis_export_record，
--           通过 source='SUBSCRIPTION' 区分订阅触发的导出记录。
-- --------------------------------------------------------------------


-- ============================ [032] register report jobs ============================

-- ============================================================
-- V1.0.0_032  P1-5 注册报表定时任务
-- ============================================================
-- 说明：定时报表生成与分发（P1-5）
--   report-daily    日报：每天 08:00
--   report-weekly   周报：每周一 08:00
--   report-monthly  月报：每月 1 日 08:00
-- 表 pmis_job 已在 V1.0.0_006 创建。
-- ============================================================

-- 清理旧记录（保证可重跑）


-- ---------- 日报表生成与分发 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, params_json, status, remark, tenant_id)
VALUES (
    '日报表生成与分发任务',
    'PMIS_REPORT',
    'reportDailyJob',
    'reportScheduleJobHandler',
    '0 0 8 * * ?',
    'DAILY',
    'NORMAL',
    '每日 08:00 生成驾驶舱/EVM/利润/利用率/Bench/风险日报并分发到订阅人',
    1
) ON CONFLICT DO NOTHING;

-- ---------- 周报表生成与分发 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, params_json, status, remark, tenant_id)
VALUES (
    '周报表生成与分发任务',
    'PMIS_REPORT',
    'reportWeeklyJob',
    'reportScheduleJobHandler',
    '0 0 8 ? * MON',
    'WEEKLY',
    'NORMAL',
    '每周一 08:00 生成周报表并分发到订阅人',
    1
) ON CONFLICT DO NOTHING;

-- ---------- 月报表生成与分发 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, params_json, status, remark, tenant_id)
VALUES (
    '月报表生成与分发任务',
    'PMIS_REPORT',
    'reportMonthlyJob',
    'reportScheduleJobHandler',
    '0 0 8 1 * ?',
    'MONTHLY',
    'NORMAL',
    '每月 1 日 08:00 生成月报表并分发到订阅人',
    1
) ON CONFLICT DO NOTHING;

-- -------------------------------------------
-- 2. pmis_flow_node 已存在 slaConfig 字段（V1.0.0_026 引入），无需变更
--    扩展约定：
--    slaConfig = {
--      "timeoutMinutes": 120,            // 超时阈值
--      "action": "AUTO_PASS",            // 动作：REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT
--      "reminderIntervalMinutes": 60,    // 重复提醒间隔（仅 action=REMIND 生效）
--      "maxReminders": 3,                // 最大提醒次数（仅 action=REMIND 生效）
--      "escalateUserId": 1001,           // 升级目标用户（仅 action=ESCALATE 生效；空=管理员=1）
--      "escalateRoleCode": "manager",    // 升级目标角色（仅 action=ESCALATE 生效；可空）
--      "autoComment": "已超时自动通过"  // 自动操作时写入的审批意见
--    }
-- -------------------------------------------

-- --------------------------------------------------------------------

-- ============================ [035] register consistency job ============================

-- ============================================================
-- V1.0.0_035  P2-6 注册数据一致性校验定时任务
-- ============================================================
-- 说明：每日 02:30 执行数据一致性校验
--   1. 发票总额 vs 回款总额
--   2. 预算 vs 实际成本（超支检测）
--   3. WBS 进度 vs 工时完成率
--   差异超阈值自动记录日志并触发告警。
-- 表 pmis_job 已在 V1.0.0_006 创建。
-- 注意：版本号 033/034 已被流程引擎占用，本任务使用 035。
-- ============================================================

-- 清理旧记录（保证可重跑，按 job_key 唯一键清理）


-- ---------- 数据一致性校验任务 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, params_json, status, remark, tenant_id)
VALUES (
    'data-consistency-check',
    'PMIS_CRONJOB',
    'data-consistency-check',
    'dataConsistencyJobHandler',
    '0 30 2 * * ?',
    '{}',
    'NORMAL',
    '数据一致性校验（发票vs回款、预算vs成本、WBSvs工时）',
    1
) ON CONFLICT DO NOTHING;

-- 注册归档任务到 pmis_job（每日 03:00 触发，阈值 30 天）
INSERT INTO pmis_job
    (job_name, job_group, job_key, handler, cron_expression, params_json, status, remark, tenant_id, created_at, updated_at, deleted)
VALUES
    ('流程历史归档任务', 'WORKFLOW', 'flowHistoryArchiveJob',
     'flowHistoryArchiveJobHandler', '0 0 3 * * ?',
     '{"days":30,"batchSize":100,"maxProcessMs":30000}',
     'NORMAL', '每日 03:00 归档 30 天前的历史流程实例, 单批 100 条, 单次最长 30s',
     1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (job_key, deleted) WHERE deleted = 0 DO NOTHING;

-- =====================================================================
--  4) 预警 / 对账（4.2.2/4.2.3）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_recipient
    ON pmis_alert_dispatch (target_role, sent_at DESC)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_retry
    ON pmis_alert_dispatch (retry_count, sent_at DESC)
    WHERE status = 'FAILED' AND retry_count < 3;

ANALYZE pmis_alert_dispatch;

-- ============================================================
-- 七、补齐遗漏的 10 张业务表 tenant_id 字段
--   首轮扫描漏掉，启用 TenantLineInnerInterceptor 前必须补齐
-- ============================================================

-- 任务执行日志表
ALTER TABLE pmis_job_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_job_log_tenant ON pmis_job_log(tenant_id);

ANALYZE pmis_job_log;

CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_trace
    ON pmis_alert_dispatch (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

DO $$ BEGIN
  COMMENT ON COLUMN pmis_job_dag_node.condition_expression IS '条件表达式(CONDITION节点): 如 ${nodeA.result==''success''}';
  COMMENT ON COLUMN pmis_job_dag_node.node_type IS '节点类型: TASK(普通任务) / CONDITION(条件分支) / LOOP(循环) / PARALLEL_GATEWAY(并行网关)';
  COMMENT ON COLUMN pmis_job_dag_node.loop_count IS '循环次数(LOOP节点)';
  COMMENT ON COLUMN pmis_job_dag_node.parallel_branches IS '并行分支数(PARALLEL_GATEWAY节点)';
  COMMENT ON COLUMN pmis_job_dag_node.node_type IS '节点类型: TASK(普通任务) / CONDITION(条件分支) / LOOP(循环) / PARALLEL_GATEWAY(并行网关)';
  COMMENT ON COLUMN pmis_job_dag_node.loop_count IS '循环次数(LOOP节点)';
  COMMENT ON COLUMN pmis_job_dag_node.parallel_branches IS '并行分支数(PARALLEL_GATEWAY节点)';
EXCEPTION WHEN undefined_table THEN
  RAISE NOTICE 'pmis_job_dag_node not found, skipping';
END $$;

