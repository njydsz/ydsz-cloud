-- ============================================================
-- PMIS common module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================
-- 本脚本承载全局 PG 扩展 (uuid-ossp / pgcrypto / pg_trgm / vector) 与
--  PL/pgSQL 函数 / 触发器,无业务 DDL。所有业务表按"物理 Mapper 所在后端模块"
-- 归位到 system / userinfo / project / cronjob / message / workflow / agent / literule 各自脚本。
-- 跨服务引用:Feign + NameAssembler,统一在 CommonAutoConfiguration 注册。
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE EXTENSION IF NOT EXISTS vector;

SET client_min_messages = WARNING;

-- Lock down search_path so unqualified table names resolve only
-- to the expected schema. (We use qualified names throughout, but
-- this guards against future contributors adding unqualified DDL.)
SET search_path = public, pg_catalog;

-- Wrap the entire init in one transaction so any failure rolls
-- back cleanly. If the script is already inside a transaction
-- (e.g. a tool-driven init), SAVEPOINTs below still isolate us.
BEGIN;

-- ----------------------------------------------------------------------------
-- [P2-1] DAG 节点类型扩展（CONDITION / LOOP / PARALLEL_GATEWAY）
-- ----------------------------------------------------------------------------
-- DAG 节点定义存储在 pmis_job_dag.dag_definition JSON 字段中（非独立表），
-- 节点类型扩展字段（nodeType / conditionExpression / loopCount / parallelBranches）
-- 直接在 JSON 中管理，无需 ALTER TABLE。
--
-- JSON 节点格式（P2-1 增强后）：
-- {
--   "jobKey": "nodeA",
--   "jobId": "1",
--   "label": "条件判断",
--   "x": 100, "y": 200,
--   "paramsJson": "{}",
--   "nodeType": "CONDITION",            -- TASK(默认) / CONDITION / LOOP / PARALLEL_GATEWAY
--   "conditionExpression": "${nodeA.result=='success'}",  -- CONDITION 节点
--   "loopCount": 3,                     -- LOOP 节点循环次数
--   "parallelBranches": 2               -- PARALLEL_GATEWAY 并行分支数
-- }
--
-- 以下 ALTER 语句用于兼容性（若未来引入独立的 pmis_job_dag_node 表），
-- 当前为 no-op（表不存在时跳过）。
ALTER TABLE IF EXISTS pmis_job_dag_node ADD COLUMN IF NOT EXISTS node_type VARCHAR(32) NOT NULL DEFAULT 'TASK';

ALTER TABLE IF EXISTS pmis_job_dag_node ADD COLUMN IF NOT EXISTS condition_expression VARCHAR(512);

ALTER TABLE IF EXISTS pmis_job_dag_node ADD COLUMN IF NOT EXISTS loop_count INTEGER;

ALTER TABLE IF EXISTS pmis_job_dag_node ADD COLUMN IF NOT EXISTS parallel_branches INTEGER;

EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'ivfflat not available, skipping';

END $$;
EXCEPTION WHEN feature_not_supported THEN
  RAISE NOTICE 'ivfflat index not available (pgvector not installed), skipping';
END $$;

-- =====================================================
-- 2. 人员标签表 pmis_employee_tag (已在 [001] 章节创建, [014_1] 已 ALTER 扩展新字段)
-- =====================================================
-- 注意:历史 [SKIPPED-CLEANUP-REBUILD] 标记下的旧版 DDL 已废弃,字段定义以 [001]+[014_1] 为准
-- 本节保留 COMMENT ON COLUMN 用于覆盖 [001] 的简短注释,提供更详细的字段说明
-- (以下 CREATE TABLE IF NOT EXISTS 因表已存在会被跳过,不会重建)
-- =====================================================
COMMENT ON TABLE  pmis_employee_tag IS '人员标签表: 员工的技能/行业/领域/资质标签,支撑资源推荐智能体匹配';

-- [AUTO-MIGRATION] pmis_employee_tag: rebuild pattern detected.
-- 注: 历史兼容代码 (兼容 V1.0.0_014_1 旧版 [SKIPPED-CLEANUP-REBUILD] 的字段补齐逻辑)
--   已被前面 CREATE TABLE 取代 (IF NOT EXISTS 已包含全部字段),此处保留
--   空 DO 块以保留脚本兼容性,无任何实际效果。
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'pmis_employee_tag') THEN
        -- 字段已由上方 CREATE TABLE IF NOT EXISTS 完整定义,这里无需重复 ALTER
        NULL;
    END IF;
END $$;

-- 注释说明: 上方 CREATE TABLE 已包含 tag_name / proficiency / years_exp / remark / tenant_id / provider_trace_id
-- 字段及其 COMMENT,此处不再重复定义,避免与上方 COMMENT 重复执行。

-- --------------------------------------------------------------------

-- ============================ [014] init pmis admin full perm ============================

-- ====================================================================
-- 9. 初始化菜单权限 + 角色授权 (admin 拥有全部权限)
-- ====================================================================

-- 一. 初始化菜单权限
-- 拆成多步插入：先插入顶层节点（parent_id=0），再插入二级子菜单，
-- 最后插入三级按钮权限。每一步都通过 perm_code 关联父节点。
-- 关键：PostgreSQL 在单条 INSERT VALUES 中，所有子查询都在语句开始时求值，
--       看不到同语句中正在插入的行；因此必须分多语句执行。

-- 步骤 1：插入顶层节点
INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
VALUES
    (0, 'dashboard',  '仪表盘',   'MENU', '/dashboard',  'dashboard/index', 'odometer',  1, 1, 'ENABLED', 0),
    (0, 'system',     '系统管理', 'MENU', '/system',     'Layout',          'setting',   2, 1, 'ENABLED', 0),
    (0, 'business',   '业务管理', 'MENU', '/business',   'Layout',          'briefcase', 3, 1, 'ENABLED', 0),
    (0, 'execution',  '项目执行', 'MENU', '/execution',  'Layout',          'cpu',       4, 1, 'ENABLED', 0),
    (0, 'finance',    '财务收支', 'MENU', '/finance',    'Layout',          'credit-card', 5, 1, 'ENABLED', 0),
    (0, 'report',     '经营报表', 'MENU', '/report',     'Layout',          'data-analysis', 6, 1, 'ENABLED', 0),
    (0, 'ai',         '智能助手', 'MENU', '/ai',         'Layout',          'magic-stick',  7, 1, 'ENABLED', 0)
ON CONFLICT (perm_code, deleted) DO NOTHING;

-- ----------------------------
-- 4. 项目风险预警视图
-- ----------------------------
CREATE OR REPLACE VIEW pmis_view_risk_dashboard
    WITH (security_invoker = true) AS
SELECT tenant_id,
       risk_level,
       COUNT(*) AS cnt
FROM pmis_execution_risk
WHERE deleted = 0 AND status IN ('OPEN','MITIGATING')
GROUP BY tenant_id, risk_level;

COMMENT ON VIEW pmis_view_risk_dashboard IS '项目风险预警视图: 按 tenant_id + risk_level 聚合未关闭风险数,AdvancedReportService#riskDashboard 读取,单租户场景可按 risk_level 过滤';

-- ----------------------------
-- 5. 人效排行（按员工聚合活跃项目数 + 平均 allocation）
-- ----------------------------
CREATE OR REPLACE VIEW pmis_view_employee_utilization
    WITH (security_invoker = true) AS
SELECT tenant_id,
       employee_id,
       COUNT(*) FILTER (WHERE status = 'ACTIVE')                    AS active_count,
       COUNT(*) FILTER (WHERE status IN ('ACTIVE','RESERVED','TRANSFERRING')) AS assigned_count,
       COALESCE(AVG(allocation) FILTER (WHERE status = 'ACTIVE'), 0) AS avg_allocation,
       COALESCE(SUM(allocation) FILTER (WHERE status = 'ACTIVE'), 0) AS total_allocation
FROM pmis_resource_assignment
WHERE deleted = 0
GROUP BY tenant_id, employee_id;

COMMENT ON VIEW pmis_view_employee_utilization IS '人效排行视图: 按 tenant_id + 员工聚合 active_count/assigned_count/avg_allocation,AdvancedReportService#utilizationRank 读取;Feign + try-catch 降级到 0,跨模块故障不阻塞驾驶舱';

-- --------------------------------------------------------------------

-- ============================ [027] init undo log ============================

-- ====================================================================
--  Seata AT 模式 undo_log 表
--  --------------------------------------------------------------------
--  说明：
--    1) AT 模式依赖此表保存 before/after 镜像，用于分支事务回滚
--    2) 必须在每个业务库（pmis / pmis_bill / pmis_archive ...）都建
--    3) 配套 Nacos 配置：data-id = seata-client.properties
--    4) 配套脚本：deploy/seata/verify-seata.sh 会自动检查本表存在
--  --------------------------------------------------------------------
--  版本：V1.0.0_027
--  适用：PostgreSQL 16+
-- ====================================================================

-- ---------- 表结构 ----------
-- id            主键自增
-- branch_id     分支事务 ID（Seata 生成）
-- xid           全局事务 ID（跨服务唯一）
-- context       事务上下文（序列化信息）
-- rollback_info 回滚信息（before/after 镜像 ZIP 压缩）
-- log_status    日志状态 0=正常 1=全局完成 2=全局回滚
-- log_created   创建时间
-- log_modified  最后修改时间
CREATE TABLE IF NOT EXISTS undo_log (
    id            VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    branch_id     VARCHAR(20)       NOT NULL,
    xid           VARCHAR(100) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info BYTEA        NOT NULL,
    log_status    INT          NOT NULL,
    log_created   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    log_modified  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_undo_log UNIQUE (xid, branch_id)
);

-- ---------- 字段注释 ----------
COMMENT ON TABLE  undo_log             IS 'Seata AT 模式分布式事务回滚日志表（每个业务库都需要）';

COMMENT ON COLUMN undo_log.id          IS '主键 ID';

COMMENT ON COLUMN undo_log.branch_id   IS '分支事务 ID（Seata 内部生成）';

COMMENT ON COLUMN undo_log.xid         IS '全局事务 ID（跨服务唯一标识）';

COMMENT ON COLUMN undo_log.context     IS '事务上下文（序列化信息，如应用名、分组等）';

COMMENT ON COLUMN undo_log.rollback_info IS '回滚信息（ZIP 压缩的 before/after 镜像，Base64 编码）';

COMMENT ON COLUMN undo_log.log_status  IS '日志状态：0=正常 1=全局完成 2=全局回滚';

COMMENT ON COLUMN undo_log.log_created IS '创建时间';

COMMENT ON COLUMN undo_log.log_modified IS '最后修改时间';

-- ---------- 性能索引 ----------
-- 建议添加以下索引（百万行级别可显著提升回滚扫描性能）
-- CREATE INDEX IF NOT EXISTS idx_undo_log_xid ON undo_log (xid);
-- CREATE INDEX IF NOT EXISTS idx_undo_log_status_modified ON undo_log (log_status, log_modified);

-- --------------------------------------------------------------------

-- ============================ [028] add flow gap columns ============================

-- =============================================================
-- 工作流引擎对标差距补全 — 新增字段
--
-- GAP-P0: 表单字段权限 (pmis_flow_node.form_fields_config)
-- GAP-P1: SLA 超时配置 (pmis_flow_node.sla_config)
-- GAP-P1: 子流程父子关系 (pmis_flow_instance.parent_instance_id / parent_node_code)
-- GAP-P1: 会签并发版本号 (pmis_flow_run_task.version)
-- =============================================================

-- -------------------------------------------
-- 1. pmis_flow_node 新增字段
-- -------------------------------------------
ALTER TABLE pmis_flow_node ADD COLUMN IF NOT EXISTS form_fields_config TEXT;

-- 更新触发器
CREATE OR REPLACE FUNCTION update_rule_test_case_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_rule_test_case_updated_at ON pmis_rule_test_case;

COMMENT ON CONSTRAINT ck_rule_def_status_valid ON pmis_rule_def IS
    '规则状态合法性约束，配合应用层 RuleStatus.canTransitionTo 状态机校验';

SET statement_timeout = '5min';

-- ============================================================
-- 六、undo_log 性能索引（H1.8）
--   Seata AT 模式回滚按 xid 扫描，无索引会全表扫描
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_undo_log_xid ON undo_log(xid);

CREATE INDEX IF NOT EXISTS idx_undo_log_status_modified ON undo_log(log_status, log_modified);

ANALYZE undo_log;

-- pg_hint_plan: 需 preload 预加载, 未加载时跳过
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_hint_plan;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_hint_plan 不可用, 跳过: %', SQLERRM;
END $$;

-- ----------------------------------------------------------------------------
-- 2) pmis_flow_audit_log 月度分区 (2026-01 ~ 2027-12 共 24 个月)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    i INT;
    partition_date DATE;
    next_date DATE;
    partition_name TEXT;
BEGIN
    FOR i IN 0..23 LOOP
        partition_date := DATE '2026-01-01' + (i || ' month')::INTERVAL;
        next_date := partition_date + INTERVAL '1 month';
        partition_name := 'pmis_flow_audit_log_y' ||
                          TO_CHAR(partition_date, 'YYYY') || 'm' ||
                          TO_CHAR(partition_date, 'MM');
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF pmis_flow_audit_log FOR VALUES FROM (%L) TO (%L)',
            partition_name, partition_date, next_date
        );
    END LOOP;
END $$;

COMMENT ON FUNCTION pmis_set_updated_at() IS
    '通用 updated_at 维护:BEFORE UPDATE 时将 NEW.updated_at 置为 CURRENT_TIMESTAMP;'
    '仅当 NEW 与 OLD 实际不同时触发(避免 no-op UPDATE 引起的批量时间漂移)';

COMMENT ON FUNCTION pmis_attach_updated_at_trigger(TEXT) IS
    '通用挂载函数: 为指定 public 表添加 tg_<table>_updated_at BEFORE UPDATE 触发器;'
    '已挂载 / 表不存在 / 缺 updated_at 列时静默跳过';

-- 用户账号
SELECT pmis_attach_updated_at_trigger('pmis_employee');

-- 员工
SELECT pmis_attach_updated_at_trigger('pmis_department');

-- 部门
SELECT pmis_attach_updated_at_trigger('pmis_position');

-- 岗位
SELECT pmis_attach_updated_at_trigger('pmis_role');

-- 角色
SELECT pmis_attach_updated_at_trigger('pmis_config');

-- 系统配置
SELECT pmis_attach_updated_at_trigger('pmis_dict_item');

-- 字典项
SELECT pmis_attach_updated_at_trigger('pmis_dict_version');

-- 字典版本
SELECT pmis_attach_updated_at_trigger('pmis_project_initiation');

-- 立项
SELECT pmis_attach_updated_at_trigger('pmis_project_change');

-- 变更
SELECT pmis_attach_updated_at_trigger('pmis_finance_contract');

-- 合同
SELECT pmis_attach_updated_at_trigger('pmis_finance_invoice');

-- 发票
SELECT pmis_attach_updated_at_trigger('pmis_finance_payment');

-- 回款
SELECT pmis_attach_updated_at_trigger('pmis_flow_instance');

-- 流程实例
SELECT pmis_attach_updated_at_trigger('pmis_flow_definition');

-- 流程定义

-- ----------------------------------------------------------------------------
-- 3.1) 批量挂载剩余所有含 updated_at 列的 pmis_ 表
--      上方 15 张核心表已显式挂载; 此处用 DO 块动态扫描 information_schema,
--      为所有尚未挂载触发器且含 updated_at 列的 pmis_ 表自动挂载。
--      pmis_attach_updated_at_trigger() 自身幂等: 已挂载 / 表不存在 / 缺
--      updated_at 列时均静默跳过, 故可安全覆盖全部表。
--      覆盖: 规则/成本/利润/EVM/费率/资源/考勤/运维/工单/满意度/对账/
--      利用率/工作流子表/报表/导出/2FA/会话/敏感操作等(约 80+ 张表)。
--      日志表(pmis_operation_log / pmis_flow_audit_log 等)无 updated_at 列,
--      会被辅助函数自动跳过。
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    t_name TEXT;
BEGIN
    FOR t_name IN
        SELECT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema
         AND t.table_name = c.table_name
        WHERE c.table_schema = 'public'
          AND c.column_name = 'updated_at'
          AND t.table_type = 'BASE TABLE'
          AND c.table_name LIKE 'pmis\_%' ESCAPE '\'
          -- 排除分区子表(由父表继承,无需单独挂载)
          AND c.table_name NOT LIKE '%_default'
    LOOP
        PERFORM pmis_attach_updated_at_trigger(t_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;

COMMENT ON INDEX idx_pmis_project_change_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_execution_delivery_standard_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_execution_delivery_item_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_execution_closure_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_agent_prediction_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_finance_invoice_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_finance_payment_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_finance_customer_credit_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_evm_measure_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rate_card_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rate_internal_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_profit_simulation_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_resource_pool_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_employee_tag_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_resource_assignment_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_bench_record_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_warranty_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_ops_ticket_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_satisfaction_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_alert_dispatch_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_reconcile_daily_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_attendance_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_overtime_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_leave_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_definition_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_node_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_skip_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_instance_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_run_task_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_his_task_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_his_instance_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_user_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_cc_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_cc_rule_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_timer_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_delegate_auth_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_delegate_log_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_report_subscription_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_def_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_version_history_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_template_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_test_case_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_execution_trace_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_decision_table_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_event_subscription_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_canary_bucket_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_scorecard_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_decision_tree_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_script_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_variable_def_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_chain_graph_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_dependency_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_ab_policy_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_ab_rollback_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_pack_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_rule_pack_install_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_third_party_account_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_third_party_log_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_dmn_table_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_template_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_auto_trigger_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_notify_channel_trace IS 'P1-7: provider_trace_id 反查';

COMMENT ON INDEX idx_pmis_flow_task_comment_trace IS 'P1-7: provider_trace_id 反查';

-- 1) 初始化一条元数据行
INSERT INTO pmis_meta_schema_version
    (version, pg_version, files_merged, generated_at, applied_at, pending_tables, notes)
VALUES
    ('V1.0.0', '18', 58,
     '2026-07-06 21:00:00+08',
     CURRENT_TIMESTAMP,
     NULL,
     'P1-7: 75/75 provider_trace_id 索引已全量覆盖;'
     'P2-8: 112/112 COMMENT ON TABLE 覆盖率 100%;'
     'P2-9: 引入 pmis_meta_schema_version 元数据表;'
     '历史前向引用表已全部落地(表名重命名后已存在),无 pending 表')
ON CONFLICT (version, deleted) DO NOTHING;

-- 2) 创建通用查询视图(供应用启动时探测当前 schema 版本)
CREATE OR REPLACE VIEW pmis_view_current_schema_version
    WITH (security_invoker = true) AS
SELECT
    version,
    pg_version,
    files_merged,
    generated_at,
    applied_at,
    pending_tables,
    notes
FROM pmis_meta_schema_version
WHERE deleted = 0
ORDER BY applied_at DESC
LIMIT 1;

COMMENT ON VIEW pmis_view_current_schema_version IS
    'P2-9: 当前生效的 schema 版本快照(取 applied_at 最近一条)';

-- 2) 把 P3 任务说明写进 V1.0.0 这次初始化
UPDATE pmis_meta_schema_version
   SET plan_notes = COALESCE(plan_notes, '') ||
        E'\nP3-13 [PERF] 冷热数据分层:' ||
        E'\n  - 目标: pmis_operation_log / pmis_flow_audit_log 月份超过 12 个月的冷分区' ||
        E'\n          ATTACH 到独立 cold tablespace + OSS 归档' ||
        E'\n  - 实施: 引入 pg_partman 扩展(parent table + retention 配置)' ||
        E'\n  - 影响: 表/索引结构不变,仅物理文件搬迁;Java 实体无需调整' ||
        E'\n' ||
        E'\nP3-14 [SEC] 敏感字段加密:' ||
        E'\n  - 目标: pmis_employee.id_card / phone / bank_card 等 7 类敏感字段' ||
        E'\n          落盘前用 SM4 加密(列: <col>_cipher VARCHAR(512))' ||
        E'\n          同步增加 <col>_hash VARCHAR(64) 唯一索引列(支持等值查询)' ||
        E'\n  - 实施: 引入 pgcrypto + 自研 KMS 密钥版本号' ||
        E'\n  - 影响: 字段数翻倍,Java 实体需配套 @SensitiveField 注解 + 加密拦截器' ||
        E'\n' ||
        E'\nP3-15 [AUDIT] OPLOG 字段:' ||
        E'\n  - 目标: pmis_data_export_audit 增 op_log_id (BIGINT) + op_log_type (VARCHAR)' ||
        E'\n          关联到 pmis_operation_log.id,支持"导出行为 → 原始操作"的反查' ||
        E'\n  - 实施: ALTER TABLE ADD COLUMN,新增索引 idx_pmis_data_export_audit_oplog' ||
        E'\n  - 影响: 导出服务实现需在写导出审计时填这两个字段'
   WHERE version = 'V1.0.0' AND deleted = 0;

