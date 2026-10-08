-- =============================================================================
-- Flyway Migration: V26.10.09
-- Description: 全库 boolean 字段迁移为 SMALLINT NOT NULL DEFAULT 0
-- Author:      ydsz-team
-- Created:     2026-10-09
-- =============================================================================
--
-- 背景: 布尔类型字段统一使用 SMALLINT（0=否 / 1=是），以符合团队编码规范
--       （DB-007 / OOP-006），保持 MyBatis-Plus 映射一致性。
--       现有部分列使用 PostgreSQL 原生 BOOLEAN 类型，需统一转换。
--
-- 转换规则:
--   - 目标类型: SMALLINT NOT NULL DEFAULT 0
--   - 转换表达式: USING CASE WHEN col IS TRUE THEN 1 ELSE 0 END
--   - 若列已带 CHECK 约束（布尔检查），先 DROP CHECK 再 MODIFY
--
-- 影响范围: 全库所有含 BOOLEAN 布尔列的业务表
-- 执行建议: 表数据量不高，建议在低峰期执行；DDL 不锁长时间仅改元数据
-- =============================================================================

BEGIN;

-- ===========================================================================
-- 模块: sys（系统引擎）
-- ===========================================================================
ALTER TABLE ydsz_sys_config
  ALTER COLUMN is_public SET DATA TYPE SMALLINT USING CASE WHEN is_public IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_public SET NOT NULL,
  ALTER COLUMN is_public SET DEFAULT 0;

-- ===========================================================================
-- 模块: idm（身份引擎）
-- ===========================================================================
ALTER TABLE ydsz_idm_auth_apikey
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_idm_auth_policy
  ALTER COLUMN is_password_require_uppercase SET DATA TYPE SMALLINT USING CASE WHEN is_password_require_uppercase IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_password_require_uppercase SET NOT NULL,
  ALTER COLUMN is_password_require_uppercase SET DEFAULT 0,
  ALTER COLUMN is_password_require_digit SET DATA TYPE SMALLINT USING CASE WHEN is_password_require_digit IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_password_require_digit SET NOT NULL,
  ALTER COLUMN is_password_require_digit SET DEFAULT 0,
  ALTER COLUMN is_mfa_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_mfa_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_mfa_enabled SET NOT NULL,
  ALTER COLUMN is_mfa_enabled SET DEFAULT 0,
  ALTER COLUMN is_captcha_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_captcha_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_captcha_enabled SET NOT NULL,
  ALTER COLUMN is_captcha_enabled SET DEFAULT 0;

ALTER TABLE ydsz_idm_role
  ALTER COLUMN is_built_in SET DATA TYPE SMALLINT USING CASE WHEN is_built_in IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_built_in SET NOT NULL,
  ALTER COLUMN is_built_in SET DEFAULT 0;

-- ===========================================================================
-- 模块: agt（智能引擎）
-- ===========================================================================
ALTER TABLE ydsz_agt_dag_workflow
  ALTER COLUMN is_published SET DATA TYPE SMALLINT USING CASE WHEN is_published IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_published SET NOT NULL,
  ALTER COLUMN is_published SET DEFAULT 0;

ALTER TABLE ydsz_agt_prompt_template
  ALTER COLUMN is_ab_test_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_ab_test_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_ab_test_enabled SET NOT NULL,
  ALTER COLUMN is_ab_test_enabled SET DEFAULT 0;

-- ===========================================================================
-- 模块: job（任务引擎）
-- ===========================================================================
ALTER TABLE ydsz_job_tenant_quota
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_job_alert_rule
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_job_history
  ALTER COLUMN is_deleted SET DATA TYPE SMALLINT USING CASE WHEN is_deleted IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_deleted SET NOT NULL,
  ALTER COLUMN is_deleted SET DEFAULT 0;

-- ===========================================================================
-- 模块: rule（规则引擎）
-- ===========================================================================
ALTER TABLE ydsz_rule_decision_table
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_rule_ab_policy
  ALTER COLUMN is_auto_rollback_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_auto_rollback_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_auto_rollback_enabled SET NOT NULL,
  ALTER COLUMN is_auto_rollback_enabled SET DEFAULT 0;

ALTER TABLE ydsz_rule_ab_rollback
  ALTER COLUMN is_from_canary SET DATA TYPE SMALLINT USING CASE WHEN is_from_canary IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_from_canary SET NOT NULL,
  ALTER COLUMN is_from_canary SET DEFAULT 0;

ALTER TABLE ydsz_rule_decision_tree
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_rule_def
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0,
  ALTER COLUMN is_drilldown_available SET DATA TYPE SMALLINT USING CASE WHEN is_drilldown_available IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_drilldown_available SET NOT NULL,
  ALTER COLUMN is_drilldown_available SET DEFAULT 0;

ALTER TABLE ydsz_rule_dependency
  ALTER COLUMN is_cascade_on_disable SET DATA TYPE SMALLINT USING CASE WHEN is_cascade_on_disable IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_cascade_on_disable SET NOT NULL,
  ALTER COLUMN is_cascade_on_disable SET DEFAULT 0;

ALTER TABLE ydsz_rule_execution_trace
  ALTER COLUMN is_triggered SET DATA TYPE SMALLINT USING CASE WHEN is_triggered IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_triggered SET NOT NULL,
  ALTER COLUMN is_triggered SET DEFAULT 0;

ALTER TABLE ydsz_rule_pack
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0,
  ALTER COLUMN is_official SET DATA TYPE SMALLINT USING CASE WHEN is_official IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_official SET NOT NULL,
  ALTER COLUMN is_official SET DEFAULT 0;

ALTER TABLE ydsz_rule_scorecard
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_rule_script
  ALTER COLUMN is_sandbox_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_sandbox_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_sandbox_enabled SET NOT NULL,
  ALTER COLUMN is_sandbox_enabled SET DEFAULT 0,
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_rule_variable_def
  ALTER COLUMN is_required SET DATA TYPE SMALLINT USING CASE WHEN is_required IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_required SET NOT NULL,
  ALTER COLUMN is_required SET DEFAULT 0,
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

-- ===========================================================================
-- 模块: msg（消息引擎）
-- ===========================================================================
ALTER TABLE ydsz_msg_preference
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0,
  ALTER COLUMN is_dnd_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_dnd_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_dnd_enabled SET NOT NULL,
  ALTER COLUMN is_dnd_enabled SET DEFAULT 0,
  ALTER COLUMN is_digest_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_digest_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_digest_enabled SET NOT NULL,
  ALTER COLUMN is_digest_enabled SET DEFAULT 0;

-- ===========================================================================
-- 模块: wiki（文件引擎）
-- ===========================================================================
ALTER TABLE ydsz_file_file_acl
  ALTER COLUMN is_inherited SET DATA TYPE SMALLINT USING CASE WHEN is_inherited IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_inherited SET NOT NULL,
  ALTER COLUMN is_inherited SET DEFAULT 0,
  ALTER COLUMN is_owner SET DATA TYPE SMALLINT USING CASE WHEN is_owner IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_owner SET NOT NULL,
  ALTER COLUMN is_owner SET DEFAULT 0;

ALTER TABLE ydsz_file_file_comment
  ALTER COLUMN is_resolved SET DATA TYPE SMALLINT USING CASE WHEN is_resolved IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_resolved SET NOT NULL,
  ALTER COLUMN is_resolved SET DEFAULT 0,
  ALTER COLUMN is_edited SET DATA TYPE SMALLINT USING CASE WHEN is_edited IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_edited SET NOT NULL,
  ALTER COLUMN is_edited SET DEFAULT 0;

ALTER TABLE ydsz_file_file_node
  ALTER COLUMN is_preview_ready SET DATA TYPE SMALLINT USING CASE WHEN is_preview_ready IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_preview_ready SET NOT NULL,
  ALTER COLUMN is_preview_ready SET DEFAULT 0,
  ALTER COLUMN is_starred SET DATA TYPE SMALLINT USING CASE WHEN is_starred IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_starred SET NOT NULL,
  ALTER COLUMN is_starred SET DEFAULT 0;

ALTER TABLE ydsz_file_file_version
  ALTER COLUMN is_active SET DATA TYPE SMALLINT USING CASE WHEN is_active IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_active SET NOT NULL,
  ALTER COLUMN is_active SET DEFAULT 0;

ALTER TABLE ydsz_file_share_link
  ALTER COLUMN is_reminder_sent SET DATA TYPE SMALLINT USING CASE WHEN is_reminder_sent IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_reminder_sent SET NOT NULL,
  ALTER COLUMN is_reminder_sent SET DEFAULT 0;

ALTER TABLE ydsz_file_space_template
  ALTER COLUMN is_system SET DATA TYPE SMALLINT USING CASE WHEN is_system IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_system SET NOT NULL,
  ALTER COLUMN is_system SET DEFAULT 0,
  ALTER COLUMN is_public_access SET DATA TYPE SMALLINT USING CASE WHEN is_public_access IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_public_access SET NOT NULL,
  ALTER COLUMN is_public_access SET DEFAULT 0;

-- ===========================================================================
-- 模块: flow（流程引擎）
-- ===========================================================================
ALTER TABLE ydsz_flow_admin_role
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_flow_auto_trigger
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_flow_cc_rule
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

-- ===========================================================================
-- 模块: gen（代码生成器）
-- ===========================================================================
ALTER TABLE ydsz_gen_datasource
  ALTER COLUMN is_default SET DATA TYPE SMALLINT USING CASE WHEN is_default IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_default SET NOT NULL,
  ALTER COLUMN is_default SET DEFAULT 0;

ALTER TABLE ydsz_gen_column_meta
  ALTER COLUMN is_nullable SET DATA TYPE SMALLINT USING CASE WHEN is_nullable IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_nullable SET NOT NULL,
  ALTER COLUMN is_nullable SET DEFAULT 0,
  ALTER COLUMN is_pk SET DATA TYPE SMALLINT USING CASE WHEN is_pk IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_pk SET NOT NULL,
  ALTER COLUMN is_pk SET DEFAULT 0,
  ALTER COLUMN is_dto_skipped SET DATA TYPE SMALLINT USING CASE WHEN is_dto_skipped IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_dto_skipped SET NOT NULL,
  ALTER COLUMN is_dto_skipped SET DEFAULT 0,
  ALTER COLUMN is_vo_skipped SET DATA TYPE SMALLINT USING CASE WHEN is_vo_skipped IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_vo_skipped SET NOT NULL,
  ALTER COLUMN is_vo_skipped SET DEFAULT 0,
  ALTER COLUMN is_query_skipped SET DATA TYPE SMALLINT USING CASE WHEN is_query_skipped IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_query_skipped SET NOT NULL,
  ALTER COLUMN is_query_skipped SET DEFAULT 0;

ALTER TABLE ydsz_gen_template
  ALTER COLUMN is_folder SET DATA TYPE SMALLINT USING CASE WHEN is_folder IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_folder SET NOT NULL,
  ALTER COLUMN is_folder SET DEFAULT 0,
  ALTER COLUMN is_active SET DATA TYPE SMALLINT USING CASE WHEN is_active IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_active SET NOT NULL,
  ALTER COLUMN is_active SET DEFAULT 0;

ALTER TABLE ydsz_gen_template_group
  ALTER COLUMN is_system SET DATA TYPE SMALLINT USING CASE WHEN is_system IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_system SET NOT NULL,
  ALTER COLUMN is_system SET DEFAULT 0,
  ALTER COLUMN is_active SET DATA TYPE SMALLINT USING CASE WHEN is_active IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_active SET NOT NULL,
  ALTER COLUMN is_active SET DEFAULT 0;

COMMIT;
