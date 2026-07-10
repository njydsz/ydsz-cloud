-- ============================================================
-- PMIS message module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================

-- ====================================================================
-- 5. 通知中心（ydsz-pmis-message 引擎 - 大厂级独立自研）
--    表前缀 pmis_msg_* 统一管理：站内通知 / 用户偏好 / 订阅
--    消息模板 / 发送日志 / 路由 / 回执 / 聚合 / 灰度 见第 7.x 节
-- ====================================================================

-- 站内通知表 pmis_msg_notification（由原 pmis_notification 重构升级）
CREATE TABLE IF NOT EXISTS pmis_msg_notification(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT replace(gen_random_uuid()::text,'-',''),
    title           VARCHAR(255)   NOT NULL,
    content         TEXT,
    level           VARCHAR(16)    NOT NULL DEFAULT 'INFO',
    category        VARCHAR(32)    NOT NULL,
    priority        VARCHAR(16)    NOT NULL DEFAULT 'NORMAL',
    sender_id       VARCHAR(20),
    receiver_id     VARCHAR(20)         NOT NULL,
    biz_type        VARCHAR(64),
    biz_id          VARCHAR(20),
    message_group   VARCHAR(64),
    batch_id        VARCHAR(20),
    action_url      VARCHAR(512),
    action_text     VARCHAR(64),
    icon            VARCHAR(64),
    extra           TEXT,
    source_module   VARCHAR(32),
    read_status     SMALLINT       NOT NULL DEFAULT 0,
    read_time       TIMESTAMPTZ,
    recall_status   VARCHAR(16)    NOT NULL DEFAULT 'NONE',
    recall_at       TIMESTAMPTZ,
    expired_at      TIMESTAMPTZ,
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT ck_pmn_level_enum     CHECK (level IN ('INFO', 'WARN', 'ERROR', 'URGENT')),
    CONSTRAINT ck_pmn_category_enum  CHECK (category IN ('SYSTEM', 'WORKFLOW', 'ALERT', 'TODO', 'ANNOUNCE')),
    CONSTRAINT ck_pmn_priority_enum  CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_pmn_read_enum      CHECK (read_status IN (0, 1)),
    CONSTRAINT ck_pmn_recall_enum    CHECK (recall_status IN ('NONE', 'RECALLED')),
    CONSTRAINT ck_pmn_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_msg_notification IS '站内通知表: 系统消息/待办/预警/公告统一入口,支持优先级/聚合/撤回/业务跳转';

COMMENT ON COLUMN pmis_msg_notification.id IS '主键 ID';

COMMENT ON COLUMN pmis_msg_notification.title IS '通知标题';

COMMENT ON COLUMN pmis_msg_notification.content IS '通知内容(支持富文本/Markdown)';

COMMENT ON COLUMN pmis_msg_notification.level IS '通知级别: INFO 提示 / WARN 警告 / ERROR 错误 / URGENT 紧急';

COMMENT ON COLUMN pmis_msg_notification.category IS '通知分类: SYSTEM 系统消息 / WORKFLOW 流程审批 / ALERT 预警通知 / TODO 待办 / ANNOUNCE 公告';

COMMENT ON COLUMN pmis_msg_notification.priority IS '发送优先级: LOW 低 / NORMAL 普通 / HIGH 高 / URGENT 紧急(影响排队与聚合)';

COMMENT ON COLUMN pmis_msg_notification.sender_id IS '发送人 ID(系统通知为 SYSTEM)';

COMMENT ON COLUMN pmis_msg_notification.receiver_id IS '接收人 ID(关联 pmis_employee.id)';

COMMENT ON COLUMN pmis_msg_notification.biz_type IS '关联业务类型(如 contract/invoice/risk)';

COMMENT ON COLUMN pmis_msg_notification.biz_id IS '关联业务单据 ID';

COMMENT ON COLUMN pmis_msg_notification.message_group IS '聚合组(同组通知可合并为摘要,如 RISK:contract-123)';

COMMENT ON COLUMN pmis_msg_notification.batch_id IS '聚合批次 ID(关联 pmis_msg_aggregate.id)';

COMMENT ON COLUMN pmis_msg_notification.action_url IS '点击跳转 URL(前端路由或外链)';

COMMENT ON COLUMN pmis_msg_notification.action_text IS '跳转按钮文案(如"去处理")';

COMMENT ON COLUMN pmis_msg_notification.icon IS '通知图标标识(Element Plus icon name)';

COMMENT ON COLUMN pmis_msg_notification.extra IS '扩展字段 JSON(业务自定义透传)';

COMMENT ON COLUMN pmis_msg_notification.source_module IS '来源模块(system/project/workflow/agent)';

COMMENT ON COLUMN pmis_msg_notification.read_status IS '已读状态: 0 未读 / 1 已读';

COMMENT ON COLUMN pmis_msg_notification.read_time IS '首次阅读时间';

COMMENT ON COLUMN pmis_msg_notification.recall_status IS '撤回状态: NONE 未撤回 / RECALLED 已撤回';

COMMENT ON COLUMN pmis_msg_notification.recall_at IS '撤回时间';

COMMENT ON COLUMN pmis_msg_notification.expired_at IS '过期时间(过期后不再展示)';

COMMENT ON COLUMN pmis_msg_notification.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_msg_notification.created_at IS '发送时间';

COMMENT ON COLUMN pmis_msg_notification.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_msg_notification.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_msg_notification.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_msg_notification.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmn_receiver ON pmis_msg_notification (receiver_id, read_status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmn_biz ON pmis_msg_notification (biz_type, biz_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmn_tenant ON pmis_msg_notification(tenant_id);

CREATE INDEX IF NOT EXISTS idx_pmn_tenant_created
    ON pmis_msg_notification(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmn_sender
    ON pmis_msg_notification(sender_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmn_group
    ON pmis_msg_notification(receiver_id, message_group) WHERE deleted = 0 AND message_group IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmn_batch
    ON pmis_msg_notification(batch_id) WHERE deleted = 0 AND batch_id IS NOT NULL;

-- 用户消息偏好表 pmis_msg_preference（免打扰 / 频率上限 / 聚合开关 / 语言）
CREATE TABLE IF NOT EXISTS pmis_msg_preference(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT replace(gen_random_uuid()::text,'-',''),
    user_id           VARCHAR(20)   NOT NULL,
    channel           VARCHAR(32)   NOT NULL,
    biz_type          VARCHAR(64)   NOT NULL DEFAULT '__DEFAULT__',
    enabled           SMALLINT      NOT NULL DEFAULT 1,
    dnd_enabled       SMALLINT      NOT NULL DEFAULT 0,
    dnd_start         VARCHAR(8),
    dnd_end           VARCHAR(8),
    daily_limit       INTEGER,
    hourly_limit      INTEGER,
    digest_enabled    SMALLINT      NOT NULL DEFAULT 0,
    digest_frequency  VARCHAR(16),
    locale            VARCHAR(16),
    extra             TEXT,
    created_by        VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)       NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmp_user_chan_biz UNIQUE (user_id, channel, biz_type, tenant_id, deleted),
    CONSTRAINT ck_pmp_channel_enum  CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'INAPP', 'WEBHOOK', 'DINGTALK', 'WECOM', 'FEISHU')),
    CONSTRAINT ck_pmp_enabled_enum  CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_pmp_dnd_enum      CHECK (dnd_enabled IN (0, 1)),
    CONSTRAINT ck_pmp_digest_enum   CHECK (digest_enabled IN (0, 1)),
    CONSTRAINT ck_pmp_deleted_enum  CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_pmp_daily_nonneg  CHECK (daily_limit IS NULL OR daily_limit >= 0),
    CONSTRAINT ck_pmp_hourly_nonneg CHECK (hourly_limit IS NULL OR hourly_limit >= 0)
);

COMMENT ON TABLE pmis_msg_preference IS '用户消息偏好表: 免打扰时段 / 频率上限 / 聚合开关 / 偏好语言';

COMMENT ON COLUMN pmis_msg_preference.user_id IS '用户 ID(关联 pmis_employee.id)';

COMMENT ON COLUMN pmis_msg_preference.channel IS '通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU';

COMMENT ON COLUMN pmis_msg_preference.biz_type IS '业务类型(__DEFAULT__ 表示该通道全局默认偏好)';

COMMENT ON COLUMN pmis_msg_preference.enabled IS '是否启用该通道: 0 关闭 / 1 开启(关闭后不发送)';

COMMENT ON COLUMN pmis_msg_preference.dnd_enabled IS '免打扰开关: 0 关闭 / 1 开启';

COMMENT ON COLUMN pmis_msg_preference.dnd_start IS '免打扰开始时间 HH:mm(如 22:00)';

COMMENT ON COLUMN pmis_msg_preference.dnd_end IS '免打扰结束时间 HH:mm(如 08:00)';

COMMENT ON COLUMN pmis_msg_preference.daily_limit IS '每日发送上限(超过则暂存或丢弃)';

COMMENT ON COLUMN pmis_msg_preference.hourly_limit IS '每小时发送上限';

COMMENT ON COLUMN pmis_msg_preference.digest_enabled IS '聚合开关: 0 即时发送 / 1 聚合摘要';

COMMENT ON COLUMN pmis_msg_preference.digest_frequency IS '聚合频率: HOURLY / DAILY / WEEKLY';

COMMENT ON COLUMN pmis_msg_preference.locale IS '偏好语言(如 zh-CN / en-US,影响模板 i18n 选择)';

COMMENT ON COLUMN pmis_msg_preference.extra IS '扩展字段 JSON';

CREATE INDEX IF NOT EXISTS idx_pmp_user ON pmis_msg_preference(user_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmp_tenant ON pmis_msg_preference(tenant_id);

-- 订阅关系表 pmis_msg_subscription（用户订阅/退订主题）
CREATE TABLE IF NOT EXISTS pmis_msg_subscription(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT replace(gen_random_uuid()::text,'-',''),
    user_id         VARCHAR(20)   NOT NULL,
    topic_code      VARCHAR(128)  NOT NULL,
    channel         VARCHAR(32)   NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'SUBSCRIBED',
    role_scope      VARCHAR(128),
    extra           TEXT,
    unsubscribed_at TIMESTAMPTZ,
    created_by      VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)       NOT NULL DEFAULT '1',
    CONSTRAINT uk_pms_user_topic_chan UNIQUE (user_id, topic_code, channel, tenant_id, deleted),
    CONSTRAINT ck_pms_channel_enum    CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'INAPP', 'WEBHOOK', 'DINGTALK', 'WECOM', 'FEISHU')),
    CONSTRAINT ck_pms_status_enum     CHECK (status IN ('SUBSCRIBED', 'UNSUBSCRIBED')),
    CONSTRAINT ck_pms_deleted_enum    CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_msg_subscription IS '订阅关系表: 用户对主题(topic_code)在指定通道的订阅/退订状态';

COMMENT ON COLUMN pmis_msg_subscription.user_id IS '用户 ID';

COMMENT ON COLUMN pmis_msg_subscription.topic_code IS '主题编码(如 RISK_ALERT / CONTRACT_APPROVAL / APPROVAL_TODO)';

COMMENT ON COLUMN pmis_msg_subscription.channel IS '通道';

COMMENT ON COLUMN pmis_msg_subscription.status IS '订阅状态: SUBSCRIBED 已订阅 / UNSUBSCRIBED 已退订';

COMMENT ON COLUMN pmis_msg_subscription.role_scope IS '角色范围(如 PM|MEMBER,限定角色内可见性)';

COMMENT ON COLUMN pmis_msg_subscription.extra IS '扩展字段 JSON';

COMMENT ON COLUMN pmis_msg_subscription.unsubscribed_at IS '退订时间(P1-5:仅 status=UNSUBSCRIBED 时有意义)';

CREATE INDEX IF NOT EXISTS idx_pms_user ON pmis_msg_subscription(user_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pms_topic ON pmis_msg_subscription(topic_code, channel) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pms_unsub_status ON pmis_msg_subscription(status, unsubscribed_at) WHERE deleted = 0;

-- ============================================================================
-- [INLINE-OPT] 已统一为单文件 V1.0.0.sql 的最终形态:
--   1) 时间字段 TIMESTAMP → TIMESTAMPTZ
--   2) 审计字段 create_by/create_time → created_by/created_at 规范命名
--   3) tenant_id 改为 NOT NULL DEFAULT 1
--   4) 内联 status/channel/deleted CHECK 约束
--   5) 内联 P0-D3 MQ 投递元信息(msg_id/topic/reconsume_times)至 message_log
--   6) 内联 (tenant_id, created_at DESC) WHERE deleted = 0 复合部分索引
--   7) 内联 provider_trace_id 索引(按服务商回执 ID 反查发送记录)
-- =====================================================
-- PMIS 消息通道模块 DDL
-- 版本: V1.0.0_007 (merged into V1.0.0.sql)
-- 描述: 短信/邮件/推送/站内信/Webhook 发送日志 + 模板
-- =====================================================

-- 消息发送日志表 pmis_msg_log（由原 pmis_message_log 重构升级，新增优先级/聚合/撤回/回执/路由/灰度/重试调度字段）
-- P2-3: 改为 PostgreSQL 月度 RANGE 分区表，按 created_at 分区，便于按时间范围查询与冷数据归档。
-- 分区表主键必须包含分区键 created_at，故采用 (id, created_at) 复合主键。
-- 业务代码通过 MyBatis-Plus BaseMapper 以 id 单字段查询时，PostgreSQL 会扫描所有分区上的本地索引，
-- 配合 partition pruning（带 created_at 范围条件时）能保证查询性能。
CREATE TABLE IF NOT EXISTS pmis_msg_log(
    id                VARCHAR(20)    NOT NULL,
    channel           VARCHAR(32)    NOT NULL,
    biz_type          VARCHAR(64),
    biz_id            VARCHAR(20),
    receiver          VARCHAR(256)   NOT NULL,
    template_code     VARCHAR(128),
    template_params   TEXT,
    content           TEXT,
    status            VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    error_message     TEXT,
    priority          VARCHAR(16)    NOT NULL DEFAULT 'NORMAL',
    sender_id         VARCHAR(20),
    message_group     VARCHAR(64),
    batch_id          VARCHAR(20),
    route_rule_id     VARCHAR(20),
    canary            SMALLINT       NOT NULL DEFAULT 0,
    -- P1-6: 灰度实验键(命中时记录原始 canaryKey=切换前 templateCode,用于 A/B 报表分组;未命中为 NULL)
    canary_key        VARCHAR(64),
    dedup_key         VARCHAR(128),
    recall_status     VARCHAR(16)    NOT NULL DEFAULT 'NONE',
    recall_at         TIMESTAMPTZ,
    receipt_status    VARCHAR(16)    NOT NULL DEFAULT 'NONE',
    receipt_at        TIMESTAMPTZ,
    retry_count       INTEGER        NOT NULL DEFAULT 0,
    next_retry_at     TIMESTAMPTZ,
    -- 三方服务商回执 + 链路追踪
    provider_trace_id VARCHAR(128),
    cost_ms           BIGINT,
    -- P2-4: 发送成本(元),按通道单价计算
    cost              NUMERIC(10,4) DEFAULT 0,
    trace_id          VARCHAR(64),
    -- MQ 投递元信息
    msg_id            VARCHAR(64),
    topic             VARCHAR(128),
    reconsume_times   INTEGER        NOT NULL DEFAULT 0,
    -- P2-6: 级联发送父消息 ID(用于追溯级联关系,顶层消息为 NULL)
    parent_msg_id     VARCHAR(64),
    -- P0-3: 定时发送时间(非空时 status=SCHEDULED, 到期后由调度器触发发送)
    scheduled_at      TIMESTAMPTZ,
    -- 审计字段统一
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    -- 复合主键(分区表要求分区键 created_at 必须在主键中)
    CONSTRAINT pk_pml PRIMARY KEY (id, created_at),
    -- 数据完整性约束
    CONSTRAINT ck_pml_channel_enum      CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'INAPP', 'WEBHOOK', 'DINGTALK', 'WECOM', 'FEISHU')),
    CONSTRAINT ck_pml_status_enum       CHECK (status IN ('PENDING', 'SENDING', 'SUCCESS', 'FAILED', 'RETRY', 'DEAD', 'RECALLED', 'SCHEDULED')),
    CONSTRAINT ck_pml_priority_enum     CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_pml_recall_enum       CHECK (recall_status IN ('NONE', 'RECALLED')),
    CONSTRAINT ck_pml_receipt_enum      CHECK (receipt_status IN ('NONE', 'DELIVERED', 'READ', 'CLICKED', 'FAILED', 'TIMEOUT')),
    CONSTRAINT ck_pml_canary_enum       CHECK (canary IN (0, 1)),
    CONSTRAINT ck_pml_cost_nonneg       CHECK (cost_ms IS NULL OR cost_ms >= 0),
    CONSTRAINT ck_pml_money_nonneg      CHECK (cost IS NULL OR cost >= 0),
    CONSTRAINT ck_pml_reconsume_nonneg  CHECK (reconsume_times >= 0),
    CONSTRAINT ck_pml_retry_nonneg      CHECK (retry_count >= 0),
    CONSTRAINT ck_pml_deleted_enum      CHECK (deleted IN (0, 1))
) PARTITION BY RANGE (created_at);

-- 月度分区: 预创建 2026 全年 12 个分区 + DEFAULT 兜底分区。
-- 归档策略: 由 MsgLogArchiveService 在每月 1 号 DETACH 90 天前分区并重命名为 pmis_msg_log_archive_yyyymm。
-- 后续新增分区也由该服务动态 CREATE,避免人为遗漏。
CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m01 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m02 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m03 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m04 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m05 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m06 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m07 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m08 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');

CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m09 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');

CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m10 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m11 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');

CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m12 PARTITION OF pmis_msg_log FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

-- DEFAULT 分区: 兜底不在预创建范围内的数据,避免插入失败;运维应监控该分区并补建新分区。
CREATE TABLE IF NOT EXISTS pmis_msg_log_default PARTITION OF pmis_msg_log DEFAULT;

COMMENT ON TABLE pmis_msg_log IS '消息发送日志: 全通道发送全量记录,支持优先级/聚合/撤回/回执/路由/灰度/重试调度';

COMMENT ON COLUMN pmis_msg_log.channel IS '发送通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU';

COMMENT ON COLUMN pmis_msg_log.status IS '发送状态: PENDING 待发送 / SENDING 发送中 / SUCCESS 成功 / FAILED 失败 / RETRY 重试中 / DEAD 死信 / RECALLED 已撤回';

COMMENT ON COLUMN pmis_msg_log.priority IS '发送优先级: LOW/NORMAL/HIGH/URGENT(影响排队与并发)';

COMMENT ON COLUMN pmis_msg_log.sender_id IS '触发发送的用户 ID(系统发送为 SYSTEM)';

COMMENT ON COLUMN pmis_msg_log.message_group IS '聚合组(同组消息可合并为摘要发送)';

COMMENT ON COLUMN pmis_msg_log.batch_id IS '聚合批次 ID(关联 pmis_msg_aggregate.id)';

COMMENT ON COLUMN pmis_msg_log.route_rule_id IS '命中的路由规则 ID(关联 pmis_msg_route_rule.id)';

COMMENT ON COLUMN pmis_msg_log.canary IS '是否灰度命中: 0 正式 / 1 灰度';

COMMENT ON COLUMN pmis_msg_log.canary_key IS 'P1-6: 灰度实验键(命中时记录原始 canaryKey=切换前 templateCode,用于 A/B 报表分组;未命中为 NULL)';

COMMENT ON COLUMN pmis_msg_log.dedup_key IS '幂等去重键(用于消费端幂等,Redis SET NX EX)';

COMMENT ON COLUMN pmis_msg_log.recall_status IS '撤回状态: NONE 未撤回 / RECALLED 已撤回';

COMMENT ON COLUMN pmis_msg_log.receipt_at IS '回执到达时间';

COMMENT ON COLUMN pmis_msg_log.receipt_status IS '回执状态: NONE 无 / DELIVERED 已送达 / READ 已读 / CLICKED 已点击 / FAILED 失败 / TIMEOUT 超时(ReceiptPuller 标记)';

COMMENT ON COLUMN pmis_msg_log.retry_count IS '已重试次数';

COMMENT ON COLUMN pmis_msg_log.next_retry_at IS '下次重试时间(退避调度)';

COMMENT ON COLUMN pmis_msg_log.provider_trace_id IS '三方服务商回执 ID';

COMMENT ON COLUMN pmis_msg_log.cost_ms IS '发送耗时(毫秒)';

COMMENT ON COLUMN pmis_msg_log.cost IS 'P2-4: 发送成本(元),按通道单价计算(SMS/EMAIL/PUSH 有成本,IM/INAPP 免费)';

COMMENT ON COLUMN pmis_msg_log.trace_id IS '系统链路追踪 ID';

COMMENT ON COLUMN pmis_msg_log.msg_id IS 'RocketMQ 消息 ID';

COMMENT ON COLUMN pmis_msg_log.topic IS 'RocketMQ Topic(DLQ 消息填充原 Topic)';

COMMENT ON COLUMN pmis_msg_log.reconsume_times IS 'RocketMQ 重试次数';

COMMENT ON COLUMN pmis_msg_log.parent_msg_id IS 'P2-6: 级联发送父消息 ID(顶层消息为 NULL)';

COMMENT ON COLUMN pmis_msg_log.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pml_channel
    ON pmis_msg_log (channel) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pml_status
    ON pmis_msg_log (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pml_biz
    ON pmis_msg_log (biz_type, biz_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pml_receiver
    ON pmis_msg_log (receiver) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pml_tenant_created
    ON pmis_msg_log (tenant_id, created_at DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pml_msg_id
    ON pmis_msg_log (msg_id) WHERE deleted = 0 AND msg_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pml_provider_trace
    ON pmis_msg_log (provider_trace_id) WHERE deleted = 0 AND provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pml_priority
    ON pmis_msg_log (status, priority, next_retry_at) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pml_dedup
    ON pmis_msg_log (dedup_key) WHERE deleted = 0 AND dedup_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pml_batch
    ON pmis_msg_log (batch_id) WHERE deleted = 0 AND batch_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pml_recall
    ON pmis_msg_log (recall_status) WHERE deleted = 0 AND recall_status = 'RECALLED';

CREATE INDEX IF NOT EXISTS idx_pml_retry_due
    ON pmis_msg_log (status, next_retry_at) WHERE deleted = 0 AND status = 'RETRY' AND next_retry_at IS NOT NULL;

-- P2-6: 级联发送父子关系查询索引(按 parent_msg_id 查询某条消息触发的全部级联消息)
CREATE INDEX IF NOT EXISTS idx_pml_parent
    ON pmis_msg_log (parent_msg_id) WHERE deleted = 0 AND parent_msg_id IS NOT NULL;

-- P1-6: 灰度 A/B 报表查询索引(按 canary_key 分组统计实验组数据)
CREATE INDEX IF NOT EXISTS idx_pml_canary_key
    ON pmis_msg_log (canary_key) WHERE deleted = 0 AND canary_key IS NOT NULL;

-- 消息模板表 pmis_msg_template（由原 pmis_message_template 重构升级，新增 i18n/版本/审核/分类/场景）
CREATE TABLE IF NOT EXISTS pmis_msg_template(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT replace(gen_random_uuid()::text,'-',''),
    template_code   VARCHAR(128)   NOT NULL,
    channel         VARCHAR(32)    NOT NULL,
    locale          VARCHAR(16)    NOT NULL DEFAULT 'zh-CN',
    version         VARCHAR(32)    NOT NULL DEFAULT '1.0.0',
    category        VARCHAR(64),
    scene_code      VARCHAR(128),
    subject         VARCHAR(256),
    content         TEXT           NOT NULL,
    provider        VARCHAR(64),
    provider_key    VARCHAR(128),
    sign_name       VARCHAR(64),
    status          VARCHAR(32)    NOT NULL DEFAULT 'ENABLED',
    audit_status    VARCHAR(32)    NOT NULL DEFAULT 'APPROVED',
    audit_by        VARCHAR(20),
    audit_at        TIMESTAMPTZ,
    audit_remark    VARCHAR(512),
    description     VARCHAR(512),
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmt_code_chan_locale_tenant UNIQUE (template_code, channel, locale, tenant_id, deleted),
    CONSTRAINT ck_pmt_channel_enum   CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'INAPP', 'WEBHOOK', 'DINGTALK', 'WECOM', 'FEISHU')),
    CONSTRAINT ck_pmt_status_enum    CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pmt_audit_enum     CHECK (audit_status IN ('DRAFT', 'AUDITING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_pmt_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_msg_template IS '消息模板表: 支持 ${var} 嵌套占位符 / 多语言 i18n / 版本 / 审核 / 分类 / 场景';

COMMENT ON COLUMN pmis_msg_template.template_code IS '模板编码(同 code 不同 channel/locale 形成多版本)';

COMMENT ON COLUMN pmis_msg_template.locale IS '语言区域(如 zh-CN / en-US),影响 i18n 模板选择';

COMMENT ON COLUMN pmis_msg_template.version IS '语义版本(如 1.0.0),支持模板版本回滚';

COMMENT ON COLUMN pmis_msg_template.category IS '模板分类(如 ALERT/APPROVAL/NOTICE/VERIFY)';

COMMENT ON COLUMN pmis_msg_template.scene_code IS '场景编码(如 BUDGET_YELLOW / CONTRACT_SIGN),用于业务侧精确匹配';

COMMENT ON COLUMN pmis_msg_template.audit_status IS '审核状态: DRAFT 草稿 / AUDITING 审核中 / APPROVED 已通过 / REJECTED 已驳回';

COMMENT ON COLUMN pmis_msg_template.audit_by IS '审核人 ID';

COMMENT ON COLUMN pmis_msg_template.audit_at IS '审核时间';

COMMENT ON COLUMN pmis_msg_template.audit_remark IS '审核备注';

CREATE INDEX IF NOT EXISTS idx_pmt_channel
    ON pmis_msg_template (channel) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmt_status
    ON pmis_msg_template (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmt_tenant_status
    ON pmis_msg_template (tenant_id, status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmt_scene
    ON pmis_msg_template (scene_code, channel) WHERE deleted = 0 AND scene_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmt_audit
    ON pmis_msg_template (audit_status) WHERE deleted = 0 AND audit_status IN ('DRAFT', 'AUDITING');

-- 消息路由规则表 pmis_msg_route_rule（条件路由 / 通道降级）
CREATE TABLE IF NOT EXISTS pmis_msg_route_rule(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT replace(gen_random_uuid()::text,'-',''),
    rule_code         VARCHAR(128)  NOT NULL,
    rule_name         VARCHAR(255)  NOT NULL,
    biz_type          VARCHAR(64),
    channel           VARCHAR(32),
    priority          INTEGER       NOT NULL DEFAULT 100,
    condition_expr    TEXT          NOT NULL,
    target_channel    VARCHAR(32)   NOT NULL,
    fallback_channel  VARCHAR(32),
    fallback_chain    VARCHAR(255),
    status            VARCHAR(16)   NOT NULL DEFAULT 'ENABLED',
    description       VARCHAR(512),
    sort_order        INTEGER       NOT NULL DEFAULT 0,
    created_by        VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)       NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmrr_code UNIQUE (rule_code, tenant_id, deleted),
    CONSTRAINT ck_pmrr_chan_enum   CHECK (channel IS NULL OR channel IN ('SMS', 'EMAIL', 'PUSH', 'INAPP', 'WEBHOOK', 'DINGTALK', 'WECOM', 'FEISHU')),
    CONSTRAINT ck_pmrr_target_enum CHECK (target_channel IN ('SMS', 'EMAIL', 'PUSH', 'INAPP', 'WEBHOOK', 'DINGTALK', 'WECOM', 'FEISHU')),
    CONSTRAINT ck_pmrr_fb_enum     CHECK (fallback_channel IS NULL OR fallback_channel IN ('SMS', 'EMAIL', 'PUSH', 'INAPP', 'WEBHOOK', 'DINGTALK', 'WECOM', 'FEISHU')),
    CONSTRAINT ck_pmrr_status_enum CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pmrr_deleted_enum CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_pmrr_priority_nonneg CHECK (priority >= 0)
);

COMMENT ON TABLE pmis_msg_route_rule IS '消息路由规则表: 按 biz_type/channel/条件表达式路由到目标通道,支持降级';

COMMENT ON COLUMN pmis_msg_route_rule.condition_expr IS '路由条件(SpEL 表达式,如 #request.bizType==''ALERT'' and #request.priority==''URGENT'')';

COMMENT ON COLUMN pmis_msg_route_rule.target_channel IS '命中后目标通道';

COMMENT ON COLUMN pmis_msg_route_rule.fallback_channel IS '目标通道发送失败时降级通道(单通道,兼容旧版)';

COMMENT ON COLUMN pmis_msg_route_rule.fallback_chain IS 'P1-8: 多级降级链(逗号分隔通道列表,如 SMS,EMAIL,INAPP),按顺序逐个尝试,优先于 fallback_channel';

CREATE INDEX IF NOT EXISTS idx_pmrt_biz ON pmis_msg_route_rule(biz_type) WHERE deleted = 0 AND status = 'ENABLED';

CREATE INDEX IF NOT EXISTS idx_pmrt_sort ON pmis_msg_route_rule(status, sort_order) WHERE deleted = 0;

-- 消息回执表 pmis_msg_receipt（服务商回执 / 已读 / 点击回调）
CREATE TABLE IF NOT EXISTS pmis_msg_receipt(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT replace(gen_random_uuid()::text,'-',''),
    log_id            VARCHAR(20)   NOT NULL,
    provider_trace_id VARCHAR(128),
    receipt_type      VARCHAR(16)   NOT NULL,
    receipt_time      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider_code     VARCHAR(64),
    provider_msg      VARCHAR(512),
    raw_response      TEXT,
    created_by        VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)       NOT NULL DEFAULT '1',
    CONSTRAINT ck_pmrt_type_enum   CHECK (receipt_type IN ('DELIVERED', 'READ', 'CLICKED', 'FAILED')),
    CONSTRAINT ck_pmrt_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_msg_receipt IS '消息回执表: 服务商送达/已读/点击/失败回调记录';

COMMENT ON COLUMN pmis_msg_receipt.log_id IS '关联 pmis_msg_log.id';

COMMENT ON COLUMN pmis_msg_receipt.receipt_type IS '回执类型: DELIVERED 送达 / READ 已读 / CLICKED 点击 / FAILED 失败';

CREATE INDEX IF NOT EXISTS idx_pmrc_log ON pmis_msg_receipt(log_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmrc_trace ON pmis_msg_receipt(provider_trace_id) WHERE deleted = 0 AND provider_trace_id IS NOT NULL;

-- 聚合批次表 pmis_msg_aggregate（同组消息合并为摘要发送）
CREATE TABLE IF NOT EXISTS pmis_msg_aggregate(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT replace(gen_random_uuid()::text,'-',''),
    aggregate_group   VARCHAR(64)   NOT NULL,
    receiver          VARCHAR(256)  NOT NULL,
    channel           VARCHAR(32)   NOT NULL,
    batch_status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    message_count     INTEGER       NOT NULL DEFAULT 0,
    first_message_at  TIMESTAMPTZ,
    last_message_at   TIMESTAMPTZ,
    scheduled_send_at TIMESTAMPTZ,
    sent_at           TIMESTAMPTZ,
    digest_content    TEXT,
    created_by        VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)       NOT NULL DEFAULT '1',
    CONSTRAINT ck_pmag_chan_enum   CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'INAPP', 'WEBHOOK', 'DINGTALK', 'WECOM', 'FEISHU')),
    CONSTRAINT ck_pmag_status_enum CHECK (batch_status IN ('PENDING', 'READY', 'SENT', 'CANCELLED')),
    CONSTRAINT ck_pmag_count_nonneg CHECK (message_count >= 0),
    CONSTRAINT ck_pmag_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_msg_aggregate IS '聚合批次表: 同 aggregate_group+receiver 的消息按频率合并为摘要发送';

COMMENT ON COLUMN pmis_msg_aggregate.batch_status IS '批次状态: PENDING 攒批中 / READY 就绪待发 / SENT 已发送 / CANCELLED 已取消';

COMMENT ON COLUMN pmis_msg_aggregate.scheduled_send_at IS '计划发送时间(到达后触发摘要发送)';

COMMENT ON COLUMN pmis_msg_aggregate.digest_content IS '聚合后摘要内容(渲染后)';

CREATE INDEX IF NOT EXISTS idx_pmag_group ON pmis_msg_aggregate(aggregate_group, receiver) WHERE deleted = 0 AND batch_status IN ('PENDING', 'READY');

CREATE INDEX IF NOT EXISTS idx_pmag_due ON pmis_msg_aggregate(scheduled_send_at) WHERE deleted = 0 AND batch_status = 'READY' AND scheduled_send_at IS NOT NULL;

-- 灰度桶表 pmis_msg_canary（按 template_code/biz_type 灰度发布）
CREATE TABLE IF NOT EXISTS pmis_msg_canary(
    id                       VARCHAR(20)      PRIMARY KEY DEFAULT replace(gen_random_uuid()::text,'-',''),
    canary_key               VARCHAR(128)  NOT NULL,
    bucket_total             INTEGER       NOT NULL DEFAULT 100,
    bucket_selected          TEXT,
    percentage               INTEGER       NOT NULL DEFAULT 0,
    experiment_template_code VARCHAR(128),
    experiment_channel       VARCHAR(32),
    status                   VARCHAR(16)   NOT NULL DEFAULT 'ENABLED',
    description              VARCHAR(512),
    created_by               VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                  SMALLINT     NOT NULL DEFAULT 0,
    tenant_id                VARCHAR(20)       NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmc_key UNIQUE (canary_key, tenant_id, deleted),
    CONSTRAINT ck_pmc_status_enum CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pmc_pct_range   CHECK (percentage >= 0 AND percentage <= 100),
    CONSTRAINT ck_pmc_bucket_pos  CHECK (bucket_total > 0),
    CONSTRAINT ck_pmc_exp_chan_enum CHECK (experiment_channel IS NULL OR experiment_channel IN ('SMS', 'EMAIL', 'PUSH', 'INAPP', 'WEBHOOK', 'DINGTALK', 'WECOM', 'FEISHU')),
    CONSTRAINT ck_pmc_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_msg_canary IS '灰度桶表: 按 canary_key(template_code/biz_type)做百分比灰度发布,命中后可切换实验模板/通道';

COMMENT ON COLUMN pmis_msg_canary.canary_key IS '灰度键(如 template_code 或 biz_type)';

COMMENT ON COLUMN pmis_msg_canary.bucket_selected IS '命中的桶列表 JSON(如 [0,1,2,...,4] 表示 0-4 号桶命中)';

COMMENT ON COLUMN pmis_msg_canary.percentage IS '灰度比例(0-100)';

COMMENT ON COLUMN pmis_msg_canary.experiment_template_code IS '灰度命中后切换的实验模板编码(可空,空则不切换)';

COMMENT ON COLUMN pmis_msg_canary.experiment_channel IS '灰度命中后切换的实验通道(可空,空则不切换)';

CREATE INDEX IF NOT EXISTS idx_pmc_key ON pmis_msg_canary(canary_key) WHERE deleted = 0 AND status = 'ENABLED';

-- --------------------------------------------------------------------

-- ============================ [022] init pmis alert templates ============================

-- ============================================================
-- V1.0.0_022  智能化升级 P5  消息模板（预警中心）
-- ============================================================
-- 说明：批次 16 智能化升级-预警分级推送消息模板
--   模板命名规范: ALERT_<TYPE>_<LEVEL>  e.g. ALERT_BUDGET_YELLOW
--   占位符使用 ${var} 语法
-- ============================================================

-- 预算黄色预警
INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, created_at, updated_at, deleted)
SELECT 'ALERT_BUDGET_YELLOW', 'INAPP',
       '【预算黄色预警】${projectName}',
       '项目[${projectCode}] ${bizType}本次新增 ${delta} 元，累计已发生 ${usedAfter} 元 / 预算 ${budget} 元，使用率 ${ratio}%',
       'INAPP', 'PMIS', 'ENABLED', '预算黄色预警(80%)', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_BUDGET_YELLOW' AND channel = 'INAPP');

INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, created_at, updated_at, deleted)
SELECT 'ALERT_BUDGET_YELLOW', 'EMAIL',
       '【预算黄色预警】${projectName}',
       '<p>项目[${projectCode}] ${bizType}本次新增 <b>${delta} 元</b>，累计已发生 <b>${usedAfter} 元</b> / 预算 <b>${budget} 元</b>，使用率 <b>${ratio}%</b>，已触及黄色阈值(80%)。</p>',
       'EMAIL', 'PMIS', 'ENABLED', '预算黄色预警邮件', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_BUDGET_YELLOW' AND channel = 'EMAIL');

-- 预算红色预警
INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, created_at, updated_at, deleted)
SELECT 'ALERT_BUDGET_RED', 'INAPP',
       '【预算红色预警】${projectName}',
       '项目[${projectCode}] ${bizType}本次新增 ${delta} 元，累计已发生 ${usedAfter} 元 / 预算 ${budget} 元，使用率 ${ratio}%，已触及红色阈值(95%)，请立即关注',
       'INAPP', 'PMIS', 'ENABLED', '预算红色预警(95%)', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_BUDGET_RED' AND channel = 'INAPP');

INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, created_at, updated_at, deleted)
SELECT 'ALERT_BUDGET_RED', 'EMAIL',
       '【预算红色预警】${projectName}',
       '<p>项目[${projectCode}] ${bizType}本次新增 <b>${delta} 元</b>，累计已发生 <b>${usedAfter} 元</b> / 预算 <b>${budget} 元</b>，使用率 <b>${ratio}%</b>，已触及红色阈值(95%)，请立即关注。</p>',
       'EMAIL', 'PMIS', 'ENABLED', '预算红色预警邮件', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_BUDGET_RED' AND channel = 'EMAIL');

-- EVM 红色预警
INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, created_at, updated_at, deleted)
SELECT 'ALERT_EVM_RED', 'INAPP',
       '【EVM 红色预警】${title}',
       '${content}',
       'INAPP', 'PMIS', 'ENABLED', 'EVM 红色预警(CPI<0.85 或 SPI<0.85)', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_EVM_RED' AND channel = 'INAPP');

INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, created_at, updated_at, deleted)
SELECT 'ALERT_EVM_RED', 'EMAIL',
       '【EVM 红色预警】${title}',
       '<p>${content}</p>',
       'EMAIL', 'PMIS', 'ENABLED', 'EVM 红色预警邮件', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_EVM_RED' AND channel = 'EMAIL');

-- SLA 红色预警（工单超时）
INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, created_at, updated_at, deleted)
SELECT 'ALERT_SLA_RED', 'INAPP',
       '【SLA 红色预警】工单 ${alertCode} 超时',
       '${content}',
       'INAPP', 'PMIS', 'ENABLED', '运维工单 SLA 超时红色预警', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_SLA_RED' AND channel = 'INAPP');

-- 通用黄色预警兜底
INSERT INTO pmis_message_template (template_code, channel, subject, content, provider, sign_name, status, description, tenant_id, created_at, updated_at, deleted)
SELECT 'ALERT_OTHER_YELLOW', 'INAPP',
       '【黄色预警】${title}',
       '${content}',
       'INAPP', 'PMIS', 'ENABLED', '黄色预警通用兜底模板', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pmis_message_template WHERE template_code = 'ALERT_OTHER_YELLOW' AND channel = 'INAPP');

-- 注：pmis_voucher 表尚未创建，相关索引暂时注释，待凭证表落地后启用
-- CREATE INDEX IF NOT EXISTS idx_pmis_voucher_period_status
--     ON pmis_voucher (period, status, created_at DESC);

-- =====================================================================
--  7) 时区/时间相关 BRIN 索引（日志/审计表 100w+ 行）
-- =====================================================================
-- P1-4: pmis_operation_log 的 BRIN 索引已上移到父表定义处(分区自动传播),此处跳过
--       pmis_message_log 仍非分区表,保留原 BRIN
CREATE INDEX IF NOT EXISTS idx_pmis_message_log_brin_sent
    ON pmis_message_log USING BRIN (created_at)
    WITH (pages_per_range = 32);

-- 15. 通知
ALTER TABLE pmis_notification ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_notification_tenant ON pmis_notification(tenant_id);

CREATE INDEX IF NOT EXISTS idx_notification_tenant_created
    ON pmis_notification(tenant_id, created_at DESC) WHERE deleted = 0;

-- 通知-发送人：按 sender_id 查询"我发出的通知"
CREATE INDEX IF NOT EXISTS idx_pmis_notif_sender
    ON pmis_notification(sender_id) WHERE deleted = 0;

ANALYZE pmis_notification;

-- ====================================================================
-- >>>>>>>>>> END OF SUPPLEMENT
-- ====================================================================

-- ====================================================================
-- P0-2: 消息批次表（异步批量发送）
-- ====================================================================
CREATE TABLE IF NOT EXISTS pmis_msg_batch(
    id                VARCHAR(20)    NOT NULL,
    batch_id          VARCHAR(64)    NOT NULL,
    batch_name        VARCHAR(128),
    channel           VARCHAR(32),
    template_code     VARCHAR(128),
    biz_type          VARCHAR(64),
    total             INTEGER        NOT NULL DEFAULT 0,
    success           INTEGER        NOT NULL DEFAULT 0,
    failed            INTEGER        NOT NULL DEFAULT 0,
    skipped           INTEGER        NOT NULL DEFAULT 0,
    status            VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    audience_source   VARCHAR(128),
    error_message     TEXT,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    sender_id         VARCHAR(20),
    created_by        VARCHAR(20)    NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)    NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)    NOT NULL DEFAULT '1',
    CONSTRAINT pk_pmb PRIMARY KEY (id),
    CONSTRAINT uk_pmb_batch_id UNIQUE (batch_id),
    CONSTRAINT ck_pmb_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_pmb_batch_id ON pmis_msg_batch(batch_id);

CREATE INDEX IF NOT EXISTS idx_pmb_status ON pmis_msg_batch(status);

CREATE INDEX IF NOT EXISTS idx_pmb_created_at ON pmis_msg_batch(created_at);

-- ====================================================================
-- P1-6: 消息模板版本历史表
-- ====================================================================
CREATE TABLE IF NOT EXISTS pmis_msg_template_version(
    id                VARCHAR(20)    NOT NULL,
    template_code     VARCHAR(128)   NOT NULL,
    version           INTEGER        NOT NULL,
    content           TEXT,
    variable_defs     TEXT,
    audit_status      VARCHAR(16)    NOT NULL DEFAULT 'APPROVED',
    auditor           VARCHAR(20),
    audit_remark      VARCHAR(512),
    created_by        VARCHAR(20)    NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)    NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)    NOT NULL DEFAULT '1',
    CONSTRAINT pk_pmtv PRIMARY KEY (id),
    CONSTRAINT uk_pmtv_code_version UNIQUE (template_code, version),
    CONSTRAINT ck_pmtv_audit_status CHECK (audit_status IN ('APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_pmtv_template_code ON pmis_msg_template_version(template_code);

