-- =============================================================
-- V1.0.0_059__init_pmis_flow_dmn.sql
-- DMN 决策表定义表
--
-- P0-4: DMN 决策表引擎（对标 Camunda/Flowable DMN）
--   1. pmis_flow_dmn_table — 决策表定义主表，存储输入/输出列与规则行的 JSON。
--   2. 命中策略支持 UNIQUE/FIRST/PRIORITY/ANY/COLLECT 五种模式。
--   3. 规则行/列定义以 JSON 存储，便于前端动态编辑，无需 DDL 变更。
--
-- 兼容性：
--   - 全部使用 IF NOT EXISTS，可重复执行
--   - 审计字段与 BaseDO 对齐（created_by/created_at/updated_by/updated_at/deleted）
--   - tenant_id 默认值 1，单租户部署不影响数据
-- =============================================================

-- -------------------------------------------
-- DMN 决策表定义表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_dmn_table (
    id                BIGSERIAL       PRIMARY KEY,
    table_key         VARCHAR(128)    NOT NULL,
    table_name        VARCHAR(128)    NOT NULL,
    description       VARCHAR(512),
    hit_policy        VARCHAR(20)     NOT NULL DEFAULT 'UNIQUE',
    collect_operator  VARCHAR(20)     DEFAULT 'LIST',
    inputs_json       TEXT,
    outputs_json      TEXT,
    rules_json        TEXT,
    version           INT             NOT NULL DEFAULT 1,
    status            VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    tenant_id         BIGINT          NOT NULL DEFAULT 1,
    created_by        BIGINT,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT        NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_flow_dmn_table IS 'P0-4: DMN 决策表定义';
COMMENT ON COLUMN pmis_flow_dmn_table.table_key IS '决策表唯一标识';
COMMENT ON COLUMN pmis_flow_dmn_table.table_name IS '决策表名称';
COMMENT ON COLUMN pmis_flow_dmn_table.description IS '决策表描述';
COMMENT ON COLUMN pmis_flow_dmn_table.hit_policy IS '命中策略: UNIQUE/FIRST/PRIORITY/ANY/COLLECT';
COMMENT ON COLUMN pmis_flow_dmn_table.collect_operator IS 'COLLECT 聚合运算符: LIST/SUM/MIN/MAX/COUNT';
COMMENT ON COLUMN pmis_flow_dmn_table.inputs_json IS '输入列定义(JSON)';
COMMENT ON COLUMN pmis_flow_dmn_table.outputs_json IS '输出列定义(JSON)';
COMMENT ON COLUMN pmis_flow_dmn_table.rules_json IS '规则行定义(JSON)';
COMMENT ON COLUMN pmis_flow_dmn_table.version IS '版本号';
COMMENT ON COLUMN pmis_flow_dmn_table.status IS '状态: DRAFT/PUBLISHED/DEPRECATED';
COMMENT ON COLUMN pmis_flow_dmn_table.tenant_id IS '租户 ID（多租户隔离）';
COMMENT ON COLUMN pmis_flow_dmn_table.deleted IS '逻辑删除标记 0=未删 1=已删';

-- 唯一约束：table_key 在未删除范围内唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_flow_dmn_table_key
    ON pmis_flow_dmn_table (table_key)
    WHERE deleted = 0;

-- 索引：按状态筛选已发布决策表
CREATE INDEX IF NOT EXISTS idx_flow_dmn_table_status
    ON pmis_flow_dmn_table (status, deleted)
    WHERE deleted = 0;

-- 索引：按名称模糊查询
CREATE INDEX IF NOT EXISTS idx_flow_dmn_table_name
    ON pmis_flow_dmn_table (table_name, deleted)
    WHERE deleted = 0;
