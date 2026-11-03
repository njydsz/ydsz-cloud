# 数据库索引设计分析

> **生成日期**：2026-10-30
> **生成方式**：由 CatPaw AI 基于 `data/sqls/flyway/` 目录下全部版本脚本静态分析生成
> **覆盖版本**：V26.10.01（基线）+ V26.10.02 \~ V26.10.30（增量脚本）
> **使用方法**：每次 Flyway 版本迭代后，在生产环境用 `EXPLAIN ANALYZE` 验证新增索引命中率；
> 建议每季度用 `pg_stat_user_indexes` 巡检一次，识别零扫描的冷索引。

---

## 索引设计原则（参照 ydsz 编码规范 §数据库设计）

| # | 原则 | 说明 |
|---|------|------|
| 1 | **外键字段须有索引** | 如 `tenant_id`、`agent_id`、`flow_definition_id` 等跨表关联列 |
| 2 | **状态/类型查询字段须有索引** | 如 `status`、`is_deleted`、`step_type` 等高频筛选列 |
| 3 | **时间范围查询字段须有索引** | 如 `created_at`、`operation_time`、`expire_at` 等排序/范围扫描列 |
| 4 | **唯一约束须有唯一索引** | 业务 code 字段（如 `agent_code`、`flow_code`、`role_code`）通过 `UNIQUE` 约束保证 |
| 5 | **联合索引遵循最左前缀原则** | 高频联合查询 `(tenant_id, is_deleted)`、`(tenant_id, status, created_at)` 等 |
| 6 | **高频写入表避免冗余索引** | OutBox / 日志类分区表索引数量受控，按查询模式精确设计 |
| 7 | **部分索引减少体积** | 软删除场景使用 `WHERE is_deleted = 0` 部分唯一索引，加速活跃数据 |

---

## 版本 V26.10.01（基线）— 索引清单

> 基线共包含 **435 条 CREATE INDEX** 语句和 **53 条 UNIQUE 约束**。
> 以下按模块组织，选取每类代表性索引展示；完整清单可通过下方 SQL 提取：
> ```sql
> SELECT indexname, tablename, indexdef
>   FROM pg_indexes
>  WHERE schemaname = 'public' AND tablename LIKE 'ydsz\_%'
>  ORDER BY tablename, indexname;
> ```

### Agent 模块（ydsz_agt\_*）

| 表名 | 索引名 | 类型 | 字段 | 用途 |
|------|--------|------|------|------|
| ydsz_agt_approval | idx_ydsz_agt_approval_approval_conversation | BTREE | conversation_id | 按对话查审批记录 |
| ydsz_agt_approval | idx_ydsz_agt_approval_approval_status | BTREE | status | 审批状态筛选 |
| ydsz_agt_approval | idx_ydsz_agt_approval_approval_tenant | BTREE | tenant_id | 多租户过滤 |
| ydsz_agt_approval | idx_ydsz_agt_approval_approval_trace | BTREE | trace_id | 链路追踪 |
| ydsz_agt_approval | idx_ydsz_agt_approval_tenant_created | BTREE | tenant_id, created_at | 租户时间范围查询 |
| ydsz_agt_approval | idx_ydsz_agt_approval_tenant_deleted | BTREE | tenant_id, is_deleted | 逻辑删除过滤 |
| ydsz_agt_async_task | idx_ydsz_agt_async_task_created_at | BTREE | created_at | 时间排序 |
| ydsz_agt_async_task | idx_ydsz_agt_async_task_expire_at | BTREE | expire_at | 过期任务扫描 |
| ydsz_agt_async_task | idx_ydsz_agt_async_task_status_type | BTREE | status, task_type | 按类型+状态筛选 |
| ydsz_agt_async_task | idx_ydsz_agt_async_task_tenant_status | BTREE | tenant_id, status | 租户任务列表 |
| ydsz_agt_dag_workflow | idx_dag_wf_category | BTREE | category | 分类筛选 |
| ydsz_agt_dag_workflow | idx_dag_wf_code | BTREE | workflow_code | 业务码精确查询 |
| ydsz_agt_dag_workflow | idx_dag_wf_published | BTREE | is_published | 发布状态筛选 |
| ydsz_agt_definition | idx_ydsz_agt_definition_agent_type | BTREE | agent_type | 按类型筛选 |
| ydsz_agt_definition | idx_ydsz_agt_definition_tenant_is_deleted | BTREE | tenant_id, is_deleted | 逻辑删除过滤 |
| ydsz_agt_document_chunk | idx_ydsz_agt_document_chunk_document_id | BTREE | document_id | 文档分块反查 |
| ydsz_agt_document_chunk | idx_ydsz_agt_document_chunk_tenant | BTREE | tenant_id | 多租户过滤 |
| ydsz_agt_insight_report | idx_ydsz_agt_insight_report_status | BTREE | status | 报告状态筛选 |
| ydsz_agt_insight_report | idx_ydsz_agt_insight_report_tenant_deleted | BTREE | tenant_id, is_deleted | 逻辑删除过滤 |
| ydsz_agt_insight_report | idx_ydsz_agt_insight_report_user_id | BTREE | user_id | 用户维度查询 |
| ydsz_agt_prompt_template | idx_ydsz_agt_prompt_template_category | BTREE | category | 分类筛选 |
| ydsz_agt_prompt_template | idx_ydsz_agt_prompt_template_tenant_is_deleted | BTREE | tenant_id, is_deleted | 逻辑删除过滤 |
| ydsz_agt_prompt_version | idx_ydsz_agt_prompt_version_template_code | BTREE | template_code | 按模板查版本 |
| ydsz_agt_token_usage | idx_ydsz_agt_token_usage_conversation_created | BTREE | conversation_id, created_at | 对话维度用量 |
| ydsz_agt_token_usage | idx_ydsz_agt_token_usage_tenant_is_deleted | BTREE | tenant_id, is_deleted | 逻辑删除过滤 |
| ydsz_agt_trace | idx_ydsz_agt_trace_tenant_is_deleted | BTREE | tenant_id, is_deleted | 逻辑删除过滤 |
| ydsz_agt_trace | idx_ydsz_agt_trace_trace_agent | BTREE | agent_id | Agent 维度追踪 |
| ydsz_agt_trace | idx_ydsz_agt_trace_trace_conversation | BTREE | conversation_id | 对话链路查询 |
| ydsz_agt_trace | idx_ydsz_agt_trace_trace_status | BTREE | status | 执行状态筛选 |
| ydsz_agt_trace_step | idx_ydsz_agt_trace_step_tenant | BTREE | tenant_id | 多租户过滤 |
| ydsz_agt_trace_step | idx_ydsz_agt_trace_step_trace_step_cost | BTREE | cost | 成本排序 |
| ydsz_agt_user_profile | idx_ydsz_agt_user_profile_last_interaction_at | BTREE | last_interaction_at DESC | 最近活跃排序 |

#### Agent 模块 UNIQUE 约束

| 表名 | 约束名 | 字段 | 用途 |
|------|--------|------|------|
| ydsz_agt_dag_workflow | ydsz_agt_dag_workflow_workflow_code_key | workflow_code | 业务码唯一 |
| ydsz_agt_definition | uk_ydsz_agt_definition_agent_code | agent_code, tenant_id | 租户内 Agent 编码唯一 |
| ydsz_agt_insight_report | uk_ydsz_agt_insight_report_report_id | report_id | 报告 ID 唯一 |
| ydsz_agt_prompt_template | uk_ydsz_agt_prompt_template_template_code | template_code, tenant_id | 租户内模板编码唯一 |
| ydsz_agt_prompt_version | uk_ydsz_agt_prompt_version_template_version | template_code, version, tenant_id | 模板版本唯一 |

---

### 公共引擎模块（ydsz_comm\_*）

> **高增长表已按月 RANGE 分区**（V26.10.08/V26.10.09），分区表索引在分区键上构建。

| 表名 | 索引名 | 类型 | 字段 | 用途 |
|------|--------|------|------|------|
| ydsz_comm_audit_log | idx_ydsz_comm_audit_log_operation_time | BTREE | operation_time DESC | 审计时间排序 |
| ydsz_comm_audit_log | idx_ydsz_comm_audit_log_operator_id | BTREE | operator_id, operation_time DESC | 操作人维度查询 |
| ydsz_comm_audit_log | idx_ydsz_comm_audit_log_action | BTREE | action, operation_time DESC | 操作类型筛选 |
| ydsz_comm_audit_log | idx_ydsz_comm_audit_log_module_action | BTREE | module, action, operation_time DESC | 模块+操作联合筛选 |
| ydsz_comm_audit_log | idx_ydsz_comm_audit_log_app_key | BTREE | app_key, operation_time DESC | 应用维度查询 |
| ydsz_comm_audit_log | idx_ydsz_comm_audit_log_trace_id | BTREE | trace_id | 链路追踪 |
| ydsz_comm_audit_log | idx_ydsz_comm_audit_log_tenant_time | BTREE | tenant_id, operation_time DESC | 租户时间范围审计 |
| ydsz_comm_audit_log | idx_ydsz_comm_audit_log_status | BTREE | status, operation_time DESC | 状态筛选 |
| ydsz_comm_outbox | idx_ydsz_comm_outbox_pending | BTREE | status, created_at ASC | 待投递任务扫描 |
| ydsz_comm_outbox | idx_ydsz_comm_outbox_retry | BTREE | status, next_retry_at | 重试任务调度 |
| ydsz_comm_outbox | idx_ydsz_comm_outbox_processing | BTREE | status, updated_at | 处理中任务监控 |
| ydsz_comm_outbox | idx_ydsz_comm_outbox_sent_at | BTREE | status, sent_at | 已发送时间线 |
| ydsz_comm_outbox | idx_ydsz_comm_outbox_tenant | BTREE | tenant_id, status | 租户 OutBox 查询 |
| ydsz_comm_outbox | idx_ydsz_comm_outbox_dedup | BTREE | idempotency_key, status | 幂等去重 |
| ydsz_comm_outbox | idx_ydsz_comm_outbox_aggregate | BTREE | aggregate_type, aggregate_id, created_at DESC | 聚合根事件回放 |
| ydsz_comm_outbox_archive | idx_ydsz_comm_outbox_archive_aggregate | BTREE | aggregate_id, created_at | 归档聚合查询 |
| ydsz_comm_outbox_archive | idx_ydsz_comm_outbox_archive_archived_at | BTREE | archived_at | 归档时间排序 |
| ydsz_comm_outbox_archive | idx_ydsz_comm_outbox_archive_created_at | BTREE | created_at | 创建时间排序 |

---

### 文件引擎模块（ydsz_file\_*）

| 表名 | 索引名 | 类型 | 字段 | 用途 |
|------|--------|------|------|------|
| ydsz_file_file_node | idx_filenode_overview_agg | BTREE | created_by, is_deleted, node_type | 仪表盘聚合（V26.10.30 新增） |
| ydsz_file_search_index | idx_search_content_trgm | GIN | content gin_trgm_ops | 内容模糊搜索（V26.10.30） |
| ydsz_file_search_index | idx_search_name_trgm | GIN | name gin_trgm_ops | 文件名模糊搜索（V26.10.30） |

#### 文件引擎 UNIQUE 约束

| 表名 | 约束名 | 字段 | 用途 |
|------|--------|------|------|
| ydsz_file_file_acl | uk_ydsz_file_file_acl_file_grantee | file_node_id, grantee_type, grantee_id | 同文件+被授权者唯一 |
| ydsz_file_file_tag | uk_ydsz_file_file_tag_file_node_tag | file_node_id, tag_id | 文件标签唯一 |
| ydsz_file_file_version | uk_ydsz_file_file_version_file_node_version | file_node_id, version_number | 文件版本号唯一 |
| ydsz_file_search_index | uk_ydsz_file_search_index_file_node_id | file_node_id | 搜索索引与文件一一对应 |
| ydsz_file_share_link | uk_ydsz_file_share_link_share_code | share_code | 分享码唯一 |
| ydsz_file_share_recipient | uk_ydsz_file_share_recipient_share_recipient | share_id, recipient_type, recipient_id | 分享接收者唯一 |
| ydsz_file_space | uk_ydsz_file_space_tenant_name | tenant_id, name | 租户内空间名唯一 |
| ydsz_file_space_member | uk_ydsz_file_space_member_space_user | space_id, user_id | 空间成员唯一 |
| ydsz_file_storage_quota | uk_ydsz_file_storage_quota_scope | scope_type, scope_id | 配额作用域唯一 |
| ydsz_file_tag | uk_ydsz_file_tag_tenant_tag_name | tenant_id, name | 租户内标签名唯一 |
| ydsz_file_user_favorite | uk_ydsz_file_user_favorite_user_node | user_id, node_id | 用户收藏唯一 |
| ydsz_file_user_recent | uk_ydsz_file_user_recent_user_node | user_id, node_id | 最近访问唯一 |

---

### 流程引擎模块（ydsz_flow\_*）

| 表名 | 索引名 | 类型 | 字段 | 来源版本 | 用途 |
|------|--------|------|------|---------|------|
| ydsz_flow_join_token | idx_ydsz_flow_join_token_instance_node | BTREE | instance_id, join_node_code, is_deleted | V26.10.08 | 并行网关节点查询 |
| ydsz_flow_join_token | idx_ydsz_flow_join_token_status | BTREE | join_status, is_deleted | V26.10.08 | 聚合状态过滤 |
| ydsz_flow_join_token | idx_ydsz_flow_join_token_tenant_deleted | BTREE | tenant_id, is_deleted | V26.10.08 | 逻辑删除过滤 |
| ydsz_flow_instance | idx_ydsz_flow_instance_definition_id | BTREE | flow_definition_id, tenant_id, created_at DESC | V26.10.10 | 按定义查实例 |
| ydsz_flow_instance | idx_ydsz_flow_instance_business | BTREE | business_type, business_id | V26.10.10 | 多态关联反查 |
| ydsz_flow_instance | idx_ydsz_flow_instance_status_tenant | BTREE | status, tenant_id, created_at DESC | V26.10.10 | 状态+租户筛选 |
| ydsz_flow_task | idx_ydsz_flow_task_instance_id | BTREE | flow_instance_id, created_at DESC | V26.10.10 | 实例下任务列表 |
| ydsz_flow_task | idx_ydsz_flow_task_status_tenant | BTREE | status, tenant_id, created_at ASC | V26.10.10 | 待办任务查询 |
| ydsz_flow_comment | idx_ydsz_flow_comment_instance_id | BTREE | flow_instance_id, created_at DESC | V26.10.10 | 实例评论列表 |
| ydsz_flow_attachment | idx_ydsz_flow_attachment_instance_id | BTREE | flow_instance_id, is_deleted | V26.10.10 | 实例附件查询 |
| ydsz_flow_definition | idx_ydsz_flow_definition_flow_code | BTREE | flow_code, tenant_id, is_deleted | V26.10.10 | 流程定义编码查询 |

#### 流程引擎 UNIQUE 约束

| 表名 | 约束名 | 字段 | 用途 |
|------|--------|------|------|
| ydsz_flow_admin_role | uk_ydsz_flow_admin_role_user_role | user_id, role_code | 用户角色唯一 |
| ydsz_flow_category | uk_ydsz_flow_category_category_code | category_code, tenant_id | 分类编码唯一 |
| ydsz_flow_definition | uk_ydsz_flow_definition_flow_code_version | flow_code, flow_version, tenant_id | 流程定义版本唯一 |
| ydsz_flow_his_instance | uk_ydsz_flow_his_instance_business_type_id | business_type, business_id | 历史实例业务唯一 |
| ydsz_flow_instance | uk_ydsz_flow_instance_business_type_id | business_type, business_id | 运行实例业务唯一 |
| ydsz_flow_node | uk_ydsz_flow_node_definition_node_code | definition_id, node_code | 流程内节点编码唯一 |
| ydsz_flow_run_task | uk_ydsz_flow_run_task_instance_node_assignee | instance_id, node_code, assignee_id, iter_var | 任务实例唯一（含迭代变量） |
| ydsz_flow_template | uk_ydsz_flow_template_template_code_version | template_code, version, tenant_id | 流程模板版本唯一 |
| ydsz_flow_user_task | uk_ydsz_flow_user_task_user | task_id, user_id, sign_type | 用户任务签名唯一 |

---

### 身份引擎模块（ydsz_idm\_* / ydsz\_idm-account\_*）

#### 身份引擎 UNIQUE 约束

| 表名 | 约束名 | 字段 | 用途 |
|------|--------|------|------|
| ydsz_idm_account_user | uk_ydsz_idm_account_user_username | username | 用户名全局唯一 |
| ydsz_idm_account_user_language | uk_ydsz_idm_account_user_language_language_code | language_code | 语言编码唯一 |
| ydsz_idm_auth_apikey | uk_ydsz_idm_auth_apikey_hash | api_key_hash | API Key 哈希唯一 |
| ydsz_idm_auth_credential | uk_ydsz_idm_auth_credential_credential_id | credential_id | 凭证 ID 唯一 |
| ydsz_idm_auth_policy | uk_ydsz_idm_auth_policy_tenant_id | tenant_id | 租户策略唯一（一租户一条） |
| ydsz_idm_auth_social_account | uk_ydsz_idm_auth_social_account_platform_open_id | platform, open_id | 第三方账号唯一 |
| ydsz_idm_auth_social_client | uk_ydsz_idm_auth_social_client_platform | platform | 社交平台配置唯一 |
| ydsz_idm_identity_oauth2_application | uk_ydsz_idm_identity_oauth2_application_client_id | client_id | OAuth2 Client ID 唯一 |
| ydsz_idm_identity_saml_config | uk_ydsz_idm_identity_saml_config_entity_id | entity_id | SAML 实体 ID 唯一 |
| ydsz_idm_menu | uk_ydsz_idm_menu_menu_code | menu_code | 菜单编码全局唯一 |
| ydsz_idm_org_company | uk_ydsz_idm_org_company_company_code | company_code | 公司编码唯一 |
| ydsz_idm_org_department | uk_ydsz_idm_org_department_dept_code | dept_code | 部门编码唯一 |
| ydsz_idm_post | uk_ydsz_idm_post_post_code | post_code | 岗位编码唯一 |
| ydsz_idm_role | uk_ydsz_idm_role_role_code | role_code, tenant_id | 租户内角色编码唯一 |

---

### 任务引擎模块（ydsz\_job\_*）

#### 任务引擎 UNIQUE 约束

| 表名 | 约束名 | 字段 | 用途 |
|------|--------|------|------|
| ydsz_job_alert_dispatch | uk_ydsz_job_alert_dispatch_ad_alert_code | alert_code | 告警编码唯一 |
| ydsz_job_dag | uk_ydsz_job_dag_dag_key | dag_key, tenant_id | 租户内 DAG 编码唯一 |
| ydsz_job_dag_context | uk_ydsz_job_dag_context_inst_node | dag_instance_id, node_key | DAG 实例节点唯一 |
| ydsz_job_dag_version | uk_ydsz_job_dag_version_dv_dag_version | dag_id, version | DAG 版本唯一 |
| ydsz_job_daily_stats | uk_ydsz_job_daily_stats_jds_job_date | job_id, stats_date | 任务日统计唯一 |
| ydsz_job_glue | uk_ydsz_job_glue_glue_job_version | job_id, version | Glue 任务版本唯一 |
| ydsz_job_history | uk_ydsz_job_history_jh_job_version | job_id, version | 任务历史版本唯一 |
| ydsz_job_log_content | uk_ydsz_job_log_content_jlc_log_line | log_id, line_no | 日志行号唯一 |

---

### 消息引擎模块（ydsz\_msg\_*）

> 消息日志和 OutBox 已在 V26.10.08 按月 RANGE 分区。

| 表名 | 索引名 | 类型 | 字段 | 来源版本 | 用途 |
|------|--------|------|------|---------|------|
| ydsz_msg_log | idx_ydsz_msg_log_batch_id | BTREE | batch_id | V26.10.08 | 批次查询 |
| ydsz_msg_log | idx_ydsz_msg_log_biz | BTREE | biz_type, biz_id | V26.10.08 | 业务实体反查 |
| ydsz_msg_log | idx_ydsz_msg_log_dedup_key | BTREE | dedup_key | V26.10.08 | 幂等去重 |
| ydsz_msg_log | idx_ydsz_msg_log_msg_id | BTREE | msg_id | V26.10.08 | 消息 ID 定位 |
| ydsz_msg_log | idx_ydsz_msg_log_provider_trace_id | BTREE | provider_trace_id | V26.10.08 | 供应商追踪 |
| ydsz_msg_log | idx_ydsz_msg_log_receiver | BTREE | receiver | V26.10.08 | 接收人查询 |
| ydsz_msg_log | idx_ydsz_msg_log_scheduled_at | BTREE | scheduled_at | V26.10.08 | 定时发送扫描 |
| ydsz_msg_log | idx_ydsz_msg_log_status_created | BTREE | status, created_at | V26.10.08 | 状态时间线 |
| ydsz_msg_log | idx_ydsz_msg_log_template_code | BTREE | template_code | V26.10.08 | 模板维度统计 |
| ydsz_msg_log | idx_ydsz_msg_log_tenant_channel_time | BTREE | tenant_id, channel, created_at | V26.10.08 | 租户+渠道时间范围 |
| ydsz_msg_log | idx_ydsz_msg_log_tenant_is_deleted | BTREE | tenant_id, is_deleted | V26.10.08 | 逻辑删除过滤 |
| ydsz_msg_log | idx_ydsz_msg_log_trace_id | BTREE | trace_id | V26.10.08 | 链路追踪 |
| ydsz_msg_outbox | idx_ydsz_msg_outbox_aggregate | BTREE | aggregate_type, aggregate_id | V26.10.08 | 聚合事件查询 |
| ydsz_msg_outbox | idx_ydsz_msg_outbox_status_created | BTREE | status, created_at | V26.10.08 | 待发布扫描 |
| ydsz_msg_outbox | idx_ydsz_msg_outbox_status_published | BTREE | status, published_at | V26.10.08 | 已发布时间线 |
| ydsz_msg_outbox | idx_ydsz_msg_outbox_tenant_status | BTREE | tenant_id, status | V26.10.08 | 租户状态查询 |

---

### 规则引擎（ydsz\_rule\_*） — GIN 模糊搜索索引

| 表名 | 索引名 | 类型 | 字段 | 来源版本 | 用途 |
|------|--------|------|------|---------|------|
| ydsz_rule_def | idx_ydsi_rule_def_rule_code_trgm | GIN | rule_code gin_trgm_ops | V26.10.08 | 规则编码模糊搜索 |
| ydsz_rule_def | idx_ydsi_rule_def_rule_name_trgm | GIN | rule_name gin_trgm_ops | V26.10.08 | 规则名称模糊搜索 |
| ydsz_rule_def | idx_ydsi_rule_def_description_trgm | GIN | description gin_trgm_ops | V26.10.08 | 描述全文搜索 |
| ydsz_rule_def | idx_ydsi_rule_def_condition_expr_trgm | GIN | condition_expression gin_trgm_ops | V26.10.08 | 条件表达式搜索 |
| ydsz_rule_def | idx_ydsi_rule_def_category_trgm | GIN | category gin_trgm_ops | V26.10.08 | 分类模糊搜索 |
| ydsz_rule_def | idx_ydsi_rule_def_category_path_trgm | GIN | category_path gin_trgm_ops | V26.10.08 | 分类路径搜索 |
| ydsz_rule_def | idx_ydsi_rule_def_owner_trgm | GIN | owner gin_trgm_ops | V26.10.08 | 所有者模糊搜索 |

---

### 通用模块（ydsz\_gen\_* 生成器）

| 表名 | 约束名 | 字段 | 用途 |
|------|--------|------|------|
| ydsz_gen_datasource | uk_ydsz_gen_datasource_name | name | 数据源名称唯一 |
| ydsz_gen_template_group | uk_ydsz_gen_template_group_name | name | 模板组名称唯一 |

---

## 增量版本（V26.10.02 \~ V26.10.30）新增索引

### V26.10.06 — 业务码部分唯一索引（软删除兼容）

> **关键设计**：通过 `WHERE is_deleted = 0` 部分唯一索引，允许已删除记录与新记录共存，解决"删除后无法重建同码记录"问题。

| 表名 | 索引名 | 类型 | 字段 | 条件 |
|------|--------|------|------|------|
| ydsz_idm_role | uk_idm_role_code_active | UNIQUE BTREE | role_code, tenant_id | WHERE is_deleted = 0 |
| ydsz_sys_dict_type | uk_sys_dict_type_code_active | UNIQUE BTREE | type_code | WHERE is_deleted = 0 |
| ydsz_sys_dict_item | uk_sys_dict_item_code_active | UNIQUE BTREE | type_code, item_code | WHERE is_deleted = 0 |
| ydsz_flow_definition | uk_flow_definition_code_ver_active | UNIQUE BTREE | flow_code, flow_version, tenant_id | WHERE is_deleted = 0 |
| ydsz_flow_category | uk_flow_category_code_active | UNIQUE BTREE | category_code, tenant_id | WHERE is_deleted = 0 |
| ydsz_msg_template | uk_msg_template_code_active | UNIQUE BTREE | template_code, tenant_id | WHERE is_deleted = 0 |
| ydsz_rule_def | uk_rule_def_code_active | UNIQUE BTREE | rule_code, tenant_id | WHERE is_deleted = 0 |
| ydsz_sys_app_info | uk_sys_app_key_active | UNIQUE BTREE | tenant_id, app_key | WHERE is_deleted = 0 |

### V26.10.08 — Agent 模糊搜索 + 并行网关 + 通用导出

| 表名 | 索引名 | 类型 | 字段 |
|------|--------|------|------|
| ydsz_agt_token_usage | idx_ydsz_agt_token_usage_bot_created | BTREE | bot_id, created_at（部分索引 WHERE bot_id IS NOT NULL）|
| ydsz_flow_join_token\*3 | — | BTREE | 见上表 |
| ydsz_comm_export_task\*4 | — | BTREE | tenant_id+module+status / tenant_id+created_by+created_at / expire_at(部分) / status+created_at |
| ydsz_file_search_index\*4 | idx_ydsi_file_search_index_*_trgm | GIN | name / path / content / tags |

### V26.10.10 — 类外键列补充索引

| 表名 | 索引名 | 字段 |
|------|--------|------|
| ydsz_agt_trace_step | idx_ydsz_agt_trace_step_session_id | session_id, created_at DESC |
| ydsz_agt_trace_step | idx_ydsz_agt_trace_step_agent_id | agent_id, created_at DESC |
| ydsz_agt_chat_message | idx_ydsz_agt_chat_message_session_id | session_id, created_at ASC |
| ydsz_agt_chat_session | idx_ydsz_agt_chat_session_tenant | tenant_id, status, created_at DESC |
| ydsz_agt_async_task | idx_ydsz_agt_async_task_agent_id | agent_id, status, created_at DESC |
| ydsz_agt_prompt_version | idx_ydsz_agt_prompt_version_template_code | template_code, version DESC |
| ydsz_agt_trigger | idx_ydsz_agt_trigger_agent_id | agent_id, status |
| ydsz_agt_skill_definition | idx_ydsz_agt_skill_definition_agent_id | agent_id, is_deleted |
| ydsz_flow_instance | idx_ydsz_flow_instance_definition_id | flow_definition_id, tenant_id, created_at DESC |
| ydsz_flow_task | idx_ydsz_flow_task_instance_id | flow_instance_id, created_at DESC |
| ydsz_flow_comment | idx_ydsz_flow_comment_instance_id | flow_instance_id, created_at DESC |
| ydsz_flow_attachment | idx_ydsz_flow_attachment_instance_id | flow_instance_id, is_deleted |
| ydsz_flow_instance | idx_ydsz_flow_instance_business | business_type, business_id |
| ydsz_flow_definition | idx_ydsz_flow_definition_flow_code | flow_code, tenant_id, is_deleted |
| ydsz_flow_instance | idx_ydsz_flow_instance_status_tenant | status, tenant_id, created_at DESC |
| ydsz_flow_task | idx_ydsz_flow_task_status_tenant | status, tenant_id, created_at ASC |
| ydsz_cron_job | idx_ydsz_cronjob_job_business | business_type, business_id |
| ydsz_cron_job_log | idx_ydsz_cron_job_log_job_id | job_id, created_at DESC |
| ydsz_msg_log | idx_ydsz_msg_log_template_id | template_id, tenant_id, created_at DESC |
| ydsz_msg_log | idx_ydsz_msg_log_channel_id | channel_id, tenant_id, status, created_at DESC |

### V26.10.30 — NextWiki 搜索 + 仪表盘聚合

| 表名 | 索引名 | 类型 | 字段 |
|------|--------|------|------|
| ydsz_file_search_index | idx_search_content_trgm | GIN | content gin_trgm_ops |
| ydsz_file_search_index | idx_search_name_trgm | GIN | name gin_trgm_ops |
| ydsz_file_file_node | idx_filenode_overview_agg | BTREE | created_by, is_deleted, node_type |

---

## 未索引的外键/查询字段（待优化清单）

> 以下字段在业务代码中高频用于 `WHERE` / `JOIN` / `ORDER BY`，但在基线版本中未发现对应索引。
>
> **落地状态（2026-11-03 更新）**：P0 + P1 + 部分 P2 共 14 条已在 `V26.11.03.sql` 落地
> （`CREATE INDEX CONCURRENTLY IF NOT EXISTS`）。下表「实际索引」列标注最终采用的索引名。

| 表名 | 字段 | 查询场景 | 建议索引 | 实际索引 | 优先级 | 状态 |
|------|------|----------|----------|----------|--------|------|
| ydsz_flow_run_task | assignee_id | 待办任务查询（按办理人）| idx_flow_task_assignee | `idx_ydsz_flow_task_assignee` | **P0** | ✅ V26.11.03 |
| ydsz_flow_instance | ~~start_user_id~~ → **initiator_id** | 我发起的实例查询 | idx_flow_instance_starter | `idx_ydsz_flow_instance_starter` | P1 | ✅ V26.11.03（列名已修正）|
| ydsz_flow_run_task | node_code | 节点维度任务查询 | idx_flow_task_node_code | `idx_ydsz_flow_task_node_code` | P1 | ✅ V26.11.03 |
| ydsz_file_file_node | ~~space_id~~（列不存在）→ **path** | 空间下文件列表 / 路径前缀查询 | idx_filenode_space_id | `idx_ydsz_filenode_path` | P1 | ✅ V26.11.03（改为 path 前缀索引）|
| ydsz_file_file_node | ~~parent_node_id~~ → **parent_id** | 目录树子节点查询 | idx_filenode_parent | `idx_ydsz_filenode_parent` | P1 | ✅ V26.11.03（列名已修正）|
| ydsz_comm_audit_log | business_no | 业务编号反查审计链 | idx_comm_audit_log_business_no | `idx_ydsz_comm_audit_log_business_no` | P1 | ✅ V26.11.03 |
| ydsz_comm_outbox | trace_id | 链路追踪 | idx_comm_outbox_trace_id | `idx_ydsz_comm_outbox_trace_id` | P1 | ✅ V26.11.03 |
| ydsz_agt_async_task | user_id | 用户维度查询异步任务 | idx_agt_async_task_user_id | `idx_ydsz_agt_async_task_user_id` | P1 | ✅ V26.11.03 |
| ydsz_agt_trace_step | step_type | 步骤类型筛选（LLM_CALL / TOOL_CALL）| idx_agt_trace_step_type | `idx_ydsz_agt_trace_step_type` | P1 | ✅ V26.11.03 |
| ydsz_msg_log | receiver + status | 按接收人+状态查询消息 | idx_msg_log_receiver_status | `idx_ydsz_msg_log_receiver_status` | P1 | ✅ V26.11.03 |
| ydsz_msg_log | biz_type + biz_id | 业务实体消息反查 | idx_msg_log_biz | `idx_ydsz_msg_log_biz` | P1 | ✅ V26.11.03 |
| ydsz_rule_def | category | 按分类查询规则 | idx_rule_def_category | `idx_ydsz_rule_def_category` | P2 | ✅ V26.11.03 |
| ydsz_comm_outbox | event_type | 事件类型筛选 | idx_comm_outbox_event_type | `idx_ydsz_comm_outbox_event_type` | P2 | ✅ V26.11.03 |
| ydsz_comm_audit_log | cost_time | 慢请求筛选 | idx_comm_audit_log_cost_time | `idx_ydsz_comm_audit_log_cost_time` | P2 | ✅ V26.11.03 |
| ydsz_agt_async_task | worker_id | Worker 节点维度任务监控 | idx_agt_async_task_worker_id | — | P2 | ⏳ 待排期 |
| ydsz_agt_trace_step | session_id | 会话完整调用链 | — | — | — | ✅ 已在 V26.10.10 补建 |
| ydsz_agt_document_chunk | document_id, chunk_index | 按文档+分块序号排序 | idx_chunk_doc_chunk_idx | — | P2 | ⏳ 待排期 |
| ydsz_file_file_node | updated_at | 最近修改排序 | idx_filenode_updated_at | — | P2 | ⏳ 待排期 |
| ydsz_job_main | dag_id | DAG 任务关联查询 | idx_job_main_dag_id | — | P2 | ⏳ 待排期 |
| ydsz_job_task | task_type | 任务类型筛选 | idx_job_task_type | — | P2 | ⏳ 待排期 |
| ydsz_sys_dict_item | tenant_id | 租户级字典查询 | idx_dict_item_tenant | — | P2 | ⏳ 待排期 |
| ydsz_agt_chat_message | session_id | 消息历史查询 | idx_chat_msg_session | — | — | ✅ 已在 V26.10.10 补建 |
| ydsz_flow_join_token | created_at | 过期令牌清理 | idx_join_token_created_at | — | P2 | ⏳ 待排期 |

### 落地时的列名修正说明（2026-11-03）

本节记录「建议索引」与实际表结构的差异，均已逐条核对 `data/sqls/bootstrap/V26.10.01.bootstrap.sql`：

1. **`ydsz_flow_instance.start_user_id` 不存在** —— 真实列为 `initiator_id`（另有 `initiator_name`）。
   索引建在 `(initiator_id, tenant_id, is_deleted)`。
2. **`ydsz_file_file_node.space_id` 不存在** —— 该表通过 `path` 字段做空间/目录定位
   （见 `FileNodeRepositoryImpl.findByPathPrefix` / `batchSoftDeleteByPathPrefix`）。
   索引建在 `(path, tenant_id)`，同时覆盖路径前缀查询。
3. **`ydsz_file_file_node.parent_node_id` 不存在** —— 真实列为 `parent_id`。
   索引建在 `(parent_id, tenant_id, is_deleted)`，覆盖 `findChildren`。
4. **索引名前缀** —— 本清单建议名缺 `ydsz_` 段，实际统一为 `idx_ydsz_*`，与全仓命名一致
   （同时修正了 `V26.10.08.sql` 中 10 处 `idx_ydsi_*` 的笔误，见 P2-3）。

---

## 索引使用状态（应定期用 `pg_stat_user_indexes` 巡检）

```sql
-- ============================================================
-- 索引效率巡检：识别零/低扫描冷索引（> 30 天未使用候选清理）
-- ============================================================
SELECT schemaname,
       tablename,
       indexname,
       idx_scan,
       idx_tup_read,
       idx_tup_fetch,
       pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
  FROM pg_stat_user_indexes
 WHERE schemaname = 'public'
   AND tablename LIKE 'ydsz\_%'
 ORDER BY idx_scan ASC
 LIMIT 50;
```

```sql
-- ============================================================
-- 表级索引覆盖统计：每张表的索引数量与累计大小
-- ============================================================
SELECT tablename,
       COUNT(*)                                       AS index_count,
       pg_size_pretty(SUM(pg_relation_size(indexrelid))) AS total_index_size,
       SUM(idx_scan)                                  AS total_scans
  FROM pg_stat_user_indexes
 WHERE schemaname = 'public'
   AND tablename LIKE 'ydsz\_%'
 GROUP BY tablename
 ORDER BY total_scans ASC;
```

```sql
-- ============================================================
-- 冗余索引检测：同一表上前缀相同的复合索引（可考虑合并）
-- ============================================================
-- 示例：idx_tenant_is_deleted (tenant_id, is_deleted) 与 idx_tenant (tenant_id)
--       前者可覆盖后者查询场景，后者可考虑删除
SELECT tablename, indexname, indexdef
  FROM pg_indexes
 WHERE schemaname = 'public'
   AND tablename LIKE 'ydsz\_%'
 ORDER BY tablename, indexname;
```

```sql
-- ============================================================
-- 缺失索引建议：基于 pg_stat_user_tables 的高 seq_scan 表
-- ============================================================
SELECT relname                    AS tablename,
       seq_scan                   AS full_table_scans,
       seq_tup_read               AS rows_seq_scan,
       idx_scan                  AS index_scans,
       n_live_tup                AS estimated_rows,
       CASE WHEN seq_scan > 0
            THEN round(seq_tup_read::numeric / seq_scan, 1)
            ELSE 0 END            AS avg_rows_per_seq_scan
  FROM pg_stat_user_tables
 WHERE schemaname = 'public'
   AND relname LIKE 'ydsz\_%'
   AND seq_scan > 100
   AND (idx_scan IS NULL OR seq_scan > idx_scan)
 ORDER BY seq_tup_read DESC
 LIMIT 20;
```

---

## 分区表索引维护规范

> V26.10.08 / V26.10.09 已将以下表按月 RANGE 分区：`ydsz_comm_outbox`、`ydsz_comm_audit_log`、`ydsz_msg_log`、`ydsz_msg_outbox`、`ydsz_agt_trace_step`。
> 分区表索引在 PostgreSQL 17 中自动为每个分区创建本地索引（Partition-wise Index）。

```sql
-- ============================================================
-- 分区表专用：查看各分区索引使用情况
-- ============================================================
SELECT parent.relname  AS parent_table,
       child.relname   AS partition_name,
       idx.relname     AS index_name,
       pg_size_pretty(pg_relation_size(child.oid)) AS partition_size
  FROM pg_inherits
  JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
  JOIN pg_class child  ON pg_inherits.inhrelid  = child.oid
  LEFT JOIN pg_index i ON i.indrelid = child.oid
  LEFT JOIN pg_class idx ON idx.oid = i.indexrelid
 WHERE parent.relname IN ('ydsz_comm_outbox', 'ydsz_comm_audi                           t_log',
                          'ydsz_msg_log', 'ydsz_msg_outbox', 'ydsz_agt_trace_step')
 ORDER BY child.relname, idx.relname;
```

---

## 基线参考 SQL：从 `pg_indexes` 全量提取

```sql
-- ============================================================
-- 全库索引 DDL 导出（供 DBA 复核 / git 备份）
-- ============================================================
SELECT indexname, tablename, indexdef
  FROM pg_indexes
 WHERE schemaname = 'public'
   AND tablename LIKE 'ydsz\_%'
 ORDER BY tablename, indexname;
```

### 统计摘要（2026-10-30 基线 + 全增量）

| 维度 | 数量 |
|------|------|
| 基线版本脚本（V26.10.01）CREATE INDEX 语句 | 435 |
| 基线 UNIQUE 约束 | 53 |
| 增量版本（V26.10.02\~V26.10.30）新增索引 | ≈ 80+ |
| GIN trigram 索引（pg_trgm） | 12 |
| 部分索引（WHERE is_deleted = 0） | 8 |
| 按月 RANGE 分区表 | 5 |
| 待优化建议 | 22 |

---

> **维护提示**：
> 1. 每次新建索引后请运行 `ANALYZE <table>` 更新统计信息；
> 2. 大表请务必使用 `CREATE INDEX CONCURRENTLY` 避免锁表（`CONCURRENTLY` 不可在事务块内执行）；
> 3. 删除索引前请先用 `pg_stat_user_indexes` 确认 `idx_scan = 0`；
> 4. 分区表新分区不会自动继承索引，需定期执行 `CREATE INDEX ON ONLY <table>` 或在新建分区后手动创建。
