-- =============================================================================
-- Flyway Migration: V26.10.10
-- Description: 类外键列补充索引（高频 JOIN / WHERE 列）
-- Author:      ydsz-team
-- Created:     2026-10-10
-- =============================================================================
--
-- 背景: 部分业务表存在隐式外键列（列名以 _id 结尾或语义关联其他表），
--   这些列在 JOIN 或子查询中作为过滤条件，但缺少索引，导致大表全表扫描。
--
-- 变更一览：
--   Section 1: agent 模块 — session_id / agent_id / template_code 索引
--   Section 2: workflow 模块 — flow_definition_id / instance_id / business_type+business_id 索引
--   Section 3: 跨模块 —  polymorphic 关联（business_type + business_id）复合索引
--
-- 执行建议:
--   - 在低峰期执行，大表 CREATE INDEX CONCURRENTLY 可避免锁表
--   - 所有索引使用 IF NOT EXISTS 幂等保护
-- =============================================================================

-- =============================================================================
-- Section 1: agent 模块 — 高频 JOIN 列
-- =============================================================================

-- session_id：会话追踪查询核心条件（agent observability / runtime 页面）
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_trace_step_session_id
    ON ydsz_agt_trace_step (session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_chat_message_session_id
    ON ydzs_agt_chat_message (session_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_chat_session_tenant
    ON ydzs_agt_chat_session (tenant_id, status, created_at DESC);

-- agent_id：按 Agent 聚合统计查询
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_trace_step_agent_id
    ON ydsz_agt_trace_step (agent_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_async_task_agent_id
    ON ydzs_agt_async_task (agent_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_prompt_version_template_code
    ON ydzs_agt_prompt_version (template_code, version DESC);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_trigger_agent_id
    ON ydzs_agt_trigger (agent_id, status);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_skill_definition_agent_id
    ON ydzs_agt_skill_definition (agent_id, is_deleted);

-- =============================================================================
-- Section 2: workflow 模块 — 高频 JOIN 列
-- =============================================================================

-- flow_definition_id：按流程定义查询实例列表（运行中实例 / 历史实例）
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_instance_definition_id
    ON ydzs_flow_instance (flow_definition_id, tenant_id, created_at DESC);

-- instance_id：流程实例下子表查询（任务/评论/附件/审批记录）
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_task_instance_id
    ON ydzs_flow_task (flow_instance_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_comment_instance_id
    ON ydzs_flow_comment (flow_instance_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_attachment_instance_id
    ON ydzs_flow_attachment (flow_instance_id, is_deleted);

-- canonical_polymorphic_fk: business_type + business_id 多态关联
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_instance_business
    ON ydzs_flow_instance (business_type, business_id);
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_definition_flow_code
    ON ydzs_flow_definition (flow_code, tenant_id, is_deleted);

-- status + tenant 高频筛选
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_instance_status_tenant
    ON ydzs_flow_instance (status, tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_task_status_tenant
    ON ydzs_flow_task (status, tenant_id, created_at ASC);

-- =============================================================================
-- Section 3: 跨模块多态关联 — 通用 (business_type, business_id) 索引
-- =============================================================================
-- 注：对于使用 business_type + business_id 模式实现多态关联的表，创建复合索引
--   加速按业务对象反查关联记录（如查询某订单的全部审批记录/定时任务等）。

-- 各引擎模块多态关联表（展库后新增表时建议统一遵循此命名惯例）
CREATE INDEX IF NOT EXISTS idx_ydsz_cronjob_job_business
    ON ydzs_cron_job (business_type, business_id);
CREATE INDEX IF NOT EXISTS idx_ydsz_cron_job_log_job_id
    ON ydzs_cron_job_log (job_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_template_id
    ON ydzs_msg_log (template_id, tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_channel_id
    ON ydzs_msg_log (channel_id, tenant_id, status, created_at DESC);
