-- ============================================================================
-- V26.09.26: P1 核心表复合索引补齐
-- 解决慢查询：高频 WHERE 条件缺少复合索引导致全表扫描
-- ============================================================================

-- 【message】渠道消息历史查询：按租户+渠道+时间范围
ALTER TABLE ydsz_msg_log
    ADD INDEX idx_msg_tenant_channel_time (tenant_id, channel, created_at);

-- 【cronjob】任务执行历史检索：按租户+任务编码+状态
ALTER TABLE ydsz_cronjob_log
    ADD INDEX idx_cronjob_tenant_code_status (tenant_id, job_code, status);

-- 【nextwiki】目录树懒加载：按租户+父节点+删除标记
ALTER TABLE ydsz_nextwiki_file_node
    ADD INDEX idx_filenode_tenant_parent_deleted (tenant_id, parent_id, is_deleted);

-- 【userinfo】在线用户统计：按租户+用户ID+最后访问时间
ALTER TABLE ydsz_user_session
    ADD INDEX idx_session_tenant_user_access (tenant_id, user_id, last_access_time);

-- 【workflow】流程实例查询优化：按租户+流程定义编码+创建时间
ALTER TABLE ydsz_workflow_instance
    ADD INDEX idx_wfins_tenant_def_time (tenant_id, def_code, created_at);

-- 【literule】规则版本查询：按租户+规则编码+版本号
ALTER TABLE ydsz_literule_version
    ADD INDEX idx_literule_tenant_rule_ver (tenant_id, rule_code, version);
