-- ============================================================
-- V1.0.2: GAP-P2-10 FOREACH 循环节点 — pmis_flow_task 新增 iter_var 字段
-- ============================================================
-- 用途：存储 FOREACH 循环节点每条独立 task 对应的集合元素值
--       （如 userId、deptId），用于区分不同迭代实例
-- 非 FOREACH 节点的 task 该字段为 NULL
-- ============================================================

ALTER TABLE pmis_flow_task ADD COLUMN IF NOT EXISTS iter_var VARCHAR(255);

COMMENT ON COLUMN pmis_flow_task.iter_var IS 'GAP-P2-10: FOREACH 当前迭代元素值（循环节点每条独立 task 对应的集合元素，非循环节点为 NULL）';
