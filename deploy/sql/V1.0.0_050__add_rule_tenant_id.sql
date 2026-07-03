-- ============================================================
-- V1.0.0_050__add_rule_tenant_id.sql
-- LiteRule 模块多租户字段预留（与项目其他业务表对齐）
--
-- 说明：
--   项目其他业务表（pmis_project_*、pmis_flow_* 等）已普遍预埋
--   tenant_id BIGINT NOT NULL DEFAULT 1 字段。LiteRule 模块的表
--   此前完全缺失该字段，本次补齐以保持 schema 一致性。
--
--   本迁移仅添加字段与索引，不改变现有查询逻辑（单租户部署下
--   tenant_id 恒为 1）。运行时按租户过滤的能力待 v2.0 多租户化
--   阶段与 TenantContext/TenantLineInnerInterceptor 一并启用。
--   详见 docs/multi-tenant-evaluation.md。
-- ============================================================

-- 1. 规则定义表
ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_rule_def_tenant ON pmis_rule_def (tenant_id);
COMMENT ON COLUMN pmis_rule_def.tenant_id IS '租户 ID（单租户部署默认 1，多租户隔离待 v2.0 启用）';

-- 2. 规则版本历史表
ALTER TABLE pmis_rule_version_history
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_rule_version_tenant ON pmis_rule_version_history (tenant_id);

-- 3. 规则执行轨迹表
ALTER TABLE pmis_rule_execution_trace
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_rule_trace_tenant ON pmis_rule_execution_trace (tenant_id);

-- 4. 决策表定义表
ALTER TABLE pmis_rule_decision_table
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_rule_dt_tenant ON pmis_rule_decision_table (tenant_id);

-- 5. 评分卡定义表
ALTER TABLE pmis_rule_scorecard
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_rule_scorecard_tenant ON pmis_rule_scorecard (tenant_id);

-- 6. 决策树定义表
ALTER TABLE pmis_rule_decision_tree
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_rule_tree_tenant ON pmis_rule_decision_tree (tenant_id);

-- 7. 脚本规则定义表
ALTER TABLE pmis_rule_script
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_rule_script_tenant ON pmis_rule_script (tenant_id);

-- 8. 灰度分桶统计表
ALTER TABLE pmis_rule_canary_bucket
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_rule_canary_bucket_tenant ON pmis_rule_canary_bucket (tenant_id);
