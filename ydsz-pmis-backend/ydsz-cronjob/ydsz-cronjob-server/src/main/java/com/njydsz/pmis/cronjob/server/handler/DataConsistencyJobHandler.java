package com.njydsz.cronjob.server.handler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.job.JobHandler;

/**
 * 数据一致性定时校验 Job。
 *
 * <p>每日执行，校验发票总额 vs 回款总额、预算 vs 实际成本等关键财务数据一致性。
 * 差异超阈值自动记录并触发告警。
 *
 * <p>Bean 名称 = {@code dataConsistencyJobHandler}，
 * 在 ydsz_job 表插入记录：handler=dataConsistencyJobHandler。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component("dataConsistencyJobHandler")
public class DataConsistencyJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(DataConsistencyJobHandler.class);

    private final JdbcTemplate jdbcTemplate;

    public DataConsistencyJobHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Object execute(String paramsJson) throws Exception {
        log.info("[DataConsistency] 开始数据一致性校验");
        int issues = 0;

        // 1. 校验发票总额 vs 回款总额
        issues += checkInvoiceVsPayment();

        // 2. 校验预算 vs 实际成本
        issues += checkBudgetVsActualCost();

        // 3. 校验 WBS 进度 vs 工时完成率
        issues += checkWbsProgressVsTimeEntry();

        log.info("[DataConsistency] 校验完成，发现 {} 个不一致项", issues);
        return Map.of("issues", issues, "checkedAt", LocalDateTime.now().toString());
    }

    private int checkInvoiceVsPayment() {
        try {
            String sql = """
                SELECT i.initiation_id,
                       COALESCE(SUM(i.total_amount), 0) AS invoice_total,
                       COALESCE(SUM(p.allocated_amount), 0) AS payment_total
                FROM ydsz_invoice i
                LEFT JOIN ydsz_payment p ON i.initiation_id = p.initiation_id AND p.deleted = 0
                WHERE i.deleted = 0 AND i.status = 'ISSUED'
                GROUP BY i.initiation_id
                HAVING ABS(COALESCE(SUM(i.total_amount), 0) - COALESCE(SUM(p.allocated_amount), 0)) > 0.01
                """;
            List<Map<String, Object>> diffs = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> diff : diffs) {
                log.warn("[DataConsistency] 发票-回款不一致: initiationId={}, invoice={}, payment={}",
                        diff.get("initiation_id"), diff.get("invoice_total"), diff.get("payment_total"));
            }
            return diffs.size();
        } catch (Exception e) {
            log.error("[DataConsistency] 发票-回款校验失败: {}", e.getMessage());
            return 0;
        }
    }

    private int checkBudgetVsActualCost() {
        try {
            String sql = """
                SELECT b.initiation_id,
                       COALESCE(SUM(b.planned_amount), 0) AS budget_total,
                       COALESCE(SUM(e.actual_amount), 0) AS cost_total
                FROM ydsz_budget_item b
                LEFT JOIN ydsz_expense e ON b.initiation_id = e.initiation_id AND e.deleted = 0 AND e.status = 'CONFIRMED'
                WHERE b.deleted = 0
                GROUP BY b.initiation_id
                HAVING COALESCE(SUM(e.actual_amount), 0) > COALESCE(SUM(b.planned_amount), 0)
                """;
            List<Map<String, Object>> diffs = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> diff : diffs) {
                log.warn("[DataConsistency] 预算超支: initiationId={}, budget={}, cost={}",
                        diff.get("initiation_id"), diff.get("budget_total"), diff.get("cost_total"));
            }
            return diffs.size();
        } catch (Exception e) {
            log.error("[DataConsistency] 预算-成本校验失败: {}", e.getMessage());
            return 0;
        }
    }

    private int checkWbsProgressVsTimeEntry() {
        try {
            String sql = """
                SELECT w.id, w.task_name, w.progress,
                       (SELECT COUNT(*) FROM ydsz_time_entry te WHERE te.wbs_task_id = w.id AND te.deleted = 0 AND te.status = 'APPROVED') AS entry_count
                FROM ydsz_wbs_task w
                WHERE w.deleted = 0 AND w.progress = 100
                HAVING (SELECT COUNT(*) FROM ydsz_time_entry te WHERE te.wbs_task_id = w.id AND te.deleted = 0 AND te.status = 'APPROVED') = 0
                """;
            List<Map<String, Object>> diffs = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> diff : diffs) {
                log.warn("[DataConsistency] WBS进度-工时不一致: taskId={}, taskName={}, progress=100, entries=0",
                        diff.get("id"), diff.get("task_name"));
            }
            return diffs.size();
        } catch (Exception e) {
            log.error("[DataConsistency] WBS-工时校验失败: {}", e.getMessage());
            return 0;
        }
    }
}
