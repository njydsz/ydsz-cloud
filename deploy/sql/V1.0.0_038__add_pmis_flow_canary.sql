-- ============================================================
-- V1.0.0_038  P3-1 流程定义灰度发布字段
-- ============================================================
-- 说明：为流程定义增加灰度发布（canary release）能力：
--   - canary_percent: 灰度比例 0-100，0 表示全量走稳定版，100 表示全量走灰度版
--   - canary_status: 灰度状态 NONE / CANARYING / PROMOTED / ROLLED_BACK
--   - canary_strategy: 灰度切流策略 USER_HASH（按发起人ID hash分流）/ RANDOM（随机）/ WHITELIST（白名单）
--   - canary_rollout_log: 灰度发布历史（JSON 数组），记录每次调整比例的操作人/时间/百分比/备注
--
-- 业务流程：
--   1. 发布新版本后，调用 publishCanary(defId, percent) 将其标记为灰度版本
--   2. 启动流程实例时，FlowDefinitionService.getEffectiveDefinition()
--      根据 canary_percent + canary_strategy 决定使用稳定版或灰度版
--   3. 运营人员调用 promoteCanary(defId, percent) 提升灰度比例，最终 promoteCanary(defId, 100) 完成全量
--   4. 出现问题可调用 rollbackCanary(defId) 回滚到稳定版
-- ============================================================

ALTER TABLE pmis_flow_definition
    ADD COLUMN IF NOT EXISTS canary_percent          SMALLINT     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS canary_status           VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS canary_strategy         VARCHAR(16)  NOT NULL DEFAULT 'USER_HASH',
    ADD COLUMN IF NOT EXISTS canary_rollout_log      TEXT;

COMMENT ON COLUMN pmis_flow_definition.canary_percent IS
    '灰度比例 0-100（0=稳定版 / 100=全量灰度版）';
COMMENT ON COLUMN pmis_flow_definition.canary_status IS
    '灰度状态: NONE 无 / CANARYING 灰度中 / PROMOTED 已全量 / ROLLED_BACK 已回滚';
COMMENT ON COLUMN pmis_flow_definition.canary_strategy IS
    '灰度切流策略: USER_HASH 按发起人ID hash / RANDOM 随机 / WHITELIST 白名单';
COMMENT ON COLUMN pmis_flow_definition.canary_rollout_log IS
    '灰度发布历史 JSON 数组[{operatorId,operatorName,fromPercent,toPercent,operateAt,note}]';

-- 灰度索引（按状态快速查询正在灰度中的定义）
CREATE INDEX IF NOT EXISTS idx_pfd_canary_status
    ON pmis_flow_definition(canary_status)
    WHERE deleted = 0 AND canary_status <> 'NONE';
