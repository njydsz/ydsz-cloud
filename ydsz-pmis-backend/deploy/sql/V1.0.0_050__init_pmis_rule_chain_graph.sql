-- 规则链画布表（P0-1：可视化编排画布持久化）
-- 一条规则对应一条画布（chain_graph 表），画布版本号独立递增，便于回滚。
-- 画布内容以 JSON 形式存储节点/边/视口/样式扩展，兼容前端的 SVG 渲染需求。

CREATE TABLE IF NOT EXISTS pmis_rule_chain_graph (
    id                BIGSERIAL PRIMARY KEY,
    rule_code         VARCHAR(128) NOT NULL UNIQUE,
    name              VARCHAR(256) NOT NULL,
    description       TEXT,
    scenario          VARCHAR(64),
    tenant_id         BIGINT       NOT NULL DEFAULT 1,
    graph_version     INTEGER      NOT NULL DEFAULT 1,
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    content_json      JSONB        NOT NULL,
    created_by        VARCHAR(64),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 租户与画布版本联合索引（按租户分页查询使用）
CREATE INDEX IF NOT EXISTS idx_rule_chain_graph_tenant
    ON pmis_rule_chain_graph (tenant_id, updated_at DESC);

-- 状态索引（按状态查询：仅看 DRAFT / PUBLISHED）
CREATE INDEX IF NOT EXISTS idx_rule_chain_graph_status
    ON pmis_rule_chain_graph (status);

COMMENT ON TABLE  pmis_rule_chain_graph IS '规则链可视化编排画布（P0-1）';
COMMENT ON COLUMN pmis_rule_chain_graph.rule_code IS '关联规则编码（与 pmis_rule_def.rule_code 一对一）';
COMMENT ON COLUMN pmis_rule_chain_graph.content_json IS '画布完整内容：nodes/edges/viewport/metadata 的 JSON 序列化';
COMMENT ON COLUMN pmis_rule_chain_graph.graph_version IS '画布版本号（独立于规则 version，便于画布回滚）';
