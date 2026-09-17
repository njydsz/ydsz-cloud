-- ============================================================================
-- V26.09.17: Prompt 模板新增 A/B 灰度测试字段
-- 对标 Dify 的 Prompt 版本管理 / A/B 测试能力
-- ============================================================================

-- 新增 A/B 灰度字段
ALTER TABLE ydsz_agt_prompt_template
    ADD COLUMN IF NOT EXISTS is_ab_test_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ab_target_version    INTEGER,
    ADD COLUMN IF NOT EXISTS ab_traffic_percent   INTEGER;

-- 约束：流量百分比取值范围 1-100
ALTER TABLE ydsz_agt_prompt_template
    ADD CONSTRAINT chk_ab_traffic_percent
        CHECK (ab_traffic_percent IS NULL OR (ab_traffic_percent >= 1 AND ab_traffic_percent <= 100));

-- 注释
COMMENT ON COLUMN ydsz_agt_prompt_template.is_ab_test_enabled IS '是否启用 A/B 灰度测试';
COMMENT ON COLUMN ydsz_agt_prompt_template.ab_target_version   IS 'A/B 灰度目标版本(canary 版本号)';
COMMENT ON COLUMN ydsz_agt_prompt_template.ab_traffic_percent  IS 'A/B 灰度流量百分比(1-100)';
