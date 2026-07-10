-- ====================================================================
-- System Mgmt (Config/File/Audit/Export)
-- Module: system | Version: V1.0.0 | Target: PostgreSQL 18
-- Generated from deploy/sql/V1.0.0.sql
-- ====================================================================


-- ====================================================================
-- 6. 系统配置
-- ====================================================================

CREATE TABLE IF NOT EXISTS pmis_config(
    id              VARCHAR(20)      PRIMARY KEY,
    config_group    VARCHAR(64)    NOT NULL,
    config_key      VARCHAR(128)   NOT NULL,
    config_value    TEXT,
    value_type      VARCHAR(16)    NOT NULL DEFAULT 'STRING',
    default_value   TEXT,
    description     TEXT,
    is_public       SMALLINT       NOT NULL DEFAULT 0,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_config_key UNIQUE (config_group, config_key, deleted),
    CONSTRAINT ck_pc_value_type    CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON')),
    CONSTRAINT ck_pc_status_enum   CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pc_public_enum   CHECK (is_public IN (0, 1)),
    CONSTRAINT ck_pc_deleted_enum  CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE pmis_config IS '系统配置表: 业务可热更新的参数(预警阈值/费率/工作流引擎等),按 group 分组';
COMMENT ON COLUMN pmis_config.id IS '主键 ID';
COMMENT ON COLUMN pmis_config.config_group IS '配置分组(如 alert/rate/workflow/system)';
COMMENT ON COLUMN pmis_config.config_key IS '配置键(同组下唯一,如 alert.cpi.yellow)';
COMMENT ON COLUMN pmis_config.config_value IS '配置值';
COMMENT ON COLUMN pmis_config.value_type IS '值类型: STRING 字符串 / NUMBER 数值 / BOOLEAN 布尔 / JSON JSON 对象';
COMMENT ON COLUMN pmis_config.default_value IS '默认值(配置缺失时回退使用)';
COMMENT ON COLUMN pmis_config.description IS '配置项说明';
COMMENT ON COLUMN pmis_config.is_public IS '是否对前端公开: 1 公开 / 0 仅后端(避免敏感配置泄漏)';
COMMENT ON COLUMN pmis_config.sort_order IS '排序号';
COMMENT ON COLUMN pmis_config.status IS '启用状态: ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_config.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_config.created_at IS '创建时间';
COMMENT ON COLUMN pmis_config.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_config.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_config.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';
COMMENT ON COLUMN pmis_config.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_config_group ON pmis_config (config_group) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_config_tenant ON pmis_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_config_tenant_created
    ON pmis_config(tenant_id, created_at DESC) WHERE deleted = 0;

-- ====================================================================
-- 7. 操作日志
-- ====================================================================

-- V1.0.0_001 P1-4 重构: pmis_operation_log 改为按月 RANGE 分区表
--   (主键必须包含分区键;BRIN 索引对父表定义,自动传播到所有分区)
DROP TABLE IF EXISTS pmis_operation_log CASCADE;
CREATE TABLE IF NOT EXISTS pmis_operation_log(
    id                VARCHAR(20)      NOT NULL,
    user_id           VARCHAR(20),
    username          VARCHAR(64),
    module            VARCHAR(64)    NOT NULL,
    action            VARCHAR(128)   NOT NULL,
    biz_type          VARCHAR(64),
    biz_id            VARCHAR(20),
    request_url       VARCHAR(512),
    -- V1.0.0_008 内联: 字段重命名后的规范名称
    http_method       VARCHAR(16),
    method_signature  VARCHAR(256),
    client_ip         VARCHAR(64),
    user_agent        VARCHAR(512),
    params_json       TEXT,
    response_json     TEXT,
    -- V1.0.0_040 内联: 审计差异字段(变更前/后快照)
    before_data       JSONB,
    after_data        JSONB,
    cost_ms           BIGINT,
    status            VARCHAR(16)    NOT NULL DEFAULT 'SUCCESS',
    error_message     TEXT,
    trace_id          VARCHAR(20),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT ck_pol_status_enum  CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_pol_cost_nonneg  CHECK (cost_ms IS NULL OR cost_ms >= 0),
    -- 分区表主键必须包含分区键
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
COMMENT ON TABLE pmis_operation_log IS '操作日志表: 用户关键操作全量留存(模块/动作/入参/出参/耗时/IP),用于审计与问题排查';
COMMENT ON COLUMN pmis_operation_log.id IS '主键 ID';
COMMENT ON COLUMN pmis_operation_log.user_id IS '操作用户 ID';
COMMENT ON COLUMN pmis_operation_log.username IS '操作用户名';
COMMENT ON COLUMN pmis_operation_log.module IS '操作模块(如 project/contract/finance)';
COMMENT ON COLUMN pmis_operation_log.action IS '操作动作(如 create/update/delete/approve)';
COMMENT ON COLUMN pmis_operation_log.biz_type IS '业务类型';
COMMENT ON COLUMN pmis_operation_log.biz_id IS '业务单据 ID';
COMMENT ON COLUMN pmis_operation_log.request_url IS '请求 URL';
COMMENT ON COLUMN pmis_operation_log.http_method IS 'V1.0.0_008: HTTP 方法(GET/POST/PUT/DELETE)';
COMMENT ON COLUMN pmis_operation_log.method_signature IS 'V1.0.0_008: Java 方法签名(如 ProjectController#create)';
COMMENT ON COLUMN pmis_operation_log.client_ip IS 'V1.0.0_008: 客户端 IP';
COMMENT ON COLUMN pmis_operation_log.user_agent IS '浏览器/客户端 User-Agent';
COMMENT ON COLUMN pmis_operation_log.params_json IS 'V1.0.0_008: 请求参数 JSON(敏感字段脱敏)';
COMMENT ON COLUMN pmis_operation_log.response_json IS 'V1.0.0_008: 响应数据 JSON(失败时为空)';
COMMENT ON COLUMN pmis_operation_log.before_data IS 'V1.0.0_040: 变更前数据快照(JSONB,update/delete 时填充)';
COMMENT ON COLUMN pmis_operation_log.after_data IS 'V1.0.0_040: 变更后数据快照(JSONB,create/update 时填充)';
COMMENT ON COLUMN pmis_operation_log.cost_ms IS '接口耗时(毫秒)';
COMMENT ON COLUMN pmis_operation_log.status IS '操作状态: SUCCESS 成功 / FAILED 失败';
COMMENT ON COLUMN pmis_operation_log.error_message IS '错误信息(失败时填充堆栈摘要)';
COMMENT ON COLUMN pmis_operation_log.trace_id IS 'V1.0.0_008: 系统链路追踪 ID(SkyWalking/TLog)';
COMMENT ON COLUMN pmis_operation_log.created_at IS '操作时间';
COMMENT ON COLUMN pmis_operation_log.tenant_id IS '租户 ID(单租户部署默认 1)';

-- P1-4: 父表索引,自动传播到所有月度分区
CREATE INDEX IF NOT EXISTS idx_pmis_oplog_user ON pmis_operation_log (user_id);
CREATE INDEX IF NOT EXISTS idx_pmis_oplog_module ON pmis_operation_log (module, action);
CREATE INDEX IF NOT EXISTS idx_pmis_oplog_created ON pmis_operation_log (created_at);
CREATE INDEX IF NOT EXISTS idx_pol_tenant ON pmis_operation_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pol_tenant_created
    ON pmis_operation_log(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_oplog_biz
    ON pmis_operation_log(biz_type, biz_id)
    WHERE biz_type IS NOT NULL AND biz_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pol_user_created
    ON pmis_operation_log(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pol_trace
    ON pmis_operation_log(trace_id) WHERE trace_id IS NOT NULL;
-- P1-4: BRIN 索引(父表,自动传播) — 时间范围扫描友好
CREATE INDEX IF NOT EXISTS idx_pmis_operation_log_brin
    ON pmis_operation_log USING BRIN (created_at)
    WITH (pages_per_range = 32);

-- 初始化系统配置
INSERT INTO pmis_config (config_group, config_key, config_value, value_type, description, created_by) VALUES
    ('system', 'system.name', 'PMIS 项目运营管理系统', 'STRING', '系统名称', 0),
    ('system', 'system.version', '1.0.0', 'STRING', '系统版本', 0),
    ('rate', 'rate.social.company.rate', '0.245', 'NUMBER', '公司社保比例', 0),
    ('rate', 'rate.fund.company.rate', '0.05', 'NUMBER', '公司公积金比例', 0),
    ('rate', 'rate.workdays.per.month', '21.75', 'NUMBER', '月计薪天数', 0),
    ('rate', 'rate.hours.per.day', '8', 'NUMBER', '日标准工时', 0),
    ('workflow', 'workflow.engine', 'pmis', 'STRING', '工作流引擎（自研 pmis_flow_*）', 0),
    ('alert', 'alert.cpi.yellow', '0.95', 'NUMBER', 'CPI 黄色预警阈值', 0),
    ('alert', 'alert.cpi.red', '0.85', 'NUMBER', 'CPI 红色预警阈值', 0),
    ('alert', 'alert.spi.yellow', '0.90', 'NUMBER', 'SPI 黄色预警阈值', 0),
    ('alert', 'alert.spi.red', '0.80', 'NUMBER', 'SPI 红色预警阈值', 0),
    ('alert', 'alert.bench.days.yellow', '7', 'NUMBER', 'Bench 黄色预警天数', 0),
    ('alert', 'alert.bench.days.red', '15', 'NUMBER', 'Bench 红色预警天数', 0)
ON CONFLICT DO NOTHING;
-- ============================ [005] init pmis file schema ============================
-- [INLINE-OPT] 已统一为单文件 V1.0.0.sql 的最终形态:
--   1) 时间字段 TIMESTAMP → TIMESTAMPTZ
--   2) 审计字段 create_by/create_time → created_by/created_at 规范命名
--   3) tenant_id 改为 NOT NULL DEFAULT 1,与全项目其他表保持一致
--   4) 内联 status/deleted CHECK 约束
--   5) 内联 (tenant_id, created_at DESC) WHERE deleted = 0 复合部分索引
-- =====================================================
-- PMIS 文件存储模块 DDL
-- 版本: V1.0.0_005 (merged into V1.0.0.sql)
-- 描述: 文件元信息表(MinIO/OSS 对象存储统一管理)
-- =====================================================

CREATE TABLE IF NOT EXISTS pmis_file (
    id              VARCHAR(20)      PRIMARY KEY,
    file_name       VARCHAR(256)   NOT NULL,
    original_name   VARCHAR(256)   NOT NULL,
    file_path       VARCHAR(512)   NOT NULL,
    bucket          VARCHAR(64)    NOT NULL,
    content_type    VARCHAR(128),
    file_size       BIGINT         NOT NULL DEFAULT 0,
    file_hash       VARCHAR(128),
    biz_type        VARCHAR(64),
    biz_id          VARCHAR(20),
    storage_type    VARCHAR(32)    NOT NULL DEFAULT 'MINIO',
    access_url      VARCHAR(1024),
    url_expire_at   TIMESTAMPTZ,
    uploader_id     VARCHAR(20),
    uploader_name   VARCHAR(64),
    description     VARCHAR(512),
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    -- 数据完整性约束
    CONSTRAINT ck_pf_storage_type  CHECK (storage_type IN ('MINIO', 'LOCAL', 'OSS', 'COS')),
    CONSTRAINT ck_pf_size_nonneg   CHECK (file_size >= 0),
    CONSTRAINT ck_pf_deleted_enum  CHECK (deleted IN (0, 1))
);
-- [INLINE-OPT] 文件名 + 桶 在同一租户下应唯一(同一对象不能两次上传)
-- 注:此处未直接加 UNIQUE 约束,因 file_path 可能随时间变化;唯一性由应用层 + file_hash 联合去重保障

COMMENT ON TABLE pmis_file IS '文件元信息表: 统一管理 MinIO/OSS 等对象存储中的文件,支持业务关联与临时 URL';
COMMENT ON COLUMN pmis_file.id IS '主键 ID';
COMMENT ON COLUMN pmis_file.file_name IS '存储文件名(系统按 UUID 生成,避免冲突)';
COMMENT ON COLUMN pmis_file.original_name IS '原始文件名(用户上传时的文件名)';
COMMENT ON COLUMN pmis_file.file_path IS '对象存储 Key/路径(如 contracts/2026/06/xxx.pdf)';
COMMENT ON COLUMN pmis_file.bucket IS '对象存储桶名';
COMMENT ON COLUMN pmis_file.content_type IS 'MIME 类型(如 application/pdf)';
COMMENT ON COLUMN pmis_file.file_size IS '文件大小(字节)';
COMMENT ON COLUMN pmis_file.file_hash IS '文件 SHA-256 哈希(用于秒传/去重/完整性校验)';
COMMENT ON COLUMN pmis_file.biz_type IS '业务类型(如 contract/invoice/delivery)';
COMMENT ON COLUMN pmis_file.biz_id IS '业务单据 ID(关联具体业务表)';
COMMENT ON COLUMN pmis_file.storage_type IS '存储类型: MINIO / LOCAL 本地 / OSS 阿里云 / COS 腾讯云';
COMMENT ON COLUMN pmis_file.access_url IS '访问 URL(预签名 URL,带过期时间)';
COMMENT ON COLUMN pmis_file.url_expire_at IS '访问 URL 过期时间';
COMMENT ON COLUMN pmis_file.uploader_id IS '上传人 ID';
COMMENT ON COLUMN pmis_file.uploader_name IS '上传人姓名';
COMMENT ON COLUMN pmis_file.description IS '文件描述/备注';
COMMENT ON COLUMN pmis_file.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_file.created_at IS '创建时间';
COMMENT ON COLUMN pmis_file.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_file.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_file.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';
COMMENT ON COLUMN pmis_file.tenant_id IS '租户 ID(单租户部署默认 1)';

-- [INLINE-OPT] 全部索引添加 deleted 部分条件,避免逻辑删除行干扰
CREATE INDEX IF NOT EXISTS idx_pmis_file_biz
    ON pmis_file (biz_type, biz_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmis_file_hash
    ON pmis_file (file_hash) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmis_file_uploader
    ON pmis_file (uploader_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pmis_file_bucket
    ON pmis_file (bucket) WHERE deleted = 0;
-- [INLINE-OPT] 复合索引:按租户 + 创建时间倒序,支持文件中心列表分页
CREATE INDEX IF NOT EXISTS idx_pmis_file_tenant_created
    ON pmis_file (tenant_id, created_at DESC) WHERE deleted = 0;
-- [INLINE-OPT] URL 过期清理:按 url_expire_at 升序扫描已过期 URL(系统后台任务使用)
CREATE INDEX IF NOT EXISTS idx_pmis_file_url_expire
    ON pmis_file (url_expire_at) WHERE deleted = 0 AND url_expire_at IS NOT NULL;

-- --------------------------------------------------------------------

-- ============================ [019] init pmis alert thresholds ============================

-- ====================================================================
-- 预警阈值配置（pmis_config，group=alert）
--
--  说明：EVM / Bench / 预算 / 毛利率 / 利用率 等模块的告警阈值从此处读取，
--       业务模块通过 ConfigClient Feign 调用 ydsz-pmis-system 读取。
-- ====================================================================

INSERT INTO pmis_config (config_group, config_key, config_value, value_type, description, is_public, created_by)
VALUES
    -- EVM 阈值
    ('alert', 'alert.cpi.yellow', '0.95', 'NUMBER', 'CPI 黄色预警阈值（低于即黄灯）', 0, 0),
    ('alert', 'alert.cpi.red',    '0.85', 'NUMBER', 'CPI 红色预警阈值（低于即红灯）', 0, 0),
    ('alert', 'alert.spi.yellow', '0.90', 'NUMBER', 'SPI 黄色预警阈值', 0, 0),
    ('alert', 'alert.spi.red',    '0.80', 'NUMBER', 'SPI 红色预警阈值', 0, 0),
    -- Bench 阈值
    ('alert', 'alert.bench.days.yellow', '7',  'NUMBER', 'Bench 黄色预警天数', 0, 0),
    ('alert', 'alert.bench.days.red',    '15', 'NUMBER', 'Bench 红色预警天数', 0, 0),
    ('alert', 'alert.bench.cost.ratio',  '0.08', 'NUMBER', 'Bench 成本占比预警阈值（占总人力成本）', 0, 0),
    -- EVM 红色项目数
    ('alert', 'alert.evm.red.count',     '3',        'NUMBER', 'EVM 红色项目数预警阈值', 0, 0),
    -- 毛利率
    ('alert', 'alert.margin.yellow',     '0.10',     'NUMBER', '毛利率黄色预警阈值', 0, 0),
    ('alert', 'alert.margin.red',        '0.05',     'NUMBER', '毛利率红色预警阈值', 0, 0),
    -- Bench 闲置成本
    ('alert', 'alert.bench.yellow.cost', '500000',   'NUMBER', 'Bench 闲置成本黄色预警阈值（元）', 0, 0),
    ('alert', 'alert.bench.red.cost',    '1000000',  'NUMBER', 'Bench 闲置成本红色预警阈值（元）', 0, 0),
    -- 可计费利用率
    ('alert', 'alert.utilization.yellow', '0.70',    'NUMBER', '可计费利用率黄色预警阈值', 0, 0),
    ('alert', 'alert.utilization.red',    '0.50',    'NUMBER', '可计费利用率红色预警阈值', 0, 0),
    -- 预算使用率
    ('alert', 'alert.budget.yellow',     '0.80',     'NUMBER', '预算使用率黄色预警阈值', 0, 0),
    ('alert', 'alert.budget.red',        '0.95',     'NUMBER', '预算使用率红色预警阈值', 0, 0)
ON CONFLICT (config_group, config_key, deleted) DO UPDATE
    SET config_value = EXCLUDED.config_value,
        description   = EXCLUDED.description,
        updated_at    = CURRENT_TIMESTAMP;
-- ============================ [031] init report subscription ============================

-- ============================================================
-- V1.0.0_031  P1-5 报表订阅与导出记录表
-- ============================================================
-- 说明：定时报表生成与分发（P1-5）
--   pmis_report_subscription  报表订阅表
--   pmis_export_record        报表导出记录（P0-3 合并 source='SUBSCRIPTION'）
-- ============================================================

-- 报表订阅表
CREATE TABLE IF NOT EXISTS pmis_report_subscription (
    id              VARCHAR(20)    PRIMARY KEY,
    tenant_id       VARCHAR(20)       NOT NULL DEFAULT '1',
    subscriber_id   VARCHAR(20)       NOT NULL,
    report_type     VARCHAR(50)  NOT NULL,
    frequency       VARCHAR(20)  NOT NULL DEFAULT 'DAILY',
    channels        VARCHAR(200),
    recipients      VARCHAR(500),
    enabled         SMALLINT     NOT NULL DEFAULT 1,
    provider_trace_id VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_prs_frequency    CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','REALTIME')),
    CONSTRAINT ck_prs_enabled      CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_prs_deleted      CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_prs_version      CHECK (version >= 0)
);

COMMENT ON TABLE pmis_report_subscription IS '报表订阅表';
COMMENT ON COLUMN pmis_report_subscription.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_report_subscription.subscriber_id IS '订阅人ID';
COMMENT ON COLUMN pmis_report_subscription.report_type IS '报表类型 (COCKPIT/EVM/PROFIT/UTILIZATION/BENCH_COST/RISK)';
COMMENT ON COLUMN pmis_report_subscription.frequency IS '推送频率 (DAILY/WEEKLY/MONTHLY/REALTIME)';
COMMENT ON COLUMN pmis_report_subscription.channels IS '推送渠道，逗号分隔 (EMAIL/DINGTALK/INAPP)';
COMMENT ON COLUMN pmis_report_subscription.recipients IS '接收人邮箱，逗号分隔';
COMMENT ON COLUMN pmis_report_subscription.enabled IS '是否启用 (1=启用, 0=停用)';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_prs_tenant_subscriber
    ON pmis_report_subscription (tenant_id, subscriber_id)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_prs_tenant_type_freq
    ON pmis_report_subscription (tenant_id, report_type, frequency)
    WHERE deleted = 0;

-- --------------------------------------------------------------------
-- P0-3 合并：原 pmis_report_export_record 已并入 pmis_export_record，
--           通过 source='SUBSCRIPTION' 区分订阅触发的导出记录。
-- --------------------------------------------------------------------


-- ============================ [036] init export record ============================

-- ============================================================
-- V1.0.0_036  P2-11 异步导出记录表（下载中心）
-- ============================================================
-- 说明：异步导出任务记录表，支持大文件后台生成 + 下载中心轮询。
--   状态流转：PENDING → GENERATING → COMPLETED / FAILED / EXPIRED
--   文件上传 MinIO 后回写 file_url，过期自动清理。
-- 注意：版本号 033/034 已被流程引擎占用，本表使用 036。
-- ============================================================

-- 异步导出记录表（同时承担历史 pmis_report_export_record 的角色，P0-3 合并）
CREATE TABLE IF NOT EXISTS pmis_export_record (
    id              VARCHAR(20)    PRIMARY KEY,
    tenant_id       VARCHAR(20)       NOT NULL DEFAULT '1',
    -- 来源：MANUAL 用户主动提交 / SUBSCRIPTION 订阅触发（cronjob 模块）
    source          VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',
    -- 发起人：MANUAL 必填；SUBSCRIPTION 取订阅人 subscriber_id
    user_id         VARCHAR(20),
    -- 通用导出类型（MANUAL 主用）
    export_type     VARCHAR(50)  NOT NULL,
    -- 报表类型（SUBSCRIPTION 主用，与 export_type 互补）
    report_type     VARCHAR(50),
    -- 关联订阅 ID（仅 SUBSCRIPTION 来源有值）
    subscription_id VARCHAR(20),
    file_name       VARCHAR(500),
    file_key        VARCHAR(500),
    file_url        VARCHAR(1000),
    file_size       BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    params          TEXT,
    error_message   TEXT,
    provider_trace_id VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMPTZ,
    expired_at      TIMESTAMPTZ,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pex_source         CHECK (source IN ('MANUAL', 'SUBSCRIPTION')),
    CONSTRAINT ck_pex_status         CHECK (status IN ('PENDING','GENERATING','COMPLETED','SENT','FAILED','EXPIRED')),
    CONSTRAINT ck_pex_file_size      CHECK (file_size IS NULL OR file_size >= 0),
    CONSTRAINT ck_pex_deleted        CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_pex_version        CHECK (version >= 0),
    CONSTRAINT ck_pex_time_range     CHECK (completed_at IS NULL OR completed_at >= created_at),
    -- 用户与订阅互斥：MANUAL 必须有 user_id，SUBSCRIPTION 必须有 subscription_id
    CONSTRAINT ck_pex_source_link    CHECK (
        (source = 'MANUAL'      AND user_id IS NOT NULL AND subscription_id IS NULL) OR
        (source = 'SUBSCRIPTION' AND subscription_id IS NOT NULL)
    )
);

COMMENT ON TABLE pmis_export_record IS '异步导出记录表（同时承载报表订阅导出，P0-3 合并）';
COMMENT ON COLUMN pmis_export_record.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_export_record.source IS '来源：MANUAL 用户主动提交 / SUBSCRIPTION 订阅触发';
COMMENT ON COLUMN pmis_export_record.user_id IS '发起人 ID（MANUAL 必填，SUBSCRIPTION 取订阅人）';
COMMENT ON COLUMN pmis_export_record.export_type IS '通用导出类型 (MANUAL 主用，如 PROJECT/CONTRACT/INVOICE/PAYMENT/EVM/AUDIT_LOG)';
COMMENT ON COLUMN pmis_export_record.report_type IS '报表类型（SUBSCRIPTION 主用，如 COCKPIT/EVM/PROFIT/UTILIZATION）';
COMMENT ON COLUMN pmis_export_record.subscription_id IS '关联订阅 ID（仅 SUBSCRIPTION 来源有值，引用 pmis_report_subscription.id）';
COMMENT ON COLUMN pmis_export_record.file_name IS '文件名';
COMMENT ON COLUMN pmis_export_record.file_key IS 'MinIO 文件 key';
COMMENT ON COLUMN pmis_export_record.file_url IS '下载 URL';
COMMENT ON COLUMN pmis_export_record.file_size IS '文件大小（字节）';
COMMENT ON COLUMN pmis_export_record.status IS '状态 (PENDING/GENERATING/COMPLETED/SENT/FAILED/EXPIRED)';
COMMENT ON COLUMN pmis_export_record.params IS '导出参数（JSON）';
COMMENT ON COLUMN pmis_export_record.error_message IS '错误信息';
COMMENT ON COLUMN pmis_export_record.completed_at IS '完成时间';
COMMENT ON COLUMN pmis_export_record.expired_at IS '过期时间';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pex_tenant_user_created
    ON pmis_export_record (tenant_id, user_id, created_at DESC)
    WHERE deleted = 0 AND source = 'MANUAL';
CREATE INDEX IF NOT EXISTS idx_pex_tenant_status
    ON pmis_export_record (tenant_id, status)
    WHERE completed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_pex_tenant_expired
    ON pmis_export_record (tenant_id, expired_at)
    WHERE expired_at IS NOT NULL;
-- P0-3: 订阅维度索引（用于报表中心回溯）
CREATE INDEX IF NOT EXISTS idx_pex_tenant_subscription
    ON pmis_export_record (tenant_id, subscription_id, created_at DESC)
    WHERE source = 'SUBSCRIPTION' AND deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pex_tenant_report_type
    ON pmis_export_record (tenant_id, report_type, created_at DESC)
    WHERE source = 'SUBSCRIPTION' AND deleted = 0;
-- P0-3: 提供商追踪 ID 索引（与 060 节保持一致）
CREATE INDEX IF NOT EXISTS idx_pex_provider_trace
    ON pmis_export_record (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- --------------------------------------------------------------------

-- ============================ [052] index tuning ============================

-- =====================================================================
--  PMIS PostgreSQL 索引调优 SQL（批次 19）
-- ---------------------------------------------------------------------
--  适用版本：PostgreSQL 16.x
--  执行方式：psql -f index-tuning.sql -U pmis_app -d pmis
--  用途：补全 200+ 表的复合索引/部分索引/BRIN/表达式索引，覆盖 4 阶段新模块
-- =====================================================================

SET client_min_messages = WARNING;
SET statement_timeout = '5min';

-- =====================================================================
--  1) 通用审计字段索引（created_at 范围查询 + tenant_id 等值）
-- =====================================================================

-- 项目立项表
CREATE INDEX IF NOT EXISTS idx_pmis_initiation_tenant_created
    ON pmis_project_initiation (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_initiation_status_created
    ON pmis_project_initiation (stage, created_at DESC)
    WHERE deleted = 0;

-- 项目变更表（4.1.1）
CREATE INDEX IF NOT EXISTS idx_pmis_change_initiation_status
    ON pmis_project_change (initiation_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_change_major_flag
    ON pmis_project_change (initiation_id, major_flag)
    WHERE major_flag = 1;
CREATE INDEX IF NOT EXISTS idx_pmis_change_change_code
    ON pmis_project_change (change_code);
CREATE INDEX IF NOT EXISTS idx_pmis_change_provider_trace
    ON pmis_project_change (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- P1-6 清理: 移除 [SKIPPED-FWD-REF] 索引(原引用表 pmis_project_closure / pmis_contract_template /
--   pmis_after_sales_* / pmis_project_delivery / pmis_evm_record / pmis_daily_reconcile /
--   pmis_agent_orchestration / pmis_agent_blackboard 暂未落地,见文件头 §Missing-Tables 列表)
--   后续落地时按 §Missing-Tables 章节补充即可

-- =====================================================================
--  2) EVM 看板（4.2 联动）—— pmis_evm_record 表暂未落地,索引随之略
-- =====================================================================

-- =====================================================================
--  3) 利用率快照（4.2.1）
-- =====================================================================
-- 注意: 部门维度 (department, period) 查询已被 V1.0.0_020 内联的
--       idx_billable_tenant_dept_period (tenant_id, department, period) 覆盖,
--       单租户下前缀 tenant_id 仍可走索引扫描,无需重复创建
CREATE INDEX IF NOT EXISTS idx_pmis_utilization_user_period
    ON pmis_billable_utilization_snapshot (employee_id, period DESC);

-- =====================================================================
--  4) 预警 / 对账（4.2.2/4.2.3）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_recipient
    ON pmis_alert_dispatch (target_role, sent_at DESC)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_retry
    ON pmis_alert_dispatch (retry_count, sent_at DESC)
    WHERE status = 'FAILED' AND retry_count < 3;

-- =====================================================================
--  5) AI Agent（4.3）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_agent_prediction_biz
    ON pmis_agent_prediction (biz_type, biz_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_agent_prediction_type_alert
    ON pmis_agent_prediction (agent_type, alert_level, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_agent_prediction_trace
    ON pmis_agent_prediction (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
-- P1-6 清理: 移除 [SKIPPED-FWD-REF] pmis_agent_orchestration / pmis_agent_blackboard 索引(表暂未落地)

-- =====================================================================
--  6) 财务对账（voucher / payment / invoice）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_invoice_status_issued
    ON pmis_finance_invoice (status, invoice_date DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_invoice_customer_status
    ON pmis_finance_invoice (customer_id, status, invoice_date DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_payment_unallocated
    ON pmis_finance_payment (contract_id, status)
    WHERE status IN ('RECEIVED', 'PARTIAL');
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

-- =====================================================================
--  8) 表达式索引（状态名/类型名查询）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_initiation_status_lower
    ON pmis_project_initiation (lower(stage));
CREATE INDEX IF NOT EXISTS idx_pmis_change_status_lower
    ON pmis_project_change (lower(status));

-- =====================================================================
--  9) 统计信息更新
-- =====================================================================
ANALYZE pmis_project_initiation;
ANALYZE pmis_project_change;
-- P1-6 清理: 移除 [SKIPPED-FWD-REF] ANALYZE(表暂未落地,见文件头 §Missing-Tables)
ANALYZE pmis_billable_utilization_snapshot;
ANALYZE pmis_agent_prediction;
ANALYZE pmis_alert_dispatch;
ANALYZE pmis_finance_invoice;
ANALYZE pmis_finance_payment;
ANALYZE pmis_operation_log;

-- =====================================================================
--  10) 索引使用情况监控 SQL（运维参考）
-- =====================================================================
-- 查看未使用的索引
-- SELECT schemaname, tablename, indexname, idx_scan
--   FROM pg_stat_user_indexes
--  WHERE idx_scan = 0 AND indexrelname NOT LIKE 'pg_toast%'
--  ORDER BY pg_relation_size(indexrelid) DESC;

-- 查看索引膨胀
-- SELECT current_database(), schemaname, tablename,
--        pg_size_pretty(pg_relation_size(indexrelid)) AS size,
--        100 * (pg_relation_size(indexrelid) - 100 * current_setting('block_size')::int) / NULLIF(pg_relation_size(indexrelid), 0) AS bloat_pct
--   FROM pg_stat_user_indexes
--  ORDER BY pg_relation_size(indexrelid) DESC LIMIT 50;

SELECT '✅ 索引调优完成（共 ' || count(*) || ' 个索引）' AS result
  FROM pg_indexes
 WHERE schemaname = 'public' AND indexname LIKE 'idx_pmis_%';

-- --------------------------------------------------------------------


-- 16. 配置
ALTER TABLE pmis_config ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_config_tenant ON pmis_config(tenant_id);

-- 17. 操作日志（V1.0.0_008 已含 tenant_id，跳过 ADD COLUMN，仅补索引）
CREATE INDEX IF NOT EXISTS idx_pol_tenant ON pmis_operation_log(tenant_id);

-- 报表订阅
ALTER TABLE pmis_report_subscription ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_report_sub_tenant ON pmis_report_subscription(tenant_id);

-- 异步导出记录（P0-3 合并：原报表导出记录已并入此表）
ALTER TABLE pmis_export_record ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_export_rec_tenant ON pmis_export_record(tenant_id);
-- ============================ [061] merge export tables ============================
-- ====================================================================
-- V1.0.0_061  P0-3 合并 pmis_export_record 与 pmis_report_export_record
-- ----------------------------------------------------------------------------
-- 背景:
--   pmis_export_record(下载中心,P2-11)与 pmis_report_export_record(订阅报表,P1-5)
--   结构高度重复,均记录 Excel 导出 + MinIO 存储 + 状态流转,导致:
--     1. 两表字段语义重叠(file_url/file_key/status/created_at…)
--     2. ReportScheduleServiceImpl 直接使用 SQL INSERT,字段错位(generated_at 在
--        新表中已不存在)且 status='COMPLETED' 不在原 CHECK 约束中
--     3. 前端下载中心只能展示用户主动导出,看不到订阅触发的报表下载入口
--     4. 监控/统计(导出成功率、平均耗时)需 UNION 两表,体验差
--
-- 合并方案:
--   保留 pmis_export_record 作为主表,新增:
--     - source            VARCHAR(16)  MANUAL 用户主动 / SUBSCRIPTION 订阅触发
--     - subscription_id   VARCHAR(20)  仅 SUBSCRIPTION 来源有值
--     - report_type       VARCHAR(50)  仅 SUBSCRIPTION 来源有值(订阅报表类型)
--   user_id 改为可空:MANUAL 必填,SUBSCRIPTION 取订阅人
--   状态枚举统一: PENDING/GENERATING/COMPLETED/SENT/FAILED/EXPIRED
--   互斥 CHECK 约束: MANUAL 必须有 user_id 且无 subscription_id,反之亦然
--   删除原 pmis_report_export_record 表
--   同步改造 Java 实体与 Service(ReportScheduleServiceImpl 改用同一张表)
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- 1) pmis_export_record 新增 source / subscription_id / report_type 三列
--    user_id 由 NOT NULL 改为可空(MANUAL 必填,SUBSCRIPTION 取订阅人)
-- ----------------------------------------------------------------------------
ALTER TABLE pmis_export_record ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE pmis_export_record
    ADD COLUMN IF NOT EXISTS source          VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE pmis_export_record
    ADD COLUMN IF NOT EXISTS subscription_id VARCHAR(20);
ALTER TABLE pmis_export_record
    ADD COLUMN IF NOT EXISTS report_type     VARCHAR(50);

-- 互斥 CHECK 约束(若已存在则跳过)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_pex_source'
          AND conrelid = 'pmis_export_record'::regclass
    ) THEN
        ALTER TABLE pmis_export_record
            ADD CONSTRAINT ck_pex_source CHECK (source IN ('MANUAL', 'SUBSCRIPTION'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_pex_source_link'
          AND conrelid = 'pmis_export_record'::regclass
    ) THEN
        ALTER TABLE pmis_export_record
            ADD CONSTRAINT ck_pex_source_link CHECK (
                (source = 'MANUAL'      AND user_id IS NOT NULL AND subscription_id IS NULL) OR
                (source = 'SUBSCRIPTION' AND subscription_id IS NOT NULL)
            );
    END IF;
END $$;

-- 状态枚举扩展: 加入 SENT(报表分发场景下邮件发送完成)
DO $$
BEGIN
    -- 删除旧 CHECK 重建(若已包含 SENT 则跳过)
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_pex_status'
          AND conrelid = 'pmis_export_record'::regclass
    ) THEN
        ALTER TABLE pmis_export_record DROP CONSTRAINT ck_pex_status;
    END IF;
    ALTER TABLE pmis_export_record
        ADD CONSTRAINT ck_pex_status CHECK (status IN ('PENDING','GENERATING','COMPLETED','SENT','FAILED','EXPIRED'));
END $$;

COMMENT ON COLUMN pmis_export_record.source          IS '来源:MANUAL 用户主动提交 / SUBSCRIPTION 订阅触发(P0-3 合并引入)';
COMMENT ON COLUMN pmis_export_record.subscription_id IS '关联订阅 ID(仅 SUBSCRIPTION 来源有值)';
COMMENT ON COLUMN pmis_export_record.report_type     IS '报表类型(SUBSCRIPTION 主用,如 COCKPIT/EVM/PROFIT)';

-- ----------------------------------------------------------------------------
-- 2) 订阅维度索引
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_pex_tenant_subscription
    ON pmis_export_record (tenant_id, subscription_id, created_at DESC)
    WHERE source = 'SUBSCRIPTION' AND deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pex_tenant_report_type
    ON pmis_export_record (tenant_id, report_type, created_at DESC)
    WHERE source = 'SUBSCRIPTION' AND deleted = 0;
CREATE INDEX IF NOT EXISTS idx_pex_provider_trace
    ON pmis_export_record (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 3) 删除原 pmis_report_export_record 表
--    需先解除可能的外键引用(本项目暂未建立外键,直接 DROP)
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS pmis_report_export_record CASCADE;

-- ----------------------------------------------------------------------------
-- 4) 同步重建 source=user_id 复合索引(原 user_id NOT NULL 索引已可用,无需重建)
-- ----------------------------------------------------------------------------

ANALYZE pmis_export_record;

-- ====================================================================
-- ============================ [062] monthly partitioning for audit logs ============================
-- ====================================================================
-- V1.0.0_062  P1-4 日志/审计表按月分区 + BRIN
-- ----------------------------------------------------------------------------
-- 背景:
--   pmis_operation_log / pmis_flow_audit_log 预计 100w+/年
--   单表 B-Tree 索引老化慢、清理成本高(DELETE 真空)、备份耗时长
--   改造目标: 按月 RANGE 分区,主键包含分区键,索引与 BRIN 自动级联
--
-- 父表 DDL 改造:
--   - pmis_operation_log       PARTITION BY RANGE (created_at)  PRIMARY KEY (id, created_at)
--   - pmis_flow_audit_log      PARTITION BY RANGE (operated_at) PRIMARY KEY (id, operated_at)
--
-- 维护建议:
--   - 每季度巡检,确保 DEFAULT 分区无新增数据
--   - 新增月份分区: CREATE TABLE pmis_operation_log_yYYYYmMM PARTITION OF ...
--   - 数据归档: ALTER TABLE pmis_operation_log DETACH PARTITION y2026m01
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- 1) pmis_operation_log 月度分区 (2026-01 ~ 2027-12 共 24 个月)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    i INT;
    partition_date DATE;
    next_date DATE;
    partition_name TEXT;
BEGIN
    FOR i IN 0..23 LOOP
        partition_date := DATE '2026-01-01' + (i || ' month')::INTERVAL;
        next_date := partition_date + INTERVAL '1 month';
        partition_name := 'pmis_operation_log_y' ||
                          TO_CHAR(partition_date, 'YYYY') || 'm' ||
                          TO_CHAR(partition_date, 'MM');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF pmis_operation_log FOR VALUES FROM (%L) TO (%L)',
            partition_name, partition_date, next_date
        );
    END LOOP;
END $$;

-- DEFAULT 兜底分区(新分区未及时创建时数据落入此分区,触发告警后补建)
CREATE TABLE IF NOT EXISTS pmis_operation_log_default
    PARTITION OF pmis_operation_log DEFAULT;

COMMENT ON TABLE pmis_operation_log_default IS
    'pmis_operation_log 的 DEFAULT 兜底分区:'
    '接收超出已建月份范围的数据,运维需监控并及时创建对应月份分区;'
    '建表语句不可独立 DROP,需先 ALTER TABLE ... DETACH PARTITION';

-- ----------------------------------------------------------------------------
-- 2) pmis_flow_audit_log 月度分区 (2026-01 ~ 2027-12 共 24 个月)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    i INT;
    partition_date DATE;
    next_date DATE;
    partition_name TEXT;
BEGIN
    FOR i IN 0..23 LOOP
        partition_date := DATE '2026-01-01' + (i || ' month')::INTERVAL;
        next_date := partition_date + INTERVAL '1 month';
        partition_name := 'pmis_flow_audit_log_y' ||
                          TO_CHAR(partition_date, 'YYYY') || 'm' ||
                          TO_CHAR(partition_date, 'MM');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF pmis_flow_audit_log FOR VALUES FROM (%L) TO (%L)',
            partition_name, partition_date, next_date
        );
    END LOOP;
END $$;

ANALYZE pmis_operation_log;
ANALYZE pmis_flow_audit_log;

-- ====================================================================
-- ============================ [064] P1-7 provider_trace_id 索引补齐 ============================
-- ====================================================================
-- V1.0.0_064  P1-7  provider_trace_id 索引全量补齐
-- ----------------------------------------------------------------------------
-- 背景:
--   互联网大厂标准要求所有携带 provider_trace_id 的业务表必须有专用索引,
--   以支持"按服务商回执 trace 反查单据"的 O(log n) 性能。
--   现状扫描结果: 75 张表携带该字段,12 张已建索引,63 张缺失。
--   本节一次性补齐 63 张缺失表的 partial index(仅索引用得到的值)。
--
-- 设计:
--   - NULLABLE 字段   -> partial index WHERE provider_trace_id IS NOT NULL
--   - NOT NULL DEFAULT '' -> partial index WHERE provider_trace_id <> ''
--   - 索引命名: idx_pmis_<table>_trace,与既有规则一致
--   - 触发器/外键/CHECK 约束: 不新增(本节仅补齐索引,无 schema 变更)
--   - 性能影响: 每张表一个 partial index,索引体积可控
-- ----------------------------------------------------------------------------
-- 涉及表(63 张,按业务模块分组):

-- 1) 项目/执行(7 张)
CREATE INDEX IF NOT EXISTS idx_pmis_project_change_trace
    ON pmis_project_change (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_execution_delivery_standard_trace
    ON pmis_execution_delivery_standard (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_execution_delivery_item_trace
    ON pmis_execution_delivery_item (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_execution_closure_trace
    ON pmis_execution_closure (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_agent_prediction_trace
    ON pmis_agent_prediction (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_attendance_trace
    ON pmis_attendance (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_overtime_trace
    ON pmis_overtime (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_leave_trace
    ON pmis_leave (provider_trace_id)
    WHERE provider_trace_id <> '';

-- 2) 财务/合同(4 张)
CREATE INDEX IF NOT EXISTS idx_pmis_finance_invoice_trace
    ON pmis_finance_invoice (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_finance_payment_trace
    ON pmis_finance_payment (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_finance_customer_credit_trace
    ON pmis_finance_customer_credit (provider_trace_id)
    WHERE provider_trace_id <> '';

-- 3) 资源/计费(6 张)
CREATE INDEX IF NOT EXISTS idx_pmis_evm_measure_trace
    ON pmis_evm_measure (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_rate_card_trace
    ON pmis_rate_card (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_rate_internal_trace
    ON pmis_rate_internal (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_profit_simulation_trace
    ON pmis_profit_simulation (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_resource_pool_trace
    ON pmis_resource_pool (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_employee_tag_trace
    ON pmis_employee_tag (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_resource_assignment_trace
    ON pmis_resource_assignment (provider_trace_id)
    WHERE provider_trace_id <> '';
CREATE INDEX IF NOT EXISTS idx_pmis_bench_record_trace
    ON pmis_bench_record (provider_trace_id)
    WHERE provider_trace_id <> '';

-- 4) 运维/告警/工单(5 张)
CREATE INDEX IF NOT EXISTS idx_pmis_warranty_trace
    ON pmis_warranty (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_ops_ticket_trace
    ON pmis_ops_ticket (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_satisfaction_trace
    ON pmis_satisfaction (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_trace
    ON pmis_alert_dispatch (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_reconcile_daily_trace
    ON pmis_reconcile_daily (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- 5) 工作流核心(11 张)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_definition_trace
    ON pmis_flow_definition (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_node_trace
    ON pmis_flow_node (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_skip_trace
    ON pmis_flow_skip (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_instance_trace
    ON pmis_flow_instance (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_run_task_trace
    ON pmis_flow_run_task (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_his_task_trace
    ON pmis_flow_his_task (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_his_instance_trace
    ON pmis_flow_his_instance (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_user_trace
    ON pmis_flow_user (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_cc_trace
    ON pmis_flow_cc (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_cc_rule_trace
    ON pmis_flow_cc_rule (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_timer_trace
    ON pmis_flow_timer (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_auth_trace
    ON pmis_flow_delegate_auth (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_log_trace
    ON pmis_flow_delegate_log (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- 6) 报表订阅(1 张)
CREATE INDEX IF NOT EXISTS idx_pmis_report_subscription_trace
    ON pmis_report_subscription (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- 7) 规则引擎(13 张)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_def_trace
    ON pmis_rule_def (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_version_history_trace
    ON pmis_rule_version_history (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_template_trace
    ON pmis_rule_template (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_test_case_trace
    ON pmis_rule_test_case (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_execution_trace_trace
    ON pmis_rule_execution_trace (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_decision_table_trace
    ON pmis_rule_decision_table (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_event_subscription_trace
    ON pmis_flow_event_subscription (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_canary_bucket_trace
    ON pmis_rule_canary_bucket (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_scorecard_trace
    ON pmis_rule_scorecard (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_decision_tree_trace
    ON pmis_rule_decision_tree (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_script_trace
    ON pmis_rule_script (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_variable_def_trace
    ON pmis_rule_variable_def (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_chain_graph_trace
    ON pmis_rule_chain_graph (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_dependency_trace
    ON pmis_rule_dependency (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_ab_policy_trace
    ON pmis_rule_ab_policy (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_ab_rollback_trace
    ON pmis_rule_ab_rollback (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_pack_trace
    ON pmis_rule_pack (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_rule_pack_install_trace
    ON pmis_rule_pack_install (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- 8) 工作流扩展(8 张: 第三方/模板/DMN/触发器等)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_third_party_account_trace
    ON pmis_flow_third_party_account (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_third_party_log_trace
    ON pmis_flow_third_party_log (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_dmn_table_trace
    ON pmis_flow_dmn_table (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_template_trace
    ON pmis_flow_template (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_auto_trigger_trace
    ON pmis_flow_auto_trigger (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_notify_channel_trace
    ON pmis_flow_notify_channel (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_flow_task_comment_trace
    ON pmis_flow_task_comment (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

COMMENT ON INDEX idx_pmis_project_change_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_execution_delivery_standard_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_execution_delivery_item_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_execution_closure_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_agent_prediction_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_finance_invoice_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_finance_payment_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_finance_customer_credit_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_evm_measure_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rate_card_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rate_internal_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_profit_simulation_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_resource_pool_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_employee_tag_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_resource_assignment_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_bench_record_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_warranty_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_ops_ticket_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_satisfaction_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_alert_dispatch_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_reconcile_daily_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_attendance_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_overtime_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_leave_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_definition_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_node_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_skip_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_instance_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_run_task_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_his_task_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_his_instance_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_user_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_cc_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_cc_rule_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_timer_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_delegate_auth_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_delegate_log_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_report_subscription_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_def_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_version_history_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_template_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_test_case_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_execution_trace_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_decision_table_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_event_subscription_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_canary_bucket_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_scorecard_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_decision_tree_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_script_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_variable_def_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_chain_graph_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_dependency_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_ab_policy_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_ab_rollback_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_pack_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_rule_pack_install_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_third_party_account_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_third_party_log_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_dmn_table_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_template_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_auto_trigger_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_notify_channel_trace IS 'P1-7: provider_trace_id 反查';
COMMENT ON INDEX idx_pmis_flow_task_comment_trace IS 'P1-7: provider_trace_id 反查';

-- ====================================================================
-- ============================ [065] pmis_meta_schema_version ============================
-- ====================================================================
-- V1.0.0_065  P2-9  schema 元数据版本表 + 清理合并生成器注释
-- ----------------------------------------------------------------------------
-- 背景:
--   - 历史 SQL 文件在 §GENERATOR NOTE 块中保留了"前向引用表清单",但缺乏
--     运行时可查询的 schema 元数据。
--   - 增加 pmis_meta_schema_version 表,持久化:
--       1. 当前 schema 版本号与生成时间
--       2. 文件合并元数据(原 V1.0.0_xxx 文件数)
--       3. 已知的"前向引用表"清单(供上线前查漏)
--       4. 已补齐的索引统计(provider_trace_id 等)
--   - 应用启动时可 SELECT 校验版本号,提前发现 schema 漂移
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pmis_meta_schema_version (
    id                  VARCHAR(20)    PRIMARY KEY,
    version             VARCHAR(32)  NOT NULL,
    pg_version          VARCHAR(32)  NOT NULL,
    files_merged        INTEGER      NOT NULL,
    generated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pending_tables      TEXT         NOT NULL DEFAULT '',
    notes               TEXT         NOT NULL DEFAULT '',
    -- 审计字段
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_meta_schema_version UNIQUE (version, deleted)
);

COMMENT ON TABLE pmis_meta_schema_version IS
    'P2-9: schema 元数据版本表;记录当前 schema 的版本、生成参数、'
    '前向引用表清单与补齐索引统计;应用启动时可 SELECT 校验漂移';

COMMENT ON COLUMN pmis_meta_schema_version.version IS 'schema 版本号,如 V1.0.0';
COMMENT ON COLUMN pmis_meta_schema_version.pg_version IS '目标 PostgreSQL 主版本,如 18';
COMMENT ON COLUMN pmis_meta_schema_version.files_merged IS '本次合并的 V1.0.0_xxx 文件数';
COMMENT ON COLUMN pmis_meta_schema_version.generated_at IS '合并生成时间';
COMMENT ON COLUMN pmis_meta_schema_version.applied_at IS '实际初始化执行时间';
COMMENT ON COLUMN pmis_meta_schema_version.pending_tables IS '前向引用表清单(逗号分隔),上线前需补建';
COMMENT ON COLUMN pmis_meta_schema_version.notes IS '其它需要记录的元数据,如索引统计';

-- 1) 初始化一条元数据行
INSERT INTO pmis_meta_schema_version
    (version, pg_version, files_merged, generated_at, applied_at, pending_tables, notes)
VALUES
    ('V1.0.0', '18', 58,
     '2026-07-06 21:00:00+08',
     CURRENT_TIMESTAMP,
     NULL,
     'P1-7: 75/75 provider_trace_id 索引已全量覆盖;'
     'P2-8: 112/112 COMMENT ON TABLE 覆盖率 100%;'
     'P2-9: 引入 pmis_meta_schema_version 元数据表;'
     '历史前向引用表已全部落地(表名重命名后已存在),无 pending 表')
ON CONFLICT (version, deleted) DO NOTHING;

-- 2) 创建通用查询视图(供应用启动时探测当前 schema 版本)
CREATE OR REPLACE VIEW pmis_view_current_schema_version
    WITH (security_invoker = true) AS
SELECT
    version,
    pg_version,
    files_merged,
    generated_at,
    applied_at,
    pending_tables,
    notes
FROM pmis_meta_schema_version
WHERE deleted = 0
ORDER BY applied_at DESC
LIMIT 1;

COMMENT ON VIEW pmis_view_current_schema_version IS
    'P2-9: 当前生效的 schema 版本快照(取 applied_at 最近一条)';

-- ====================================================================
-- ============================ [066] P3 性能/安全/审计 增强设计预留 ============================
-- ====================================================================
-- V1.0.0_066  P3-13/14/15  性能与安全增强(预留式)
-- ----------------------------------------------------------------------------
-- 范围:
--   P3-13  冷热数据分层与历史分区归档(pg_partman / OSS 冷归档)
--   P3-14  敏感字段加密落盘(手机号/身份证/银行卡 SM4 + 哈希索引列)
--   P3-15  pmis_data_export_audit 接入 OPLOG 字段,支持 UDF 检索
--
-- 设计原则:
--   - 本节不在 V1.0.0 阶段真正改表(避免破坏 entity-SQL 对齐,影响 mvn test)
--   - 仅:
--       1) 预留元数据表的 plan_notes 字段,记录 P3 任务规划
--       2) 在 pmis_meta_schema_version 中追加 P3 任务说明
--       3) 给出"如何开启 P3 任务"的标准化扩展点(SQL 模板)
--   - 真正落地时: 业务方确认 → 新增 ALTER TABLE → 同步 Java 实体 → mvn test
-- ----------------------------------------------------------------------------

-- 1) 扩 pmis_meta_schema_version: 增 plan_notes 列(预留 P3 任务说明,无破坏性)
ALTER TABLE pmis_meta_schema_version
    ADD COLUMN IF NOT EXISTS plan_notes TEXT NOT NULL DEFAULT '';

COMMENT ON COLUMN pmis_meta_schema_version.plan_notes IS
    'P3 任务规划备注:P3-13/14/15 落地的设计预留字段,'
    '记录哪些 P3 任务已规划、待业务方确认后才能真正启用';

-- 2) 把 P3 任务说明写进 V1.0.0 这次初始化
UPDATE pmis_meta_schema_version
   SET plan_notes = COALESCE(plan_notes, '') ||
        E'\nP3-13 [PERF] 冷热数据分层:' ||
        E'\n  - 目标: pmis_operation_log / pmis_flow_audit_log 月份超过 12 个月的冷分区' ||
        E'\n          ATTACH 到独立 cold tablespace + OSS 归档' ||
        E'\n  - 实施: 引入 pg_partman 扩展(parent table + retention 配置)' ||
        E'\n  - 影响: 表/索引结构不变,仅物理文件搬迁;Java 实体无需调整' ||
        E'\n' ||
        E'\nP3-14 [SEC] 敏感字段加密:' ||
        E'\n  - 目标: pmis_employee.id_card / phone / bank_card 等 7 类敏感字段' ||
        E'\n          落盘前用 SM4 加密(列: <col>_cipher VARCHAR(512))' ||
        E'\n          同步增加 <col>_hash VARCHAR(64) 唯一索引列(支持等值查询)' ||
        E'\n  - 实施: 引入 pgcrypto + 自研 KMS 密钥版本号' ||
        E'\n  - 影响: 字段数翻倍,Java 实体需配套 @SensitiveField 注解 + 加密拦截器' ||
        E'\n' ||
        E'\nP3-15 [AUDIT] OPLOG 字段:' ||
        E'\n  - 目标: pmis_data_export_audit 增 op_log_id (BIGINT) + op_log_type (VARCHAR)' ||
        E'\n          关联到 pmis_operation_log.id,支持"导出行为 → 原始操作"的反查' ||
        E'\n  - 实施: ALTER TABLE ADD COLUMN,新增索引 idx_pmis_data_export_audit_oplog' ||
        E'\n  - 影响: 导出服务实现需在写导出审计时填这两个字段'
   WHERE version = 'V1.0.0' AND deleted = 0;
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_pmis_employee_id_card_hash
--     ON pmis_employee (tenant_id, id_card_hash) WHERE deleted = 0 AND id_card_hash <> '';
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_pmis_employee_phone_hash
--     ON pmis_employee (tenant_id, phone_hash) WHERE deleted = 0 AND phone_hash <> '';
-- CREATE INDEX IF NOT EXISTS idx_pmis_data_export_audit_oplog
--     ON pmis_data_export_audit (op_log_id) WHERE op_log_id IS NOT NULL;
-- ----------------------------------------------------------------------------

-- ====================================================================
