-- ============================================================================
-- 模块名: ydsz-cronjob
-- 说  明: 基于 ydsz-cronjob-infra 实体类整理的完整建表脚本（MySQL 8.0+）
-- 日  期: 2026-08-25
-- @author ydsz-team
-- ============================================================================
-- 规范：
--   - 主键 ID 应用层 Snowflake 生成（VARCHAR(32)），Outbox 表为自增 BIGINT
--   - 公共列按基类继承链累积：
--       MpBaseIdEntity     -> id
--       MpBaseAuditEntity  -> + created_by/created_at/updated_by/updated_at
--       MpSimpleEntity     -> + deleted/status/tenant_id
--       MpVersionedEntity / MpBaseEntity -> + revision（乐观锁）
--   - 禁止物理外键，逻辑外键加索引
--   - 字符集：utf8mb4
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 任务主表
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    job_name              VARCHAR(128)    NOT NULL COMMENT '任务名称',
    job_group             VARCHAR(128)    DEFAULT NULL COMMENT '任务分组',
    job_key               VARCHAR(64)     NOT NULL COMMENT '任务 KEY（唯一）',
    handler               VARCHAR(128)    NOT NULL COMMENT '任务处理器 Bean 名称',
    cron_expression       VARCHAR(64)     NOT NULL COMMENT 'Cron 表达式',
    schedule_type         VARCHAR(32)     DEFAULT NULL COMMENT '调度类型（CRON/FIXED_RATE/FIXED_DELAY）',
    fixed_rate_ms         BIGINT          DEFAULT NULL COMMENT '固定频率执行间隔（毫秒）',
    fixed_delay_ms        BIGINT          DEFAULT NULL COMMENT '固定延迟执行间隔（毫秒）',
    params_json           JSON            DEFAULT NULL COMMENT '任务参数 JSON',
    job_remark            VARCHAR(512)    DEFAULT NULL COMMENT '任务备注',
    next_fire_time        DATETIME        DEFAULT NULL COMMENT '下次触发时间',
    last_fire_time        DATETIME        DEFAULT NULL COMMENT '上次触发时间',
    fire_count            BIGINT          NOT NULL DEFAULT 0 COMMENT '总触发次数',
    success_count         BIGINT          NOT NULL DEFAULT 0 COMMENT '成功次数',
    fail_count            BIGINT          NOT NULL DEFAULT 0 COMMENT '失败次数',
    lock_ttl_ms           BIGINT          DEFAULT NULL COMMENT '分布式锁 TTL（毫秒）',
    timeout_ms            BIGINT          DEFAULT NULL COMMENT '执行超时时间（毫秒）',
    sla_ms                BIGINT          DEFAULT NULL COMMENT 'SLA 时长（毫秒）',
    slow_threshold_ms     BIGINT          DEFAULT NULL COMMENT '慢任务阈值（毫秒）',
    misfire_policy        VARCHAR(32)     DEFAULT NULL COMMENT 'Misfire 处理策略（如 FIRE_ONCE/SKIP）',
    shard_total           INT             DEFAULT NULL COMMENT '分片总数（非分片任务为 NULL）',
    job_type              VARCHAR(32)     DEFAULT NULL COMMENT '任务类型（BEAN/GLUE 等）',
    max_retries           INT             NOT NULL DEFAULT 0 COMMENT '最大重试次数',
    retry_interval_ms     BIGINT          DEFAULT NULL COMMENT '重试间隔（毫秒）',
    retry_backoff         VARCHAR(32)     DEFAULT NULL COMMENT '重试退避策略（如 FIXED/EXPONENTIAL）',
    block_strategy        VARCHAR(32)     DEFAULT NULL COMMENT '阻塞处理策略（如 SERIAL/DISCARD/COVER）',
    consecutive_fail_count INT            NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    max_consecutive_fails INT             DEFAULT NULL COMMENT '最大连续失败次数（超过后自动禁用任务）',
    auto_resume_after_minutes INT         DEFAULT NULL COMMENT '自动恢复时间（分钟）',
    priority              INT             NOT NULL DEFAULT 0 COMMENT '优先级',
    version               INT             NOT NULL DEFAULT 1 COMMENT '配置版本号',
    timezone              VARCHAR(64)     DEFAULT NULL COMMENT '时区',
    cluster               VARCHAR(64)     DEFAULT NULL COMMENT '执行集群',
    canary_ratio          INT             DEFAULT NULL COMMENT '金丝雀流量比例（百分比 0-100）',
    canary_handler        VARCHAR(128)    DEFAULT NULL COMMENT '金丝雀处理器 Bean 名称',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_job_key UNIQUE (job_key, tenant_id),
    INDEX idx_job_group (job_group),
    INDEX idx_job_next_fire (next_fire_time),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务定义主表';

-- ----------------------------------------------------------------------------
-- 2. GLUE 在线编码
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_glue (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    job_id                VARCHAR(32)     NOT NULL COMMENT '任务 ID（关联 ydsz_job.id）',
    source_code           TEXT            NOT NULL COMMENT '源代码（Groovy/Python/Shell/JavaScript 脚本内容）',
    language              VARCHAR(32)     NOT NULL DEFAULT 'GROOVY' COMMENT '语言: GROOVY(默认)/PYTHON/SHELL/JAVASCRIPT/JAVA',
    version               INT             NOT NULL DEFAULT 1 COMMENT '版本号（从 1 递增）',
    remark                VARCHAR(512)    DEFAULT NULL COMMENT '版本备注',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_glue_job_version UNIQUE (job_id, version),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GLUE 在线编码版本表';

-- ----------------------------------------------------------------------------
-- 3. MapReduce 子任务
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_task (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    job_id                VARCHAR(32)     NOT NULL COMMENT '任务 ID（关联 ydsz_job.id）',
    log_id                VARCHAR(32)     NOT NULL COMMENT '执行日志 ID（关联 ydsz_job_log.id）',
    job_key               VARCHAR(64)     NOT NULL COMMENT '任务 KEY（冗余，便于查询）',
    task_name             VARCHAR(128)    NOT NULL COMMENT '子任务名称（root task 为 "root"）',
    task_params           JSON            DEFAULT NULL COMMENT '子任务参数 JSON',
    task_type             VARCHAR(32)     NOT NULL COMMENT '子任务类型: ROOT 根任务 / SUB_TASK 子任务',
    task_status           VARCHAR(32)     NOT NULL COMMENT '执行状态: PENDING 待执行 / RUNNING 执行中 / SUCCESS 成功 / FAILED 失败',
    result                JSON            DEFAULT NULL COMMENT '执行结果 JSON（ProcessResult.result 序列化后的字符串）',
    error_message         TEXT            DEFAULT NULL COMMENT '错误信息（失败时填充）',
    exec_node_id          VARCHAR(64)     DEFAULT NULL COMMENT '执行节点 ID（hostname:port）',
    retry_count           INT             NOT NULL DEFAULT 0 COMMENT '重试次数（默认 0，每次重试递增）',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_jt_job_id (job_id),
    INDEX idx_jt_log_id (log_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MapReduce 子任务记录表';

-- ----------------------------------------------------------------------------
-- 4. 调度节点心跳
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_node (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    node_id               VARCHAR(64)     NOT NULL COMMENT '节点 ID（hostname:port 或 hostname:pid）',
    app_name              VARCHAR(128)    DEFAULT NULL COMMENT '应用名称',
    host                  VARCHAR(128)    NOT NULL COMMENT '主机名',
    port                  INT             NOT NULL COMMENT '端口',
    last_heartbeat        DATETIME        NOT NULL COMMENT '最后心跳时间',
    node_status           VARCHAR(32)     DEFAULT NULL COMMENT '节点状态: ONLINE 在线 / OFFLINE 离线 / DRAINING 排空退出中',
    cpu_usage             DECIMAL(20,6)   DEFAULT NULL COMMENT 'CPU 使用率（百分比，0-100）',
    mem_usage_pct         DECIMAL(20,6)   DEFAULT NULL COMMENT '内存使用率（百分比，0-100）',
    running_count         INT             NOT NULL DEFAULT 0 COMMENT '当前正在执行的任务数',
    tags                  JSON            DEFAULT NULL COMMENT '节点标签 JSON（用于任务亲和性选择）',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_node_id UNIQUE (node_id),
    INDEX idx_last_heartbeat (last_heartbeat),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度节点心跳表';

-- ----------------------------------------------------------------------------
-- 5. 任务配置历史版本
--    继承 MpBaseIdEntity（仅 id，无租户/审计公共列），审计字段由业务字段自持
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_history (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    job_id                VARCHAR(32)     NOT NULL COMMENT '任务 ID（关联 ydsz_job.id）',
    version               INT             NOT NULL COMMENT '版本号（对应更新前的 job.version）',
    snapshot              JSON            DEFAULT NULL COMMENT '完整 Job JSON 快照（变更后状态; DELETE 时为 NULL）',
    change_type           VARCHAR(32)     NOT NULL COMMENT '变更类型: CREATE / UPDATE / DELETE',
    before_snapshot       JSON            DEFAULT NULL COMMENT '变更前快照 JSON（CREATE 时为 NULL; UPDATE/DELETE 时记录变更前状态）',
    change_remark         VARCHAR(512)    DEFAULT NULL COMMENT '变更说明（如"任务创建"、"任务更新"、"任务删除"）',
    job_name              VARCHAR(128)    DEFAULT NULL COMMENT '任务名称（冗余，便于列表展示）',
    job_key               VARCHAR(64)     DEFAULT NULL COMMENT '任务 KEY（冗余）',
    handler               VARCHAR(128)    DEFAULT NULL COMMENT '处理器（冗余）',
    cron_expression       VARCHAR(64)     DEFAULT NULL COMMENT 'Cron 表达式（冗余）',
    params_json           JSON            DEFAULT NULL COMMENT '参数 JSON（冗余）',
    remark                VARCHAR(512)    DEFAULT NULL COMMENT '备注（冗余）',
    changed_by            VARCHAR(64)     DEFAULT NULL COMMENT '修改人 ID',
    changed_at            DATETIME        DEFAULT NULL COMMENT '修改时间',
    history_deleted       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0 未删除 / 1 已删除',
    CONSTRAINT uk_jh_job_version UNIQUE (job_id, version),
    INDEX idx_jh_job_id (job_id),
    INDEX idx_jh_changed_at (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务配置历史版本表';

-- ----------------------------------------------------------------------------
-- 6. 执行产物
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_artifact (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    job_id                VARCHAR(32)     NOT NULL COMMENT '任务 ID',
    log_id                VARCHAR(32)     NOT NULL COMMENT '执行日志 ID',
    job_key               VARCHAR(64)     NOT NULL COMMENT '任务 KEY（冗余）',
    artifact_name         VARCHAR(128)    NOT NULL COMMENT '产物名称',
    artifact_type         VARCHAR(32)     NOT NULL COMMENT '产物类型: FILE / REPORT / DATA / LOG',
    storage_path          VARCHAR(1024)   NOT NULL COMMENT '存储路径（文件系统路径或对象存储 URL）',
    size_bytes            BIGINT          DEFAULT NULL COMMENT '产物大小（字节）',
    content_type          VARCHAR(128)    DEFAULT NULL COMMENT '内容类型（MIME type）',
    metadata              JSON            DEFAULT NULL COMMENT '产物元数据 JSON',
    expire_at             DATETIME        DEFAULT NULL COMMENT '过期时间（null=不过期）',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_ja_job_id (job_id),
    INDEX idx_ja_log_id (log_id),
    INDEX idx_ja_expire_at (expire_at),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行产物记录表';

-- ----------------------------------------------------------------------------
-- 7. WebHook 事件订阅
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_webhook (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    name                  VARCHAR(128)    NOT NULL COMMENT 'WebHook 名称',
    event_type            VARCHAR(64)     NOT NULL COMMENT '订阅的事件类型: TASK_STARTED / TASK_SUCCESS / TASK_FAILED / TASK_TIMEOUT / DAG_COMPLETED',
    job_key               VARCHAR(64)     DEFAULT NULL COMMENT '订阅的任务 KEY（null=所有任务）',
    job_group             VARCHAR(128)    DEFAULT NULL COMMENT '订阅的任务组（null=所有分组）',
    callback_url          VARCHAR(1024)   NOT NULL COMMENT 'WebHook 回调 URL',
    http_method           VARCHAR(32)     DEFAULT NULL COMMENT '请求方法: POST / PUT',
    headers               JSON            DEFAULT NULL COMMENT '请求头 JSON',
    secret                VARCHAR(256)    DEFAULT NULL COMMENT '密钥（用于签名验证）',
    webhook_status        VARCHAR(32)     DEFAULT NULL COMMENT '状态: ACTIVE / INACTIVE',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_event_type (event_type),
    INDEX idx_jw_job_key (job_key),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WebHook 事件订阅表';

-- ----------------------------------------------------------------------------
-- 8. 任务告警规则
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_alert_rule (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    rule_name             VARCHAR(128)    NOT NULL COMMENT '规则名称',
    job_id                VARCHAR(32)     DEFAULT NULL COMMENT '关联任务 ID（NULL 表示全局规则）',
    job_key               VARCHAR(64)     DEFAULT NULL COMMENT '任务 KEY 冗余（NULL 表示全局规则）',
    alert_type            VARCHAR(32)     NOT NULL COMMENT '告警类型: FAIL / TIMEOUT / SLOW / FAIL_RATE / DURATION_P95',
    alert_level           VARCHAR(32)     NOT NULL COMMENT '告警级别: INFO / WARN / ERROR / CRITICAL',
    threshold             BIGINT          DEFAULT NULL COMMENT '阈值（按 alertType 解释: FAIL_RATE 百分比 0-100 / SLOW+DURATION_P95 毫秒）',
    time_window_minutes   INT             DEFAULT NULL COMMENT '统计时间窗口（分钟），仅 FAIL_RATE / DURATION_P95 生效',
    channels              JSON            DEFAULT NULL COMMENT '通知通道（JSON 数组: ["EMAIL","DINGTALK","WECOM","WEBHOOK"]）',
    receivers             JSON            DEFAULT NULL COMMENT '接收人（JSON 数组: 邮箱/手机号/userId 列表）',
    cooldown_minutes      INT             DEFAULT NULL COMMENT '冷却时间（分钟），同一规则在冷却期内不重复告警',
    enabled               TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用: 0 禁用 / 1 启用',
    source_type           VARCHAR(32)     DEFAULT NULL COMMENT '规则来源: MANUAL 手动创建(默认) / SLA 由SLA规则自动生成',
    last_alert_at         DATETIME        DEFAULT NULL COMMENT '最后告警时间（用于冷却判断）',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_ar_job_id (job_id),
    INDEX idx_ar_alert_type (alert_type),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务告警规则表';

-- ----------------------------------------------------------------------------
-- 9. 租户配额
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_tenant_quota (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    max_jobs              INT             DEFAULT NULL COMMENT '任务数上限（NULL=unlimited；超过此值拒绝创建新任务）',
    max_concurrent        INT             DEFAULT NULL COMMENT '并发执行上限（NULL=unlimited；超过此值拒绝派发）',
    max_daily_executions  INT             DEFAULT NULL COMMENT '日执行量上限（NULL=unlimited；超过此值拒绝派发）',
    enabled               TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用配额检查: 0 禁用 / 1 启用',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_tq_tenant UNIQUE (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户级配额表';

-- ----------------------------------------------------------------------------
-- 10. DAG 工作流定义
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_dag (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    dag_key               VARCHAR(64)     NOT NULL COMMENT 'DAG 唯一 KEY（调度与触发使用）',
    dag_name              VARCHAR(128)    NOT NULL COMMENT 'DAG 名称（展示用）',
    dag_definition        JSON            NOT NULL COMMENT 'DAG 定义 JSON（nodes + edges + 可视化坐标）',
    dag_status            VARCHAR(32)     DEFAULT NULL COMMENT 'DAG 状态: DRAFT 草稿 / ENABLED 启用 / DISABLED 禁用',
    trigger_type          VARCHAR(32)     DEFAULT NULL COMMENT '触发类型: MANUAL 手动 / CRON 定时',
    cron_expression       VARCHAR(64)     DEFAULT NULL COMMENT 'Cron 表达式（triggerType=CRON 时必填）',
    max_concurrent_instances INT          NOT NULL DEFAULT 1 COMMENT '最大并发实例数(0=不限制, 默认1)',
    fail_strategy         VARCHAR(32)     DEFAULT NULL COMMENT 'DAG 级失败策略: FAIL_FAST 中止 / CONTINUE_ON_FAIL 继续',
    description           VARCHAR(512)    DEFAULT NULL COMMENT 'DAG 描述',
    timeout_ms            BIGINT          DEFAULT NULL COMMENT 'DAG 超时时间（毫秒，null=不限时）',
    next_fire_time        DATETIME        DEFAULT NULL COMMENT '下次触发时间（CRON 模式）',
    last_fire_time        DATETIME        DEFAULT NULL COMMENT '上次触发时间',
    fire_count            BIGINT          NOT NULL DEFAULT 0 COMMENT '总触发次数',
    success_count         BIGINT          NOT NULL DEFAULT 0 COMMENT '成功次数',
    fail_count            BIGINT          NOT NULL DEFAULT 0 COMMENT '失败次数',
    version               INT             NOT NULL DEFAULT 1 COMMENT '版本号(乐观锁)',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_dag_key UNIQUE (dag_key, tenant_id),
    INDEX idx_dag_next_fire (next_fire_time),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DAG 工作流定义表';

-- ----------------------------------------------------------------------------
-- 11. DAG 工作流版本历史
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_dag_version (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    dag_id                VARCHAR(32)     NOT NULL COMMENT 'DAG ID（关联 ydsz_job_dag.id）',
    dag_key               VARCHAR(64)     NOT NULL COMMENT 'DAG KEY（冗余字段，便于查询）',
    version               INT             NOT NULL COMMENT '版本号（从 1 递增）',
    dag_definition        JSON            NOT NULL COMMENT 'DAG 定义 JSON 快照',
    dag_name              VARCHAR(128)    DEFAULT NULL COMMENT 'DAG 名称快照',
    trigger_type          VARCHAR(32)     DEFAULT NULL COMMENT '触发类型快照',
    cron_expression       VARCHAR(64)     DEFAULT NULL COMMENT 'Cron 表达式快照',
    fail_strategy         VARCHAR(32)     DEFAULT NULL COMMENT '失败策略快照',
    remark                VARCHAR(512)    DEFAULT NULL COMMENT '版本备注（如"新增节点A"、"修改条件分支"）',
    changed_by            VARCHAR(64)     DEFAULT NULL COMMENT '变更操作人',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_dv_dag_version UNIQUE (dag_id, version),
    INDEX idx_dv_dag_key (dag_key),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DAG 工作流版本历史表';

-- ----------------------------------------------------------------------------
-- 12. DAG 工作流实例
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_dag_instance (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    dag_id                VARCHAR(32)     NOT NULL COMMENT 'DAG 定义 ID',
    dag_key               VARCHAR(64)     NOT NULL COMMENT 'DAG KEY（冗余，便于查询）',
    instance_status       VARCHAR(32)     NOT NULL COMMENT '实例状态: PENDING/RUNNING/SUCCESS/FAILED/PARTIAL_SUCCESS/PAUSED/CANCELED',
    trigger_type          VARCHAR(32)     DEFAULT NULL COMMENT '触发类型: MANUAL/CRON/DEPENDENT',
    trigger_by            VARCHAR(64)     DEFAULT NULL COMMENT '触发人（MANUAL 时为用户 ID）',
    trigger_trace_id      VARCHAR(64)     DEFAULT NULL COMMENT '触发 traceId（用于链路追踪）',
    context_json          JSON            DEFAULT NULL COMMENT 'DAG 实例级上下文 JSON（跨节点传参）',
    started_at            DATETIME        DEFAULT NULL COMMENT '开始时间',
    finished_at           DATETIME        DEFAULT NULL COMMENT '结束时间',
    duration_ms           BIGINT          DEFAULT NULL COMMENT '执行耗时（毫秒）',
    error_message         TEXT            DEFAULT NULL COMMENT '错误信息（FAILED 时填充）',
    total_nodes           INT             DEFAULT NULL COMMENT '总节点数',
    success_nodes         INT             DEFAULT NULL COMMENT '成功节点数',
    failed_nodes          INT             DEFAULT NULL COMMENT '失败节点数',
    skipped_nodes         INT             DEFAULT NULL COMMENT '跳过节点数',
    next_fire_time        DATETIME        DEFAULT NULL COMMENT '下次触发时间（用于 DAG 的 CRON 调度）',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_di_dag_id (dag_id),
    INDEX idx_di_status (instance_status),
    INDEX idx_di_started_at (started_at),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DAG 工作流实例表';

-- ----------------------------------------------------------------------------
-- 13. DAG 节点实例
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_dag_node_instance (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    dag_instance_id       VARCHAR(32)     NOT NULL COMMENT 'DAG 实例 ID',
    dag_id                VARCHAR(32)     NOT NULL COMMENT 'DAG 定义 ID',
    job_id                VARCHAR(32)     NOT NULL COMMENT '任务 ID',
    job_key               VARCHAR(64)     NOT NULL COMMENT '任务 KEY（冗余）',
    node_status           VARCHAR(32)     DEFAULT NULL COMMENT '节点状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/RETRYING',
    log_id                VARCHAR(32)     DEFAULT NULL COMMENT '关联的任务执行日志 ID（ydsz_job_log.id）',
    retry_count           INT             DEFAULT NULL COMMENT '节点级重试次数',
    max_retries           INT             DEFAULT NULL COMMENT '节点级最大重试次数',
    started_at            DATETIME        DEFAULT NULL COMMENT '节点开始时间',
    finished_at           DATETIME        DEFAULT NULL COMMENT '节点结束时间',
    duration_ms           BIGINT          DEFAULT NULL COMMENT '节点执行耗时（毫秒）',
    result_json           JSON            DEFAULT NULL COMMENT '节点执行结果 JSON',
    error_message         TEXT            DEFAULT NULL COMMENT '节点错误信息',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_dni_dag_instance_id (dag_instance_id),
    INDEX idx_dni_job_id (job_id),
    INDEX idx_dni_log_id (log_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DAG 节点实例表';

-- ----------------------------------------------------------------------------
-- 14. 任务执行日志
--     继承 MpBaseIdEntity，deleted/created_at/updated_at 由实体自身声明
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_log (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    job_id                VARCHAR(32)     NOT NULL COMMENT '任务 ID',
    job_key               VARCHAR(64)     NOT NULL COMMENT '任务 KEY',
    start_time            DATETIME        DEFAULT NULL COMMENT '开始时间',
    end_time              DATETIME        DEFAULT NULL COMMENT '结束时间',
    duration_ms           BIGINT          DEFAULT NULL COMMENT '耗时(毫秒)',
    error_message         TEXT            DEFAULT NULL COMMENT '错误信息',
    params_json           JSON            DEFAULT NULL COMMENT '参数 JSON',
    result_json           JSON            DEFAULT NULL COMMENT '结果 JSON',
    trace_id              VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    trigger_type          VARCHAR(32)     DEFAULT NULL COMMENT '触发类型: CRON 定时 / MANUAL 手动 / RETRY 重试 / MISFIRED Misfire 触发',
    lock_holder           VARCHAR(64)     DEFAULT NULL COMMENT '持锁者标识（hostname:pid，用于超时后安全释放分布式锁）',
    exec_node_id          VARCHAR(64)     DEFAULT NULL COMMENT '执行节点 ID（hostname:port，用于故障转移时定位任务所在节点）',
    exec_thread_id        BIGINT          DEFAULT NULL COMMENT '执行线程 ID（用于超时强制中断时定位执行线程）',
    shard_index           INT             DEFAULT NULL COMMENT '分片索引（非分片任务为 NULL；分片任务为 0-based 索引）',
    shard_total           INT             DEFAULT NULL COMMENT '分片总数（非分片任务为 NULL）',
    is_slow               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '慢任务标记（0=非慢 / 1=慢）',
    slow_threshold_ms     BIGINT          DEFAULT NULL COMMENT '慢任务阈值快照（毫秒，NULL=未配置慢任务检测）',
    queue_time            DATETIME        DEFAULT NULL COMMENT '入队时间（任务被 JobScanner 扫描到并入队的时刻）',
    dispatch_time         DATETIME        DEFAULT NULL COMMENT '派发时间（任务被 Dispatcher 从队列取出并派发的时刻）',
    handler_init_time     DATETIME        DEFAULT NULL COMMENT 'Handler 初始化时间（JobHandler 实例化/资源准备完成的时刻）',
    handler_end_time      DATETIME        DEFAULT NULL COMMENT 'Handler 执行结束时间（JobHandler.execute() 返回的时刻）',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '执行状态: RUNNING/SUCCESS/FAILED/TIMEOUT',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0=未删除，1=已删除',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_jl_job_id (job_id),
    INDEX idx_jl_job_key (job_key),
    INDEX idx_jl_status (status),
    INDEX idx_jl_start_time (start_time),
    INDEX idx_jl_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志表';

-- ----------------------------------------------------------------------------
-- 15. 任务执行日志内容（行级明细）
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_log_content (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    log_id                VARCHAR(32)     NOT NULL COMMENT '任务执行日志 ID（关联 ydsz_job_log.id）',
    job_key               VARCHAR(64)     NOT NULL COMMENT '任务 KEY（冗余，避免连表查询）',
    line_no               INT             NOT NULL COMMENT '行号（从 1 递增）',
    log_level             VARCHAR(32)     DEFAULT NULL COMMENT '日志级别：DEBUG / INFO / WARN / ERROR',
    content               VARCHAR(4000)   NOT NULL COMMENT '日志内容（单行文本，最长 4000 字符）',
    CONSTRAINT uk_jlc_log_line UNIQUE (log_id, line_no),
    INDEX idx_jlc_job_key (job_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志内容表（行级明细）';

-- ----------------------------------------------------------------------------
-- 16. 任务执行每日统计
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_daily_stats (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    job_id                VARCHAR(32)     NOT NULL COMMENT '任务 ID',
    job_key               VARCHAR(64)     NOT NULL COMMENT '任务 KEY（冗余）',
    stats_date            DATE            NOT NULL COMMENT '统计日期',
    fire_count            BIGINT          NOT NULL DEFAULT 0 COMMENT '当日触发次数',
    success_count         BIGINT          NOT NULL DEFAULT 0 COMMENT '当日成功次数',
    fail_count            BIGINT          NOT NULL DEFAULT 0 COMMENT '当日失败次数',
    timeout_count         BIGINT          NOT NULL DEFAULT 0 COMMENT '当日超时次数',
    avg_duration_ms       BIGINT          DEFAULT NULL COMMENT '平均耗时（毫秒）',
    max_duration_ms       BIGINT          DEFAULT NULL COMMENT '最大耗时（毫秒）',
    min_duration_ms       BIGINT          DEFAULT NULL COMMENT '最小耗时（毫秒）',
    p95_duration_ms       BIGINT          DEFAULT NULL COMMENT 'P95 耗时（毫秒）',
    CONSTRAINT uk_jds_job_date UNIQUE (job_id, stats_date),
    INDEX idx_jds_stats_date (stats_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行每日统计表';

-- ----------------------------------------------------------------------------
-- 17. 告警派发日志
--     实体 JobAlertLog 映射到 ydsz_job_alert_dispatch（P3-1-merge，与告警模块共用），
--     cronjob 场景下 source_type 固定为 CRONJOB。继承 MpBaseIdEntity。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_alert_dispatch (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    alert_code            VARCHAR(64)     NOT NULL COMMENT '预警编码（cronjob 自动生成: CRONJOB-{timestamp}-{ruleId}）',
    source_type           VARCHAR(32)     NOT NULL COMMENT '触发源类型（cronjob 告警固定为 CRONJOB）',
    rule_id               VARCHAR(32)     DEFAULT NULL COMMENT '规则 ID（映射到 rule_id）',
    rule_name             VARCHAR(128)    DEFAULT NULL COMMENT '规则名称（映射到 title）',
    job_id                VARCHAR(32)     DEFAULT NULL COMMENT '任务 ID（NULL 表示全局告警; 映射到 source_id）',
    job_key               VARCHAR(64)     DEFAULT NULL COMMENT '任务 KEY（冗余）',
    alert_type            VARCHAR(32)     DEFAULT NULL COMMENT '告警类型: FAIL / TIMEOUT / SLOW / FAIL_RATE / DURATION_P95',
    alert_level           VARCHAR(32)     DEFAULT NULL COMMENT '告警级别: INFO / WARN / ERROR / CRITICAL',
    trigger_value         VARCHAR(64)     DEFAULT NULL COMMENT '触发时的实际值（如失败率 85.5、耗时 5000）',
    threshold             BIGINT          DEFAULT NULL COMMENT '规则阈值（冗余）',
    channels              VARCHAR(256)    DEFAULT NULL COMMENT '实际发送通道（逗号分隔: INAPP,EMAIL,DINGTALK）',
    alert_status          VARCHAR(32)     DEFAULT NULL COMMENT '告警状态: PENDING / SUCCESS / PARTIAL / FAILED / *_RECOVERY',
    error_message         TEXT            DEFAULT NULL COMMENT '错误信息（部分通道失败时记录; 映射到 fail_reason）',
    trace_id              VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID（映射到 provider_trace_id）',
    trigger_log_id        VARCHAR(32)     DEFAULT NULL COMMENT '触发该告警的任务日志 ID（关联 ydsz_job_log.id）',
    CONSTRAINT uk_ad_alert_code UNIQUE (alert_code),
    INDEX idx_ad_rule_id (rule_id),
    INDEX idx_ad_job_id (job_id),
    INDEX idx_ad_source_status (source_type, alert_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警派发日志表（cronjob 告警记录，source_type=CRONJOB）';

-- ----------------------------------------------------------------------------
-- 18. Outbox 事务性事件表
--     实体 OutboxEvent（无基类，主键为自增 Long），参考
--     ydsz-common/ydsz-common-event/src/main/resources/db/outbox_mysql.sql 风格
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_job_outbox (
    id                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '事件 ID（自增）',
    event_key             VARCHAR(64)     NOT NULL COMMENT '事件 KEY（幂等去重标识）',
    event_type            VARCHAR(128)    NOT NULL COMMENT '事件类型',
    topic                 VARCHAR(128)    NOT NULL COMMENT '目标 topic（webhook / metrics / audit）',
    payload               JSON            NOT NULL COMMENT '事件 payload（JSON 字符串）',
    status                VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '事件状态: PENDING 待发布 / PUBLISHED 已发布 / DEAD 死亡信（重试耗尽）',
    retry_count           INT             NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_time       DATETIME(3)     DEFAULT NULL COMMENT '下次重试时间',
    create_time           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_jo_event_key UNIQUE (event_key),
    INDEX idx_jo_status_retry (status, next_retry_time),
    INDEX idx_jo_status_created (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbox 事务性事件表：存储待发布的领域事件，保障业务写操作与事件投递的事务一致性';
