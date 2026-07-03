-- ============================================================
-- V1.0.0_056__add_tenant_id_to_base_tables.sql
-- 基础表多租户字段预留 + 关键查询路径复合索引
--
-- H2.1 修复：
--   README 声明"每一张业务表都带 tenant_id"，但 V1.0.0_001 中的
--   17 张核心基础表全部缺失 tenant_id 字段。本次补齐。
--
-- H2.4 修复：
--   实际业务查询几乎都是
--     WHERE tenant_id = ? AND deleted = 0 ORDER BY created_at DESC LIMIT 20
--   单列 tenant_id 索引选择率约等于全表（单租户 90%+ 数据）。
--   对未建复合索引的核心业务表统一补 (tenant_id, created_at DESC) WHERE deleted = 0。
--
-- H2.3 修复：
--   外键关联列的反向查询无索引，补 permission_id / position_id / employee_id
--   / leader_id / sender_id 索引。
--
-- H3.2 修复：
--   逻辑删除字段索引覆盖不全，对 V1.0.0_001 中缺 deleted 索引的表补建。
--
-- 兼容性：
--   - tenant_id 默认值 1，单租户部署不影响数据
--   - 多租户部署后由 TenantLineInnerInterceptor 强制 WHERE tenant_id = ?
--   - 全部使用 IF NOT EXISTS，可重复执行
-- ============================================================

-- ============================================================
-- 一、基础表 tenant_id 字段补齐（H2.1）
-- ============================================================

-- 1. 字典类型
ALTER TABLE pmis_dict_type ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_dict_type_tenant ON pmis_dict_type(tenant_id);

-- 2. 字典项
ALTER TABLE pmis_dict_item ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_dict_item_tenant ON pmis_dict_item(tenant_id);

-- 3. 字典版本
ALTER TABLE pmis_dict_version ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_dict_version_tenant ON pmis_dict_version(tenant_id);

-- 4. 角色
ALTER TABLE pmis_role ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_role_tenant ON pmis_role(tenant_id);

-- 5. 权限
ALTER TABLE pmis_permission ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_permission_tenant ON pmis_permission(tenant_id);

-- 6. 用户-角色关联
ALTER TABLE pmis_user_role ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_user_role_tenant ON pmis_user_role(tenant_id);

-- 7. 角色-权限关联
ALTER TABLE pmis_role_permission ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_role_permission_tenant ON pmis_role_permission(tenant_id);

-- 8. 部门
ALTER TABLE pmis_department ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_department_tenant ON pmis_department(tenant_id);

-- 9. 岗位
ALTER TABLE pmis_position ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_position_tenant ON pmis_position(tenant_id);

-- 10. 职级
ALTER TABLE pmis_job_level ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_job_level_tenant ON pmis_job_level(tenant_id);

-- 11. 职级费率
ALTER TABLE pmis_job_level_rate ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_job_level_rate_tenant ON pmis_job_level_rate(tenant_id);

-- 12. 员工
ALTER TABLE pmis_employee ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_employee_tenant ON pmis_employee(tenant_id);

-- 13. 员工标签
ALTER TABLE pmis_employee_tag ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_emp_tag_tenant ON pmis_employee_tag(tenant_id);

-- 14. 用户账号
ALTER TABLE pmis_user_account ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_user_account_tenant ON pmis_user_account(tenant_id);

-- 15. 通知
ALTER TABLE pmis_notification ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_notification_tenant ON pmis_notification(tenant_id);

-- 16. 配置
ALTER TABLE pmis_config ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_config_tenant ON pmis_config(tenant_id);

-- 17. 操作日志（V1.0.0_008 已含 tenant_id，跳过 ADD COLUMN，仅补索引）
CREATE INDEX IF NOT EXISTS idx_pol_tenant ON pmis_operation_log(tenant_id);

-- ============================================================
-- 二、关键查询路径复合索引（H2.4）
--   覆盖分页查询 WHERE tenant_id = ? AND deleted = 0 ORDER BY created_at DESC
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_dict_type_tenant_created
    ON pmis_dict_type(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_dict_item_tenant_created
    ON pmis_dict_item(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_role_tenant_created
    ON pmis_role(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_department_tenant_created
    ON pmis_department(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_position_tenant_created
    ON pmis_position(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_job_level_tenant_created
    ON pmis_job_level(tenant_id, sort_order) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_employee_tenant_created
    ON pmis_employee(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_user_account_tenant_created
    ON pmis_user_account(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_notification_tenant_created
    ON pmis_notification(tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_config_tenant_created
    ON pmis_config(tenant_id, created_at DESC) WHERE deleted = 0;

-- ============================================================
-- 三、外键关联列反向查询索引（H2.3）
-- ============================================================

-- 角色-权限：按 permission_id 反向查询"该权限被哪些角色引用"
CREATE INDEX IF NOT EXISTS idx_role_permission_perm
    ON pmis_role_permission(permission_id) WHERE deleted = 0;

-- 员工-岗位：按 position_id 查询"该岗位下的员工"
CREATE INDEX IF NOT EXISTS idx_pmis_emp_position
    ON pmis_employee(position_id) WHERE deleted = 0;

-- 用户账号-员工：按 employee_id 反向查询
CREATE INDEX IF NOT EXISTS idx_pmis_user_employee
    ON pmis_user_account(employee_id) WHERE deleted = 0;

-- 部门-负责人：按 leader_id 反向查询
CREATE INDEX IF NOT EXISTS idx_pmis_dept_leader
    ON pmis_department(leader_id) WHERE deleted = 0;

-- 通知-发送人：按 sender_id 查询"我发出的通知"
CREATE INDEX IF NOT EXISTS idx_pmis_notif_sender
    ON pmis_notification(sender_id) WHERE deleted = 0;

-- ============================================================
-- 四、逻辑删除字段索引覆盖（H3.2）
--   对 V1.0.0_001 中未建 deleted 索引的表补建
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_pmis_dict_version_deleted ON pmis_dict_version(deleted);
CREATE INDEX IF NOT EXISTS idx_pmis_role_permission_deleted ON pmis_role_permission(deleted);
CREATE INDEX IF NOT EXISTS idx_pmis_emp_tag_deleted ON pmis_employee_tag(deleted);

-- ============================================================
-- 五、event_outbox 表 tenant_id 索引（H2.5）
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_peo_tenant_status
    ON pmis_event_outbox(tenant_id, status, next_retry_at) WHERE deleted = 0;

-- ============================================================
-- 六、undo_log 性能索引（H1.8）
--   Seata AT 模式回滚按 xid 扫描，无索引会全表扫描
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_undo_log_xid ON undo_log(xid);
CREATE INDEX IF NOT EXISTS idx_undo_log_status_modified ON undo_log(log_status, log_modified);

-- ============================================================
-- 七、补齐遗漏的 10 张业务表 tenant_id 字段
--   首轮扫描漏掉，启用 TenantLineInnerInterceptor 前必须补齐
-- ============================================================

-- 任务执行日志表
ALTER TABLE pmis_job_log ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_job_log_tenant ON pmis_job_log(tenant_id);

-- 商机跟进记录
ALTER TABLE pmis_project_opportunity_follow ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_ppof_tenant ON pmis_project_opportunity_follow(tenant_id);

-- 项目预算明细
ALTER TABLE pmis_project_budget_item ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_ppbi_tenant ON pmis_project_budget_item(tenant_id);

-- 门径评审记录
ALTER TABLE pmis_project_gate_review ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_ppgr_tenant ON pmis_project_gate_review(tenant_id);

-- 报表订阅
ALTER TABLE pmis_report_subscription ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_report_sub_tenant ON pmis_report_subscription(tenant_id);

-- 报表导出记录
ALTER TABLE pmis_report_export_record ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_report_exp_tenant ON pmis_report_export_record(tenant_id);

-- 异步导出记录
ALTER TABLE pmis_export_record ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_export_rec_tenant ON pmis_export_record(tenant_id);

-- 流程历史变量归档表
ALTER TABLE pmis_flow_his_variable ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_flow_his_var_tenant ON pmis_flow_his_variable(tenant_id);

-- 规则模板表（053 漏补）
ALTER TABLE pmis_rule_template ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_rule_template_tenant ON pmis_rule_template(tenant_id);

-- 规则测试用例表（053 漏补）
ALTER TABLE pmis_rule_test_case ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_rule_test_case_tenant ON pmis_rule_test_case(tenant_id);

ANALYZE pmis_dict_type;
ANALYZE pmis_dict_item;
ANALYZE pmis_dict_version;
ANALYZE pmis_role;
ANALYZE pmis_permission;
ANALYZE pmis_user_role;
ANALYZE pmis_role_permission;
ANALYZE pmis_department;
ANALYZE pmis_position;
ANALYZE pmis_job_level;
ANALYZE pmis_job_level_rate;
ANALYZE pmis_employee;
ANALYZE pmis_employee_tag;
ANALYZE pmis_user_account;
ANALYZE pmis_notification;
ANALYZE pmis_config;
ANALYZE pmis_operation_log;
ANALYZE pmis_event_outbox;
ANALYZE undo_log;
ANALYZE pmis_job_log;
ANALYZE pmis_project_opportunity_follow;
ANALYZE pmis_project_budget_item;
ANALYZE pmis_project_gate_review;
ANALYZE pmis_report_subscription;
ANALYZE pmis_report_export_record;
ANALYZE pmis_export_record;
ANALYZE pmis_flow_his_variable;
ANALYZE pmis_rule_template;
ANALYZE pmis_rule_test_case;
