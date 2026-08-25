-- ----------------------------------------------------------------------------
-- 模块名   : ydsz-literule（规则引擎模块）
-- 说明     : 基于 ydsz-literule-infra 实体类整理的完整建表脚本
--            （MyBatis-Plus 实体 -> MySQL DDL，含公共审计/租户/乐观锁列）
-- 日期     : 2026-08-25
-- @author  : ydsz-team
-- ----------------------------------------------------------------------------

-- ============================================================================
-- 1. 规则定义主表
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_rule_def (
    id                          VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id                   VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    rule_code                   VARCHAR(64)     NOT NULL COMMENT '规则编码，业务唯一',
    rule_name                   VARCHAR(128)    NOT NULL COMMENT '规则名称',
    category                    VARCHAR(64)     DEFAULT NULL COMMENT '规则分类编码（一级分类标识）',
    category_path               VARCHAR(255)    DEFAULT NULL COMMENT '分类路径（/ 分隔的多级分类，如 finance/credit/loan）',
    owner                       VARCHAR(64)     DEFAULT NULL COMMENT '责任人（规则负责人工号/用户名）',
    description                 VARCHAR(512)    DEFAULT NULL COMMENT '规则描述',
    condition_expression        TEXT            COMMENT '条件表达式（LiteExpr 语法）',
    severity_expression         TEXT            COMMENT '严重度表达式，可选',
    default_severity            VARCHAR(32)     DEFAULT NULL COMMENT '默认严重级别',
    title_template              VARCHAR(512)    DEFAULT NULL COMMENT '告警标题模板',
    description_template        VARCHAR(512)    DEFAULT NULL COMMENT '告警描述模板',
    priority                    INT             NOT NULL DEFAULT 100 COMMENT '优先级，数值越小优先级越高',
    enabled                     TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用（1=启用，0=停用）',
    scope                       VARCHAR(128)    DEFAULT NULL COMMENT '适用范围',
    mutex_group                 VARCHAR(128)    DEFAULT NULL COMMENT '互斥组名称（同组内首个命中后跳过其余规则；NULL 表示无互斥组）',
    drilldown_available         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否支持下钻查看详情（1=支持，0=不支持）',
    version                     INT             NOT NULL DEFAULT 1 COMMENT '乐观锁版本号（并发更新规则时防止覆盖）',
    status                      VARCHAR(32)     DEFAULT NULL COMMENT '生命周期状态（DRAFT/PUBLISHED/DISABLED）',
    effective_from              DATETIME        DEFAULT NULL COMMENT '生效时间（NULL 表示立即生效）',
    effective_to                DATETIME        DEFAULT NULL COMMENT '失效时间（NULL 表示永不过期）',
    reviewed_by                 VARCHAR(64)     DEFAULT NULL COMMENT '审核人',
    reviewed_at                 DATETIME        DEFAULT NULL COMMENT '审核时间',
    review_comment              VARCHAR(512)    DEFAULT NULL COMMENT '审核意见',
    canary_ratio                DECIMAL(5,4)    DEFAULT NULL COMMENT '灰度比例（0.0~1.0，0 表示不启用灰度）',
    canary_conditions           JSON            DEFAULT NULL COMMENT '灰度条件表达式列表（JSON 数组）',
    canary_condition_expression TEXT            COMMENT '灰度候选版本条件表达式',
    canary_severity_expression  TEXT            COMMENT '灰度候选版本严重度表达式',
    deleted                     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision                    INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by                  VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by                  VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_rule_code UNIQUE (rule_code, tenant_id),
    INDEX idx_category (category),
    INDEX idx_status (status),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LiteRule 规则定义主表';

-- ============================================================================
-- 2. 规则变量 / 模板
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_rule_variable_def (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    var_name        VARCHAR(128)    NOT NULL COMMENT '变量名（如 cpi / budgetAmount / evmRedCount）',
    var_type        VARCHAR(32)     DEFAULT NULL COMMENT '变量类型（Number / String 等）',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '变量描述（中文，供前端编辑器提示）',
    sample_value    TEXT            COMMENT '示例值（存储为字符串，用于前端编辑器预览和 dryRun 默认 facts）',
    category        VARCHAR(64)     DEFAULT NULL COMMENT '变量来源类别（EVM / PROJECT / FINANCE / BENCH 等）',
    required        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否必填（1=必填，0=可选）',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用（1=启用，0=停用）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_var_name UNIQUE (var_name, tenant_id),
    INDEX idx_category (category),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则变量定义表';

CREATE TABLE IF NOT EXISTS ydsz_rule_template (
    id                    VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id             VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    template_code         VARCHAR(64)     NOT NULL COMMENT '模板编码，业务唯一',
    template_name         VARCHAR(128)    NOT NULL COMMENT '模板名称',
    category              VARCHAR(64)     DEFAULT NULL COMMENT '分类编码',
    description           VARCHAR(512)    DEFAULT NULL COMMENT '模板描述',
    condition_expression  TEXT            COMMENT '预置条件表达式',
    severity_expression   TEXT            COMMENT '预置严重度表达式',
    default_severity      VARCHAR(32)     DEFAULT NULL COMMENT '默认严重级别',
    title_template        VARCHAR(512)    DEFAULT NULL COMMENT '告警标题模板',
    description_template  VARCHAR(512)    DEFAULT NULL COMMENT '告警描述模板',
    priority              INT             DEFAULT NULL COMMENT '优先级，数值越小优先级越高',
    scope                 VARCHAR(128)    DEFAULT NULL COMMENT '适用范围',
    industry              VARCHAR(64)     DEFAULT NULL COMMENT '所属行业',
    tags                  VARCHAR(512)    DEFAULT NULL COMMENT '标签，逗号分隔',
    status                VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by            VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by            VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_template_code UNIQUE (template_code, tenant_id),
    INDEX idx_category (category),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LiteRule 规则模板表';

-- ============================================================================
-- 3. 规则形态：脚本 / 决策表 / 决策树 / 评分卡
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_rule_script (
    id               VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id        VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    rule_code        VARCHAR(64)     NOT NULL COMMENT '规则编码',
    rule_name        VARCHAR(128)    NOT NULL COMMENT '规则名称',
    category         VARCHAR(64)     DEFAULT NULL COMMENT '规则分类',
    description      VARCHAR(512)    DEFAULT NULL COMMENT '规则描述',
    script           LONGTEXT        COMMENT 'Groovy 脚本源码（运行在沙箱中）',
    default_severity VARCHAR(32)     DEFAULT NULL COMMENT '默认严重级别（INFO/WARN/ERROR/CRITICAL）',
    sandbox_enabled  TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用沙箱（1=启用安全限制，0=关闭）',
    priority         INT             DEFAULT NULL COMMENT '优先级',
    enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用（1=启用，0=停用）',
    scope            VARCHAR(128)    DEFAULT NULL COMMENT '适用范围',
    version          INT             NOT NULL DEFAULT 1 COMMENT '版本号',
    provider_trace_id VARCHAR(64)    DEFAULT NULL COMMENT '供应商侧追踪 ID',
    status           VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by       VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by       VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_rule_code (rule_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则脚本表';

CREATE TABLE IF NOT EXISTS ydsz_rule_decision_table (
    id                VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    table_code        VARCHAR(64)     NOT NULL COMMENT '决策表编码',
    table_name        VARCHAR(128)    NOT NULL COMMENT '决策表名称',
    description       VARCHAR(512)    DEFAULT NULL COMMENT '描述',
    category          VARCHAR(64)     DEFAULT NULL COMMENT '类别',
    condition_columns JSON            DEFAULT NULL COMMENT '条件列定义（JSON 数组）',
    action_columns    JSON            DEFAULT NULL COMMENT '动作列定义（JSON 数组）',
    `rows`            JSON            DEFAULT NULL COMMENT '决策行（JSON 数组）',
    default_actions   JSON            DEFAULT NULL COMMENT '默认动作（JSON 对象）',
    hit_policy        VARCHAR(32)     NOT NULL DEFAULT 'FIRST' COMMENT '命中策略（UNIQUE/FIRST/PRIORITY/COLLECT/ANY，默认 FIRST）',
    enabled           TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用（1=启用，0=停用）',
    priority          INT             DEFAULT NULL COMMENT '优先级',
    version           INT             NOT NULL DEFAULT 1 COMMENT '版本',
    status            VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_table_code UNIQUE (table_code, tenant_id),
    INDEX idx_category (category),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策表实体表';

CREATE TABLE IF NOT EXISTS ydsz_rule_decision_tree (
    id                VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    rule_code         VARCHAR(64)     NOT NULL COMMENT '规则编码',
    rule_name         VARCHAR(128)    NOT NULL COMMENT '规则名称',
    category          VARCHAR(64)     DEFAULT NULL COMMENT '规则分类',
    description       VARCHAR(512)    DEFAULT NULL COMMENT '规则描述',
    root_node         JSON            DEFAULT NULL COMMENT '根节点 JSON（嵌套结构，节点类型 CONDITION/ACTION/DEFAULT）',
    priority          INT             DEFAULT NULL COMMENT '优先级（数字越小越优先）',
    enabled           TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用（1=启用，0=停用）',
    scope             VARCHAR(128)    DEFAULT NULL COMMENT '适用范围',
    version           INT             NOT NULL DEFAULT 1 COMMENT '版本号',
    provider_trace_id VARCHAR(64)     DEFAULT NULL COMMENT '供应商侧追踪 ID',
    status            VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_rule_code (rule_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则决策树表';

CREATE TABLE IF NOT EXISTS ydsz_rule_scorecard (
    id                VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id         VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    rule_code         VARCHAR(64)     NOT NULL COMMENT '规则编码',
    rule_name         VARCHAR(128)    NOT NULL COMMENT '规则名称',
    category          VARCHAR(64)     DEFAULT NULL COMMENT '规则分类（RISK / QUALITY / PROFIT 等）',
    description       VARCHAR(512)    DEFAULT NULL COMMENT '规则描述',
    base_score        DECIMAL(20,6)   NOT NULL DEFAULT 100 COMMENT '基础分（满分，默认 100）',
    red_threshold     DECIMAL(20,6)   DEFAULT NULL COMMENT '红灯阈值（≤ 触发红灯）',
    yellow_threshold  DECIMAL(20,6)   DEFAULT NULL COMMENT '黄灯阈值（≤ 触发黄灯）',
    factors           JSON            DEFAULT NULL COMMENT '评分因子 JSON：[{conditionExpression, score, description}]',
    priority          INT             DEFAULT NULL COMMENT '优先级（数字越小越优先）',
    enabled           TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用（1=启用，0=停用）',
    scope             VARCHAR(128)    DEFAULT NULL COMMENT '适用范围（如 ALL / PROJECT_TYPE:CONSTRUCTION）',
    version           INT             NOT NULL DEFAULT 1 COMMENT '版本号',
    provider_trace_id VARCHAR(64)     DEFAULT NULL COMMENT '供应商侧追踪 ID',
    status            VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by        VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by        VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_rule_code (rule_code),
    INDEX idx_category (category),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则评分卡表';

-- ============================================================================
-- 4. 规则链画布 / 规则依赖
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_rule_chain_graph (
    id            VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id     VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    rule_code     VARCHAR(64)     NOT NULL COMMENT '关联规则编码（一对一）',
    name          VARCHAR(128)    NOT NULL COMMENT '画布名称',
    description   VARCHAR(512)    DEFAULT NULL COMMENT '画布描述',
    scenario      VARCHAR(64)     DEFAULT NULL COMMENT '适用场景（与 RuleContext.scenario 对应）',
    graph_version INT             NOT NULL DEFAULT 1 COMMENT '画布版本号（独立递增）',
    status        VARCHAR(32)     DEFAULT NULL COMMENT '画布状态（DRAFT/PUBLISHED/ARCHIVED）',
    content_json  JSON            DEFAULT NULL COMMENT '画布内容 JSON（包含 nodes/edges/viewport/metadata）',
    deleted       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision      INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by    VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by    VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_rule_code UNIQUE (rule_code, tenant_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则链画布表';

CREATE TABLE IF NOT EXISTS ydsz_rule_dependency (
    id                   VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id            VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    rule_code            VARCHAR(64)     NOT NULL COMMENT '主规则编码（依赖方）',
    depends_on_rule_code VARCHAR(64)     NOT NULL COMMENT '被依赖的规则编码',
    dependency_type      VARCHAR(32)     NOT NULL COMMENT '依赖类型（EXECUTE/READ_RESULT/SOFT）',
    cascade_on_disable   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '被依赖规则被禁用时是否级联禁用本规则（1=级联，0=不级联）',
    description          VARCHAR(512)    DEFAULT NULL COMMENT '依赖说明',
    status               VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by           VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by           VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_rule_dep UNIQUE (rule_code, depends_on_rule_code),
    INDEX idx_depends_on_rule_code (depends_on_rule_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则依赖关系表';

-- ============================================================================
-- 5. 规则集（知识包）与安装记录
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_rule_pack (
    id               VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id        VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    pack_code        VARCHAR(64)     NOT NULL COMMENT '规则集编码（全局唯一，用于版本间关联）',
    pack_version     VARCHAR(32)     NOT NULL COMMENT '规则集版本号（如 1.0.0）',
    pack_name        VARCHAR(128)    NOT NULL COMMENT '规则集名称',
    industry         VARCHAR(64)     DEFAULT NULL COMMENT '所属行业（FINANCE / MANUFACTURING / HEALTHCARE）',
    tags             JSON            DEFAULT NULL COMMENT '标签（JSON 数组，如 ["风控", "审批"]）',
    rule_codes       JSON            DEFAULT NULL COMMENT '包含的规则编码列表（JSON 数组）',
    rule_snapshots   JSON            DEFAULT NULL COMMENT '规则定义快照（发布时固化的 List<RuleDefinition> JSON，保证版本内容可复现）',
    previous_version VARCHAR(32)     DEFAULT NULL COMMENT '升级来源版本号（回滚/升级时记录前一版本，便于审计）',
    description      VARCHAR(512)    DEFAULT NULL COMMENT '规则集描述',
    author           VARCHAR(64)     DEFAULT NULL COMMENT '作者（创建人用户名）',
    download_count   BIGINT          NOT NULL DEFAULT 0 COMMENT '下载次数（安装时 +1）',
    rating           DECIMAL(20,6)   DEFAULT NULL COMMENT '评分（0-5，保留 1 位小数）',
    enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用（1=可用，0=已下架）',
    official         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否官方认证规则集（1=官方发布，0=社区贡献）',
    status           VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by       VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by       VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_pack_code UNIQUE (pack_code, pack_version),
    INDEX idx_pack_code (pack_code),
    INDEX idx_industry (industry),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则集（知识包）表';

CREATE TABLE IF NOT EXISTS ydsz_rule_pack_install (
    id            VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id     VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    installed_by  VARCHAR(64)     DEFAULT NULL COMMENT '安装操作人 ID',
    installed_at  DATETIME        DEFAULT NULL COMMENT '安装时间',
    status        VARCHAR(32)     DEFAULT NULL COMMENT '安装状态（INSTALLING/INSTALLED/FAILED/UNINSTALLING/UNINSTALLED）',
    error_message TEXT            COMMENT '失败原因（status=FAILED 时记录异常信息）',
    deleted       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision      INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by    VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by    VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_status (status),
    INDEX idx_installed_at (installed_at),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则包安装记录表';

-- ============================================================================
-- 6. AB Test：策略 / 灰度分桶 / 回滚历史
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_rule_ab_policy (
    id                   VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id            VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    rule_code            VARCHAR(64)     NOT NULL COMMENT '关联规则编码（一对一）',
    auto_rollback_enabled TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否启用自动回滚（1=启用，0=停用）',
    rollback_action      VARCHAR(32)     DEFAULT NULL COMMENT '回滚动作（AUTO 自动回滚 / NOTIFY 仅通知 Owner）',
    error_rate_threshold DECIMAL(20,6)   DEFAULT NULL COMMENT 'canary 桶错误率阈值（0~1.0）',
    min_sample_size      INT             DEFAULT NULL COMMENT '最小样本数',
    check_window_minutes INT             DEFAULT NULL COMMENT '监控窗口（分钟）',
    notify_channels      VARCHAR(255)    DEFAULT NULL COMMENT '通知渠道（INAPP / EMAIL / SMS / WEBHOOK，逗号分隔）',
    description          VARCHAR(512)    DEFAULT NULL COMMENT '描述',
    last_evaluated_at    DATETIME        DEFAULT NULL COMMENT '最近一次评估时间',
    last_rollback_at     DATETIME        DEFAULT NULL COMMENT '最近一次回滚时间',
    status               VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by           VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by           VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_rule_code UNIQUE (rule_code, tenant_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AB Test 自动回滚策略表';

CREATE TABLE IF NOT EXISTS ydsz_rule_canary_bucket (
    id           VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id    VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    rule_code    VARCHAR(64)     NOT NULL COMMENT '规则编码',
    bucket_type  VARCHAR(32)     NOT NULL COMMENT '桶类型（PRIMARY/CANARY）',
    bucket_count BIGINT          NOT NULL DEFAULT 0 COMMENT '桶命中次数',
    stat_date    DATE            NOT NULL COMMENT '统计日期',
    status       VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by   VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by   VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    CONSTRAINT uk_rule_bucket_date UNIQUE (rule_code, bucket_type, stat_date),
    INDEX idx_stat_date (stat_date),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则灰度分桶统计表';

CREATE TABLE IF NOT EXISTS ydsz_rule_ab_rollback (
    id             VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id      VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    rule_code      VARCHAR(64)     NOT NULL COMMENT '规则编码（关联 ydsz_rule_def.rule_code）',
    trigger_reason VARCHAR(32)     NOT NULL COMMENT '触发原因（ERROR_RATE/MANUAL/OWNER_REQUEST）',
    error_rate     DECIMAL(20,6)   DEFAULT NULL COMMENT '回滚时的错误率（triggerReason=ERROR_RATE 时记录）',
    sample_size    BIGINT          DEFAULT NULL COMMENT '回滚时的样本量（参与 AB Test 的事件总数）',
    from_canary    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已从 canary 切换回主版本（1=已回滚，0=仅通知未回滚）',
    operator       VARCHAR(64)     DEFAULT NULL COMMENT '操作人 ID（自动回滚时为 SYSTEM）',
    notify_status  VARCHAR(32)     DEFAULT NULL COMMENT '通知状态（PENDING/SENT/FAILED，回滚后通知规则责任人）',
    status         VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回滚时间',
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by     VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by     VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    INDEX idx_rule_code (rule_code),
    INDEX idx_created_at (created_at),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AB Test 回滚历史表';

-- ============================================================================
-- 7. 版本历史 / 执行轨迹
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_rule_version_history (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    rule_code       VARCHAR(64)     NOT NULL COMMENT '规则编码',
    version         INT             NOT NULL COMMENT '版本号',
    definition_json JSON            DEFAULT NULL COMMENT '该版本的规则定义 JSON 快照',
    change_desc     VARCHAR(512)    DEFAULT NULL COMMENT '变更说明',
    operator        VARCHAR(64)     DEFAULT NULL COMMENT '操作人',
    INDEX idx_rule_version (rule_code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LiteRule 规则版本历史表';

CREATE TABLE IF NOT EXISTS ydsz_rule_execution_trace (
    id               VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    trace_id         VARCHAR(64)     NOT NULL COMMENT '追踪 ID（同一批次评估共享）',
    rule_code        VARCHAR(64)     NOT NULL COMMENT '规则编码',
    rule_name        VARCHAR(128)    DEFAULT NULL COMMENT '规则名称',
    scenario         VARCHAR(64)     DEFAULT NULL COMMENT '业务场景',
    triggered        TINYINT(1)      DEFAULT NULL COMMENT '是否触发（1=触发，0=未触发）',
    severity         VARCHAR(32)     DEFAULT NULL COMMENT '触发严重度',
    condition_result TEXT            COMMENT '条件表达式求值结果描述',
    elapsed_ms       BIGINT          DEFAULT NULL COMMENT '执行耗时（毫秒）',
    facts_snapshot   JSON            DEFAULT NULL COMMENT '事实数据快照（JSON 对象）',
    result_snapshot  JSON            DEFAULT NULL COMMENT '结果快照（JSON 对象）',
    error_message    TEXT            COMMENT '错误信息',
    INDEX idx_trace_id (trace_id),
    INDEX idx_rule_code (rule_code),
    INDEX idx_scenario (scenario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则执行链路追踪表';
