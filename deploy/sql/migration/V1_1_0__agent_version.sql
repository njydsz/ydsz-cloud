-- P0-4: Agent 版本管理持久化表
-- 对标 Coze Bot 版本管理 / Dify 应用版本
CREATE TABLE IF NOT EXISTS pmis_agent_version (
    id              VARCHAR(64)   NOT NULL COMMENT '主键 ID（雪花算法）',
    agent_type      VARCHAR(128)  NOT NULL COMMENT 'Agent 类型',
    version_id      VARCHAR(32)   NOT NULL COMMENT '版本号（如 v1、v2）',
    status          VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态：DRAFT/PUBLISHED/ARCHIVED',
    config_json     TEXT          COMMENT 'Agent 配置 JSON（Prompt、参数、工具绑定等）',
    description     VARCHAR(512)  COMMENT '版本描述',
    published_at    TIMESTAMP     COMMENT '发布时间',
    is_active       INT           NOT NULL DEFAULT 0 COMMENT '是否为当前活跃版本（1=是, 0=否）',
    tenant_id       VARCHAR(64)   COMMENT '租户 ID',
    created_by      VARCHAR(64)   COMMENT '创建人 ID',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)   COMMENT '更新人 ID',
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         INT           NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0=未删, 1=已删)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_version (agent_type, version_id),
    KEY idx_agent_type (agent_type),
    KEY idx_active (agent_type, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 版本管理表';
