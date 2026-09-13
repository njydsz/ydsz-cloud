-- ============================================================================
-- ydsz-cloud 公共组件模块数据库脚本 (ydsz-common)
-- ============================================================================
-- 模块：ydsz-common（公共组件，含 ydsz-common-event、ydsz-common-search、ydsz-common-audit）
-- 说明：基于 ydsz-common-event、ydsz-common-search 与 ydsz-common-audit 既有 SQL 整理的完整建表脚本。
--       outbox 表沿用 ydsz-common-event/src/main/resources/db/outbox_mysql.sql 原定义；
--       搜索死信队列表由 PostgreSQL 版本（ydsz_com_search_dead_letter.sql）转译为 MySQL；
--       审计日志表（ydsz_com_audit_log）由审计切面（AuditAspect）自动写入。
-- 数据库：MySQL 8.0+，InnoDB / utf8mb4
-- 日期：2026-09-08
-- @author ydsz-team
-- ============================================================================

-- ============================================================================
-- 1. 事务性 Outbox 表（ydsz-common-event）
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_com_outbox (
    -- ========== 业务主键 ==========
    id                  VARCHAR(64)     NOT NULL COMMENT '消息唯一标识（Snowflake ID）',

    -- ========== 聚合根信息 ==========
    aggregate_type      VARCHAR(128)    NOT NULL COMMENT '聚合根类型（如 Order, User）',
    aggregate_id        VARCHAR(128)    NOT NULL COMMENT '聚合根 ID',

    -- ========== 事件信息 ==========
    event_type          VARCHAR(128)    NOT NULL COMMENT '事件类型（如 OrderCreated）',
    payload             MEDIUMTEXT      NOT NULL COMMENT '事件负载 JSON（最大 4MB）',

    -- ========== 投递控制 ==========
    status              ENUM('PENDING','PROCESSING','SENT','DEAD_LETTER')
                                        NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    retry_count         INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '当前重试次数',
    max_retries         INT UNSIGNED    NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    next_retry_at       DATETIME(3)             COMMENT '下次重试时间（指数退避）',
    error_message       TEXT                     COMMENT '最后一次失败的错误信息',

    -- ========== 上下文 ==========
    tenant_id           VARCHAR(64)              COMMENT '租户 ID（多租户隔离）',
    trace_id            VARCHAR(64)              COMMENT '链路追踪 ID',
    deduplication_id    VARCHAR(64)              COMMENT '幂等去重 ID',

    -- ========== 时间戳 ==========
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    sent_at             DATETIME(3)              COMMENT '投递成功时间',

    -- ========== 约束与主键 ==========
    PRIMARY KEY (id),
    -- 轮询待投递消息索引
    INDEX idx_ydsz_com_outbox_pending (status, created_at ASC),
    -- 下次重试时间索引
    INDEX idx_ydsz_com_outbox_retry (status, next_retry_at),
    -- PROCESSING 超时回收索引
    INDEX idx_ydsz_com_outbox_processing (status, updated_at),
    -- 已投递清理索引
    INDEX idx_ydsz_com_outbox_sent_at (status, sent_at),
    -- 租户隔离索引
    INDEX idx_ydsz_com_outbox_tenant (tenant_id, status),
    -- 幂等去重索引
    INDEX idx_ydsz_com_outbox_dedup (deduplication_id, status),
    -- 聚合根查询索引
    INDEX idx_ydsz_com_outbox_aggregate (aggregate_type, aggregate_id, created_at DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='事务性 Outbox 表：存储领域事件，保障业务写操作与事件投递的事务一致性';

-- ============================================================================
-- 2. 搜索索引死信队列表（ydsz-common-search）
-- ============================================================================
-- 用途：持久化存储索引写入失败的操作，
--       支持定时重放补偿 + 告警监控 + 人工介入。
-- 由 PostgreSQL 版转译：BIGSERIAL→BIGINT AUTO_INCREMENT、TIMESTAMPTZ→DATETIME、
-- CHECK 约束并入注释、部分索引转译为普通复合索引。

CREATE TABLE IF NOT EXISTS ydsz_com_search_dead_letter (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    operation     VARCHAR(20)  NOT NULL COMMENT '索引操作类型：UPSERT / DELETE / BULK',
    doc_type      VARCHAR(64)  DEFAULT NULL COMMENT '实体类型（project/wiki/user 等）',
    document_id   VARCHAR(128) DEFAULT NULL COMMENT '文档主键（DELETE 操作时使用）',
    document_json TEXT         DEFAULT NULL COMMENT '文档 JSON（UPSERT/BULK 操作时使用）',
    error_msg     TEXT         DEFAULT NULL COMMENT '最后一次失败原因（截断 2000 字符）',
    retry_count   INT          NOT NULL DEFAULT 0 COMMENT '已重试次数，达到 5 次升级为 DISCARDED',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'is_resolved-已解决 / DISCARDED-已放弃(需人工介入)',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
    resolved_at   DATETIME     DEFAULT NULL COMMENT '解决时间',
    PRIMARY KEY (id),
    -- 按状态 + 创建时间索引，支持高效扫描待处理记录
    INDEX idx_dlq_status_created (status, created_at),
    -- 按实体类型索引，支持按类型查询失败记录（原 PG 部分索引转译）
    INDEX idx_dlq_doc_type (doc_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索索引死信队列：存储索引写入失败的操作，支持定时重放补偿';

-- ============================================================================
-- 3. 审计日志表（ydsz-common-audit）
-- ============================================================================
-- 用途：存储全平台操作审计日志，支持按时间范围、操作人、行为、模块等多维度检索。
--       由审计切面（AuditAspect）自动写入，AuditAdminController 提供查询接口。
-- 由 PostgreSQL 版转译：TIMESTAMPTZ→DATETIME、SMALLINT→SMALLINT、
-- IF NOT EXISTS 语法调整、移除 CONSTRAINT 命名（MySQL 内联约束）。

CREATE TABLE IF NOT EXISTS ydsz_com_audit_log (
    id                       VARCHAR(64)     NOT NULL  COMMENT '审计记录唯一标识（雪花算法生成）',
    app_key                  VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '应用标识（区分不同微服务的审计记录）',
    tenant_id                VARCHAR(64)     DEFAULT NULL  COMMENT '租户 ID（多租户隔离）',
    operator_id              VARCHAR(64)     DEFAULT NULL  COMMENT '操作人 ID（来自 RequestContext 透传）',
    operator_name            VARCHAR(64)     DEFAULT NULL  COMMENT '操作人姓名（便于直接展示）',
    audit_type               SMALLINT        NOT NULL DEFAULT 1  COMMENT '审计类型编码（1=操作/2=登录/3=数据/4=权限/5=配置/6=文件/7=接口/8=系统）',
    action                   SMALLINT        NOT NULL DEFAULT 99  COMMENT '操作行为编码（1=新增/2=修改/3=删除/4=查询/5=导入/6=导出/7=上传/8=下载/99=其他）',
    status                   SMALLINT        NOT NULL DEFAULT 1  COMMENT '执行状态（1=成功/0=失败）',
    module                   VARCHAR(128)    DEFAULT NULL  COMMENT '模块名称（如：用户管理、代码生成等）',
    content                  VARCHAR(1024)   DEFAULT NULL  COMMENT '操作内容描述（SpEL 解析后的最终文本）',
    business_no              VARCHAR(128)    DEFAULT NULL  COMMENT '业务流水号（关联业务单据）',
    ip_address               VARCHAR(64)     DEFAULT NULL  COMMENT '请求来源 IP 地址',
    request_params           TEXT            DEFAULT NULL  COMMENT '请求参数 JSON（已脱敏/截断，最大 10KB）',
    response_result          TEXT            DEFAULT NULL  COMMENT '响应结果 JSON（已脱敏/截断，默认不记录）',
    diff_before_snapshot     TEXT            DEFAULT NULL  COMMENT '变更前快照 JSON（仅 @Audit(recordDiff=true) 时写入）',
    diff_after_snapshot      TEXT            DEFAULT NULL  COMMENT '变更后快照 JSON（仅 @Audit(recordDiff=true) 时写入）',
    error_message            VARCHAR(512)    DEFAULT NULL  COMMENT '异常信息（业务方法抛异常时记录）',
    cost_time                BIGINT          DEFAULT 0  COMMENT '执行耗时（毫秒）',
    trace_id                 VARCHAR(64)     DEFAULT NULL  COMMENT '链路追踪 ID（独立列，支持索引查询）',
    operation_time           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '操作时间（业务方法执行时刻）',
    created_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '审计日志落库时刻',
    PRIMARY KEY (id),
    -- 核心查询：按操作时间降序分页
    INDEX idx_ydsz_com_audit_log_operation_time (operation_time DESC),
    -- 按操作人查询其审计轨迹
    INDEX idx_ydsz_com_audit_log_operator_id (operator_id, operation_time DESC),
    -- 按链路追踪 ID 查询完整业务链路
    INDEX idx_ydsz_com_audit_log_trace_id (trace_id),
    -- 按租户 ID + 时间范围查询（多租户隔离）
    INDEX idx_ydsz_com_audit_log_tenant_time (tenant_id, operation_time DESC),
    -- 按状态查询（成功/失败分离）
    INDEX idx_ydsz_com_audit_log_status (status, operation_time DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='全平台操作审计日志表（ydsz-common-audit 自动落库）';

-- 以下为单独创建的索引（避免内联索引过多影响建表语句可读性）
CREATE INDEX idx_ydsz_com_audit_log_action ON ydsz_com_audit_log (action, operation_time DESC);
CREATE INDEX idx_ydsz_com_audit_log_module_action ON ydsz_com_audit_log (module, action, operation_time DESC);
CREATE INDEX idx_ydsz_com_audit_log_app_key ON ydsz_com_audit_log (app_key, operation_time DESC);
