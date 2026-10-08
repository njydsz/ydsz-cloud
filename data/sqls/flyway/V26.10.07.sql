-- =============================================================================
-- Flyway Migration: V26.10.07
-- Description: tenant_id 字段长度统一 varchar(36)
-- Author:      ydsz-team
-- Created:     2026-10-07
-- =============================================================================
--
-- 背景: 全平台约 135 张表 tenant_id 长度不一致（varchar(32) 或 character varying 无长度混用 varchar(36)）。
--       统一升级为 varchar(36)，消除跨模块 JOIN 时因长度不匹配导致的隐式转换和索引失效。
--       已在 V26.10.03 部分修复 Agent 模块 ydsz_agt_definition，本次补齐全部剩余表。
--
-- 影响范围:
--   - 修复 135 张表的 tenant_id 列（120 张 varchar(32) + 15 张 character varying 无长度限制）
--   - 涉及 10 个模块: agent(4), comm(1), cronjob(21), file(18), flow(23),
--     generator(7), literule(16), message(18), system(11), userinfo(16)
--
-- 执行建议:
--   - ALTER COLUMN TYPE 仅改元数据，不触发表重写，可在线执行
--   - 建议在低峰期执行，单条 DDL 执行时间 < 100ms（取决于表大小）
--   - 执行前建议备份 pg_catalog
--   - 如表上有涉及 tenant_id 的复合索引，无需重建（varchar(36) 兼容原类型）
-- =============================================================================

-- =============================================================================
-- 模块: agent (4 张表)
-- =============================================================================
ALTER TABLE ydsz_agt_definition ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_agt_definition.tenant_id IS '租户 ID';

ALTER TABLE ydsz_agt_prompt_template ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_agt_prompt_template.tenant_id IS '租户 ID';

ALTER TABLE ydsz_agt_prompt_version ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_agt_prompt_version.tenant_id IS '租户 ID';

ALTER TABLE ydsz_agt_token_usage ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_agt_token_usage.tenant_id IS '租户 ID';

-- =============================================================================
-- 模块: comm (1 张表)
-- =============================================================================
ALTER TABLE ydsz_comm_search_dead_letter ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_comm_search_dead_letter.tenant_id IS '租户 ID';

-- =============================================================================
-- 模块: cronjob (21 张表)
-- =============================================================================
ALTER TABLE ydsz_job_alert_dispatch ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_alert_dispatch.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_alert_rule ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_alert_rule.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_artifact ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_artifact.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_dag ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_dag.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_dag_context ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_dag_context.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_dag_instance ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_dag_instance.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_dag_node_instance ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_dag_node_instance.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_dag_version ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_dag_version.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_daily_stats ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_daily_stats.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_event_store ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_event_store.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_glue ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_glue.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_history ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_history.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_log ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_log.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_log_content ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_log_content.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_main ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_main.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_node ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_node.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_outbox ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_outbox.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_task ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_task.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_tenant_quota ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_tenant_quota.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_webhook ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_webhook.tenant_id IS '租户 ID';

ALTER TABLE ydsz_job_webhook_retry ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_job_webhook_retry.tenant_id IS '租户 ID';

-- =============================================================================
-- 模块: file (18 张表)
-- =============================================================================
ALTER TABLE ydsz_file_file_acl ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_file_acl.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_file_comment ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_file_comment.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_file_node ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_file_node.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_file_tag ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_file_tag.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_file_version ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_file_version.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_search_index ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_search_index.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_share_access_log ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_share_access_log.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_share_access_log_archive ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_share_access_log_archive.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_share_link ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_share_link.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_share_recipient ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_share_recipient.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_space ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_space.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_space_member ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_space_member.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_space_template ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_space_template.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_storage_quota ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_storage_quota.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_tag ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_tag.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_trash_item ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_trash_item.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_user_favorite ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_user_favorite.tenant_id IS '租户 ID';

ALTER TABLE ydsz_file_user_recent ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_file_user_recent.tenant_id IS '租户 ID';

-- =============================================================================
-- 模块: flow (23 张表)
-- =============================================================================
ALTER TABLE ydsz_flow_admin_role ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_admin_role.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_archive_cursor ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_archive_cursor.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_attachment ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_attachment.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_audit_log ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_audit_log.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_auto_trigger ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_auto_trigger.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_category ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_category.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_cc ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_cc.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_cc_rule ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_cc_rule.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_comment ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_comment.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_definition ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_definition.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_delegate_auth ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_delegate_auth.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_event_subscription ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_event_subscription.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_his_instance ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_his_instance.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_his_task ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_his_task.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_idempotent ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_idempotent.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_instance ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_instance.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_node ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_node.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_quick_comment ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_quick_comment.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_run_task ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_run_task.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_skip ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_skip.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_template ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_template.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_timer ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_timer.tenant_id IS '租户 ID';

ALTER TABLE ydsz_flow_user ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_flow_user.tenant_id IS '租户 ID';

-- =============================================================================
-- 模块: generator (7 张表)
-- =============================================================================
ALTER TABLE ydsz_gen_column_meta ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_gen_column_meta.tenant_id IS '租户 ID';

ALTER TABLE ydsz_gen_datasource ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_gen_datasource.tenant_id IS '租户 ID';

ALTER TABLE ydsz_gen_history ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_gen_history.tenant_id IS '租户 ID';

ALTER TABLE ydsz_gen_history_file ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_gen_history_file.tenant_id IS '租户 ID';

ALTER TABLE ydsz_gen_table_meta ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_gen_table_meta.tenant_id IS '租户 ID';

ALTER TABLE ydsz_gen_template ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_gen_template.tenant_id IS '租户 ID';

ALTER TABLE ydsz_gen_template_group ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_gen_template_group.tenant_id IS '租户 ID';

-- =============================================================================
-- 模块: literule (16 张表)
-- =============================================================================
ALTER TABLE ydsz_rule_ab_policy ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_ab_policy.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_ab_rollback ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_ab_rollback.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_canary_bucket ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_canary_bucket.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_chain_graph ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_chain_graph.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_decision_table ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_decision_table.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_decision_tree ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_decision_tree.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_def ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_def.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_dependency ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_dependency.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_execution_trace ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_execution_trace.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_pack ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_pack.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_pack_install ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_pack_install.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_scorecard ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_scorecard.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_script ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_script.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_template ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_template.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_variable_def ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_variable_def.tenant_id IS '租户 ID';

ALTER TABLE ydsz_rule_version_history ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_rule_version_history.tenant_id IS '租户 ID';

-- =============================================================================
-- 模块: message (18 张表)
-- =============================================================================
ALTER TABLE ydsz_msg_aggregate ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_aggregate.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_batch ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_batch.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_canary ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_canary.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_feedback ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_feedback.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_log ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_log.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_notification ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_notification.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_offline ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_offline.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_outbox ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_outbox.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_preference ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_preference.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_receipt ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_receipt.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_route_rule ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_route_rule.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_subscription ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_subscription.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_template ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_template.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_template_version ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_template_version.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_tenant_config ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_tenant_config.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_trace ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_trace.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_user_channel ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_user_channel.tenant_id IS '租户 ID';

ALTER TABLE ydsz_msg_variable_source ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_msg_variable_source.tenant_id IS '租户 ID';

-- =============================================================================
-- 模块: system (11 张表)
-- =============================================================================
ALTER TABLE ydsz_sys_api_permission ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_sys_api_permission.tenant_id IS '租户 ID';

ALTER TABLE ydsz_sys_app_info ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_sys_app_info.tenant_id IS '租户 ID';

ALTER TABLE ydsz_sys_config ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_sys_config.tenant_id IS '租户 ID';

ALTER TABLE ydsz_sys_dict_item ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_sys_dict_item.tenant_id IS '租户 ID';

ALTER TABLE ydsz_sys_dict_type ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_sys_dict_type.tenant_id IS '租户 ID';

ALTER TABLE ydsz_sys_entity_version ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_sys_entity_version.tenant_id IS '租户 ID';

ALTER TABLE ydsz_sys_tenant ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_sys_tenant.tenant_id IS '租户 ID';

ALTER TABLE ydsz_sys_tenant_plan ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_sys_tenant_plan.tenant_id IS '租户 ID';

ALTER TABLE ydsz_sys_tenant_plan_menu ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_sys_tenant_plan_menu.tenant_id IS '租户 ID';

ALTER TABLE ydsz_sys_variable ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_sys_variable.tenant_id IS '租户 ID';

ALTER TABLE ydsz_system_config_approval ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_system_config_approval.tenant_id IS '租户 ID';

-- =============================================================================
-- 模块: userinfo (16 张表)
-- =============================================================================
ALTER TABLE ydsz_idm_account_login_history ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_account_login_history.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_account_password_history ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_account_password_history.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_account_user ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_account_user.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_account_user_dept ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_account_user_dept.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_account_user_language ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_account_user_language.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_account_user_post ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_account_user_post.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_account_user_role ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_account_user_role.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_auth_credential ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_auth_credential.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_auth_social_account ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_auth_social_account.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_menu ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_menu.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_org_company ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_org_company.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_org_company_dept ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_org_company_dept.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_org_department ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_org_department.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_post ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_post.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_role ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_role.tenant_id IS '租户 ID';

ALTER TABLE ydsz_idm_role_permission ALTER COLUMN tenant_id TYPE varchar(36);
COMMENT ON COLUMN ydsz_idm_role_permission.tenant_id IS '租户 ID';

-- =============================================================================
-- 验证 SQL（需手动取消注释后执行，用于确认所有字段已统一为 varchar(36)）
-- =============================================================================
-- DO $$
-- DECLARE
--     v_count INTEGER;
-- BEGIN
--     SELECT COUNT(*) INTO v_count
--     FROM information_schema.columns
--     WHERE table_schema = 'public'
--       AND column_name = 'tenant_id'
--       AND data_type = 'character varying'
--       AND character_maximum_length = 36;
--
--     RAISE NOTICE '已统一为 varchar(36) 的表数量: %', v_count;
--
--     -- 列出仍为 varchar(32) 或无长度的表
--     FOR rec IN (
--         SELECT table_name, character_maximum_length
--         FROM information_schema.columns
--         WHERE table_schema = 'public'
--           AND column_name = 'tenant_id'
--           AND (data_type != 'character varying'
--                OR character_maximum_length != 36)
--         ORDER BY table_name
--     ) LOOP
--         RAISE NOTICE '待修复: % (当前长度: %', rec.table_name,
--             CASE WHEN rec.character_maximum_length IS NULL
--                  THEN '无限制'
--                  ELSE rec.character_maximum_length::text
--             END;
--     END LOOP;
-- END $$;
-- =============================================================================
