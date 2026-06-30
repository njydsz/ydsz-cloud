-- ============================================================
-- V1.0.0_015  经营驾驶舱 + 高级报表  视图脚本
-- ============================================================
-- 说明：为驾驶舱与高级报表提供跨模块聚合视图，避免在 Java 层做
--      多次单表查询。
-- ============================================================

-- ----------------------------
-- 1. 项目收入 + 成本视图（按 initiation × period）
-- ----------------------------
CREATE OR REPLACE VIEW pmis_view_initiation_revenue_cost AS
SELECT i.id              AS initiation_id,
       COALESCE(SUM(r.amount), 0)         AS total_revenue,
       COALESCE(SUM(p.invoiced_amount), 0) AS invoiced_amount,
       COALESCE((SELECT SUM(amount) FROM pmis_execution_revenue r2
                  WHERE r2.initiation_id = i.id AND r2.deleted = 0
                    AND r2.status = 'CONFIRMED'), 0) AS confirmed_revenue,
       COALESCE((SELECT SUM(amount) FROM pmis_execution_cost_allocation
                  WHERE initiation_id = i.id AND deleted = 0), 0) AS labor_cost,
       COALESCE((SELECT SUM(amount) FROM pmis_execution_purchase
                  WHERE initiation_id = i.id AND deleted = 0), 0) AS purchase_cost,
       COALESCE((SELECT SUM(amount) FROM pmis_execution_expense
                  WHERE initiation_id = i.id AND deleted = 0), 0) AS expense_cost
FROM pmis_project_initiation i
         LEFT JOIN pmis_execution_revenue r   ON r.initiation_id = i.id  AND r.deleted = 0
         LEFT JOIN pmis_finance_invoice p     ON p.initiation_id = i.id  AND p.deleted = 0
WHERE i.deleted = 0
GROUP BY i.id;

-- ----------------------------
-- 2. 项目 EVM 预警分布
-- ----------------------------
CREATE OR REPLACE VIEW pmis_view_initiation_evm AS
SELECT initiation_id,
       MAX(alert_level)                              AS top_alert,
       COUNT(*) FILTER (WHERE alert_level = 'RED')    AS red_count,
       COUNT(*) FILTER (WHERE alert_level = 'YELLOW') AS yellow_count,
       COUNT(*) FILTER (WHERE alert_level = 'NORMAL') AS green_count
FROM pmis_evm_measure
WHERE deleted = 0
GROUP BY initiation_id;

-- ----------------------------
-- 3. 经营驾驶舱 KPI 总览视图
-- ----------------------------
CREATE OR REPLACE VIEW pmis_view_cockpit_overview AS
SELECT
    (SELECT COUNT(*) FROM pmis_project_initiation
        WHERE deleted = 0 AND current_stage IN ('APPROVED','IN_PROGRESS')) AS active_projects,
    (SELECT COALESCE(SUM(amount), 0) FROM pmis_finance_invoice
        WHERE deleted = 0 AND status IN ('ISSUED','RED_REVERSED'))            AS total_invoiced,
    (SELECT COALESCE(SUM(allocated_amount), 0) FROM pmis_finance_payment
        WHERE deleted = 0 AND status = 'ALLOCATED')                          AS confirmed_revenue;

-- ----------------------------
-- 4. 项目风险预警视图
-- ----------------------------
CREATE OR REPLACE VIEW pmis_view_risk_dashboard AS
SELECT risk_level,
       COUNT(*) AS cnt
FROM pmis_execution_risk
WHERE deleted = 0 AND status IN ('IDENTIFIED','MITIGATING')
GROUP BY risk_level;

-- ----------------------------
-- 5. 人效排行（按员工聚合活跃项目数 + 平均 allocation）
-- ----------------------------
CREATE OR REPLACE VIEW pmis_view_employee_utilization AS
SELECT employee_id,
       COUNT(*) FILTER (WHERE status = 'ACTIVE')                    AS active_count,
       COUNT(*) FILTER (WHERE status IN ('ACTIVE','RESERVED','TRANSFERRING')) AS assigned_count,
       COALESCE(AVG(allocation) FILTER (WHERE status = 'ACTIVE'), 0) AS avg_allocation,
       COALESCE(SUM(allocation) FILTER (WHERE status = 'ACTIVE'), 0) AS total_allocation
FROM pmis_resource_assignment
WHERE deleted = 0
GROUP BY employee_id;
