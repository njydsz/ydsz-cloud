-- ============================================================
-- P1-1: 任务优先级 priority 字段落地
-- ============================================================
-- 用途：
--   1. 待办列表按 priority DESC, created_at ASC 默认排序（高优先级 + 先到先审）
--   2. 节点 ext.priority 由 BpmnXmlParser 解析 BPMN flowable:priority 写入
--   3. 任务创建时从 node.ext.priority 拷贝到 task.priority
--
-- 取值范围：1~100，默认 50（中等优先级）
--   1-25: 低
--   26-50: 中
--   51-75: 高
--   76-100: 紧急
-- ============================================================

ALTER TABLE pmis_flow_task
    ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 50;

COMMENT ON COLUMN pmis_flow_task.priority IS 'P1-1: 任务优先级（1-100，默认 50），待办默认按 priority DESC, created_at ASC 排序';

-- 部分索引：仅 PENDING/CLAIMED 状态按 priority 排序查询走索引
CREATE INDEX IF NOT EXISTS idx_pmis_flow_task_priority_todo
    ON pmis_flow_task (priority DESC, created_at ASC)
    WHERE task_status IN ('PENDING', 'CLAIMED')
      AND status = 'ENABLED'
      AND deleted = 0;
