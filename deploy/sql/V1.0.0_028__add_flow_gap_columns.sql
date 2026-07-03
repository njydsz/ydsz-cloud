-- =============================================================
-- V1.0.0_028__add_flow_gap_columns.sql
-- 工作流引擎对标差距补全 — 新增字段
--
-- GAP-P0: 表单字段权限 (pmis_flow_node.form_fields_config)
-- GAP-P1: SLA 超时配置 (pmis_flow_node.sla_config)
-- GAP-P1: 子流程父子关系 (pmis_flow_instance.parent_instance_id / parent_node_code)
-- GAP-P1: 会签并发版本号 (pmis_flow_task.version)
-- =============================================================

-- -------------------------------------------
-- 1. pmis_flow_node 新增字段
-- -------------------------------------------
ALTER TABLE pmis_flow_node ADD COLUMN IF NOT EXISTS form_fields_config TEXT;
ALTER TABLE pmis_flow_node ADD COLUMN IF NOT EXISTS sla_config TEXT;

COMMENT ON COLUMN pmis_flow_node.form_fields_config IS 'GAP-P0: 表单字段权限 JSON — {"fieldKey":"EDIT|READONLY|HIDDEN",...}';
COMMENT ON COLUMN pmis_flow_node.sla_config IS 'GAP-P1: SLA 超时配置 JSON — {"timeoutMinutes":120,"action":"REMIND|ESCALATE|AUTO_PASS|AUTO_REJECT","reminderCount":3,"adminUserId":1}';

-- -------------------------------------------
-- 2. pmis_flow_instance 新增子流程字段
-- -------------------------------------------
ALTER TABLE pmis_flow_instance ADD COLUMN IF NOT EXISTS parent_instance_id BIGINT;
ALTER TABLE pmis_flow_instance ADD COLUMN IF NOT EXISTS parent_node_code VARCHAR(64);

COMMENT ON COLUMN pmis_flow_instance.parent_instance_id IS 'GAP-P1: 父流程实例 ID（子流程场景，可空）';
COMMENT ON COLUMN pmis_flow_instance.parent_node_code IS 'GAP-P1: 父流程中触发子流程的节点编码（可空）';

CREATE INDEX IF NOT EXISTS idx_pmis_flow_instance_parent
    ON pmis_flow_instance (parent_instance_id)
    WHERE parent_instance_id IS NOT NULL;

-- -------------------------------------------
-- 3. pmis_flow_task 新增乐观锁版本号
-- -------------------------------------------
ALTER TABLE pmis_flow_task ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN pmis_flow_task.version IS 'GAP-P1: 乐观锁版本号 — 会签并发安全，防止多线程同时推进';

-- -------------------------------------------
-- 4. pmis_flow_audit_log 新增 action 枚举值扩展（无需 DDL，仅文档说明）
-- 新增 action 值: AUTO_PASS / COUNTERSIGN_REMOVE / MARK_READ / COMMUNICATE / SLA_TIMEOUT / SLA_ESCALATE
-- -------------------------------------------
