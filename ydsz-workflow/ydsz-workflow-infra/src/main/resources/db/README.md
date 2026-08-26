# ydsz-workflow 数据库索引基线（P0-3）

> 权威来源：`V1__baseline.sql`（与 `data/postgre/ydsz-workflow.sql` 双写同步）。
> 基线共 **21 张表 / 72 个索引**。索引口径须与 infra 实体 Javadoc 注释保持一致。

## ydsz_flow_admin_role
- `idx_ydsz_flow_admin_role_role_code` ON `ydsz_flow_admin_role`(role_code)
- `idx_ydsz_flow_admin_role_tenant_deleted` ON `ydsz_flow_admin_role`(tenant_id, deleted)

## ydsz_flow_attachment
- `idx_ydsz_flow_attachment_instance_id` ON `ydsz_flow_attachment`(instance_id)
- `idx_ydsz_flow_attachment_md5` ON `ydsz_flow_attachment`(md5)
- `idx_ydsz_flow_attachment_task_id` ON `ydsz_flow_attachment`(task_id)
- `idx_ydsz_flow_attachment_tenant_deleted` ON `ydsz_flow_attachment`(tenant_id, deleted)

## ydsz_flow_audit_log
- `idx_ydsz_flow_audit_log_business` ON `ydsz_flow_audit_log`(business_type, business_id)
- `idx_ydsz_flow_audit_log_instance_id` ON `ydsz_flow_audit_log`(instance_id)
- `idx_ydsz_flow_audit_log_operated_at` ON `ydsz_flow_audit_log`(operated_at)
- `idx_ydsz_flow_audit_log_operator_id` ON `ydsz_flow_audit_log`(operator_id)
- `idx_ydsz_flow_audit_log_tenant_deleted` ON `ydsz_flow_audit_log`(tenant_id, deleted)

## ydsz_flow_auto_trigger
- `idx_ydsz_flow_auto_trigger_enabled` ON `ydsz_flow_auto_trigger`(enabled)
- `idx_ydsz_flow_auto_trigger_source_flow_code` ON `ydsz_flow_auto_trigger`(source_flow_code)
- `idx_ydsz_flow_auto_trigger_target_flow_code` ON `ydsz_flow_auto_trigger`(target_flow_code)
- `idx_ydsz_flow_auto_trigger_tenant_deleted` ON `ydsz_flow_auto_trigger`(tenant_id, deleted)

## ydsz_flow_category
- `idx_ydsz_flow_category_parent_id` ON `ydsz_flow_category`(parent_id)
- `idx_ydsz_flow_category_tenant_deleted` ON `ydsz_flow_category`(tenant_id, deleted)

## ydsz_flow_cc
- `idx_ydsz_flow_cc_business_key` ON `ydsz_flow_cc`(business_key)
- `idx_ydsz_flow_cc_cc_user_id` ON `ydsz_flow_cc`(cc_user_id)
- `idx_ydsz_flow_cc_instance_id` ON `ydsz_flow_cc`(instance_id)
- `idx_ydsz_flow_cc_tenant_deleted` ON `ydsz_flow_cc`(tenant_id, deleted)

## ydsz_flow_cc_rule
- `idx_ydsz_flow_cc_rule_enabled` ON `ydsz_flow_cc_rule`(enabled)
- `idx_ydsz_flow_cc_rule_flow_node` ON `ydsz_flow_cc_rule`(flow_code, node_code)
- `idx_ydsz_flow_cc_rule_tenant_deleted` ON `ydsz_flow_cc_rule`(tenant_id, deleted)

## ydsz_flow_comment
- `idx_ydsz_flow_comment_instance_id` ON `ydsz_flow_comment`(instance_id)
- `idx_ydsz_flow_comment_parent_comment_id` ON `ydsz_flow_comment`(parent_comment_id)
- `idx_ydsz_flow_comment_tenant_deleted` ON `ydsz_flow_comment`(tenant_id, deleted)

## ydsz_flow_definition
- `idx_ydsz_flow_definition_category` ON `ydsz_flow_definition`(category)
- `idx_ydsz_flow_definition_tenant_deleted` ON `ydsz_flow_definition`(tenant_id, deleted)

## ydsz_flow_delegate_auth
- `idx_ydsz_flow_delegate_auth_delegate_user_id` ON `ydsz_flow_delegate_auth`(delegate_user_id)
- `idx_ydsz_flow_delegate_auth_owner_user_id` ON `ydsz_flow_delegate_auth`(owner_user_id)
- `idx_ydsz_flow_delegate_auth_status_time` ON `ydsz_flow_delegate_auth`(auth_status, end_time)
- `idx_ydsz_flow_delegate_auth_tenant_deleted` ON `ydsz_flow_delegate_auth`(tenant_id, deleted)

## ydsz_flow_event_subscription
- `idx_ydsz_flow_event_subscription_correlation_key` ON `ydsz_flow_event_subscription`(correlation_key)
- `idx_ydsz_flow_event_subscription_event_ref` ON `ydsz_flow_event_subscription`(event_ref)
- `idx_ydsz_flow_event_subscription_instance_id` ON `ydsz_flow_event_subscription`(instance_id)
- `idx_ydsz_flow_event_subscription_subscription_status` ON `ydsz_flow_event_subscription`(subscription_status)
- `idx_ydsz_flow_event_subscription_tenant_deleted` ON `ydsz_flow_event_subscription`(tenant_id, deleted)

## ydsz_flow_his_instance
- `idx_ydsz_flow_his_instance_archived_at` ON `ydsz_flow_his_instance`(archived_at)
- `idx_ydsz_flow_his_instance_end_at` ON `ydsz_flow_his_instance`(end_at)
- `idx_ydsz_flow_his_instance_tenant_deleted` ON `ydsz_flow_his_instance`(tenant_id, deleted)

## ydsz_flow_his_task
- `idx_ydsz_flow_his_task_assignee_id` ON `ydsz_flow_his_task`(assignee_id)
- `idx_ydsz_flow_his_task_business` ON `ydsz_flow_his_task`(business_type, business_id)
- `idx_ydsz_flow_his_task_finish_at` ON `ydsz_flow_his_task`(finish_at)
- `idx_ydsz_flow_his_task_instance_id` ON `ydsz_flow_his_task`(instance_id)
- `idx_ydsz_flow_his_task_tenant_deleted` ON `ydsz_flow_his_task`(tenant_id, deleted)

## ydsz_flow_instance
- `idx_ydsz_flow_instance_flow_status` ON `ydsz_flow_instance`(flow_status)
- `idx_ydsz_flow_instance_initiator_id` ON `ydsz_flow_instance`(initiator_id)
- `idx_ydsz_flow_instance_tenant_deleted` ON `ydsz_flow_instance`(tenant_id, deleted)

## ydsz_flow_node
- `idx_ydsz_flow_node_flow_code` ON `ydsz_flow_node`(flow_code)
- `idx_ydsz_flow_node_tenant_deleted` ON `ydsz_flow_node`(tenant_id, deleted)

## ydsz_flow_quick_comment
- `idx_ydsz_flow_quick_comment_sort_num` ON `ydsz_flow_quick_comment`(sort_num)
- `idx_ydsz_flow_quick_comment_tenant_deleted` ON `ydsz_flow_quick_comment`(tenant_id, deleted)
- `idx_ydsz_flow_quick_comment_use_count` ON `ydsz_flow_quick_comment`(use_count)
- `idx_ydsz_flow_quick_comment_user_id` ON `ydsz_flow_quick_comment`(user_id)

## ydsz_flow_run_task
- `idx_ydsz_flow_run_task_assignee_id` ON `ydsz_flow_run_task`(assignee_id)
- `idx_ydsz_flow_run_task_business` ON `ydsz_flow_run_task`(business_type, business_id)
- `idx_ydsz_flow_run_task_due_at` ON `ydsz_flow_run_task`(due_at)
- `idx_ydsz_flow_run_task_tenant_deleted` ON `ydsz_flow_run_task`(tenant_id, deleted)

## ydsz_flow_skip
- `idx_ydsz_flow_skip_definition_id` ON `ydsz_flow_skip`(definition_id)
- `idx_ydsz_flow_skip_flow_code` ON `ydsz_flow_skip`(flow_code)
- `idx_ydsz_flow_skip_source_node_code` ON `ydsz_flow_skip`(source_node_code)
- `idx_ydsz_flow_skip_tenant_deleted` ON `ydsz_flow_skip`(tenant_id, deleted)

## ydsz_flow_template
- `idx_ydsz_flow_template_category` ON `ydsz_flow_template`(category)
- `idx_ydsz_flow_template_parent_template_id` ON `ydsz_flow_template`(parent_template_id)
- `idx_ydsz_flow_template_tenant_deleted` ON `ydsz_flow_template`(tenant_id, deleted)

## ydsz_flow_timer
- `idx_ydsz_flow_timer_fire_at` ON `ydsz_flow_timer`(fire_at)
- `idx_ydsz_flow_timer_instance_id` ON `ydsz_flow_timer`(instance_id)
- `idx_ydsz_flow_timer_tenant_deleted` ON `ydsz_flow_timer`(tenant_id, deleted)
- `idx_ydsz_flow_timer_timer_status` ON `ydsz_flow_timer`(timer_status)

## ydsz_flow_user
- `idx_ydsz_flow_user_instance_id` ON `ydsz_flow_user`(instance_id)
- `idx_ydsz_flow_user_tenant_deleted` ON `ydsz_flow_user`(tenant_id, deleted)

## 维护约定
- 表结构变更：同步修改 `V1__baseline.sql` 与 `data/postgre/ydsz-workflow.sql`，不得只改一处。
- 索引新增：高频查询路径（待办列表、SLA 扫描、历史归档）必须提前评估索引，并在实体 Javadoc 中登记。
- 变更评审：PR 涉及 DDL 时，CI 应比对本清单与 data/ 副本是否一致。## 并发防护审计（P0-2，2026-08-26）

| 项 | 结论 |
|---|---|
| 任务完成写库 | 已改走 CAS 条件更新（`completeTask`：`WHERE task_status IN ('PENDING','CLAIMED')`），0 行时跳过重复归档（`FlowTaskArchiveService.completeAndArchive`） |
| 流程推进并发 | `DefaultFlowAdvancer.advance/start` 已有实例级分布式锁 `flow:instance:op:{instanceId}` 兜底（等锁 5s/持锁 60s） |
| 会签计数器 | PARALLEL/WEIGHTED 的 `approveFinished/approveWeight` 采用读-改-写，真并发下存在丢更新风险；建议后续接入 `incrementFinished` SQL 原子自增，并以并发集成测试固化（见 P0-1 单测扩展） |
| 历史表唯一性 | `ydsz_flow_his_task.task_id` 无唯一约束；叠加 CAS 早退后重复归档窗口已极小，若需 DB 级兜底可加 `uk_ydsz_flow_his_task_task_id UNIQUE(task_id)`（存量库需先对账去重） |
