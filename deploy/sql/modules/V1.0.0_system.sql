-- ============================================================
-- PMIS system module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================
-- 本脚本 DDL 对应后端 system 服务 (ydsz-pmis-system) 的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign + NameAssembler(在 CommonAutoConfiguration 注册)。
-- 字典版本表
CREATE TABLE IF NOT EXISTS pmis_dict_version(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    type_code       VARCHAR(64)    NOT NULL,
    version         VARCHAR(32)    NOT NULL,
    change_log      TEXT,
    effective_date  TIMESTAMP      NOT NULL,
    created_by      VARCHAR(20)         NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_dict_version IS '字典版本表: 字典变更历史快照,支持回滚与变更审计';

COMMENT ON COLUMN pmis_dict_version.id IS '主键 ID';

COMMENT ON COLUMN pmis_dict_version.type_code IS '字典类型编码';

COMMENT ON COLUMN pmis_dict_version.version IS '版本号(语义化版本,如 1.0.0)';

COMMENT ON COLUMN pmis_dict_version.change_log IS '变更说明';

COMMENT ON COLUMN pmis_dict_version.effective_date IS '生效时间';

COMMENT ON COLUMN pmis_dict_version.created_by IS '发布人 ID';

COMMENT ON COLUMN pmis_dict_version.created_at IS '发布时间';

COMMENT ON COLUMN pmis_dict_version.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- ====================================================================
-- 6. 系统配置
-- ====================================================================

CREATE TABLE IF NOT EXISTS pmis_config(
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

-- --------------------------------------------------------------------

-- ============================ [004] init pmis workflow schema ============================

-- =====================================================
-- PMIS 工作流基础模块清理 DDL（Flowable 表已下线）
-- 版本: V1.0.0_004
-- 描述: 完全移除 Flowable 引擎相关的业务关联表 / 表单定义表 / 节点配置表
--       业务流程关联信息已统一收敛到自研 pmis_flow_instance / pmis_flow_run_task
--       流程表单/节点配置已收敛到自研 pmis_flow_definition / pmis_flow_node / pmis_flow_skip
-- 历史: V1.0.0_004 旧版本曾创建 pmis_workflow_business / pmis_workflow_form / pmis_workflow_node_config
--       现已废弃，本次迁移仅 DROP（不重建），以保证幂等
-- =====================================================

-- 清理：业务流程实例关联表（功能已被 pmis_flow_instance 替代）
-- P1-6: DROP 改 CREATE IF NOT EXISTS 即可,无需 DROP(已废弃表)

-- 清理：流程表单定义表（功能已通过 pmis_flow_definition.form_path 替代）
-- P1-6: 已废弃,无需 DROP

-- 清理：流程节点配置表（功能已通过 pmis_flow_node.permission_flag / ext 替代）
-- P1-6: 已废弃,无需 DROP

-- --------------------------------------------------------------------

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

-- ============================ [006e] P7-2 租户级配额 ============================

-- [P7-2] 租户级配额表：控制单个租户可创建任务数、并发执行数、日执行总量
-- 未配置记录的租户视为 unlimited（由应用层 CronjobProperties.Quota.defaultMax* 兜底）
CREATE TABLE IF NOT EXISTS pmis_tenant_quota(
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
    created_by            VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT       NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_ptq_max_jobs_pos        CHECK (max_jobs IS NULL OR max_jobs > 0),
    CONSTRAINT ck_ptq_max_concurrent_pos CHECK (max_concurrent IS NULL OR max_concurrent > 0),
    CONSTRAINT ck_ptq_max_daily_pos      CHECK (max_daily_executions IS NULL OR max_daily_executions > 0),
    CONSTRAINT ck_ptq_enabled_enum       CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_ptq_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_tenant_quota IS '租户级配额表（P7-2）：控制单个租户的任务数/并发数/日执行量上限';

COMMENT ON COLUMN pmis_tenant_quota.id IS '主键 ID';

COMMENT ON COLUMN pmis_tenant_quota.tenant_id IS '租户 ID（唯一，一个租户一条配额记录）';

COMMENT ON COLUMN pmis_tenant_quota.max_jobs IS '任务数上限（NULL=unlimited）';

COMMENT ON COLUMN pmis_tenant_quota.max_concurrent IS '并发执行上限（NULL=unlimited，P7-3 实现）';

COMMENT ON COLUMN pmis_tenant_quota.max_daily_executions IS '日执行量上限（NULL=unlimited，P7-3 实现）';

COMMENT ON COLUMN pmis_tenant_quota.enabled IS '是否启用配额检查: 0 禁用 / 1 启用';

COMMENT ON COLUMN pmis_tenant_quota.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_tenant_quota.created_at IS '创建时间';

COMMENT ON COLUMN pmis_tenant_quota.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_tenant_quota.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_tenant_quota.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- 默认租户（tenant_id='1'）的初始配额记录（unlimited，便于单租户部署直接使用）
INSERT INTO pmis_tenant_quota (id, tenant_id, max_jobs, max_concurrent, max_daily_executions, enabled)
VALUES ('1', '1', NULL, NULL, NULL, 1)
ON CONFLICT (tenant_id) DO NOTHING;

-- --------------------------------------------------------------------

-- ============================ [016] init pmis security ============================

-- ============================================================
-- V1.0.0_016  权限安全体系  脚本
-- ============================================================
-- 说明：批次 13 权限安全体系
-- 1) 数据权限：pmis_user_account 增加 data_scope / custom_dept_ids 字段
-- 2) 登录审计：pmis_login_audit
-- 3) 双因素认证：pmis_user_2fa
-- 4) 数据导出审计：pmis_data_export_audit
-- 5) 敏感操作复核：pmis_sensitive_operation
-- 6) 会话管理：pmis_user_session
-- ============================================================

-- ----------------------------
-- 1) 增强用户账号表 (已优化内联至 V1.0.0_001 pmis_user_account 定义)
-- ----------------------------
-- data_scope / custom_dept_ids / mfa_enabled / mfa_type / last_pwd_change_at / pwd_change_count
-- 已在 V1.0.0_001 中以最终结构内联(含 CHECK 约束),此处不再重复 ADD COLUMN

-- ----------------------------
-- 2) 登录审计
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_login_audit (
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

COMMENT ON TABLE  pmis_login_audit IS '登录审计日志表: 等保 2.0 要求,登录成功/失败全留存,支持溯源审计';

COMMENT ON COLUMN pmis_login_audit.username IS '登录用户名: 失败时也可记录,便于排查撞库';

COMMENT ON COLUMN pmis_login_audit.user_id IS '登录用户 ID: 成功时记录,失败可为 NULL';

COMMENT ON COLUMN pmis_login_audit.login_at IS '登录时间';

COMMENT ON COLUMN pmis_login_audit.login_ip IS '登录 IP: 用于异常登录检测';

COMMENT ON COLUMN pmis_login_audit.user_agent IS '浏览器 UA: 用于设备指纹';

COMMENT ON COLUMN pmis_login_audit.status IS '状态: SUCCESS 成功 / FAIL 失败 / LOCKED 锁定 / MFA_REQUIRED 待 MFA';

COMMENT ON COLUMN pmis_login_audit.fail_reason IS '失败原因: 密码错误/账号锁定/MFA 失败等';

COMMENT ON COLUMN pmis_login_audit.mfa_used IS '是否使用 MFA: true=已启用并使用';

COMMENT ON COLUMN pmis_login_audit.mfa_success IS 'MFA 是否通过: NULL=未使用,true=通过,false=失败';

COMMENT ON COLUMN pmis_login_audit.trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_login_audit.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_login_audit.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_login_audit_*)
CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_user_at
    ON pmis_login_audit(tenant_id, username, login_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_ip_at
    ON pmis_login_audit(tenant_id, login_ip, login_at DESC)
    WHERE deleted = 0 AND login_ip IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_status_at
    ON pmis_login_audit(tenant_id, status, login_at DESC)
    WHERE deleted = 0;

-- ----------------------------
-- 4) 数据导出审计
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_data_export_audit (
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

COMMENT ON TABLE  pmis_data_export_audit IS '数据导出审计表: 合同/财务/薪酬等敏感数据导出全留存,@DataExportAudit 自动捕获';

COMMENT ON COLUMN pmis_data_export_audit.user_id IS '导出用户 ID';

COMMENT ON COLUMN pmis_data_export_audit.username IS '导出用户姓名（冗余）';

COMMENT ON COLUMN pmis_data_export_audit.export_module IS '导出模块: PROJECT/EXECUTION/FINANCE 等';

COMMENT ON COLUMN pmis_data_export_audit.export_action IS '导出动作: EXPORT 导出 / PRINT 打印 / DOWNLOAD 下载';

COMMENT ON COLUMN pmis_data_export_audit.biz_type IS '业务类型';

COMMENT ON COLUMN pmis_data_export_audit.row_count IS '导出行数: 自动检测 Collection/Number,作为审计基数';

COMMENT ON COLUMN pmis_data_export_audit.file_name IS '导出文件名';

COMMENT ON COLUMN pmis_data_export_audit.file_size IS '文件大小(字节)';

COMMENT ON COLUMN pmis_data_export_audit.export_format IS '导出格式: XLSX/CSV/PDF';

COMMENT ON COLUMN pmis_data_export_audit.query_summary IS '查询条件摘要: 用于审计导出范围';

COMMENT ON COLUMN pmis_data_export_audit.trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_data_export_audit.client_ip IS '客户端 IP';

COMMENT ON COLUMN pmis_data_export_audit.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_data_export_audit.exported_at IS '导出时间';

COMMENT ON COLUMN pmis_data_export_audit.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_export_audit_*)
CREATE INDEX IF NOT EXISTS idx_dea_tenant_user_at
    ON pmis_data_export_audit(tenant_id, user_id, exported_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_dea_tenant_module_at
    ON pmis_data_export_audit(tenant_id, export_module, exported_at DESC)
    WHERE deleted = 0;

-- ----------------------------
-- 5) 敏感操作二次确认
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_sensitive_operation (
    id              VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    user_id         VARCHAR(20)        NOT NULL,
    username        VARCHAR(64)   NOT NULL,
    operation_code  VARCHAR(64)   NOT NULL,
    operation_name  VARCHAR(128)  NOT NULL,
    biz_type        VARCHAR(32),
    biz_id          VARCHAR(20),
    re_auth_method  VARCHAR(16)   NOT NULL,
    re_auth_token   VARCHAR(256),
    verified_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at       TIMESTAMPTZ   NOT NULL,
    client_ip       VARCHAR(64),
    trace_id        VARCHAR(20),
    tenant_id       VARCHAR(20)        NOT NULL DEFAULT '1',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_sensitive_op_method    CHECK (re_auth_method IN ('PASSWORD','MFA','SMS')),
    CONSTRAINT ck_sensitive_op_expire    CHECK (expire_at >= verified_at),
    CONSTRAINT ck_sensitive_op_deleted   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_sensitive_operation IS '敏感操作二次确认记录表: @RequireReAuth 注解触发的二次认证,token 一次性消费,防重放';

COMMENT ON COLUMN pmis_sensitive_operation.user_id IS '操作用户 ID';

COMMENT ON COLUMN pmis_sensitive_operation.username IS '操作用户姓名（冗余）';

COMMENT ON COLUMN pmis_sensitive_operation.operation_code IS '操作编码: 例如 USER_DELETE / CONTRACT_REVERSE';

COMMENT ON COLUMN pmis_sensitive_operation.operation_name IS '操作名称';

COMMENT ON COLUMN pmis_sensitive_operation.biz_type IS '业务类型';

COMMENT ON COLUMN pmis_sensitive_operation.biz_id IS '业务对象 ID';

COMMENT ON COLUMN pmis_sensitive_operation.re_auth_method IS '二次认证方式: PASSWORD 密码 / MFA / SMS';

COMMENT ON COLUMN pmis_sensitive_operation.re_auth_token IS '二次认证 Token: Redis Key 一次性消费';

COMMENT ON COLUMN pmis_sensitive_operation.verified_at IS '验证时间';

COMMENT ON COLUMN pmis_sensitive_operation.expire_at IS 'Token 过期时间';

COMMENT ON COLUMN pmis_sensitive_operation.client_ip IS '客户端 IP';

COMMENT ON COLUMN pmis_sensitive_operation.trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_sensitive_operation.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_sensitive_operation.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_sensitive_op_*)
CREATE INDEX IF NOT EXISTS idx_sensitive_op_tenant_user_at
    ON pmis_sensitive_operation(tenant_id, user_id, verified_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_sensitive_op_tenant_code_at
    ON pmis_sensitive_operation(tenant_id, operation_code, verified_at DESC)
    WHERE deleted = 0;

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

-- --------------------------------------------------------------------

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

ANALYZE pmis_operation_log;

-- 3. 字典版本
ALTER TABLE pmis_dict_version ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_dict_version_tenant ON pmis_dict_version(tenant_id);

-- 16. 配置
ALTER TABLE pmis_config ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_config_tenant ON pmis_config(tenant_id);

-- 17. 操作日志（V1.0.0_008 已含 tenant_id，跳过 ADD COLUMN，仅补索引）
CREATE INDEX IF NOT EXISTS idx_pol_tenant ON pmis_operation_log(tenant_id);

CREATE INDEX IF NOT EXISTS idx_config_tenant_created
    ON pmis_config(tenant_id, created_at DESC) WHERE deleted = 0;

-- ============================================================
-- 四、逻辑删除字段索引覆盖（H3.2）
--   对 V1.0.0_001 中未建 deleted 索引的表补建
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_pmis_dict_version_deleted ON pmis_dict_version(deleted);

-- 报表订阅
ALTER TABLE pmis_report_subscription ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_report_sub_tenant ON pmis_report_subscription(tenant_id);

-- 异步导出记录（P0-3 合并：原报表导出记录已并入此表）
ALTER TABLE pmis_export_record ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_export_rec_tenant ON pmis_export_record(tenant_id);

ANALYZE pmis_dict_version;

ANALYZE pmis_config;

ANALYZE pmis_operation_log;

ANALYZE pmis_report_subscription;

ANALYZE pmis_export_record;

-- ----------------------------------------------------------------------------
-- 3) pmis_dict_version 字段补齐
--    - 新增 updated_at / updated_by / tenant_id(对齐 BaseDO 5 字段基线)
--    - created_at / effective_date 统一为 TIMESTAMPTZ(全工程时间字段统一约定)
-- ----------------------------------------------------------------------------
ALTER TABLE pmis_dict_version ADD COLUMN IF NOT EXISTS updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE pmis_dict_version ADD COLUMN IF NOT EXISTS updated_by    VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';

ALTER TABLE pmis_dict_version ADD COLUMN IF NOT EXISTS tenant_id     VARCHAR(20) NOT NULL DEFAULT '1';

ALTER TABLE pmis_dict_version ALTER COLUMN created_at     TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE pmis_dict_version ALTER COLUMN effective_date TYPE TIMESTAMPTZ USING effective_date AT TIME ZONE 'UTC';

COMMENT ON COLUMN pmis_dict_version.updated_by    IS '最后修改人 ID';

COMMENT ON COLUMN pmis_dict_version.updated_at    IS '最后修改时间';

COMMENT ON COLUMN pmis_dict_version.tenant_id     IS '租户 ID(单租户部署默认 1)';

-- 复合索引(与全工程惯例一致)
CREATE INDEX IF NOT EXISTS idx_pdv_tenant_type
    ON pmis_dict_version (tenant_id, type_code)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pdv_tenant_type_created
    ON pmis_dict_version (tenant_id, type_code, created_at DESC)
    WHERE deleted = 0;

ANALYZE pmis_dict_version;

-- ====================================================================
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

ANALYZE pmis_operation_log;

-- 6) 报表订阅(1 张)
CREATE INDEX IF NOT EXISTS idx_pmis_report_subscription_trace
    ON pmis_report_subscription (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

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
    id                  VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
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

