-- ============================================================
-- YDSZ system module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================
-- 本脚本 DDL 对应后端 system 服务 (ydsz-system) 的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign + NameAssembler(在 CommonAutoConfiguration 注册)。
--
-- ydsz-common 不是独立后端服务(公共依赖库,无 Mapper/Service),
-- 不持有独立 DDL。全局 PG 扩展 / PL/pgSQL 函数 / 触发器 / undo_log
-- 已全部合并到本文件(2026-07-10 重构)。
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

SET client_min_messages = WARNING;

-- Lock down search_path so unqualified table names resolve only
-- to the expected schema. (We use qualified names throughout, but
-- this guards against future contributors adding unqualified DDL.)
SET search_path = public, pg_catalog;

-- Wrap the entire init in one transaction so any failure rolls
-- back cleanly. If the script is already inside a transaction
-- (e.g. a tool-driven init), SAVEPOINTs below still isolate us.
BEGIN;
-- 字典版本表
CREATE TABLE IF NOT EXISTS ydsz_dict_version(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    type_code       VARCHAR(64)    NOT NULL,
    version         VARCHAR(32)    NOT NULL,
    change_log      TEXT,
    effective_date  TIMESTAMP      NOT NULL,
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0
);

COMMENT ON TABLE ydsz_dict_version IS '字典版本表: 字典变更历史快照,支持回滚与变更审计';

COMMENT ON COLUMN ydsz_dict_version.id IS '主键 ID';

COMMENT ON COLUMN ydsz_dict_version.type_code IS '字典类型编码';

COMMENT ON COLUMN ydsz_dict_version.version IS '版本号(语义化版本,如 1.0.0)';

COMMENT ON COLUMN ydsz_dict_version.change_log IS '变更说明';

COMMENT ON COLUMN ydsz_dict_version.effective_date IS '生效时间';

COMMENT ON COLUMN ydsz_dict_version.created_by IS '发布人 ID';

COMMENT ON COLUMN ydsz_dict_version.created_at IS '发布时间';

COMMENT ON COLUMN ydsz_dict_version.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- ====================================================================
-- 6. 系统配置
-- ====================================================================

CREATE TABLE IF NOT EXISTS ydsz_config(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    config_group    VARCHAR(64)    NOT NULL,
    config_key      VARCHAR(128)   NOT NULL,
    config_value    TEXT,
    value_type      VARCHAR(16)    NOT NULL DEFAULT 'STRING',
    default_value   TEXT,
    description     TEXT,
    is_public       SMALLINT       NOT NULL DEFAULT 0,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_config_key UNIQUE (config_group, config_key, deleted),
    CONSTRAINT ck_pc_value_type    CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON')),
    CONSTRAINT ck_pc_status_enum   CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pc_public_enum   CHECK (is_public IN (0, 1)),
    CONSTRAINT ck_pc_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_config IS '系统配置表: 业务可热更新的参数(预警阈值/费率/工作流引擎等),按 group 分组';

COMMENT ON COLUMN ydsz_config.id IS '主键 ID';

COMMENT ON COLUMN ydsz_config.config_group IS '配置分组(如 alert/rate/workflow/system)';

COMMENT ON COLUMN ydsz_config.config_key IS '配置键(同组下唯一,如 alert.cpi.yellow)';

COMMENT ON COLUMN ydsz_config.config_value IS '配置值';

COMMENT ON COLUMN ydsz_config.value_type IS '值类型: STRING 字符串 / NUMBER 数值 / BOOLEAN 布尔 / JSON JSON 对象';

COMMENT ON COLUMN ydsz_config.default_value IS '默认值(配置缺失时回退使用)';

COMMENT ON COLUMN ydsz_config.description IS '配置项说明';

COMMENT ON COLUMN ydsz_config.is_public IS '是否对前端公开: 1 公开 / 0 仅后端(避免敏感配置泄漏)';

COMMENT ON COLUMN ydsz_config.sort_order IS '排序号';

COMMENT ON COLUMN ydsz_config.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN ydsz_config.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_config.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_config.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_config.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_config.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_config.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_ydsz_config_group ON ydsz_config (config_group) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_config_tenant ON ydsz_config(tenant_id);

CREATE INDEX IF NOT EXISTS idx_config_tenant_created
    ON ydsz_config(tenant_id, created_at DESC) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS ydsz_operation_log(
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

COMMENT ON TABLE ydsz_operation_log IS '操作日志表: 用户关键操作全量留存(模块/动作/入参/出参/耗时/IP),用于审计与问题排查';

COMMENT ON COLUMN ydsz_operation_log.id IS '主键 ID';

COMMENT ON COLUMN ydsz_operation_log.user_id IS '操作用户 ID';

COMMENT ON COLUMN ydsz_operation_log.username IS '操作用户名';

COMMENT ON COLUMN ydsz_operation_log.module IS '操作模块(如 project/contract/finance)';

COMMENT ON COLUMN ydsz_operation_log.action IS '操作动作(如 create/update/delete/approve)';

COMMENT ON COLUMN ydsz_operation_log.biz_type IS '业务类型';

COMMENT ON COLUMN ydsz_operation_log.biz_id IS '业务单据 ID';

COMMENT ON COLUMN ydsz_operation_log.request_url IS '请求 URL';

COMMENT ON COLUMN ydsz_operation_log.http_method IS 'V1.0.0_008: HTTP 方法(GET/POST/PUT/DELETE)';

COMMENT ON COLUMN ydsz_operation_log.method_signature IS 'V1.0.0_008: Java 方法签名(如 ProjectController#create)';

COMMENT ON COLUMN ydsz_operation_log.client_ip IS 'V1.0.0_008: 客户端 IP';

COMMENT ON COLUMN ydsz_operation_log.user_agent IS '浏览器/客户端 User-Agent';

COMMENT ON COLUMN ydsz_operation_log.params_json IS 'V1.0.0_008: 请求参数 JSON(敏感字段脱敏)';

COMMENT ON COLUMN ydsz_operation_log.response_json IS 'V1.0.0_008: 响应数据 JSON(失败时为空)';

COMMENT ON COLUMN ydsz_operation_log.before_data IS 'V1.0.0_040: 变更前数据快照(JSONB,update/delete 时填充)';

COMMENT ON COLUMN ydsz_operation_log.after_data IS 'V1.0.0_040: 变更后数据快照(JSONB,create/update 时填充)';

COMMENT ON COLUMN ydsz_operation_log.cost_ms IS '接口耗时(毫秒)';

COMMENT ON COLUMN ydsz_operation_log.status IS '操作状态: SUCCESS 成功 / FAILED 失败';

COMMENT ON COLUMN ydsz_operation_log.error_message IS '错误信息(失败时填充堆栈摘要)';

COMMENT ON COLUMN ydsz_operation_log.trace_id IS 'V1.0.0_008: 系统链路追踪 ID(SkyWalking/TLog)';

COMMENT ON COLUMN ydsz_operation_log.created_at IS '操作时间';

COMMENT ON COLUMN ydsz_operation_log.tenant_id IS '租户 ID(单租户部署默认 1)';

-- P1-4: 父表索引,自动传播到所有月度分区
CREATE INDEX IF NOT EXISTS idx_ydsz_oplog_user ON ydsz_operation_log (user_id);

CREATE INDEX IF NOT EXISTS idx_ydsz_oplog_module ON ydsz_operation_log (module, action);

CREATE INDEX IF NOT EXISTS idx_ydsz_oplog_created ON ydsz_operation_log (created_at);

CREATE INDEX IF NOT EXISTS idx_pol_tenant ON ydsz_operation_log(tenant_id);

CREATE INDEX IF NOT EXISTS idx_pol_tenant_created
    ON ydsz_operation_log(tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ydsz_oplog_biz
    ON ydsz_operation_log(biz_type, biz_id)
    WHERE biz_type IS NOT NULL AND biz_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pol_user_created
    ON ydsz_operation_log(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pol_trace
    ON ydsz_operation_log(trace_id) WHERE trace_id IS NOT NULL;

-- P1-4: BRIN 索引(父表,自动传播) — 时间范围扫描友好
CREATE INDEX IF NOT EXISTS idx_ydsz_operation_log_brin
    ON ydsz_operation_log USING BRIN (created_at)
    WITH (pages_per_range = 32);

-- 初始化系统配置
INSERT INTO ydsz_config (config_group, config_key, config_value, value_type, description, created_by) VALUES
    ('system', 'system.name', 'YDSZ 项目运营管理系统', 'STRING', '系统名称', 0),
    ('system', 'system.version', '1.0.0', 'STRING', '系统版本', 0),
    ('rate', 'rate.social.company.rate', '0.245', 'NUMBER', '公司社保比例', 0),
    ('rate', 'rate.fund.company.rate', '0.05', 'NUMBER', '公司公积金比例', 0),
    ('rate', 'rate.workdays.per.month', '21.75', 'NUMBER', '月计薪天数', 0),
    ('rate', 'rate.hours.per.day', '8', 'NUMBER', '日标准工时', 0),
    ('workflow', 'workflow.engine', 'ydsz', 'STRING', '工作流引擎（自研 ydsz_flow_*）', 0),
    ('alert', 'alert.cpi.yellow', '0.95', 'NUMBER', 'CPI 黄色预警阈值', 0),
    ('alert', 'alert.cpi.red', '0.85', 'NUMBER', 'CPI 红色预警阈值', 0),
    ('alert', 'alert.spi.yellow', '0.90', 'NUMBER', 'SPI 黄色预警阈值', 0),
    ('alert', 'alert.spi.red', '0.80', 'NUMBER', 'SPI 红色预警阈值', 0),
    ('alert', 'alert.bench.days.yellow', '7', 'NUMBER', 'Bench 黄色预警天数', 0),
    ('alert', 'alert.bench.days.red', '15', 'NUMBER', 'Bench 红色预警天数', 0)
ON CONFLICT DO NOTHING;

-- --------------------------------------------------------------------

-- ============================ [004] init ydsz workflow schema ============================

-- =====================================================
-- YDSZ 工作流基础模块清理 DDL（Flowable 表已下线）
-- 版本: V1.0.0_004
-- 描述: 完全移除 Flowable 引擎相关的业务关联表 / 表单定义表 / 节点配置表
--       业务流程关联信息已统一收敛到自研 ydsz_flow_instance / ydsz_flow_run_task
--       流程表单/节点配置已收敛到自研 ydsz_flow_definition / ydsz_flow_node / ydsz_flow_skip
-- 历史: V1.0.0_004 旧版本曾创建 ydsz_workflow_business / ydsz_workflow_form / ydsz_workflow_node_config
--       现已废弃，本次迁移仅 DROP（不重建），以保证幂等
-- =====================================================

-- 清理：业务流程实例关联表（功能已被 ydsz_flow_instance 替代）
-- P1-6: DROP 改 CREATE IF NOT EXISTS 即可,无需 DROP(已废弃表)

-- 清理：流程表单定义表（功能已通过 ydsz_flow_definition.form_path 替代）
-- P1-6: 已废弃,无需 DROP

-- 清理：流程节点配置表（功能已通过 ydsz_flow_node.permission_flag / ext 替代）
-- P1-6: 已废弃,无需 DROP

-- --------------------------------------------------------------------

-- ============================ [005] init ydsz file schema ============================
-- [INLINE-OPT] 已统一为单文件 V1.0.0.sql 的最终形态:
--   1) 时间字段 TIMESTAMP → TIMESTAMPTZ
--   2) 审计字段 create_by/create_time → created_by/created_at 规范命名
--   3) tenant_id 改为 NOT NULL DEFAULT 1,与全项目其他表保持一致
--   4) 内联 status/deleted CHECK 约束
--   5) 内联 (tenant_id, created_at DESC) WHERE deleted = 0 复合部分索引
-- =====================================================
-- YDSZ 文件存储模块 DDL
-- 版本: V1.0.0_005 (merged into V1.0.0.sql)
-- 描述: 文件元信息表(MinIO/OSS 对象存储统一管理)
-- =====================================================

CREATE TABLE IF NOT EXISTS ydsz_file (
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
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
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
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

COMMENT ON TABLE ydsz_file IS '文件元信息表: 统一管理 MinIO/OSS 等对象存储中的文件,支持业务关联与临时 URL';

COMMENT ON COLUMN ydsz_file.id IS '主键 ID';

COMMENT ON COLUMN ydsz_file.file_name IS '存储文件名(系统按 UUID 生成,避免冲突)';

COMMENT ON COLUMN ydsz_file.original_name IS '原始文件名(用户上传时的文件名)';

COMMENT ON COLUMN ydsz_file.file_path IS '对象存储 Key/路径(如 contracts/2026/06/xxx.pdf)';

COMMENT ON COLUMN ydsz_file.bucket IS '对象存储桶名';

COMMENT ON COLUMN ydsz_file.content_type IS 'MIME 类型(如 application/pdf)';

COMMENT ON COLUMN ydsz_file.file_size IS '文件大小(字节)';

COMMENT ON COLUMN ydsz_file.file_hash IS '文件 SHA-256 哈希(用于秒传/去重/完整性校验)';

COMMENT ON COLUMN ydsz_file.biz_type IS '业务类型(如 contract/invoice/delivery)';

COMMENT ON COLUMN ydsz_file.biz_id IS '业务单据 ID(关联具体业务表)';

COMMENT ON COLUMN ydsz_file.storage_type IS '存储类型: MINIO / LOCAL 本地 / OSS 阿里云 / COS 腾讯云';

COMMENT ON COLUMN ydsz_file.access_url IS '访问 URL(预签名 URL,带过期时间)';

COMMENT ON COLUMN ydsz_file.url_expire_at IS '访问 URL 过期时间';

COMMENT ON COLUMN ydsz_file.uploader_id IS '上传人 ID';

COMMENT ON COLUMN ydsz_file.uploader_name IS '上传人姓名';

COMMENT ON COLUMN ydsz_file.description IS '文件描述/备注';

COMMENT ON COLUMN ydsz_file.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_file.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_file.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_file.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_file.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN ydsz_file.tenant_id IS '租户 ID(单租户部署默认 1)';

-- [INLINE-OPT] 全部索引添加 deleted 部分条件,避免逻辑删除行干扰
CREATE INDEX IF NOT EXISTS idx_ydsz_file_biz
    ON ydsz_file (biz_type, biz_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_file_hash
    ON ydsz_file (file_hash) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_file_uploader
    ON ydsz_file (uploader_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ydsz_file_bucket
    ON ydsz_file (bucket) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:按租户 + 创建时间倒序,支持文件中心列表分页
CREATE INDEX IF NOT EXISTS idx_ydsz_file_tenant_created
    ON ydsz_file (tenant_id, created_at DESC) WHERE deleted = 0;

-- [INLINE-OPT] URL 过期清理:按 url_expire_at 升序扫描已过期 URL(系统后台任务使用)
CREATE INDEX IF NOT EXISTS idx_ydsz_file_url_expire
    ON ydsz_file (url_expire_at) WHERE deleted = 0 AND url_expire_at IS NOT NULL;

-- ============================ [006e] P7-2 租户级配额 ============================

-- [P7-2] 租户级配额表：控制单个租户可创建任务数、并发执行数、日执行总量
-- 未配置记录的租户视为 unlimited（由应用层 CronjobProperties.Quota.defaultMax* 兜底）
CREATE TABLE IF NOT EXISTS ydsz_tenant_quota(
    id                    VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id             VARCHAR(20)      NOT NULL UNIQUE,
    -- 任务数上限（NULL=unlimited；超过此值拒绝创建新任务）
    max_jobs              INTEGER,
    -- 并发执行上限（NULL=unlimited；超过此值拒绝派发，P7-3 实现）
    max_concurrent        INTEGER,
    -- 日执行量上限（NULL=unlimited；超过此值拒绝派发，P7-3 实现）
    max_daily_executions  INTEGER,
    -- 是否启用配额检查（false=该租户不受配额限制，即使配置了上限）
    enabled               SMALLINT       NOT NULL DEFAULT 1,
    -- 审计字段
    created_by            VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT       NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_ptq_max_jobs_pos        CHECK (max_jobs IS NULL OR max_jobs > 0),
    CONSTRAINT ck_ptq_max_concurrent_pos CHECK (max_concurrent IS NULL OR max_concurrent > 0),
    CONSTRAINT ck_ptq_max_daily_pos      CHECK (max_daily_executions IS NULL OR max_daily_executions > 0),
    CONSTRAINT ck_ptq_enabled_enum       CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_ptq_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_tenant_quota IS '租户级配额表（P7-2）：控制单个租户的任务数/并发数/日执行量上限';

COMMENT ON COLUMN ydsz_tenant_quota.id IS '主键 ID';

COMMENT ON COLUMN ydsz_tenant_quota.tenant_id IS '租户 ID（唯一，一个租户一条配额记录）';

COMMENT ON COLUMN ydsz_tenant_quota.max_jobs IS '任务数上限（NULL=unlimited）';

COMMENT ON COLUMN ydsz_tenant_quota.max_concurrent IS '并发执行上限（NULL=unlimited，P7-3 实现）';

COMMENT ON COLUMN ydsz_tenant_quota.max_daily_executions IS '日执行量上限（NULL=unlimited，P7-3 实现）';

COMMENT ON COLUMN ydsz_tenant_quota.enabled IS '是否启用配额检查: 0 禁用 / 1 启用';

COMMENT ON COLUMN ydsz_tenant_quota.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_tenant_quota.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_tenant_quota.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_tenant_quota.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_tenant_quota.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- 默认租户（tenant_id='1'）的初始配额记录（unlimited，便于单租户部署直接使用）
INSERT INTO ydsz_tenant_quota (id, tenant_id, max_jobs, max_concurrent, max_daily_executions, enabled)
VALUES ('1', '1', NULL, NULL, NULL, 1)
ON CONFLICT (tenant_id) DO NOTHING;

-- ============================ [006f] 租户元数据管理 ============================

-- [P0-1] 租户主表：SaaS 多租户核心元数据
CREATE TABLE IF NOT EXISTS ydsz_tenant(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_code         VARCHAR(64)      NOT NULL UNIQUE,
    tenant_name         VARCHAR(128)     NOT NULL,
    contact_name        VARCHAR(64),
    contact_phone       VARCHAR(32),
    contact_email       VARCHAR(128),
    status              VARCHAR(16)      NOT NULL DEFAULT 'ACTIVE',
    plan_id             VARCHAR(20),
    expire_at           TIMESTAMPTZ,
    datasource_key      VARCHAR(64),
    remark              VARCHAR(512),
    -- 审计字段
    created_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT         NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_tenant_status    CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_tenant_deleted   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_tenant IS '租户主表：SaaS 多租户核心元数据管理';
COMMENT ON COLUMN ydsz_tenant.id IS '主键 ID';
COMMENT ON COLUMN ydsz_tenant.tenant_code IS '租户编码（唯一业务标识，如 TENANT_001）';
COMMENT ON COLUMN ydsz_tenant.tenant_name IS '租户名称';
COMMENT ON COLUMN ydsz_tenant.contact_name IS '联系人姓名';
COMMENT ON COLUMN ydsz_tenant.contact_phone IS '联系电话';
COMMENT ON COLUMN ydsz_tenant.contact_email IS '联系邮箱';
COMMENT ON COLUMN ydsz_tenant.status IS '租户状态: ACTIVE 正常 / INACTIVE 未激活 / SUSPENDED 已停用';
COMMENT ON COLUMN ydsz_tenant.plan_id IS '关联套餐 ID（ydsz_tenant_plan.id）';
COMMENT ON COLUMN ydsz_tenant.expire_at IS '订阅到期时间（NULL=永不过期）';
COMMENT ON COLUMN ydsz_tenant.datasource_key IS '独立数据源标识（ISOLATE_DB 模式下使用，对应 DynamicRoutingDataSource 的 key）';
COMMENT ON COLUMN ydsz_tenant.remark IS '备注';
COMMENT ON COLUMN ydsz_tenant.created_by IS '创建人 ID';
COMMENT ON COLUMN ydsz_tenant.created_at IS '创建时间';
COMMENT ON COLUMN ydsz_tenant.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN ydsz_tenant.updated_at IS '最后修改时间';
COMMENT ON COLUMN ydsz_tenant.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- 索引：按状态查询
CREATE INDEX IF NOT EXISTS idx_tenant_status ON ydsz_tenant (status) WHERE deleted = 0;
-- 索引：按套餐查询
CREATE INDEX IF NOT EXISTS idx_tenant_plan_id ON ydsz_tenant (plan_id) WHERE deleted = 0;

-- 默认租户（tenant_id='1'）的初始记录
INSERT INTO ydsz_tenant (id, tenant_code, tenant_name, contact_name, contact_email, status, plan_id, expire_at)
VALUES ('1', 'DEFAULT', '默认租户', '系统管理员', 'admin@ydsz.example.com', 'ACTIVE', NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- ============================ [006g] 租户套餐管理 ============================

-- [P0-2] 租户套餐表：定义不同租户的权限/功能集合
CREATE TABLE IF NOT EXISTS ydsz_tenant_plan(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    plan_code           VARCHAR(64)      NOT NULL UNIQUE,
    plan_name           VARCHAR(128)     NOT NULL,
    description         VARCHAR(512),
    status              VARCHAR(16)      NOT NULL DEFAULT 'ACTIVE',
    sort_order          INTEGER          NOT NULL DEFAULT 0,
    -- 审计字段
    created_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT         NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_tplan_status    CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_tplan_deleted   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_tenant_plan IS '租户套餐表：定义不同租户的权限/功能集合';
COMMENT ON COLUMN ydsz_tenant_plan.id IS '主键 ID';
COMMENT ON COLUMN ydsz_tenant_plan.plan_code IS '套餐编码（唯一，如 BASIC / PROFESSIONAL / ENTERPRISE）';
COMMENT ON COLUMN ydsz_tenant_plan.plan_name IS '套餐名称';
COMMENT ON COLUMN ydsz_tenant_plan.description IS '套餐描述';
COMMENT ON COLUMN ydsz_tenant_plan.status IS '套餐状态: ACTIVE 启用 / INACTIVE 停用';
COMMENT ON COLUMN ydsz_tenant_plan.sort_order IS '排序号';
COMMENT ON COLUMN ydsz_tenant_plan.created_by IS '创建人 ID';
COMMENT ON COLUMN ydsz_tenant_plan.created_at IS '创建时间';
COMMENT ON COLUMN ydsz_tenant_plan.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN ydsz_tenant_plan.updated_at IS '最后修改时间';
COMMENT ON COLUMN ydsz_tenant_plan.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- 索引：按状态查询
CREATE INDEX IF NOT EXISTS idx_tplan_status ON ydsz_tenant_plan (status) WHERE deleted = 0;

-- 默认套餐（基础版）
INSERT INTO ydsz_tenant_plan (id, plan_code, plan_name, description, status, sort_order)
VALUES ('1', 'BASIC', '基础版', '默认基础套餐，包含核心功能', 'ACTIVE', 1)
ON CONFLICT (id) DO NOTHING;

-- ============================ [006h] 租户套餐-菜单关联 ============================

-- [P0-2] 租户套餐菜单关联表：套餐与菜单的多对多关系
CREATE TABLE IF NOT EXISTS ydsz_tenant_plan_menu(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    plan_id             VARCHAR(20)      NOT NULL,
    menu_id             VARCHAR(20)      NOT NULL,
    -- 审计字段
    created_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT         NOT NULL DEFAULT 0,
    -- 唯一约束：同一套餐下同一菜单只能关联一次
    CONSTRAINT uk_tplan_menu UNIQUE (plan_id, menu_id),
    -- 数据完整性约束
    CONSTRAINT ck_tpm_deleted   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_tenant_plan_menu IS '租户套餐菜单关联表：套餐与菜单的多对多关系';
COMMENT ON COLUMN ydsz_tenant_plan_menu.id IS '主键 ID';
COMMENT ON COLUMN ydsz_tenant_plan_menu.plan_id IS '套餐 ID（ydsz_tenant_plan.id）';
COMMENT ON COLUMN ydsz_tenant_plan_menu.menu_id IS '菜单 ID（ydsz_menu.id）';
COMMENT ON COLUMN ydsz_tenant_plan_menu.created_by IS '创建人 ID';
COMMENT ON COLUMN ydsz_tenant_plan_menu.created_at IS '创建时间';
COMMENT ON COLUMN ydsz_tenant_plan_menu.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- 索引：按套餐查询
CREATE INDEX IF NOT EXISTS idx_tpm_plan_id ON ydsz_tenant_plan_menu (plan_id) WHERE deleted = 0;
-- 索引：按菜单查询
CREATE INDEX IF NOT EXISTS idx_tpm_menu_id ON ydsz_tenant_plan_menu (menu_id) WHERE deleted = 0;

-- --------------------------------------------------------------------

-- ============================ [016] init ydsz security ============================

-- ============================================================
-- V1.0.0_016  权限安全体系  脚本
-- ============================================================
-- 说明：批次 13 权限安全体系
-- 1) 数据权限：ydsz_user_account 增加 data_scope / custom_dept_ids 字段
-- 2) 登录审计：ydsz_login_audit
-- 3) 双因素认证：ydsz_user_2fa
-- 4) 数据导出审计：ydsz_data_export_audit
-- 5) 会话管理：ydsz_user_session
-- ============================================================

-- ----------------------------
-- 1) 增强用户账号表 (已优化内联至 V1.0.0_001 ydsz_user_account 定义)
-- ----------------------------
-- data_scope / custom_dept_ids / mfa_enabled / mfa_type / last_pwd_change_at / pwd_change_count
-- 已在 V1.0.0_001 中以最终结构内联(含 CHECK 约束),此处不再重复 ADD COLUMN

-- ----------------------------
-- 2) 登录审计
-- ----------------------------
CREATE TABLE IF NOT EXISTS ydsz_login_audit (
    id              VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    username        VARCHAR(64)   NOT NULL,
    user_id         VARCHAR(20),
    login_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    login_ip        VARCHAR(64),
    user_agent      VARCHAR(512),
    status          VARCHAR(16)   NOT NULL,
    fail_reason     VARCHAR(64),
    mfa_used        BOOLEAN       NOT NULL DEFAULT FALSE,
    mfa_success     BOOLEAN,
    trace_id        VARCHAR(20),
    tenant_id       VARCHAR(20)        NOT NULL DEFAULT '1',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_login_audit_status    CHECK (status IN ('SUCCESS','FAIL','LOCKED','MFA_REQUIRED')),
    CONSTRAINT ck_login_audit_deleted   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  ydsz_login_audit IS '登录审计日志表: 等保 2.0 要求,登录成功/失败全留存,支持溯源审计';

COMMENT ON COLUMN ydsz_login_audit.username IS '登录用户名: 失败时也可记录,便于排查撞库';

COMMENT ON COLUMN ydsz_login_audit.user_id IS '登录用户 ID: 成功时记录,失败可为 NULL';

COMMENT ON COLUMN ydsz_login_audit.login_at IS '登录时间';

COMMENT ON COLUMN ydsz_login_audit.login_ip IS '登录 IP: 用于异常登录检测';

COMMENT ON COLUMN ydsz_login_audit.user_agent IS '浏览器 UA: 用于设备指纹';

COMMENT ON COLUMN ydsz_login_audit.status IS '状态: SUCCESS 成功 / FAIL 失败 / LOCKED 锁定 / MFA_REQUIRED 待 MFA';

COMMENT ON COLUMN ydsz_login_audit.fail_reason IS '失败原因: 密码错误/账号锁定/MFA 失败等';

COMMENT ON COLUMN ydsz_login_audit.mfa_used IS '是否使用 MFA: true=已启用并使用';

COMMENT ON COLUMN ydsz_login_audit.mfa_success IS 'MFA 是否通过: NULL=未使用,true=通过,false=失败';

COMMENT ON COLUMN ydsz_login_audit.trace_id IS '链路追踪 ID';

COMMENT ON COLUMN ydsz_login_audit.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_login_audit.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_login_audit_*)
CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_user_at
    ON ydsz_login_audit(tenant_id, username, login_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_ip_at
    ON ydsz_login_audit(tenant_id, login_ip, login_at DESC)
    WHERE deleted = 0 AND login_ip IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_status_at
    ON ydsz_login_audit(tenant_id, status, login_at DESC)
    WHERE deleted = 0;

-- ----------------------------
-- 4) 数据导出审计
-- ----------------------------
CREATE TABLE IF NOT EXISTS ydsz_data_export_audit (
    id              VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    user_id         VARCHAR(20)        NOT NULL,
    username        VARCHAR(64)   NOT NULL,
    export_module   VARCHAR(64)   NOT NULL,
    export_action   VARCHAR(64)   NOT NULL,
    biz_type        VARCHAR(32),
    row_count       INT           NOT NULL DEFAULT 0,
    file_name       VARCHAR(256),
    file_size       BIGINT,
    export_format   VARCHAR(16),
    query_summary   TEXT,
    trace_id        VARCHAR(20),
    client_ip       VARCHAR(64),
    tenant_id       VARCHAR(20)        NOT NULL DEFAULT '1',
    exported_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_dea_export_action      CHECK (export_action IN ('EXPORT','PRINT','DOWNLOAD')),
    CONSTRAINT ck_dea_export_format      CHECK (export_format IS NULL OR export_format IN ('XLSX','CSV','PDF','JSON','XML')),
    CONSTRAINT ck_dea_row_count_nonneg   CHECK (row_count >= 0),
    CONSTRAINT ck_dea_file_size_nonneg   CHECK (file_size IS NULL OR file_size >= 0),
    CONSTRAINT ck_dea_deleted            CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  ydsz_data_export_audit IS '数据导出审计表: 合同/财务/薪酬等敏感数据导出全留存,@DataExportAudit 自动捕获';

COMMENT ON COLUMN ydsz_data_export_audit.user_id IS '导出用户 ID';

COMMENT ON COLUMN ydsz_data_export_audit.username IS '导出用户姓名（冗余）';

COMMENT ON COLUMN ydsz_data_export_audit.export_module IS '导出模块: PROJECT/EXECUTION/FINANCE 等';

COMMENT ON COLUMN ydsz_data_export_audit.export_action IS '导出动作: EXPORT 导出 / PRINT 打印 / DOWNLOAD 下载';

COMMENT ON COLUMN ydsz_data_export_audit.biz_type IS '业务类型';

COMMENT ON COLUMN ydsz_data_export_audit.row_count IS '导出行数: 自动检测 Collection/Number,作为审计基数';

COMMENT ON COLUMN ydsz_data_export_audit.file_name IS '导出文件名';

COMMENT ON COLUMN ydsz_data_export_audit.file_size IS '文件大小(字节)';

COMMENT ON COLUMN ydsz_data_export_audit.export_format IS '导出格式: XLSX/CSV/PDF';

COMMENT ON COLUMN ydsz_data_export_audit.query_summary IS '查询条件摘要: 用于审计导出范围';

COMMENT ON COLUMN ydsz_data_export_audit.trace_id IS '链路追踪 ID';

COMMENT ON COLUMN ydsz_data_export_audit.client_ip IS '客户端 IP';

COMMENT ON COLUMN ydsz_data_export_audit.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_data_export_audit.exported_at IS '导出时间';

COMMENT ON COLUMN ydsz_data_export_audit.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_export_audit_*)
CREATE INDEX IF NOT EXISTS idx_dea_tenant_user_at
    ON ydsz_data_export_audit(tenant_id, user_id, exported_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_dea_tenant_module_at
    ON ydsz_data_export_audit(tenant_id, export_module, exported_at DESC)
    WHERE deleted = 0;

-- 5) 敏感操作二次确认: 已下线（@RequireReAuth 流程整体移除,2026-07 简化）

-- --------------------------------------------------------------------

-- ============================ [019] init ydsz alert thresholds ============================

-- ====================================================================
-- 预警阈值配置（ydsz_config，group=alert）
--
--  说明：EVM / Bench / 预算 / 毛利率 / 利用率 等模块的告警阈值从此处读取，
--       业务模块通过 ConfigClient Feign 调用 ydsz-system 读取。
-- ====================================================================

INSERT INTO ydsz_config (config_group, config_key, config_value, value_type, description, is_public, created_by)
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

-- --------------------------------------------------------------------

-- ============================ [031] init report subscription ============================

-- ============================================================
-- V1.0.0_031  P1-5 报表订阅与导出记录表
-- ============================================================
-- 说明：定时报表生成与分发（P1-5）
--   ydsz_report_subscription  报表订阅表
--   ydsz_export_record        报表导出记录（P0-3 合并 source='SUBSCRIPTION'）
-- ============================================================

-- 报表订阅表
CREATE TABLE IF NOT EXISTS ydsz_report_subscription (
    id              VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
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

COMMENT ON TABLE ydsz_report_subscription IS '报表订阅表';

COMMENT ON COLUMN ydsz_report_subscription.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_report_subscription.subscriber_id IS '订阅人ID';

COMMENT ON COLUMN ydsz_report_subscription.report_type IS '报表类型 (COCKPIT/EVM/PROFIT/UTILIZATION/BENCH_COST/RISK)';

COMMENT ON COLUMN ydsz_report_subscription.frequency IS '推送频率 (DAILY/WEEKLY/MONTHLY/REALTIME)';

COMMENT ON COLUMN ydsz_report_subscription.channels IS '推送渠道，逗号分隔 (EMAIL/DINGTALK/INAPP)';

COMMENT ON COLUMN ydsz_report_subscription.recipients IS '接收人邮箱，逗号分隔';

COMMENT ON COLUMN ydsz_report_subscription.enabled IS '是否启用 (1=启用, 0=停用)';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_prs_tenant_subscriber
    ON ydsz_report_subscription (tenant_id, subscriber_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_prs_tenant_type_freq
    ON ydsz_report_subscription (tenant_id, report_type, frequency)
    WHERE deleted = 0;

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

-- 异步导出记录表（同时承担历史 ydsz_report_export_record 的角色，P0-3 合并）
CREATE TABLE IF NOT EXISTS ydsz_export_record (
    id              VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
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

COMMENT ON TABLE ydsz_export_record IS '异步导出记录表（同时承载报表订阅导出，P0-3 合并）';

COMMENT ON COLUMN ydsz_export_record.tenant_id IS '租户 ID';

COMMENT ON COLUMN ydsz_export_record.source IS '来源：MANUAL 用户主动提交 / SUBSCRIPTION 订阅触发';

COMMENT ON COLUMN ydsz_export_record.user_id IS '发起人 ID（MANUAL 必填，SUBSCRIPTION 取订阅人）';

COMMENT ON COLUMN ydsz_export_record.export_type IS '通用导出类型 (MANUAL 主用，如 PROJECT/CONTRACT/INVOICE/PAYMENT/EVM/AUDIT_LOG)';

COMMENT ON COLUMN ydsz_export_record.report_type IS '报表类型（SUBSCRIPTION 主用，如 COCKPIT/EVM/PROFIT/UTILIZATION）';

COMMENT ON COLUMN ydsz_export_record.subscription_id IS '关联订阅 ID（仅 SUBSCRIPTION 来源有值，引用 ydsz_report_subscription.id）';

COMMENT ON COLUMN ydsz_export_record.file_name IS '文件名';

COMMENT ON COLUMN ydsz_export_record.file_key IS 'MinIO 文件 key';

COMMENT ON COLUMN ydsz_export_record.file_url IS '下载 URL';

COMMENT ON COLUMN ydsz_export_record.file_size IS '文件大小（字节）';

COMMENT ON COLUMN ydsz_export_record.status IS '状态 (PENDING/GENERATING/COMPLETED/SENT/FAILED/EXPIRED)';

COMMENT ON COLUMN ydsz_export_record.params IS '导出参数（JSON）';

COMMENT ON COLUMN ydsz_export_record.error_message IS '错误信息';

COMMENT ON COLUMN ydsz_export_record.completed_at IS '完成时间';

COMMENT ON COLUMN ydsz_export_record.expired_at IS '过期时间';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pex_tenant_user_created
    ON ydsz_export_record (tenant_id, user_id, created_at DESC)
    WHERE deleted = 0 AND source = 'MANUAL';

CREATE INDEX IF NOT EXISTS idx_pex_tenant_status
    ON ydsz_export_record (tenant_id, status)
    WHERE completed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_pex_tenant_expired
    ON ydsz_export_record (tenant_id, expired_at)
    WHERE expired_at IS NOT NULL;

-- P0-3: 订阅维度索引（用于报表中心回溯）
CREATE INDEX IF NOT EXISTS idx_pex_tenant_subscription
    ON ydsz_export_record (tenant_id, subscription_id, created_at DESC)
    WHERE source = 'SUBSCRIPTION' AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pex_tenant_report_type
    ON ydsz_export_record (tenant_id, report_type, created_at DESC)
    WHERE source = 'SUBSCRIPTION' AND deleted = 0;

-- P0-3: 提供商追踪 ID 索引（与 060 节保持一致）
CREATE INDEX IF NOT EXISTS idx_pex_provider_trace
    ON ydsz_export_record (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

ANALYZE ydsz_operation_log;

-- 3. 字典版本
ALTER TABLE ydsz_dict_version ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_dict_version_tenant ON ydsz_dict_version(tenant_id);

-- 16. 配置
ALTER TABLE ydsz_config ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_config_tenant ON ydsz_config(tenant_id);

-- 17. 操作日志（V1.0.0_008 已含 tenant_id，跳过 ADD COLUMN，仅补索引）
CREATE INDEX IF NOT EXISTS idx_pol_tenant ON ydsz_operation_log(tenant_id);

CREATE INDEX IF NOT EXISTS idx_config_tenant_created
    ON ydsz_config(tenant_id, created_at DESC) WHERE deleted = 0;

-- ============================================================
-- 四、逻辑删除字段索引覆盖（H3.2）
--   对 V1.0.0_001 中未建 deleted 索引的表补建
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_ydsz_dict_version_deleted ON ydsz_dict_version(deleted);

-- 报表订阅
ALTER TABLE ydsz_report_subscription ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_report_sub_tenant ON ydsz_report_subscription(tenant_id);

-- 异步导出记录（P0-3 合并：原报表导出记录已并入此表）
ALTER TABLE ydsz_export_record ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_export_rec_tenant ON ydsz_export_record(tenant_id);

ANALYZE ydsz_dict_version;

ANALYZE ydsz_config;

ANALYZE ydsz_operation_log;

ANALYZE ydsz_report_subscription;

ANALYZE ydsz_export_record;

-- ----------------------------------------------------------------------------
-- 3) ydsz_dict_version 字段补齐
--    - 新增 updated_at / updated_by / tenant_id(对齐 BaseDO 5 字段基线)
--    - created_at / effective_date 统一为 TIMESTAMPTZ(全工程时间字段统一约定)
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_dict_version ADD COLUMN IF NOT EXISTS updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE ydsz_dict_version ADD COLUMN IF NOT EXISTS updated_by    VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';

ALTER TABLE ydsz_dict_version ADD COLUMN IF NOT EXISTS tenant_id     VARCHAR(20) NOT NULL DEFAULT '1';

ALTER TABLE ydsz_dict_version ALTER COLUMN created_at     TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE ydsz_dict_version ALTER COLUMN effective_date TYPE TIMESTAMPTZ USING effective_date AT TIME ZONE 'UTC';

COMMENT ON COLUMN ydsz_dict_version.updated_by    IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_dict_version.updated_at    IS '最后修改时间';

COMMENT ON COLUMN ydsz_dict_version.tenant_id     IS '租户 ID(单租户部署默认 1)';

-- 复合索引(与全工程惯例一致)
CREATE INDEX IF NOT EXISTS idx_pdv_tenant_type
    ON ydsz_dict_version (tenant_id, type_code)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pdv_tenant_type_created
    ON ydsz_dict_version (tenant_id, type_code, created_at DESC)
    WHERE deleted = 0;

ALTER TABLE ydsz_dict_version ADD COLUMN IF NOT EXISTS snapshot_json TEXT;

COMMENT ON COLUMN ydsz_dict_version.snapshot_json IS '字典项列表 JSON 快照(用于版本回滚)';

ANALYZE ydsz_dict_version;

-- ====================================================================
-- ============================ [061] merge export tables ============================
-- ====================================================================
-- V1.0.0_061  P0-3 合并 ydsz_export_record 与 ydsz_report_export_record
-- ----------------------------------------------------------------------------
-- 背景:
--   ydsz_export_record(下载中心,P2-11)与 ydsz_report_export_record(订阅报表,P1-5)
--   结构高度重复,均记录 Excel 导出 + MinIO 存储 + 状态流转,导致:
--     1. 两表字段语义重叠(file_url/file_key/status/created_at…)
--     2. ReportScheduleServiceImpl 直接使用 SQL INSERT,字段错位(generated_at 在
--        新表中已不存在)且 status='COMPLETED' 不在原 CHECK 约束中
--     3. 前端下载中心只能展示用户主动导出,看不到订阅触发的报表下载入口
--     4. 监控/统计(导出成功率、平均耗时)需 UNION 两表,体验差
--
-- 合并方案:
--   保留 ydsz_export_record 作为主表,新增:
--     - source            VARCHAR(16)  MANUAL 用户主动 / SUBSCRIPTION 订阅触发
--     - subscription_id   VARCHAR(20)  仅 SUBSCRIPTION 来源有值
--     - report_type       VARCHAR(50)  仅 SUBSCRIPTION 来源有值(订阅报表类型)
--   user_id 改为可空:MANUAL 必填,SUBSCRIPTION 取订阅人
--   状态枚举统一: PENDING/GENERATING/COMPLETED/SENT/FAILED/EXPIRED
--   互斥 CHECK 约束: MANUAL 必须有 user_id 且无 subscription_id,反之亦然
--   删除原 ydsz_report_export_record 表
--   同步改造 Java 实体与 Service(ReportScheduleServiceImpl 改用同一张表)
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- 1) ydsz_export_record 新增 source / subscription_id / report_type 三列
--    user_id 由 NOT NULL 改为可空(MANUAL 必填,SUBSCRIPTION 取订阅人)
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_export_record ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE ydsz_export_record
    ADD COLUMN IF NOT EXISTS source          VARCHAR(16) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE ydsz_export_record
    ADD COLUMN IF NOT EXISTS subscription_id VARCHAR(20);

ALTER TABLE ydsz_export_record
    ADD COLUMN IF NOT EXISTS report_type     VARCHAR(50);

-- 互斥 CHECK 约束(若已存在则跳过)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_pex_source'
          AND conrelid = 'ydsz_export_record'::regclass
    ) THEN
        ALTER TABLE ydsz_export_record
            ADD CONSTRAINT ck_pex_source CHECK (source IN ('MANUAL', 'SUBSCRIPTION'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_pex_source_link'
          AND conrelid = 'ydsz_export_record'::regclass
    ) THEN
        ALTER TABLE ydsz_export_record
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
          AND conrelid = 'ydsz_export_record'::regclass
    ) THEN
        ALTER TABLE ydsz_export_record DROP CONSTRAINT ck_pex_status;
    END IF;
    ALTER TABLE ydsz_export_record
        ADD CONSTRAINT ck_pex_status CHECK (status IN ('PENDING','GENERATING','COMPLETED','SENT','FAILED','EXPIRED'));
END $$;

COMMENT ON COLUMN ydsz_export_record.source          IS '来源:MANUAL 用户主动提交 / SUBSCRIPTION 订阅触发(P0-3 合并引入)';

COMMENT ON COLUMN ydsz_export_record.subscription_id IS '关联订阅 ID(仅 SUBSCRIPTION 来源有值)';

COMMENT ON COLUMN ydsz_export_record.report_type     IS '报表类型(SUBSCRIPTION 主用,如 COCKPIT/EVM/PROFIT)';

-- ----------------------------------------------------------------------------
-- 2) 订阅维度索引
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_pex_tenant_subscription
    ON ydsz_export_record (tenant_id, subscription_id, created_at DESC)
    WHERE source = 'SUBSCRIPTION' AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pex_tenant_report_type
    ON ydsz_export_record (tenant_id, report_type, created_at DESC)
    WHERE source = 'SUBSCRIPTION' AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pex_provider_trace
    ON ydsz_export_record (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 4) 同步重建 source=user_id 复合索引(原 user_id NOT NULL 索引已可用,无需重建)
-- ----------------------------------------------------------------------------

ANALYZE ydsz_export_record;

-- ====================================================================
-- ============================ [062] monthly partitioning for audit logs ============================
-- ====================================================================
-- V1.0.0_062  P1-4 日志/审计表按月分区 + BRIN
-- ----------------------------------------------------------------------------
-- 背景:
--   ydsz_operation_log / ydsz_flow_audit_log 预计 100w+/年
--   单表 B-Tree 索引老化慢、清理成本高(DELETE 真空)、备份耗时长
--   改造目标: 按月 RANGE 分区,主键包含分区键,索引与 BRIN 自动级联
--
-- 父表 DDL 改造:
--   - ydsz_operation_log       PARTITION BY RANGE (created_at)  PRIMARY KEY (id, created_at)
--   - ydsz_flow_audit_log      PARTITION BY RANGE (operated_at) PRIMARY KEY (id, operated_at)
--
-- 维护建议:
--   - 每季度巡检,确保 DEFAULT 分区无新增数据
--   - 新增月份分区: CREATE TABLE ydsz_operation_log_yYYYYmMM PARTITION OF ...
--   - 数据归档: ALTER TABLE ydsz_operation_log DETACH PARTITION y2026m01
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- 1) ydsz_operation_log 月度分区 (2026-01 ~ 2027-12 共 24 个月)
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
        partition_name := 'ydsz_operation_log_y' ||
                          TO_CHAR(partition_date, 'YYYY') || 'm' ||
                          TO_CHAR(partition_date, 'MM');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF ydsz_operation_log FOR VALUES FROM (%L) TO (%L)',
            partition_name, partition_date, next_date
        );
    END LOOP;
END $$;

-- DEFAULT 兜底分区(新分区未及时创建时数据落入此分区,触发告警后补建)
CREATE TABLE IF NOT EXISTS ydsz_operation_log_default
    PARTITION OF ydsz_operation_log DEFAULT;

COMMENT ON TABLE ydsz_operation_log_default IS
    'ydsz_operation_log 的 DEFAULT 兜底分区:'
    '接收超出已建月份范围的数据,运维需监控并及时创建对应月份分区;'
    '建表语句不可独立 DROP,需先 ALTER TABLE ... DETACH PARTITION';

ANALYZE ydsz_operation_log;

-- 6) 报表订阅(1 张)
CREATE INDEX IF NOT EXISTS idx_ydsz_report_subscription_trace
    ON ydsz_report_subscription (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- ====================================================================
-- ============================ [065] ydsz_meta_schema_version ============================
-- ====================================================================
-- V1.0.0_065  P2-9  schema 元数据版本表 + 清理合并生成器注释
-- ----------------------------------------------------------------------------
-- 背景:
--   - 历史 SQL 文件在 §GENERATOR NOTE 块中保留了"前向引用表清单",但缺乏
--     运行时可查询的 schema 元数据。
--   - 增加 ydsz_meta_schema_version 表,持久化:
--       1. 当前 schema 版本号与生成时间
--       2. 文件合并元数据(原 V1.0.0_xxx 文件数)
--       3. 已知的"前向引用表"清单(供上线前查漏)
--       4. 已补齐的索引统计(provider_trace_id 等)
--   - 应用启动时可 SELECT 校验版本号,提前发现 schema 漂移
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_meta_schema_version (
    id                  VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    version             VARCHAR(32)  NOT NULL,
    pg_version          VARCHAR(32)  NOT NULL,
    files_merged        INTEGER      NOT NULL,
    generated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pending_tables      TEXT         NOT NULL DEFAULT '',
    notes               TEXT         NOT NULL DEFAULT '',
    -- 审计字段
    created_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_meta_schema_version UNIQUE (version, deleted)
);

COMMENT ON TABLE ydsz_meta_schema_version IS
    'P2-9: schema 元数据版本表;记录当前 schema 的版本、生成参数、'
    '前向引用表清单与补齐索引统计;应用启动时可 SELECT 校验漂移';

COMMENT ON COLUMN ydsz_meta_schema_version.version IS 'schema 版本号,如 V1.0.0';

COMMENT ON COLUMN ydsz_meta_schema_version.pg_version IS '目标 PostgreSQL 主版本,如 18';

COMMENT ON COLUMN ydsz_meta_schema_version.files_merged IS '本次合并的 V1.0.0_xxx 文件数';

COMMENT ON COLUMN ydsz_meta_schema_version.generated_at IS '合并生成时间';

COMMENT ON COLUMN ydsz_meta_schema_version.applied_at IS '实际初始化执行时间';

COMMENT ON COLUMN ydsz_meta_schema_version.pending_tables IS '前向引用表清单(逗号分隔),上线前需补建';

COMMENT ON COLUMN ydsz_meta_schema_version.notes IS '其它需要记录的元数据,如索引统计';

-- ====================================================================
-- ============================ [066] P3 性能/安全/审计 增强设计预留 ============================
-- ====================================================================
-- V1.0.0_066  P3-13/14/15  性能与安全增强(预留式)
-- ----------------------------------------------------------------------------
-- 范围:
--   P3-13  冷热数据分层与历史分区归档(pg_partman / OSS 冷归档)
--   P3-14  敏感字段加密落盘(手机号/身份证/银行卡 SM4 + 哈希索引列)
--   P3-15  ydsz_data_export_audit 接入 OPLOG 字段,支持 UDF 检索
--
-- 设计原则:
--   - 本节不在 V1.0.0 阶段真正改表(避免破坏 entity-SQL 对齐,影响 mvn test)
--   - 仅:
--       1) 预留元数据表的 plan_notes 字段,记录 P3 任务规划
--       2) 在 ydsz_meta_schema_version 中追加 P3 任务说明
--       3) 给出"如何开启 P3 任务"的标准化扩展点(SQL 模板)
--   - 真正落地时: 业务方确认 → 新增 ALTER TABLE → 同步 Java 实体 → mvn test
-- ----------------------------------------------------------------------------

-- 1) 扩 ydsz_meta_schema_version: 增 plan_notes 列(预留 P3 任务说明,无破坏性)
ALTER TABLE ydsz_meta_schema_version
    ADD COLUMN IF NOT EXISTS plan_notes TEXT NOT NULL DEFAULT '';

COMMENT ON COLUMN ydsz_meta_schema_version.plan_notes IS
    'P3 任务规划备注:P3-13/14/15 落地的设计预留字段,'
    '记录哪些 P3 任务已规划、待业务方确认后才能真正启用';


-- ----------------------------------------------------------------------------
-- [P2-1] DAG 节点类型扩展（CONDITION / LOOP / PARALLEL_GATEWAY）
-- ----------------------------------------------------------------------------
-- DAG 节点定义存储在 ydsz_job_dag.dag_definition JSON 字段中（非独立表），
-- 节点类型扩展字段（nodeType / conditionExpression / loopCount / parallelBranches）
-- 直接在 JSON 中管理，无需 ALTER TABLE。
--
-- JSON 节点格式（P2-1 增强后）：
-- {
--   "jobKey": "nodeA",
--   "jobId": "1",
--   "label": "条件判断",
--   "x": 100, "y": 200,
--   "paramsJson": "{}",
--   "nodeType": "CONDITION",            -- TASK(默认) / CONDITION / LOOP / PARALLEL_GATEWAY
--   "conditionExpression": "${nodeA.result=='success'}",  -- CONDITION 节点
--   "loopCount": 3,                     -- LOOP 节点循环次数
--   "parallelBranches": 2               -- PARALLEL_GATEWAY 并行分支数
-- }
--
-- 以下 ALTER 语句用于兼容性（若未来引入独立的 ydsz_job_dag_node 表），
-- 当前为 no-op（表不存在时跳过）。
ALTER TABLE IF EXISTS ydsz_job_dag_node ADD COLUMN IF NOT EXISTS node_type VARCHAR(32) NOT NULL DEFAULT 'TASK';

ALTER TABLE IF EXISTS ydsz_job_dag_node ADD COLUMN IF NOT EXISTS condition_expression VARCHAR(512);

ALTER TABLE IF EXISTS ydsz_job_dag_node ADD COLUMN IF NOT EXISTS loop_count INTEGER;

ALTER TABLE IF EXISTS ydsz_job_dag_node ADD COLUMN IF NOT EXISTS parallel_branches INTEGER;


-- =====================================================
-- 2. 人员标签表 ydsz_employee_tag (已在 [001] 章节创建, [014_1] 已 ALTER 扩展新字段)
-- =====================================================
-- 注意:历史 [SKIPPED-CLEANUP-REBUILD] 标记下的旧版 DDL 已废弃,字段定义以 [001]+[014_1] 为准
-- 本节保留 COMMENT ON COLUMN 用于覆盖 [001] 的简短注释,提供更详细的字段说明
-- (以下 CREATE TABLE IF NOT EXISTS 因表已存在会被跳过,不会重建)
-- =====================================================
COMMENT ON TABLE  ydsz_employee_tag IS '人员标签表: 员工的技能/行业/领域/资质标签,支撑资源推荐智能体匹配';

-- [AUTO-MIGRATION] ydsz_employee_tag: rebuild pattern detected.
-- 注: 历史兼容代码 (兼容 V1.0.0_014_1 旧版 [SKIPPED-CLEANUP-REBUILD] 的字段补齐逻辑)
--   已被前面 CREATE TABLE 取代 (IF NOT EXISTS 已包含全部字段),此处保留
--   空 DO 块以保留脚本兼容性,无任何实际效果。
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'ydsz_employee_tag') THEN
        -- 字段已由上方 CREATE TABLE IF NOT EXISTS 完整定义,这里无需重复 ALTER
        NULL;
    END IF;
END $$;

-- 注释说明: 上方 CREATE TABLE 已包含 tag_name / proficiency / years_exp / remark / tenant_id / provider_trace_id
-- 字段及其 COMMENT,此处不再重复定义,避免与上方 COMMENT 重复执行。

-- --------------------------------------------------------------------

-- ============================ [014] init ydsz admin full perm ============================

-- ====================================================================
-- 9. 初始化菜单权限 + 角色授权 (admin 拥有全部权限)
-- ====================================================================

-- 一. 初始化菜单权限
-- 拆成多步插入：先插入顶层节点（parent_id=0），再插入二级子菜单，
-- 最后插入三级按钮权限。每一步都通过 perm_code 关联父节点。
-- 关键：PostgreSQL 在单条 INSERT VALUES 中，所有子查询都在语句开始时求值，
--       看不到同语句中正在插入的行；因此必须分多语句执行。

-- 步骤 1：插入顶层节点
INSERT INTO ydsz_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    (0, 'dashboard',  '仪表盘',   'MENU', '/dashboard',  'dashboard/index', 'odometer',  1, 1, 'ENABLED', 0),
    (0, 'system',     '系统管理', 'MENU', '/system',     'Layout',          'setting',   2, 1, 'ENABLED', 0),
    (0, 'business',   '业务管理', 'MENU', '/business',   'Layout',          'briefcase', 3, 1, 'ENABLED', 0),
    (0, 'execution',  '项目执行', 'MENU', '/execution',  'Layout',          'cpu',       4, 1, 'ENABLED', 0),
    (0, 'finance',    '财务收支', 'MENU', '/finance',    'Layout',          'credit-card', 5, 1, 'ENABLED', 0),
    (0, 'report',     '经营报表', 'MENU', '/report',     'Layout',          'data-analysis', 6, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- ----------------------------
-- 4. 项目风险预警视图
-- ----------------------------
CREATE OR REPLACE VIEW ydsz_view_risk_dashboard
    WITH (security_invoker = true) AS
SELECT tenant_id,
       risk_level,
       COUNT(*) AS cnt
FROM ydsz_execution_risk
WHERE deleted = 0 AND status IN ('OPEN','MITIGATING')
GROUP BY tenant_id, risk_level;

COMMENT ON VIEW ydsz_view_risk_dashboard IS '项目风险预警视图: 按 tenant_id + risk_level 聚合未关闭风险数,AdvancedReportService#riskDashboard 读取,单租户场景可按 risk_level 过滤';

-- ----------------------------
-- 5. 人效排行（按员工聚合活跃项目数 + 平均 allocation）
-- ----------------------------
CREATE OR REPLACE VIEW ydsz_view_employee_utilization
    WITH (security_invoker = true) AS
SELECT tenant_id,
       employee_id,
       COUNT(*) FILTER (WHERE status = 'ACTIVE')                    AS active_count,
       COUNT(*) FILTER (WHERE status IN ('ACTIVE','RESERVED','TRANSFERRING')) AS assigned_count,
       COALESCE(AVG(allocation) FILTER (WHERE status = 'ACTIVE'), 0) AS avg_allocation,
       COALESCE(SUM(allocation) FILTER (WHERE status = 'ACTIVE'), 0) AS total_allocation
FROM ydsz_resource_assignment
WHERE deleted = 0
GROUP BY tenant_id, employee_id;

COMMENT ON VIEW ydsz_view_employee_utilization IS '人效排行视图: 按 tenant_id + 员工聚合 active_count/assigned_count/avg_allocation,AdvancedReportService#utilizationRank 读取;Feign + try-catch 降级到 0,跨模块故障不阻塞驾驶舱';

-- --------------------------------------------------------------------

-- ============================ [027] init undo log ============================

-- ====================================================================
--  Seata AT 模式 undo_log 表
--  --------------------------------------------------------------------
--  说明：
--    1) AT 模式依赖此表保存 before/after 镜像，用于分支事务回滚
--    2) 必须在每个业务库（ydsz / ydsz_bill / ydsz_archive ...）都建
--    3) 配套 Nacos 配置：data-id = seata-client.properties
--    4) 配套脚本：deploy/seata/verify-seata.sh 会自动检查本表存在
--  --------------------------------------------------------------------
--  版本：V1.0.0_027
--  适用：PostgreSQL 16+
-- ====================================================================

-- ---------- 表结构 ----------
-- id            主键自增
-- branch_id     分支事务 ID（Seata 生成）
-- xid           全局事务 ID（跨服务唯一）
-- context       事务上下文（序列化信息）
-- rollback_info 回滚信息（before/after 镜像 ZIP 压缩）
-- log_status    日志状态 0=正常 1=全局完成 2=全局回滚
-- log_created   创建时间
-- log_modified  最后修改时间
CREATE TABLE IF NOT EXISTS undo_log (
    id            VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    branch_id     VARCHAR(20)       NOT NULL,
    xid           VARCHAR(100) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info BYTEA        NOT NULL,
    log_status    INT          NOT NULL,
    log_created   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    log_modified  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_undo_log UNIQUE (xid, branch_id)
);

-- ---------- 字段注释 ----------
COMMENT ON TABLE  undo_log             IS 'Seata AT 模式分布式事务回滚日志表（每个业务库都需要）';

COMMENT ON COLUMN undo_log.id          IS '主键 ID';

COMMENT ON COLUMN undo_log.branch_id   IS '分支事务 ID（Seata 内部生成）';

COMMENT ON COLUMN undo_log.xid         IS '全局事务 ID（跨服务唯一标识）';

COMMENT ON COLUMN undo_log.context     IS '事务上下文（序列化信息，如应用名、分组等）';

COMMENT ON COLUMN undo_log.rollback_info IS '回滚信息（ZIP 压缩的 before/after 镜像，Base64 编码）';

COMMENT ON COLUMN undo_log.log_status  IS '日志状态：0=正常 1=全局完成 2=全局回滚';

COMMENT ON COLUMN undo_log.log_created IS '创建时间';

COMMENT ON COLUMN undo_log.log_modified IS '最后修改时间';

-- ---------- 性能索引 ----------
-- 建议添加以下索引（百万行级别可显著提升回滚扫描性能）
-- CREATE INDEX IF NOT EXISTS idx_undo_log_xid ON undo_log (xid);
-- CREATE INDEX IF NOT EXISTS idx_undo_log_status_modified ON undo_log (log_status, log_modified);

-- --------------------------------------------------------------------

-- ============================ [028] add flow gap columns ============================

-- =============================================================
-- 工作流引擎对标差距补全 — 新增字段
--
-- GAP-P0: 表单字段权限 (ydsz_flow_node.form_fields_config)
-- GAP-P1: SLA 超时配置 (ydsz_flow_node.sla_config)
-- GAP-P1: 子流程父子关系 (ydsz_flow_instance.parent_instance_id / parent_node_code)
-- GAP-P1: 会签并发版本号 (ydsz_flow_run_task.version)
-- =============================================================

-- -------------------------------------------
-- 1. ydsz_flow_node 新增字段
-- -------------------------------------------
ALTER TABLE ydsz_flow_node ADD COLUMN IF NOT EXISTS form_fields_config TEXT;

-- 更新触发器
CREATE OR REPLACE FUNCTION update_rule_test_case_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_rule_test_case_updated_at ON ydsz_rule_test_case;

COMMENT ON CONSTRAINT ck_rule_def_status_valid ON ydsz_rule_def IS
    '规则状态合法性约束，配合应用层 RuleStatus.canTransitionTo 状态机校验';

SET statement_timeout = '5min';

-- ============================================================
-- 六、undo_log 性能索引（H1.8）
--   Seata AT 模式回滚按 xid 扫描，无索引会全表扫描
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_undo_log_xid ON undo_log(xid);

CREATE INDEX IF NOT EXISTS idx_undo_log_status_modified ON undo_log(log_status, log_modified);

ANALYZE undo_log;

-- pg_hint_plan: 需 preload 预加载, 未加载时跳过
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_hint_plan;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_hint_plan 不可用, 跳过: %', SQLERRM;
END $$;

-- ----------------------------------------------------------------------------
-- 2) ydsz_flow_audit_log 月度分区 (2026-01 ~ 2027-12 共 24 个月)
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
        partition_name := 'ydsz_flow_audit_log_y' ||
                          TO_CHAR(partition_date, 'YYYY') || 'm' ||
                          TO_CHAR(partition_date, 'MM');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF ydsz_flow_audit_log FOR VALUES FROM (%L) TO (%L)',
            partition_name, partition_date, next_date
        );
    END LOOP;
END $$;

COMMENT ON FUNCTION ydsz_set_updated_at() IS
    '通用 updated_at 维护:BEFORE UPDATE 时将 NEW.updated_at 置为 CURRENT_TIMESTAMP;'
    '仅当 NEW 与 OLD 实际不同时触发(避免 no-op UPDATE 引起的批量时间漂移)';

COMMENT ON FUNCTION ydsz_attach_updated_at_trigger(TEXT) IS
    '通用挂载函数: 为指定 public 表添加 tg_<table>_updated_at BEFORE UPDATE 触发器;'
    '已挂载 / 表不存在 / 缺 updated_at 列时静默跳过';

-- 用户账号
SELECT ydsz_attach_updated_at_trigger('ydsz_employee');

-- 员工
SELECT ydsz_attach_updated_at_trigger('ydsz_department');

-- 部门
SELECT ydsz_attach_updated_at_trigger('ydsz_position');

-- 岗位
SELECT ydsz_attach_updated_at_trigger('ydsz_role');

-- 角色
SELECT ydsz_attach_updated_at_trigger('ydsz_config');

-- 系统配置
SELECT ydsz_attach_updated_at_trigger('ydsz_dict_item');

-- 字典项
SELECT ydsz_attach_updated_at_trigger('ydsz_dict_version');

-- 字典版本
SELECT ydsz_attach_updated_at_trigger('ydsz_project_initiation');

-- 立项
SELECT ydsz_attach_updated_at_trigger('ydsz_project_change');

-- 变更
SELECT ydsz_attach_updated_at_trigger('ydsz_finance_contract');

-- 合同
SELECT ydsz_attach_updated_at_trigger('ydsz_project_invoice');

-- 发票
SELECT ydsz_attach_updated_at_trigger('ydsz_project_payment');

-- 回款
SELECT ydsz_attach_updated_at_trigger('ydsz_flow_instance');

-- 流程实例
SELECT ydsz_attach_updated_at_trigger('ydsz_flow_definition');

-- 流程定义

-- ----------------------------------------------------------------------------
-- 3.1) 批量挂载剩余所有含 updated_at 列的 ydsz_ 表
--      上方 15 张核心表已显式挂载; 此处用 DO 块动态扫描 information_schema,
--      为所有尚未挂载触发器且含 updated_at 列的 ydsz_ 表自动挂载。
--      ydsz_attach_updated_at_trigger() 自身幂等: 已挂载 / 表不存在 / 缺
--      updated_at 列时均静默跳过, 故可安全覆盖全部表。
--      覆盖: 规则/成本/利润/EVM/费率/资源/考勤/运维/工单/满意度/对账/
--      利用率/工作流子表/报表/导出/2FA/会话等(约 80+ 张表)。
--      日志表(ydsz_operation_log / ydsz_flow_audit_log 等)无 updated_at 列,
--      会被辅助函数自动跳过。
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    t_name TEXT;
BEGIN
    FOR t_name IN
        SELECT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema
         AND t.table_name = c.table_name
        WHERE c.table_schema = 'public'
          AND c.column_name = 'updated_at'
          AND t.table_type = 'BASE TABLE'
          AND c.table_name LIKE 'ydsz\_%' ESCAPE '\'
          -- 排除分区子表(由父表继承,无需单独挂载)
          AND c.table_name NOT LIKE '%_default'
    LOOP
        PERFORM ydsz_attach_updated_at_trigger(t_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;

COMMENT ON INDEX idx_ydsz_project_change_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_execution_delivery_standard_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_execution_delivery_item_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_execution_closure_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_agent_prediction_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_finance_invoice_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_finance_payment_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_finance_customer_credit_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_evm_measure_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rate_card_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rate_internal_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_profit_simulation_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_resource_pool_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_employee_tag_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_resource_assignment_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_bench_record_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_warranty_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_ops_ticket_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_satisfaction_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_alert_dispatch_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_reconcile_daily_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_attendance_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_overtime_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_leave_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_definition_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_node_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_skip_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_instance_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_run_task_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_his_task_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_his_instance_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_user_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_cc_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_cc_rule_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_timer_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_delegate_auth_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_delegate_log_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_report_subscription_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_def_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_version_history_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_template_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_test_case_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_execution_trace_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_decision_table_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_event_subscription_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_canary_bucket_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_scorecard_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_decision_tree_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_script_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_variable_def_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_chain_graph_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_dependency_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_ab_policy_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_ab_rollback_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_pack_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_rule_pack_install_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_third_party_account_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_third_party_log_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_template_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_ydsz_flow_auto_trigger_trace IS 'P1-7: provider_trace_id 反查';


COMMENT ON INDEX idx_ydsz_flow_task_comment_trace IS 'P1-7: provider_trace_id 反查';

-- 1) 初始化一条元数据行
INSERT INTO ydsz_meta_schema_version
    (version, pg_version, files_merged, generated_at, applied_at, pending_tables, notes)
VALUES
    ('V1.0.0', '18', 58,
     '2026-07-06 21:00:00+08',
     CURRENT_TIMESTAMP,
     NULL,
     'P1-7: 75/75 provider_trace_id 索引已全量覆盖;'
     'P2-8: 112/112 COMMENT ON TABLE 覆盖率 100%;'
     'P2-9: 引入 ydsz_meta_schema_version 元数据表;'
     '历史前向引用表已全部落地(表名重命名后已存在),无 pending 表')
ON CONFLICT (version, deleted) DO NOTHING;

-- 2) 创建通用查询视图(供应用启动时探测当前 schema 版本)
CREATE OR REPLACE VIEW ydsz_view_current_schema_version
    WITH (security_invoker = true) AS
SELECT
    version,
    pg_version,
    files_merged,
    generated_at,
    applied_at,
    pending_tables,
    notes
FROM ydsz_meta_schema_version
WHERE deleted = 0
ORDER BY applied_at DESC
LIMIT 1;

COMMENT ON VIEW ydsz_view_current_schema_version IS
    'P2-9: 当前生效的 schema 版本快照(取 applied_at 最近一条)';

-- 2) 把 P3 任务说明写进 V1.0.0 这次初始化
UPDATE ydsz_meta_schema_version
   SET plan_notes = COALESCE(plan_notes, '') ||
        E'\nP3-13 [PERF] 冷热数据分层:' ||
        E'\n  - 目标: ydsz_operation_log / ydsz_flow_audit_log 月份超过 12 个月的冷分区' ||
        E'\n          ATTACH 到独立 cold tablespace + OSS 归档' ||
        E'\n  - 实施: 引入 pg_partman 扩展(parent table + retention 配置)' ||
        E'\n  - 影响: 表/索引结构不变,仅物理文件搬迁;Java 实体无需调整' ||
        E'\n' ||
        E'\nP3-14 [SEC] 敏感字段加密:' ||
        E'\n  - 目标: ydsz_employee.id_card / phone / bank_card 等 7 类敏感字段' ||
        E'\n          落盘前用 SM4 加密(列: <col>_cipher VARCHAR(512))' ||
        E'\n          同步增加 <col>_hash VARCHAR(64) 唯一索引列(支持等值查询)' ||
        E'\n  - 实施: 引入 pgcrypto + 自研 KMS 密钥版本号' ||
        E'\n  - 影响: 字段数翻倍,Java 实体需配套 @SensitiveField 注解 + 加密拦截器' ||
        E'\n' ||
        E'\nP3-15 [AUDIT] OPLOG 字段:' ||
        E'\n  - 目标: ydsz_data_export_audit 增 op_log_id (BIGINT) + op_log_type (VARCHAR)' ||
        E'\n          关联到 ydsz_operation_log.id,支持"导出行为 → 原始操作"的反查' ||
        E'\n  - 实施: ALTER TABLE ADD COLUMN,新增索引 idx_ydsz_data_export_audit_oplog' ||
        E'\n  - 影响: 导出服务实现需在写导出审计时填这两个字段'
   WHERE version = 'V1.0.0' AND deleted = 0;



-- ============================================================
-- 10. 应用注册（sdt-app.AppInfo 迁移，OAuth2 client_id 校验）
-- ============================================================

-- 应用注册表
CREATE TABLE IF NOT EXISTS ydsz_app_info(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    app_code        VARCHAR(64)    NOT NULL,
    app_name        VARCHAR(128)   NOT NULL,
    app_key         VARCHAR(128)   NOT NULL,
    app_secret      VARCHAR(256)   NOT NULL,
    redirect_url    VARCHAR(512),
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_app_code UNIQUE (app_code, deleted),
    CONSTRAINT uk_ydsz_app_key UNIQUE (app_key, deleted),
    CONSTRAINT ck_app_status_enum CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_app_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_app_info IS '应用注册表: OAuth2 client_id/client_secret 校验数据源(来自 sdt-app.AppInfo)';
COMMENT ON COLUMN ydsz_app_info.id IS '主键 ID';
COMMENT ON COLUMN ydsz_app_info.app_code IS '应用编码(全局唯一)';
COMMENT ON COLUMN ydsz_app_info.app_name IS '应用名称';
COMMENT ON COLUMN ydsz_app_info.app_key IS '应用 Key(client_id)';
COMMENT ON COLUMN ydsz_app_info.app_secret IS '应用密钥(client_secret,BCrypt 加密存储)';
COMMENT ON COLUMN ydsz_app_info.redirect_url IS '授权回调地址';
COMMENT ON COLUMN ydsz_app_info.description IS '应用描述';
COMMENT ON COLUMN ydsz_app_info.status IS '启用状态: ENABLED / DISABLED';
COMMENT ON COLUMN ydsz_app_info.deleted IS '逻辑删除';
COMMENT ON COLUMN ydsz_app_info.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_ydsz_app_status ON ydsz_app_info(status) WHERE deleted = 0;

-- ============================================================
-- 11. 系统变量（sdt-ids.Variable 迁移，业务级变量管理）
-- ============================================================

-- 系统变量表
CREATE TABLE IF NOT EXISTS ydsz_variable(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    variable_key    VARCHAR(128)   NOT NULL,
    variable_value  TEXT,
    value_type      VARCHAR(16)    NOT NULL DEFAULT 'STRING',
    description     TEXT,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_ydsz_var_key UNIQUE (variable_key, deleted),
    CONSTRAINT ck_var_value_type CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON')),
    CONSTRAINT ck_var_status_enum CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_var_deleted_enum CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_variable IS '系统变量表: 业务级变量管理(来自 sdt-ids.Variable,可对接 common-config 热加载)';
COMMENT ON COLUMN ydsz_variable.id IS '主键 ID';
COMMENT ON COLUMN ydsz_variable.variable_key IS '变量键(全局唯一)';
COMMENT ON COLUMN ydsz_variable.variable_value IS '变量值';
COMMENT ON COLUMN ydsz_variable.value_type IS '值类型: STRING/NUMBER/BOOLEAN/JSON';
COMMENT ON COLUMN ydsz_variable.description IS '变量说明';
COMMENT ON COLUMN ydsz_variable.status IS '启用状态';
COMMENT ON COLUMN ydsz_variable.deleted IS '逻辑删除';
COMMENT ON COLUMN ydsz_variable.tenant_id IS '租户 ID';

CREATE INDEX IF NOT EXISTS idx_ydsz_var_status ON ydsz_variable(status) WHERE deleted = 0;
