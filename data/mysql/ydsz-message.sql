-- ============================================================================
-- ydsz-cloud: ydsz-message（消息中心模块）MySQL DDL 脚本
-- ----------------------------------------------------------------------------
-- 说明: 基于 ydsz-message-infra 模块实体类整理的完整建表脚本
--       (MsgTemplate / MsgTemplateVersion / MsgNotification / MsgLog /
--        MsgBatch / MsgReceipt / MsgTrace / MsgSubscription / MsgPreference /
--        MsgOffline / MsgUserChannel / MsgVariableSource / MsgRouteRule /
--        MsgTenantConfig / MsgCanary / MsgFeedback / MsgAggregate / OutboxEvent)
-- 规范:
--   - 主键为应用层 Snowflake ID（String → VARCHAR(32)）
--   - 公共列按实体继承链累积:
--       MpBaseIdEntity     → id
--       MpBaseAuditEntity  → + created_by / created_at / updated_by / updated_at
--       MpSimpleEntity     → + deleted / status / tenant_id
--       MpBaseEntity       → + revision（乐观锁）
--   - 禁止物理外键，逻辑外键列加普通索引
--   - 日志/轨迹/回执类表对业务时间列与发送状态列加索引
-- 字符集: utf8mb4
-- 日期: 2026-08-25
-- @author ydsz-team
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 消息模板主表（支持 ${var} 嵌套占位符 / 多语言 i18n / 版本 / 审核 / 分类 / 场景）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_template (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    template_code   VARCHAR(64)     NOT NULL COMMENT '模板唯一编码（业务标识）',
    channel         VARCHAR(32)     NOT NULL COMMENT '发送通道（SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/DINGTALK_WORK/WECOM/WECOM_APP/FEISHU/WX_MINI/ALIPAY_MINI）',
    locale          VARCHAR(16)     DEFAULT 'zh-CN' COMMENT '语言区域（如 zh-CN / en-US，影响 i18n 选择）',
    version         VARCHAR(32)     NOT NULL DEFAULT '1' COMMENT '模板版本号',
    category        VARCHAR(64)     DEFAULT NULL COMMENT '模板分类',
    scene_code      VARCHAR(64)     DEFAULT NULL COMMENT '场景编码',
    subject         VARCHAR(255)    DEFAULT NULL COMMENT '消息标题',
    content         TEXT            NOT NULL COMMENT '模板内容（支持 ${var} 嵌套占位符）',
    provider        VARCHAR(64)     DEFAULT NULL COMMENT '服务商编码',
    provider_key    VARCHAR(128)    DEFAULT NULL COMMENT '服务商侧模板 Key',
    sign_name       VARCHAR(128)    DEFAULT NULL COMMENT '签名名称（如短信签名）',
    status          VARCHAR(32)     NOT NULL DEFAULT 'DISABLED' COMMENT '模板状态: ENABLED 启用 / DISABLED 禁用',
    audit_status    VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态: DRAFT 草稿 / AUDITING 审核中 / APPROVED 已通过 / REJECTED 已驳回',
    audit_by        VARCHAR(64)     DEFAULT NULL COMMENT '审核人',
    audit_at        DATETIME        DEFAULT NULL COMMENT '审核时间',
    audit_remark    VARCHAR(512)    DEFAULT NULL COMMENT '审核意见',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '描述说明',
    variable_defs   JSON            DEFAULT NULL COMMENT '模板变量定义（JSON）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_template_code UNIQUE (template_code, tenant_id),
    INDEX idx_channel (channel),
    INDEX idx_scene_code (scene_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息模板主表';

-- ----------------------------------------------------------------------------
-- 2. 消息模板版本历史表（每次审核通过/拒绝的版本快照，支持版本回滚与历史对比）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_template_version (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    template_code   VARCHAR(64)     NOT NULL COMMENT '模板编码（关联 ydsz_msg_template.template_code）',
    version         INT             NOT NULL COMMENT '版本号（每次审核通过递增，如 1, 2, 3）',
    content         TEXT            NOT NULL COMMENT '模板内容快照',
    variable_defs   JSON            DEFAULT NULL COMMENT '模板变量定义快照（JSON）',
    audit_status    VARCHAR(32)     DEFAULT NULL COMMENT '审核状态: APPROVED 已通过 / REJECTED 已拒绝',
    auditor         VARCHAR(64)     DEFAULT NULL COMMENT '审核人',
    audit_remark    VARCHAR(512)    DEFAULT NULL COMMENT '审核意见',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_tpl_version UNIQUE (template_code, version),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息模板版本历史表';

-- ----------------------------------------------------------------------------
-- 3. 站内通知表（系统消息/待办/预警/公告统一入口）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_notification (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    title           VARCHAR(255)    NOT NULL COMMENT '通知标题',
    content         TEXT            COMMENT '通知内容',
    level           VARCHAR(32)     NOT NULL DEFAULT 'INFO' COMMENT '通知级别: INFO 提示 / WARN 警告 / ERROR 错误 / URGENT 紧急',
    category        VARCHAR(32)     NOT NULL DEFAULT 'SYSTEM' COMMENT '通知分类: SYSTEM 系统 / WORKFLOW 流程 / ALERT 告警 / TO_DO 待办 / ANNOUNCE 公告',
    priority        VARCHAR(32)     NOT NULL DEFAULT 'NORMAL' COMMENT '发送优先级: LOW / NORMAL / HIGH / URGENT',
    sender_id       VARCHAR(32)     DEFAULT NULL COMMENT '发送人用户 ID',
    receiver_id     VARCHAR(32)     NOT NULL COMMENT '接收人用户 ID',
    biz_type        VARCHAR(64)     DEFAULT NULL COMMENT '业务类型',
    biz_id          VARCHAR(64)     DEFAULT NULL COMMENT '业务单据 ID',
    message_group   VARCHAR(64)     DEFAULT NULL COMMENT '消息分组（同组消息合并展示）',
    batch_id        VARCHAR(64)     DEFAULT NULL COMMENT '批次 ID（关联 ydsz_msg_batch.batch_id）',
    action_url      VARCHAR(1024)   DEFAULT NULL COMMENT '跳转链接',
    action_text     VARCHAR(128)    DEFAULT NULL COMMENT '跳转文案',
    icon            VARCHAR(255)    DEFAULT NULL COMMENT '图标',
    extra           JSON            DEFAULT NULL COMMENT '扩展信息（JSON）',
    source_module   VARCHAR(64)     DEFAULT NULL COMMENT '来源模块',
    read_status     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '已读状态: 0 未读 / 1 已读',
    read_time       DATETIME        DEFAULT NULL COMMENT '已读时间',
    recall_status   VARCHAR(32)     NOT NULL DEFAULT 'NONE' COMMENT '撤回状态: NONE 未撤回 / RECALLED 已撤回',
    recall_at       DATETIME        DEFAULT NULL COMMENT '撤回时间',
    expired_at      DATETIME        DEFAULT NULL COMMENT '过期时间',
    mention_user_ids JSON           DEFAULT NULL COMMENT '提及用户 ID 列表（JSON 数组）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_receiver_read (receiver_id, read_status),
    INDEX idx_biz (biz_type, biz_id),
    INDEX idx_batch_id (batch_id),
    INDEX idx_message_group (message_group),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表';

-- ----------------------------------------------------------------------------
-- 4. 用户通道绑定表（userId → 各通道联系方式映射）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_user_channel (
    id                VARCHAR(32)   PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id           VARCHAR(32)   NOT NULL COMMENT '用户 ID（关联 ydsz_employee.id）',
    channel_type      VARCHAR(32)   NOT NULL COMMENT '通道类型: SMS/EMAIL/PUSH/DINGTALK/WECOM/FEISHU 等',
    channel_user_id   VARCHAR(128)  NOT NULL COMMENT '通道用户标识（手机号/邮箱/钉钉userId/企微userId/飞书userId/个推cid）',
    verified          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已验证: 0 未验证 / 1 已验证',
    is_primary        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否主绑定: 0 否 / 1 是（同通道多绑定时优先使用主绑定）',
    extra             JSON          DEFAULT NULL COMMENT '扩展字段（JSON，如 deviceToken / openId 等）',
    status            VARCHAR(32)   DEFAULT NULL COMMENT '状态标识',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_user_channel UNIQUE (user_id, channel_type, channel_user_id),
    INDEX idx_channel_user_id (channel_user_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通道绑定表';

-- ----------------------------------------------------------------------------
-- 5. 订阅关系表（用户对主题 topic_code 在指定通道的订阅/退订状态）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_subscription (
    id                VARCHAR(32)   PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id           VARCHAR(32)   NOT NULL COMMENT '用户 ID',
    topic_code        VARCHAR(64)   NOT NULL COMMENT '主题编码（如 RISK_ALERT / CONTRACT_APPROVAL / APPROVAL_TODO）',
    channel           VARCHAR(32)   NOT NULL COMMENT '通道',
    status            VARCHAR(32)   NOT NULL DEFAULT 'SUBSCRIBED' COMMENT '订阅状态: SUBSCRIBED 已订阅 / UNSUBSCRIBED 已退订',
    role_scope        VARCHAR(128)  DEFAULT NULL COMMENT '角色范围（如 PM|MEMBER，限定角色内可见性）',
    extra             JSON          DEFAULT NULL COMMENT '扩展字段（JSON）',
    unsubscribed_at   DATETIME      DEFAULT NULL COMMENT '退订时间（仅 status=UNSUBSCRIBED 时有意义）',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_subscription UNIQUE (user_id, topic_code, channel),
    INDEX idx_topic_code (topic_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅关系表';

-- ----------------------------------------------------------------------------
-- 6. 用户消息偏好表（免打扰时段 / 频率上限 / 聚合开关 / 偏好语言）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_preference (
    id                VARCHAR(32)   PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id           VARCHAR(32)   NOT NULL COMMENT '用户 ID（关联 ydsz_employee.id）',
    channel           VARCHAR(32)   NOT NULL COMMENT '通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU',
    biz_type          VARCHAR(64)   NOT NULL DEFAULT '__DEFAULT__' COMMENT '业务类型（__DEFAULT__ 表示该通道全局默认偏好）',
    enabled           TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用该通道: 0 关闭 / 1 开启（关闭后不发送）',
    dnd_enabled       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '免打扰开关: 0 关闭 / 1 开启',
    dnd_start         VARCHAR(8)    DEFAULT NULL COMMENT '免打扰开始时间 HH:mm（如 22:00）',
    dnd_end           VARCHAR(8)    DEFAULT NULL COMMENT '免打扰结束时间 HH:mm（如 08:00）',
    daily_limit       INT           DEFAULT NULL COMMENT '每日发送上限（超过则暂存或丢弃）',
    hourly_limit      INT           DEFAULT NULL COMMENT '每小时发送上限',
    digest_enabled    TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '聚合开关: 0 即时发送 / 1 聚合摘要',
    digest_frequency  VARCHAR(32)   DEFAULT NULL COMMENT '聚合频率: HOURLY / DAILY / WEEKLY',
    locale            VARCHAR(16)   DEFAULT NULL COMMENT '偏好语言（如 zh-CN / en-US，影响模板 i18n 选择）',
    extra             JSON          DEFAULT NULL COMMENT '扩展字段（JSON）',
    status            VARCHAR(32)   DEFAULT NULL COMMENT '状态标识',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_preference UNIQUE (user_id, channel, biz_type),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息偏好表';

-- ----------------------------------------------------------------------------
-- 7. 消息路由规则表（按 biz_type/channel/条件表达式路由到目标通道，支持降级）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_route_rule (
    id                VARCHAR(32)   PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    rule_code         VARCHAR(64)   NOT NULL COMMENT '规则编码（唯一）',
    rule_name         VARCHAR(128)  NOT NULL COMMENT '规则名称',
    biz_type          VARCHAR(64)   DEFAULT NULL COMMENT '业务类型',
    channel           VARCHAR(32)   DEFAULT NULL COMMENT '通道',
    priority          INT           DEFAULT NULL COMMENT '优先级（数值越小越优先）',
    condition_expr    VARCHAR(512)  DEFAULT NULL COMMENT '路由条件（SpEL 表达式）',
    target_channel    VARCHAR(32)   NOT NULL COMMENT '命中后目标通道',
    fallback_channel  VARCHAR(32)   DEFAULT NULL COMMENT '目标通道发送失败时降级通道',
    description       VARCHAR(512)  DEFAULT NULL COMMENT '描述说明',
    sort_order        INT           DEFAULT NULL COMMENT '排序序号',
    status            VARCHAR(32)   DEFAULT NULL COMMENT '状态标识',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_rule_code UNIQUE (rule_code, tenant_id),
    INDEX idx_biz_channel (biz_type, channel),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息路由规则表';

-- ----------------------------------------------------------------------------
-- 8. 消息变量数据源绑定表（模板变量绑定 BEAN/SQL/HTTP/STATIC 数据源）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_variable_source (
    id                VARCHAR(32)   PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    template_code     VARCHAR(64)   NOT NULL COMMENT '模板编码',
    variable_name     VARCHAR(64)   NOT NULL COMMENT '变量名（与模板 ${var} 对应）',
    source_type       VARCHAR(32)   NOT NULL COMMENT '数据源类型: BEAN / SQL / HTTP / STATIC',
    source_expr       VARCHAR(512)  NOT NULL COMMENT '数据源表达式',
    cache_ttl         INT           DEFAULT NULL COMMENT '缓存有效期（秒），0=不缓存',
    description       VARCHAR(512)  DEFAULT NULL COMMENT '描述说明',
    status            VARCHAR(32)   DEFAULT NULL COMMENT '状态标识',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_variable UNIQUE (template_code, variable_name),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息变量数据源绑定表';

-- ----------------------------------------------------------------------------
-- 9. 灰度实验表（支撑消息模板 A/B 对照实验）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_canary (
    id                VARCHAR(32)   PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    canary_key        VARCHAR(128)  NOT NULL COMMENT '灰度实验唯一键，格式 canary_{templateCode}_{timestamp}',
    experiment_name   VARCHAR(128)  NOT NULL COMMENT 'A/B 实验名称',
    template_code     VARCHAR(64)   NOT NULL COMMENT '关联模板编码',
    channel           VARCHAR(32)   NOT NULL COMMENT '通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU',
    bucket_total      INT           NOT NULL DEFAULT 100 COMMENT '总分桶数（默认 100）',
    bucket_selected   INT           NOT NULL DEFAULT 0 COMMENT '命中桶号上限（桶号 < bucketSelected 归入 VARIANT 组）',
    percentage        INT           NOT NULL DEFAULT 0 COMMENT '当前放量百分比（0~100）',
    experiment_group  VARCHAR(32)   DEFAULT NULL COMMENT '实验组: CONTROL 对照组 / VARIANT 实验组',
    metrics_goal      VARCHAR(32)   DEFAULT NULL COMMENT '目标指标: DELIVERY_RATE 送达率 / READ_RATE 阅读率 / CLICK_RATE 点击率',
    status            VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE' COMMENT '实验状态: ACTIVE 运行中 / PAUSED 已暂停 / COMPLETED 已结束',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_canary_key UNIQUE (canary_key),
    INDEX idx_template_code (template_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灰度实验表';

-- ----------------------------------------------------------------------------
-- 10. 多租户消息配置表（租户级发送配额与通道覆盖）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_tenant_config (
    id                  VARCHAR(32) PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32) NOT NULL COMMENT '租户 ID',
    tenant_name         VARCHAR(128) DEFAULT NULL COMMENT '租户名称',
    daily_limit         BIGINT      DEFAULT NULL COMMENT '租户级每日发送上限（null 表示使用全局默认值）',
    hourly_limit        BIGINT      DEFAULT NULL COMMENT '租户级每小时发送上限（null 表示使用全局默认值）',
    channel_overrides   JSON        DEFAULT NULL COMMENT '租户级通道开关（JSON Map，如 {"SMS": true, "EMAIL": false}）',
    provider_overrides  JSON        DEFAULT NULL COMMENT '租户级通道映射（JSON Map，如 {"SMS": "aliyun", "EMAIL": "sendgrid"}）',
    status              VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '配置状态: ENABLED / DISABLED',
    CONSTRAINT uk_tenant_id UNIQUE (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多租户消息配置表';

-- ----------------------------------------------------------------------------
-- 11. 消息发送批次表（异步批量发送的批次状态与进度）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_batch (
    id                VARCHAR(32)   PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    batch_id          VARCHAR(64)   NOT NULL COMMENT '批次 ID（业务侧生成，全局唯一）',
    batch_name        VARCHAR(128)  DEFAULT NULL COMMENT '批次名称',
    channel           VARCHAR(32)   NOT NULL COMMENT '发送通道',
    template_code     VARCHAR(64)   DEFAULT NULL COMMENT '模板编码',
    biz_type          VARCHAR(64)   DEFAULT NULL COMMENT '业务类型',
    total             INT           NOT NULL DEFAULT 0 COMMENT '总数',
    success           INT           NOT NULL DEFAULT 0 COMMENT '成功数',
    failed            INT           NOT NULL DEFAULT 0 COMMENT '失败数',
    skipped           INT           NOT NULL DEFAULT 0 COMMENT '跳过数（限流/拦截）',
    status            VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT '批次状态: PENDING 待处理 / PROCESSING 处理中 / COMPLETED 已完成 / FAILED 失败',
    audience_source   VARCHAR(128)  DEFAULT NULL COMMENT '人群包来源（CSV 文件名 / 标签 ID）',
    error_message     VARCHAR(512)  DEFAULT NULL COMMENT '错误信息',
    started_at        DATETIME      DEFAULT NULL COMMENT '开始处理时间',
    completed_at      DATETIME      DEFAULT NULL COMMENT '完成时间',
    sender_id         VARCHAR(32)   DEFAULT NULL COMMENT '触发发送的用户 ID',
    payload           JSON          DEFAULT NULL COMMENT '消息请求列表 JSON（断点续传恢复用）',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_batch_id UNIQUE (batch_id),
    INDEX idx_status (status),
    INDEX idx_sender_id (sender_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息发送批次表';

-- ----------------------------------------------------------------------------
-- 12. 聚合批次表（同 aggregate_group+receiver 的消息按频率合并为摘要发送）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_aggregate (
    id                 VARCHAR(32)  PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id          VARCHAR(32)  NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    aggregate_group    VARCHAR(64)  NOT NULL COMMENT '聚合组',
    receiver           VARCHAR(128) NOT NULL COMMENT '接收人',
    channel            VARCHAR(32)  NOT NULL COMMENT '通道',
    batch_status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '批次状态: PENDING 攒批中 / READY 就绪待发 / SENT 已发送 / CANCELLED 已取消',
    message_count      INT          NOT NULL DEFAULT 0 COMMENT '消息数量',
    first_message_at   DATETIME     DEFAULT NULL COMMENT '首条消息时间',
    last_message_at    DATETIME     DEFAULT NULL COMMENT '末条消息时间',
    scheduled_send_at  DATETIME     DEFAULT NULL COMMENT '计划发送时间（到达后触发摘要发送）',
    sent_at            DATETIME     DEFAULT NULL COMMENT '实际发送时间',
    digest_content     TEXT         COMMENT '聚合后摘要内容（渲染后）',
    status             VARCHAR(32)  DEFAULT NULL COMMENT '状态标识',
    deleted            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision           INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by         VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    updated_by         VARCHAR(64)  DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_group_receiver (aggregate_group, receiver),
    INDEX idx_batch_status (batch_status),
    INDEX idx_scheduled_send_at (scheduled_send_at),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聚合批次表';

-- ----------------------------------------------------------------------------
-- 13. 离线消息持久化表（Redis 溢出持久化，支持 30 天回溯）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_offline (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id         VARCHAR(32)     NOT NULL COMMENT '接收人用户 ID',
    msg_type        VARCHAR(32)     DEFAULT NULL COMMENT '消息类型标签（如 NOTIFICATION / ALERT）',
    payload         JSON            NOT NULL COMMENT '消息内容（JSON）',
    msg_timestamp   BIGINT          DEFAULT NULL COMMENT '消息时间戳（毫秒）',
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '推送状态: PENDING 待推送 / PUSHED 已推送 / EXPIRED 已过期',
    pushed_at       DATETIME        DEFAULT NULL COMMENT '推送时间',
    expired_at      DATETIME        DEFAULT NULL COMMENT '过期时间（默认 createdAt + 30 天）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_user_status (user_id, status),
    INDEX idx_expired_at (expired_at),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离线消息持久化表';

-- ----------------------------------------------------------------------------
-- 14. 消息发送日志表（全通道发送全量记录的事实表，消息中心核心表）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_log (
    id                VARCHAR(32)   PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    channel           VARCHAR(32)   NOT NULL COMMENT '发送通道（SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/DINGTALK_WORK/WECOM/WECOM_APP/FEISHU/WX_MINI/ALIPAY_MINI）',
    biz_type          VARCHAR(64)   DEFAULT NULL COMMENT '业务类型',
    biz_id            VARCHAR(64)   DEFAULT NULL COMMENT '业务单据 ID',
    receiver          VARCHAR(128)  DEFAULT NULL COMMENT '接收人（手机号/邮箱/通道用户标识等）',
    template_code     VARCHAR(64)   DEFAULT NULL COMMENT '模板编码',
    template_params   JSON          DEFAULT NULL COMMENT '模板参数（JSON）',
    content           TEXT          COMMENT '消息内容',
    status            VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT '发送状态: PENDING 待发送 / SENDING 发送中 / SUCCESS 成功 / FAILED 失败 / RETRY 重试中 / DEAD 死信 / RECALLED 已撤回 / SCHEDULED 定时 / SKIPPED 跳过',
    error_message     TEXT          COMMENT '失败错误信息',
    priority          VARCHAR(32)   NOT NULL DEFAULT 'NORMAL' COMMENT '发送优先级: LOW / NORMAL / HIGH / URGENT',
    sender_id         VARCHAR(32)   DEFAULT NULL COMMENT '发送人用户 ID',
    message_group     VARCHAR(64)   DEFAULT NULL COMMENT '消息分组',
    batch_id          VARCHAR(64)   DEFAULT NULL COMMENT '批次 ID（关联 ydsz_msg_batch.batch_id）',
    route_rule_id     VARCHAR(32)   DEFAULT NULL COMMENT '命中的路由规则 ID（关联 ydsz_msg_route_rule.id）',
    canary            TINYINT(1)    DEFAULT 0 COMMENT '是否命中灰度: 0 否 / 1 是',
    canary_key        VARCHAR(128)  DEFAULT NULL COMMENT '灰度实验标识（关联 ydsz_msg_canary.canary_key）',
    dedup_key         VARCHAR(128)  DEFAULT NULL COMMENT '幂等去重键',
    recall_status     VARCHAR(32)   NOT NULL DEFAULT 'NONE' COMMENT '撤回状态: NONE 未撤回 / RECALLED 已撤回',
    recall_at         DATETIME      DEFAULT NULL COMMENT '撤回时间',
    receipt_status    VARCHAR(32)   NOT NULL DEFAULT 'NONE' COMMENT '回执状态: NONE 无回执 / DELIVERED 已送达 / READ 已读 / CLICKED 已点击 / FAILED 投递失败 / TIMEOUT 回执超时',
    receipt_at        DATETIME      DEFAULT NULL COMMENT '回执时间',
    retry_count       INT           NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_at     DATETIME      DEFAULT NULL COMMENT '下次重试时间',
    provider_trace_id VARCHAR(128)  DEFAULT NULL COMMENT '服务商追踪/回执 ID',
    cost_ms           BIGINT        DEFAULT NULL COMMENT '发送耗时（毫秒）',
    cost              DECIMAL(20,6) DEFAULT NULL COMMENT '发送费用',
    trace_id          VARCHAR(64)   DEFAULT NULL COMMENT '链路追踪 ID（跨服务链路串联）',
    msg_id            VARCHAR(64)   DEFAULT NULL COMMENT '消息 ID（全局唯一，串联轨迹/反馈链路）',
    topic             VARCHAR(128)  DEFAULT NULL COMMENT '订阅主题编码',
    reconsume_times   INT           DEFAULT NULL COMMENT 'MQ 重新消费次数',
    parent_msg_id     VARCHAR(64)   DEFAULT NULL COMMENT '父消息 ID（级联消息溯源）',
    scheduled_at      DATETIME      DEFAULT NULL COMMENT '定时发送时间',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_status_created (status, created_at),
    INDEX idx_receiver (receiver),
    INDEX idx_biz (biz_type, biz_id),
    INDEX idx_template_code (template_code),
    INDEX idx_msg_id (msg_id),
    INDEX idx_trace_id (trace_id),
    INDEX idx_batch_id (batch_id),
    INDEX idx_dedup_key (dedup_key),
    INDEX idx_provider_trace_id (provider_trace_id),
    INDEX idx_scheduled_at (scheduled_at),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息发送日志表';

-- ----------------------------------------------------------------------------
-- 15. 消息回执表（服务商送达/已读/点击/失败回调记录）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_receipt (
    id                VARCHAR(32)   PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    log_id            VARCHAR(32)   NOT NULL COMMENT '关联 ydsz_msg_log.id',
    provider_trace_id VARCHAR(128)  DEFAULT NULL COMMENT '三方服务商回执 ID',
    receipt_type      VARCHAR(32)   NOT NULL COMMENT '回执类型: DELIVERED 送达 / READ 已读 / CLICKED 点击 / FAILED 失败',
    receipt_time      DATETIME      NOT NULL COMMENT '回执时间',
    provider_code     VARCHAR(64)   DEFAULT NULL COMMENT '供应商编码',
    provider_msg      VARCHAR(512)  DEFAULT NULL COMMENT '供应商消息',
    raw_response      JSON          DEFAULT NULL COMMENT '原始响应（JSON）',
    status            VARCHAR(32)   DEFAULT NULL COMMENT '状态标识',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_log_id (log_id),
    INDEX idx_receipt_time (receipt_time),
    INDEX idx_provider_trace_id (provider_trace_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息回执表';

-- ----------------------------------------------------------------------------
-- 16. 消息轨迹表（消息从接入到投递全链路的关键节点记录）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_trace (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    msg_id          VARCHAR(64)     NOT NULL COMMENT '消息 ID（关联 ydsz_msg_log.msg_id）',
    trace_id        VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID（关联 ydsz_msg_log.trace_id，用于跨服务链路串联）',
    node            VARCHAR(64)     NOT NULL COMMENT '轨迹节点类型（RECEIVED/CHANNEL_CHECK/ROUTE_MATCHED/CANARY_HIT/SUBSCRIPTION_CHECK/PREFERENCE_CHECK/DEDUP_CHECK/RATE_LIMIT_CHECK/TEMPLATE_LOADED/TEMPLATE_RENDERED/SENSITIVE_FILTERED/PERSISTED/SCHEDULED/AGGREGATED/DISPATCH_START/DISPATCH_SUCCESS/FALLBACK/RETRY/SEND_FAILED/RECEIPT_RECEIVED/RECALLED/CASCADE_SENT）',
    status          VARCHAR(32)     NOT NULL COMMENT '节点状态: SUCCESS / FAILED / SKIPPED / PENDING',
    channel         VARCHAR(32)     DEFAULT NULL COMMENT '通道（节点关联的通道，部分节点如 RECEIVED 无通道则为 NULL）',
    receiver        VARCHAR(128)    DEFAULT NULL COMMENT '接收人（脱敏后的）',
    biz_type        VARCHAR(64)     DEFAULT NULL COMMENT '业务类型',
    biz_id          VARCHAR(64)     DEFAULT NULL COMMENT '业务单据 ID',
    template_code   VARCHAR(64)     DEFAULT NULL COMMENT '模板编码',
    cost_ms         BIGINT          DEFAULT NULL COMMENT '节点耗时（毫秒）',
    message         VARCHAR(512)    DEFAULT NULL COMMENT '节点描述 / 错误信息',
    extra           JSON            DEFAULT NULL COMMENT '扩展信息（JSON，如路由规则 ID、降级链、灰度配置等）',
    event_at        DATETIME        NOT NULL COMMENT '节点发生时间',
    INDEX idx_msg_event (msg_id, event_at),
    INDEX idx_trace_id (trace_id),
    INDEX idx_node_status (node, status),
    INDEX idx_event_at (event_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息轨迹表';

-- ----------------------------------------------------------------------------
-- 17. 消息用户反馈表（用户对消息质量的评分和反馈）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_feedback (
    id                VARCHAR(32)   PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)   NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    msg_id            VARCHAR(64)   NOT NULL COMMENT '消息 ID（关联 ydsz_msg_log.msg_id）',
    notification_id   VARCHAR(32)   DEFAULT NULL COMMENT '站内通知 ID（关联 ydsz_msg_notification.id，可为 NULL）',
    user_id           VARCHAR(32)   NOT NULL COMMENT '用户 ID',
    channel           VARCHAR(32)   DEFAULT NULL COMMENT '通道',
    biz_type          VARCHAR(64)   DEFAULT NULL COMMENT '业务类型',
    rating            INT           NOT NULL COMMENT '评分: 1-5 分（1=非常不满意, 5=非常满意）',
    feedback_type     VARCHAR(32)   DEFAULT NULL COMMENT '反馈类型: TOO_FREQUENT 太频繁 / IRRELEVANT 不相关 / TOO_LONG 内容太长 / SPAM 垃圾信息 / GOOD 有用 / OTHER 其他',
    content           VARCHAR(512)  DEFAULT NULL COMMENT '反馈内容（用户自由文本输入）',
    status            VARCHAR(32)   DEFAULT NULL COMMENT '状态标识',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)   DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_msg_id (msg_id),
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息用户反馈表';

-- ----------------------------------------------------------------------------
-- 18. Outbox 事件表（事务性 Outbox 模式，保障业务写操作与事件投递的事务一致性）
--     参考 ydsz-common-event 的 outbox_mysql.sql，以 OutboxEvent 实体实际字段为准
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_msg_outbox (
    id                VARCHAR(32)   PRIMARY KEY COMMENT '事件唯一 ID（Snowflake）',
    aggregate_type    VARCHAR(128)  NOT NULL COMMENT '聚合根类型',
    aggregate_id      VARCHAR(128)  NOT NULL COMMENT '聚合根 ID',
    event_type        VARCHAR(128)  NOT NULL COMMENT '事件类型',
    payload           JSON          NOT NULL COMMENT '事件负载（JSON）',
    tenant_id         VARCHAR(32)   DEFAULT NULL COMMENT '租户 ID（多租户隔离）',
    status            VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT '发布状态: PENDING 待发布 / PROCESSING 发布中 / SENT 已发布 / DEAD_LETTER 死信',
    publish_attempts  INT           NOT NULL DEFAULT 0 COMMENT '发布尝试次数',
    published_at      DATETIME      DEFAULT NULL COMMENT '发布时间',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_status_created (status, created_at),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_aggregate (aggregate_type, aggregate_id),
    INDEX idx_status_published (status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbox 事件表（事务性 Outbox 模式）';
