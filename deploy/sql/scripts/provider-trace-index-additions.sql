-- ====================================================================
-- ============ P1-7 increment: provider_trace_id index fill-up ==========
-- ====================================================================
-- Background: industry standard requires all tables carrying
--             provider_trace_id to have a dedicated index to support
--             O(log n) reverse lookups. This file fills 63 gaps.
-- Design:
--   * NULLABLE columns  -> partial index WHERE provider_trace_id IS NOT NULL
--   * NOT NULL DEFAULT  -> partial index WHERE provider_trace_id <> ''
-- Generated on 2026-07-06 21:58:52
-- ====================================================================

-- pmis_project_change (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_project_change_trace
    ON pmis_project_change (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_execution_delivery_standard (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_execution_delivery_standard_trace
    ON pmis_execution_delivery_standard (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_execution_delivery_item (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_execution_delivery_item_trace
    ON pmis_execution_delivery_item (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_execution_closure (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_execution_closure_trace
    ON pmis_execution_closure (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_agent_prediction (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_agent_prediction_trace
    ON pmis_agent_prediction (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_finance_invoice (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_finance_invoice_trace
    ON pmis_finance_invoice (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_finance_payment (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_finance_payment_trace
    ON pmis_finance_payment (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_finance_customer_credit (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_finance_customer_credit_trace
    ON pmis_finance_customer_credit (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_evm_measure (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_evm_measure_trace
    ON pmis_evm_measure (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_rate_card (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_rate_card_trace
    ON pmis_rate_card (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_rate_internal (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_rate_internal_trace
    ON pmis_rate_internal (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_profit_simulation (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_profit_simulation_trace
    ON pmis_profit_simulation (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_resource_pool (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_resource_pool_trace
    ON pmis_resource_pool (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_employee_tag (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_employee_tag_trace
    ON pmis_employee_tag (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_resource_assignment (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_resource_assignment_trace
    ON pmis_resource_assignment (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_bench_record (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_bench_record_trace
    ON pmis_bench_record (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_warranty (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_warranty_trace
    ON pmis_warranty (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_ops_ticket (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_ops_ticket_trace
    ON pmis_ops_ticket (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_satisfaction (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_satisfaction_trace
    ON pmis_satisfaction (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_alert_dispatch (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_trace
    ON pmis_alert_dispatch (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_reconcile_daily (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_reconcile_daily_trace
    ON pmis_reconcile_daily (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_definition (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_definition_trace
    ON pmis_flow_definition (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_node (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_node_trace
    ON pmis_flow_node (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_skip (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_skip_trace
    ON pmis_flow_skip (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_instance (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_instance_trace
    ON pmis_flow_instance (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_run_task (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_run_task_trace
    ON pmis_flow_run_task (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_his_task (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_his_task_trace
    ON pmis_flow_his_task (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_user (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_user_trace
    ON pmis_flow_user (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_cc (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_cc_trace
    ON pmis_flow_cc (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_cc_rule (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_cc_rule_trace
    ON pmis_flow_cc_rule (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_timer (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_timer_trace
    ON pmis_flow_timer (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_delegate_auth (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_auth_trace
    ON pmis_flow_delegate_auth (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_delegate_log (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_log_trace
    ON pmis_flow_delegate_log (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_report_subscription (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_report_subscription_trace
    ON pmis_report_subscription (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_his_instance (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_his_instance_trace
    ON pmis_flow_his_instance (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_attendance (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_attendance_trace
    ON pmis_attendance (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_overtime (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_overtime_trace
    ON pmis_overtime (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_leave (NOT NULL)
CREATE INDEX IF NOT EXISTS idx_pmis_leave_trace
    ON pmis_leave (provider_trace_id)
    WHERE provider_trace_id <> '';

-- pmis_rule_def (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_def_trace
    ON pmis_rule_def (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_version_history (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_version_history_trace
    ON pmis_rule_version_history (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_template (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_template_trace
    ON pmis_rule_template (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_test_case (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_test_case_trace
    ON pmis_rule_test_case (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_execution_trace (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_execution_trace_trace
    ON pmis_rule_execution_trace (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_decision_table (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_decision_table_trace
    ON pmis_rule_decision_table (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_event_subscription (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_event_subscription_trace
    ON pmis_flow_event_subscription (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_canary_bucket (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_canary_bucket_trace
    ON pmis_rule_canary_bucket (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_scorecard (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_scorecard_trace
    ON pmis_rule_scorecard (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_decision_tree (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_decision_tree_trace
    ON pmis_rule_decision_tree (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_script (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_script_trace
    ON pmis_rule_script (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_variable_def (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_variable_def_trace
    ON pmis_rule_variable_def (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_third_party_account (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_third_party_account_trace
    ON pmis_flow_third_party_account (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_third_party_log (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_third_party_log_trace
    ON pmis_flow_third_party_log (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_dmn_table (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_dmn_table_trace
    ON pmis_flow_dmn_table (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_template (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_template_trace
    ON pmis_flow_template (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_auto_trigger (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_auto_trigger_trace
    ON pmis_flow_auto_trigger (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_notify_channel (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_notify_channel_trace
    ON pmis_flow_notify_channel (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_flow_task_comment (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_task_comment_trace
    ON pmis_flow_task_comment (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_chain_graph (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_chain_graph_trace
    ON pmis_rule_chain_graph (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_dependency (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_dependency_trace
    ON pmis_rule_dependency (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_ab_policy (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_ab_policy_trace
    ON pmis_rule_ab_policy (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_ab_rollback (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_ab_rollback_trace
    ON pmis_rule_ab_rollback (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_pack (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_pack_trace
    ON pmis_rule_pack (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- pmis_rule_pack_install (NULLABLE)
CREATE INDEX IF NOT EXISTS idx_pmis_rule_pack_install_trace
    ON pmis_rule_pack_install (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

