-- ============================================================================
-- Prompt 模板管理表
-- ============================================================================
-- 支持 Prompt 模板的 CRUD、版本管理和变量替换能力。
-- 包含模板主表（ydsz_prompt_template）和版本历史表（ydsz_prompt_version）。
--
-- @author ydsz-team
-- @since 1.0.0
-- ============================================================================

-- Prompt 模板主表
CREATE TABLE IF NOT EXISTS ydsz_prompt_template (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    template_code   VARCHAR(64)     NOT NULL COMMENT '模板唯一编码（业务标识，创建后不可变）',
    template_name   VARCHAR(128)    NOT NULL COMMENT '模板名称（展示用）',
    content         TEXT            NOT NULL COMMENT '模板内容，支持 #{var} 占位符',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '模板描述',
    category        VARCHAR(64)     DEFAULT NULL COMMENT '分类（用于分组检索）',
    current_version INT             NOT NULL DEFAULT 1 COMMENT '当前版本号，自 1 起每次更新递增',
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE COMMENT '逻辑删除标识',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',

    -- 索引
    CONSTRAINT uk_template_code UNIQUE (template_code, tenant_id),
    INDEX idx_category (category),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 模板主表';

-- Prompt 模板版本历史表
CREATE TABLE IF NOT EXISTS ydsz_prompt_version (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    template_code   VARCHAR(64)     NOT NULL COMMENT '所属模板编码（关联 ydsz_prompt_template.template_code）',
    version         INT             NOT NULL COMMENT '版本号（与 template 的 current_version 对应）',
    content         TEXT            NOT NULL COMMENT '该版本的模板内容快照',
    change_note     VARCHAR(512)    DEFAULT NULL COMMENT '版本备注（描述本次变更内容）',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '版本创建时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '操作人',

    -- 索引
    CONSTRAINT uk_template_version UNIQUE (template_code, version, tenant_id),
    INDEX idx_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 模板版本历史表';

-- ============================================================================
-- 初始化数据：默认系统 Prompt 模板
-- ============================================================================
INSERT INTO ydsz_prompt_template (id, tenant_id, template_code, template_name, content, description, category, current_version, deleted)
VALUES ('100000000000000001', '0', 'DEFAULT_SYSTEM', '默认系统 Prompt',
        '你是 YDSZ 项目管理信息系统的智能助手。你可以帮助用户查询项目信息、分析项目进度、发起审批流程、发送消息通知等。请用中文回答。',
        '系统默认的通用助手 Prompt', 'system', 1, FALSE);

INSERT INTO ydsz_prompt_version (id, tenant_id, template_code, version, content, change_note)
VALUES ('100000000000000002', '0', 'DEFAULT_SYSTEM', 1,
        '你是 YDSZ 项目管理信息系统的智能助手。你可以帮助用户查询项目信息、分析项目进度、发起审批流程、发送消息通知等。请用中文回答。',
        '初始版本');

INSERT INTO ydsz_prompt_template (id, tenant_id, template_code, template_name, content, description, category, current_version, deleted)
VALUES ('100000000000000003', '0', 'REACT_SYSTEM', 'ReAct Agent Prompt',
        '你是 YDSZ 项目管理信息系统的智能助手。你可以使用工具来帮助用户完成任务。请根据用户需求决定是否使用工具。如果不需要工具，直接回答即可。',
        'ReAct 模式下的工具调用助手 Prompt', 'system', 1, FALSE);

INSERT INTO ydsz_prompt_version (id, tenant_id, template_code, version, content, change_note)
VALUES ('100000000000000004', '0', 'REACT_SYSTEM', 1,
        '你是 YDSZ 项目管理信息系统的智能助手。你可以使用工具来帮助用户完成任务。请根据用户需求决定是否使用工具。如果不需要工具，直接回答即可。',
        '初始版本');
