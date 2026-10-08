-- =============================================================================
-- Flyway Migration: V26.10.15
-- Description: Token 用量记录新增 bot_id 列（按 Agent 定义维度聚合指标）
-- Author:      ydsz-team
-- Created:     2026-10-09
-- =============================================================================
--
-- 背景:
--   ObservabilityController 的 /metrics/bot/{botId} 端点需要按 Agent 定义聚合
--   LLM 调用次数、Token 消耗、成本等指标。原 token_usage 表缺少 bot_id 列，
--   无法直接按 botId 过滤查询，需补充该维度。
--
-- 变更:
--   1. ydsz_agt_token_usage 新增 bot_id 列（VARCHAR(36)，与 conversation_id 同宽）
--   2. 创建 idx_ydsz_agt_token_usage_bot_created 复合索引（bot_id + created_at）
--
-- 兼容性:
--   - bot_id 列允许 NULL（历史数据无此值，新增记录由应用层填充）
--   - 索引使用 WHERE bot_id IS NOT NULL 部分索引，避免 NULL 值占用索引空间
-- =============================================================================

BEGIN;

-- 1. 新增 bot_id 列
ALTER TABLE ydsz_agt_token_usage
    ADD COLUMN IF NOT EXISTS bot_id VARCHAR(36) DEFAULT NULL;

COMMENT ON COLUMN ydsz_agt_token_usage.bot_id IS
    '关联的 Agent 定义 ID（botId），由 RequestContext 在记录时注入，支持按 Agent 维度聚合指标';

-- 2. 创建 bot_id + created_at 复合索引（加速按 botId 时间范围查询）
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_token_usage_bot_created
    ON ydsz_agt_token_usage (bot_id, created_at)
    WHERE bot_id IS NOT NULL;

COMMIT;
