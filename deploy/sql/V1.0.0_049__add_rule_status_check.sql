-- ============================================================
-- V1.0.0_049__add_rule_status_check.sql
-- 规则状态字段数据库层 CHECK 约束（纵深防御，配合应用层 RuleStatus 状态机校验）
-- ============================================================

-- pmis_rule_def.status 限定合法状态值
ALTER TABLE pmis_rule_def
    DROP CONSTRAINT IF EXISTS ck_rule_def_status_valid;
ALTER TABLE pmis_rule_def
    ADD CONSTRAINT ck_rule_def_status_valid
    CHECK (status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'DISABLED', 'ARCHIVED'));

COMMENT ON CONSTRAINT ck_rule_def_status_valid ON pmis_rule_def IS
    '规则状态合法性约束，配合应用层 RuleStatus.canTransitionTo 状态机校验';
