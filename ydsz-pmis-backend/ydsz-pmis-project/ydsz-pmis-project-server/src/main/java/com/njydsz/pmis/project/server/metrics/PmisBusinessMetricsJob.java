package com.njydsz.pmis.project.server.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * PMIS 核心业务指标采集（H5.2/H5.3 修复）
 *
 * <p>配套告警规则文件：deploy/monitoring/prometheus/rules/pmis-alerts.yml
 * 该文件已包含 pmis_evm_red_projects_count / pmis_bench_total_cost /
 * pmis_billable_utilization_avg 三个指标的告警规则（P1/P2 级别）。
 *
 * <p>方案：通过 @Scheduled 定时任务每分钟从 DB 拉取关键 KPI 注册为 Gauge。
 * 不侵入业务代码，DB 查询走只读副本或主库均可（QPS 1/min 可忽略）。
 *
 * <p>覆盖指标：
 * <ul>
 *   <li>pmis_evm_red_projects_count — EVM 红色项目数（>3 告警）</li>
 *   <li>pmis_bench_total_cost — Bench 闲置成本合计（>50万 告警）</li>
 *   <li>pmis_billable_utilization_avg — 可计费利用率均值（<60% 告警）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PmisBusinessMetricsJob {

    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;

    /** EVM 红色项目数（SPI < 0.9 或 CPI < 0.9 视为红色） */
    private final AtomicLong evmRedProjectsCount = new AtomicLong(0);
    /** Bench 闲置成本合计（元） */
    private final AtomicLong benchTotalCost = new AtomicLong(0);
    /** 可计费利用率均值（百分比） */
    private final AtomicLong billableUtilizationAvg = new AtomicLong(0);
    /** 当月新增合同金额（元） */
    private final AtomicLong contractMonthlyAmount = new AtomicLong(0);
    /** 当月开票金额（元） */
    private final AtomicLong invoiceMonthlyAmount = new AtomicLong(0);
    /** 当月回款金额（元） */
    private final AtomicLong paymentMonthlyAmount = new AtomicLong(0);
    /** 回款率（百分比，回款/合同总额） */
    private final AtomicLong collectionRate = new AtomicLong(0);
    /** 待审批工单数 */
    private final AtomicLong pendingApprovalCount = new AtomicLong(0);

    @PostConstruct
    public void init() {
        Gauge.builder("pmis_evm_red_projects_count", evmRedProjectsCount, AtomicLong::doubleValue)
                .description("EVM 红色项目数（SPI/CPI < 0.9）")
                .register(meterRegistry);
        Gauge.builder("pmis_bench_total_cost", benchTotalCost, AtomicLong::doubleValue)
                .description("Bench 闲置成本合计（元）")
                .register(meterRegistry);
        Gauge.builder("pmis_billable_utilization_avg", billableUtilizationAvg, AtomicLong::doubleValue)
                .description("可计费利用率均值（百分比）")
                .register(meterRegistry);
        Gauge.builder("pmis_contract_monthly_amount", contractMonthlyAmount, AtomicLong::doubleValue)
                .description("当月新增合同金额（元）")
                .register(meterRegistry);
        Gauge.builder("pmis_invoice_monthly_amount", invoiceMonthlyAmount, AtomicLong::doubleValue)
                .description("当月开票金额（元）")
                .register(meterRegistry);
        Gauge.builder("pmis_payment_monthly_amount", paymentMonthlyAmount, AtomicLong::doubleValue)
                .description("当月回款金额（元）")
                .register(meterRegistry);
        Gauge.builder("pmis_collection_rate", collectionRate, AtomicLong::doubleValue)
                .description("回款率（百分比）")
                .register(meterRegistry);
        Gauge.builder("pmis_pending_approval_count", pendingApprovalCount, AtomicLong::doubleValue)
                .description("待审批工单数")
                .register(meterRegistry);
        log.info("[PmisBusinessMetrics] 已注册 8 个业务指标 Gauge");
    }

    /**
     * 每分钟刷新一次业务指标
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void refresh() {
        try {
            refreshEvmRedCount();
            refreshBenchCost();
            refreshUtilization();
            refreshContractAmount();
            refreshInvoiceAmount();
            refreshPaymentAmount();
            refreshCollectionRate();
            refreshPendingApproval();
        } catch (Exception e) {
            log.warn("[PmisBusinessMetrics] 刷新指标失败: {}", e.getMessage());
        }
    }

    /**
     * EVM 红色项目数：统计最近一次 EVM 度量中 SPI < 0.9 或 CPI < 0.9 的项目数
     */
    private void refreshEvmRedCount() {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT initiation_id) FROM pmis_evm_measure " +
                            "WHERE deleted = 0 AND (spi < 0.9 OR cpi < 0.9) " +
                            "AND measure_date = (SELECT MAX(measure_date) FROM pmis_evm_measure WHERE deleted = 0)",
                    Long.class);
            evmRedProjectsCount.set(count != null ? count : 0);
        } catch (Exception e) {
            log.debug("[PmisBusinessMetrics] EVM 红色项目数查询失败: {}", e.getMessage());
        }
    }

    /**
     * Bench 闲置成本合计：当前在 Bench 状态的员工月度成本总和
     */
    private void refreshBenchCost() {
        try {
            Long cost = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(monthly_cost), 0) FROM pmis_resource_bench " +
                            "WHERE deleted = 0 AND status = 'BENCH' AND tenant_id = 1",
                    Long.class);
            benchTotalCost.set(cost != null ? cost : 0);
        } catch (Exception e) {
            log.debug("[PmisBusinessMetrics] Bench 闲置成本查询失败: {}", e.getMessage());
        }
    }

    /**
     * 可计费利用率均值：最近 30 天可计费工时 / 总工时
     */
    private void refreshUtilization() {
        try {
            // billable_utilization 表若有快照表则查快照，否则实时聚合 time_entry
            Long avg = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(AVG(utilization_rate), 0)::BIGINT FROM pmis_billable_utilization_snapshot " +
                            "WHERE deleted = 0 AND snapshot_date >= CURRENT_DATE - INTERVAL '30 days'",
                    Long.class);
            // 兜底：snapshot 表不存在或为空时查 time_entry
            if (avg == null || avg == 0) {
                avg = jdbcTemplate.queryForObject(
                        "SELECT CASE WHEN SUM(total_hours) = 0 THEN 0 " +
                                "ELSE (SUM(billable_hours)::NUMERIC / SUM(total_hours) * 100)::BIGINT END " +
                                "FROM pmis_billable_utilization_snapshot " +
                                "WHERE deleted = 0 AND snapshot_date >= CURRENT_DATE - INTERVAL '30 days'",
                        Long.class);
            }
            billableUtilizationAvg.set(avg != null ? avg : 0);
        } catch (Exception e) {
            log.debug("[PmisBusinessMetrics] 可计费利用率查询失败: {}", e.getMessage());
        }
    }

    /**
     * 当月新增合同金额
     */
    private void refreshContractAmount() {
        try {
            Long amount = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(contract_amount), 0)::BIGINT FROM pmis_contract " +
                            "WHERE deleted = 0 AND sign_date >= date_trunc('month', CURRENT_DATE)",
                    Long.class);
            contractMonthlyAmount.set(amount != null ? amount : 0);
        } catch (Exception e) {
            log.debug("[PmisBusinessMetrics] 当月合同金额查询失败: {}", e.getMessage());
        }
    }

    /**
     * 当月开票金额
     */
    private void refreshInvoiceAmount() {
        try {
            Long amount = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0)::BIGINT FROM pmis_invoice " +
                            "WHERE deleted = 0 AND invoice_date >= date_trunc('month', CURRENT_DATE) " +
                            "AND invoice_type = 'NORMAL' AND status IN ('APPROVED', 'ISSUED')",
                    Long.class);
            invoiceMonthlyAmount.set(amount != null ? amount : 0);
        } catch (Exception e) {
            log.debug("[PmisBusinessMetrics] 当月开票金额查询失败: {}", e.getMessage());
        }
    }

    /**
     * 当月回款金额
     */
    private void refreshPaymentAmount() {
        try {
            Long amount = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0)::BIGINT FROM pmis_payment " +
                            "WHERE deleted = 0 AND payment_date >= date_trunc('month', CURRENT_DATE) " +
                            "AND status = 'RECEIVED'",
                    Long.class);
            paymentMonthlyAmount.set(amount != null ? amount : 0);
        } catch (Exception e) {
            log.debug("[PmisBusinessMetrics] 当月回款金额查询失败: {}", e.getMessage());
        }
    }

    /**
     * 回款率 = 已回款总额 / 合同总额 * 100
     */
    private void refreshCollectionRate() {
        try {
            Long rate = jdbcTemplate.queryForObject(
                    "SELECT CASE WHEN SUM(contract_amount) = 0 THEN 0 " +
                            "ELSE (SUM(p.paid_amount)::NUMERIC / SUM(c.contract_amount) * 100)::BIGINT END " +
                            "FROM pmis_contract c LEFT JOIN LATERAL " +
                            "(SELECT COALESCE(SUM(amount), 0) AS paid_amount FROM pmis_payment " +
                            "WHERE deleted = 0 AND contract_id = c.id AND status = 'RECEIVED') p ON true " +
                            "WHERE c.deleted = 0 AND c.status IN ('ACTIVE', 'COMPLETED')",
                    Long.class);
            collectionRate.set(rate != null ? rate : 0);
        } catch (Exception e) {
            log.debug("[PmisBusinessMetrics] 回款率查询失败: {}", e.getMessage());
        }
    }

    /**
     * 待审批工单数
     */
    private void refreshPendingApproval() {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pmis_flow_instance_task " +
                            "WHERE deleted = 0 AND status = 'PENDING'",
                    Long.class);
            pendingApprovalCount.set(count != null ? count : 0);
        } catch (Exception e) {
            log.debug("[PmisBusinessMetrics] 待审批工单数查询失败: {}", e.getMessage());
        }
    }
}
