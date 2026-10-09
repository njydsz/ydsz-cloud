-- ============================================================================
-- Flyway Migration: V26.10.09 - 类外键列补索引
-- Date: 2026-10-09
-- Author: ydsz-team
-- ============================================================================
--
-- 背景:
--   为业务表中的外键关联列统一添加索引，提升 JOIN 查询性能、外键校验效率，
--   同时优化等值查询和排序操作。所有索引使用 IF NOT EXISTS 幂等创建，可重复执行。
--   索引命名规则: idx_{表名}_{列名}，符合项目索引命名规范。
--
-- 执行建议:
--   建议在低峰期执行，大表创建索引过程中会短暂加锁，建议在测试环境先验证执行时间。
-- ============================================================================

-- ==================== File 模块外键索引 ====================
-- 1. ydsz_file_file_tag.file_node_id
CREATE INDEX IF NOT EXISTS idx_ydsz_file_file_tag_file_node_id
    ON ydsz_file_file_tag (file_node_id);
COMMENT ON INDEX idx_ydsz_file_file_tag_file_node_id IS '文件标签表外键索引，优化按文件节点查标签的JOIN操作';

-- 2. ydsz_file_file_version.file_node_id
CREATE INDEX IF NOT EXISTS idx_ydsz_file_file_version_file_node_id
    ON ydsz_file_file_version (file_node_id);
COMMENT ON INDEX idx_ydsz_file_file_version_file_node_id IS '文件版本表外键索引，优化按文件节点查历史版本的查询性能';

-- 3. ydsz_file_search_index.file_node_id
CREATE INDEX IF NOT EXISTS idx_ydsz_file_search_index_file_node_id
    ON ydsz_file_search_index (file_node_id);
COMMENT ON INDEX idx_ydsz_file_search_index_file_node_id IS '文件搜索索引表外键索引，优化按文件节点同步搜索数据的操作';

-- ==================== Workflow 历史模块外键索引 ====================
-- 4. ydsz_flow_his_instance.definition_id
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_his_instance_definition_id
    ON ydsz_flow_his_instance (definition_id);
COMMENT ON INDEX idx_ydsz_flow_his_instance_definition_id IS '流程历史实例表外键索引，优化按流程定义查历史实例的查询性能';

-- 5. ydsz_flow_his_task.task_id
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_his_task_task_id
    ON ydsz_flow_his_task (task_id);
COMMENT ON INDEX idx_ydsz_flow_his_task_task_id IS '流程历史任务表外键索引，优化按任务查历史任务记录的查询性能';

-- ==================== Message 模块外键索引 ====================
-- 6. ydsz_msg_preference.user_id
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_preference_user_id
    ON ydsz_msg_preference (user_id);
COMMENT ON INDEX idx_ydsz_msg_preference_user_id IS '消息偏好设置表外键索引，优化按用户查偏好配置的查询性能';

-- 7. ydsz_msg_subscription.user_id
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_subscription_user_id
    ON ydsz_msg_subscription (user_id);
COMMENT ON INDEX idx_ydsz_msg_subscription_user_id IS '消息订阅表外键索引，优化按用户查订阅配置的查询性能';

-- 11. ydsz_msg_log.batch_id
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_batch_id
    ON ydsz_msg_log (batch_id);
COMMENT ON INDEX idx_ydsz_msg_log_batch_id IS '消息发送日志表外键索引，优化按批量任务ID查发送记录的查询性能';

-- ==================== Agent 模块外键索引 ====================
-- 8. ydsz_agt_async_task.user_id
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_async_task_user_id
    ON ydsz_agt_async_task (user_id);
COMMENT ON INDEX idx_ydsz_agt_async_task_user_id IS 'Agent异步任务表外键索引，优化按用户查异步任务的查询性能';

-- 9. ydsz_agt_trace_step.trace_id
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_trace_step_trace_id
    ON ydsz_agt_trace_step (trace_id);
COMMENT ON INDEX idx_ydsz_agt_trace_step_trace_id IS 'Agent链路步骤表外键索引，优化按链路ID查步骤记录的查询性能';

-- 10. ydsz_agt_token_usage.conversation_id
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_token_usage_conversation_id
    ON ydsz_agt_token_usage (conversation_id);
COMMENT ON INDEX idx_ydsz_agt_token_usage_conversation_id IS 'Agent令牌用量表外键索引，优化按会话ID查用量记录的查询性能';

-- ==================== Generator 模块外键索引 ====================
-- 12. ydsz_gen_history.datasource_id
CREATE INDEX IF NOT EXISTS idx_ydsz_gen_history_datasource_id
    ON ydsz_gen_history (datasource_id);
COMMENT ON INDEX idx_ydsz_gen_history_datasource_id IS '代码生成历史表外键索引，优化按数据源查生成历史的查询性能';

-- ==================== Cronjob 模块外键索引 ====================
-- 13. ydsz_job_log.job_id
CREATE INDEX IF NOT EXISTS idx_ydsz_job_log_job_id
    ON ydsz_job_log (job_id);
COMMENT ON INDEX idx_ydsz_job_log_job_id IS '任务执行日志表外键索引，优化按任务ID查执行日志的查询性能';

-- ==================== Rule 模块外键索引 ====================
-- 14. ydsz_rule_dependency.depends_on_rule_code
CREATE INDEX IF NOT EXISTS idx_ydsz_rule_dependency_depends_on_rule_code
    ON ydsz_rule_dependency (depends_on_rule_code);
COMMENT ON INDEX idx_ydsz_rule_dependency_depends_on_rule_code IS '规则依赖表外键索引，优化按依赖规则编码查依赖关系的查询性能';

-- ============================================================================
-- 完成
-- ============================================================================
