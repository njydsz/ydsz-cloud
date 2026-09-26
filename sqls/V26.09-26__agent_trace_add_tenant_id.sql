-- ============================================================================
-- V26.09.26: Agent 执行链路表补 tenant_id 多租户隔离字段
-- 问题：ydsz_agt_trace / ydsz_agt_trace_step 缺少 tenant_id 列，多租户场景下存在
--       跨租户数据泄漏风险（YDIZ-TENANT-001 违规）
-- 修复：ALTER TABLE 追加 tenant_id 列 + 创建复合索引
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 修复 ydsz_agt_trace：补 tenant_id 列（AgentTrace 通过 MpBaseEntity 继承该字段，Java 侧已存在）
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_agt_trace
    ADD COLUMN tenant_id VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户 ID（多租户隔离）';

ALTER TABLE ydsz_agt_trace
    ADD INDEX idx_trace_tenant (tenant_id),
    ADD INDEX idx_trace_tenant_conversation (tenant_id, conversation_id),
    ADD INDEX idx_trace_tenant_status (tenant_id, status);

-- ----------------------------------------------------------------------------
-- 修复 ydsz_agt_trace_step：补 tenant_id 列 + 复合索引
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_agt_trace_step
    ADD COLUMN tenant_id VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户 ID（多租户隔离）';

ALTER TABLE ydsz_agt_trace_step
    ADD INDEX idx_step_tenant (tenant_id),
    ADD INDEX idx_step_tenant_trace (tenant_id, trace_id);
