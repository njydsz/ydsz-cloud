-- =============================================================
-- V1.0.0_034__add_pmis_flow_sla_reminder.sql
-- 流程 SLA 超时自动策略 + 催办计数
--
-- P1-6: 后端超时自动策略（PASS/REJECT/NOTIFY/ESCALATE）
--      对标钉钉/飞书的审批超时自动化能力：
--      1. 节点可配 slaConfig.timeoutMinutes（超时阈值）
--      2. 节点可配 slaConfig.action（REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT）
--      3. 节点可配 slaConfig.reminderIntervalMinutes（重复提醒间隔，默认 60）
--      4. 节点可配 slaConfig.maxReminders（最大提醒次数，默认 3）
--      5. 节点可配 slaConfig.escalateUserId（升级目标用户，可空=管理员）
--      6. 任务表 pmis_flow_task 记录 reminder_count / last_reminded_at / sla_action
-- =============================================================

-- -------------------------------------------
-- 1. pmis_flow_task 增加 SLA 跟踪字段
-- -------------------------------------------
ALTER TABLE pmis_flow_task
    ADD COLUMN IF NOT EXISTS reminder_count   INTEGER       NOT NULL DEFAULT 0;

ALTER TABLE pmis_flow_task
    ADD COLUMN IF NOT EXISTS last_reminded_at TIMESTAMP     DEFAULT NULL;

ALTER TABLE pmis_flow_task
    ADD COLUMN IF NOT EXISTS sla_action       VARCHAR(32)   DEFAULT NULL;

ALTER TABLE pmis_flow_task
    ADD COLUMN IF NOT EXISTS sla_escalated    SMALLINT      NOT NULL DEFAULT 0;

COMMENT ON COLUMN pmis_flow_task.reminder_count   IS '已发送的 SLA 催办次数';
COMMENT ON COLUMN pmis_flow_task.last_reminded_at IS '最近一次催办时间';
COMMENT ON COLUMN pmis_flow_task.sla_action       IS '最终触发的 SLA 动作（REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT）';
COMMENT ON COLUMN pmis_flow_task.sla_escalated    IS '是否已升级（0 否 / 1 是，避免重复升级）';

-- -------------------------------------------
-- 2. pmis_flow_node 已存在 slaConfig 字段（V1.0.0_026 引入），无需变更
--    扩展约定：
--    slaConfig = {
--      "timeoutMinutes": 120,            // 超时阈值
--      "action": "AUTO_PASS",            // 动作：REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT
--      "reminderIntervalMinutes": 60,    // 重复提醒间隔（仅 action=REMIND 生效）
--      "maxReminders": 3,                // 最大提醒次数（仅 action=REMIND 生效）
--      "escalateUserId": 1001,           // 升级目标用户（仅 action=ESCALATE 生效；空=管理员=1）
--      "escalateRoleCode": "manager",    // 升级目标角色（仅 action=ESCALATE 生效；可空）
--      "autoComment": "已超时自动通过"  // 自动操作时写入的审批意见
--    }
-- -------------------------------------------
