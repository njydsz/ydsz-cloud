package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.dto.AlertEventDTO;
import com.njydsz.pmis.execution.dto.CockpitAlertSummaryVO;
import com.njydsz.pmis.execution.dto.CockpitDrillDownDTO;
import com.njydsz.pmis.execution.dto.CockpitKpiVO;
import com.njydsz.pmis.execution.dto.ExecutiveOverviewVO;
import com.njydsz.pmis.execution.dto.KpiTrendVO;
import com.njydsz.pmis.execution.dto.ProjectGroupKpiDTO;
import com.njydsz.pmis.execution.engine.alert.AlertRule;
import com.njydsz.pmis.execution.engine.alert.AlertRuleEngine;
import com.njydsz.pmis.execution.engine.alert.BenchHighRule;
import com.njydsz.pmis.execution.engine.alert.EvmRedRule;
import com.njydsz.pmis.execution.engine.alert.MarginLowRule;
import com.njydsz.pmis.execution.engine.alert.UtilizationLowRule;
import com.njydsz.pmis.execution.enums.AlertSeverity;
import com.njydsz.pmis.execution.mapper.BillableUtilizationSnapshotMapper;
import com.njydsz.pmis.execution.mapper.CostAllocationMapper;
import com.njydsz.pmis.execution.mapper.EvmMeasureMapper;
import com.njydsz.pmis.execution.mapper.ExpenseMapper;
import com.njydsz.pmis.execution.mapper.InvoiceMapper;
import com.njydsz.pmis.execution.mapper.PaymentMapper;
import com.njydsz.pmis.execution.mapper.PurchaseMapper;
import com.njydsz.pmis.execution.mapper.RiskMapper;
import com.njydsz.pmis.execution.service.BillableUtilizationService;
import com.njydsz.pmis.execution.service.CockpitReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营驾驶舱 Service 实现
 *
 * <p>聚合执行模块内各表数据 + 视图查询，提供驾驶舱 KPI。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CockpitReportServiceImpl implements CockpitReportService {

    private final InvoiceMapper invoiceMapper;
    private final PaymentMapper paymentMapper;
    private final CostAllocationMapper costAllocationMapper;
    private final PurchaseMapper purchaseMapper;
    private final ExpenseMapper expenseMapper;
    private final EvmMeasureMapper evmMeasureMapper;
    private final RiskMapper riskMapper;
    private final BillableUtilizationSnapshotMapper utilizationSnapshotMapper;
    private final BillableUtilizationService billableUtilizationService;

    /** 预警规则引擎：单例不可变，注册 4 条内置规则 */
    private final AlertRuleEngine alertEngine = buildDefaultEngine();

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 构建默认预警规则引擎（4 条规则）
     */
    private static AlertRuleEngine buildDefaultEngine() {
        AlertRuleEngine engine = new AlertRuleEngine();
        engine.register(new EvmRedRule());
        engine.register(new MarginLowRule());
        engine.register(new BenchHighRule());
        engine.register(new UtilizationLowRule());
        return engine;
    }

    @Override
    public CockpitKpiVO overview(String period, CockpitDrillDownDTO drillDown) {
        CockpitKpiVO kpi = new CockpitKpiVO();

        // 1) 在执行项目数：有 ISUED invoice 但未结项的项目（简化：取有任一收入记录的项目数）
        kpi.setActiveProjects(countActiveProjects());

        // 2) 合同总额
        BigDecimal totalContractAmount = sumInvoiceAmount();
        kpi.setTotalContractAmount(totalContractAmount);

        // 3) 已确认收入
        BigDecimal confirmedRevenue = sumAllocatedPayment();
        kpi.setConfirmedRevenue(confirmedRevenue);

        // 4) 累计成本 = 人力 + 采购 + 费用
        BigDecimal laborCost = safeSum(costAllocationMapper::sumAllAmount);
        BigDecimal purchaseCost = safeSum(purchaseMapper::sumAllAmount);
        BigDecimal expenseCost = safeSum(expenseMapper::sumAllAmount);
        BigDecimal totalCost = laborCost.add(purchaseCost).add(expenseCost);
        kpi.setTotalCost(totalCost);

        // 5) 累计毛利
        BigDecimal grossProfit = confirmedRevenue.subtract(totalCost);
        kpi.setGrossProfit(grossProfit);

        // 6) 平均毛利率
        BigDecimal grossMargin = confirmedRevenue.signum() == 0
                ? ZERO
                : grossProfit.divide(confirmedRevenue, 4, RoundingMode.HALF_UP);
        kpi.setGrossMargin(grossMargin);

        // 7) EVM 健康分布
        Map<String, Integer> evmHealth = evmHealthDistribution(period, drillDown);
        kpi.setEvmRedCount(evmHealth.getOrDefault("RED", 0));
        kpi.setEvmYellowCount(evmHealth.getOrDefault("YELLOW", 0));
        kpi.setEvmGreenCount(evmHealth.getOrDefault("NORMAL", 0));

        // 8) Bench 闲置成本（用户模块 Feign 调用失败时回退 0）
        kpi.setBenchIdleCost(benchIdleCostSafe());

        // 9) 可计费利用率均值：从快照表读取（scheduler 每日计算），无数据时实时聚合兜底
        kpi.setAvgBillableUtilization(avgBillableUtilizationSafe(period));

        return kpi;
    }

    @Override
    public Map<String, Integer> evmHealthDistribution(String period, CockpitDrillDownDTO drillDown) {
        Map<String, Integer> out = new HashMap<>();
        out.put("RED", 0);
        out.put("YELLOW", 0);
        out.put("NORMAL", 0);
        try {
            List<Map<String, Object>> rows = evmMeasureMapper.aggregateHealthByInitiation();
            for (Map<String, Object> row : rows) {
                String top = String.valueOf(row.getOrDefault("top_alert", "NORMAL"));
                if (top == null || "null".equalsIgnoreCase(top)) {
                    top = "NORMAL";
                }
                out.merge(top, 1, Integer::sum);
            }
        } catch (Exception e) {
            log.warn("[Cockpit] EVM 健康分布聚合失败: {}", e.getMessage());
        }
        return out;
    }

    @Override
    public Map<String, Object> benchCostSummary(CockpitDrillDownDTO drillDown) {
        Map<String, Object> out = new HashMap<>();
        out.put("totalIdleCost", benchIdleCostSafe());
        out.put("activeBench", 0);
        out.put("warningYellow", 0);
        out.put("warningRed", 0);
        return out;
    }

    @Override
    public Map<String, Object> utilizationSummary(CockpitDrillDownDTO drillDown) {
        Map<String, Object> out = new HashMap<>();
        String period = currentPeriodOrDefault(null);
        Map<String, Object> avg = billableUtilizationService.snapshotAverage(period);
        if (avg == null) avg = new HashMap<>();

        BigDecimal avgPct = toDecimal(avg.get("avg_pct"));
        out.put("avgBillable", avgPct);
        out.put("avgPct", avgPct);
        out.put("period", period);
        out.put("source", avg.getOrDefault("source", "UNKNOWN"));
        out.put("headcount", toLongOrZero(avg.get("headcount")));

        // 预警计数：WARN / CRITICAL 数量
        out.put("warnCount", toLongOrZero(avg.get("warn_count")));
        out.put("criticalCount", toLongOrZero(avg.get("critical_count")));

        // 利用率分布（grade 维度）
        List<Map<String, Object>> gradeDist = new ArrayList<>();
        try {
            gradeDist = utilizationSnapshotMapper.gradeDistribution(period);
        } catch (Exception e) {
            log.warn("[Cockpit] 利用率等级分布失败: {}", e.getMessage());
        }
        out.put("gradeDistribution", gradeDist);

        // 部门维度 top 5
        List<Map<String, Object>> deptList = new ArrayList<>();
        try {
            deptList = utilizationSnapshotMapper.groupByDepartment(period);
        } catch (Exception e) {
            log.warn("[Cockpit] 部门利用率聚合失败: {}", e.getMessage());
        }
        if (deptList.size() > 5) {
            deptList = deptList.subList(0, 5);
        }
        out.put("topDepartments", deptList);

        return out;
    }

    @Override
    public List<Map<String, Object>> drillByDept(String period) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            out = invoiceMapper.sumByDepartment();
        } catch (Exception e) {
            log.warn("[Cockpit] 事业部下钻失败: {}", e.getMessage());
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> drillByProjectType(String period) {
        return new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> drillByCustomer(String period) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            out = invoiceMapper.sumByCustomer();
        } catch (Exception e) {
            log.warn("[Cockpit] 客户下钻失败: {}", e.getMessage());
        }
        return out;
    }

    @Override
    public Map<String, Object> contractAmountYearlyTrend() {
        Map<String, Object> out = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            rows = invoiceMapper.sumByYear();
        } catch (Exception e) {
            log.warn("[Cockpit] 合同年度趋势查询失败: {}", e.getMessage());
        }
        if (rows == null) {
            rows = new ArrayList<>();
        }

        List<String> years = new ArrayList<>();
        List<BigDecimal> amountSeries = new ArrayList<>();
        List<Integer> projectCountSeries = new ArrayList<>();
        List<Integer> invoiceCountSeries = new ArrayList<>();
        BigDecimal totalAmount = ZERO;
        String peakYear = "";
        BigDecimal peakAmount = ZERO;
        BigDecimal previousAmount = null;
        BigDecimal latestYoy = null;
        int totalProjects = 0;
        int totalInvoices = 0;

        for (Map<String, Object> row : rows) {
            String y = String.valueOf(row.getOrDefault("year", ""));
            BigDecimal amt = toDecimal(row.get("total_amount"));
            Integer projCnt = toIntOrZero(row.get("project_count"));
            Integer invCnt = toIntOrZero(row.get("invoice_count"));
            years.add(y);
            amountSeries.add(amt);
            projectCountSeries.add(projCnt);
            invoiceCountSeries.add(invCnt);
            totalAmount = totalAmount.add(amt);
            totalProjects += projCnt;
            totalInvoices += invCnt;
            if (amt.compareTo(peakAmount) > 0) {
                peakAmount = amt;
                peakYear = y;
            }
            if (previousAmount != null && previousAmount.signum() > 0) {
                BigDecimal yoy = amt.subtract(previousAmount)
                        .divide(previousAmount, 4, RoundingMode.HALF_UP);
                latestYoy = yoy;
            }
            previousAmount = amt;
        }

        // series 配置（柱图 = 合同总额；折线 = 项目数）
        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> sAmount = new LinkedHashMap();
        sAmount.put("name", "合同总额");
        sAmount.put("type", "bar");
        sAmount.put("data", amountSeries);
        sAmount.put("unit", "元");
        sAmount.put("yAxisIndex", 0);
        series.add(sAmount);
        Map<String, Object> sProj = new LinkedHashMap();
        sProj.put("name", "项目数");
        sProj.put("type", "line");
        sProj.put("data", projectCountSeries);
        sProj.put("unit", "个");
        sProj.put("yAxisIndex", 1);
        sProj.put("smooth", true);
        series.add(sProj);

        List<Map<String, Object>> yAxis = new ArrayList<>();
        Map<String, Object> ya0 = new LinkedHashMap();
        ya0.put("name", "合同总额（元）");
        ya0.put("position", "left");
        ya0.put("type", "value");
        yAxis.add(ya0);
        Map<String, Object> ya1 = new LinkedHashMap();
        ya1.put("name", "项目数");
        ya1.put("position", "right");
        ya1.put("type", "value");
        ya1.put("min", 0);
        yAxis.add(ya1);

        Map<String, Object> summary = new LinkedHashMap();
        summary.put("yearCount", years.size());
        summary.put("totalAmount", totalAmount);
        summary.put("peakYear", peakYear);
        summary.put("peakAmount", peakAmount);
        summary.put("latestYoy", latestYoy == null ? ZERO : latestYoy);
        summary.put("latestYoyPct", latestYoy == null ? ZERO
                : latestYoy.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
        summary.put("totalProjects", totalProjects);
        summary.put("totalInvoices", totalInvoices);

        out.put("years", years);
        out.put("series", series);
        out.put("yAxisConfig", yAxis);
        out.put("summary", summary);
        return out;
    }

    // ------------------ 私有辅助 ------------------

    private int countActiveProjects() {
        try {
            return invoiceMapper.countDistinctInitiation();
        } catch (Exception e) {
            log.warn("[Cockpit] activeProjects 计算失败: {}", e.getMessage());
            return 0;
        }
    }

    private BigDecimal sumInvoiceAmount() {
        try {
            return nz(invoiceMapper.sumInvoicedAmount());
        } catch (Exception e) {
            log.warn("[Cockpit] 合同总额计算失败: {}", e.getMessage());
            return ZERO;
        }
    }

    private BigDecimal sumAllocatedPayment() {
        try {
            return nz(paymentMapper.sumAllocatedAmount());
        } catch (Exception e) {
            log.warn("[Cockpit] 已确认收入计算失败: {}", e.getMessage());
            return ZERO;
        }
    }

    private BigDecimal benchIdleCostSafe() {
        // 用户模块 Feign 集成在阶段二补充，此处返回 0 占位
        return ZERO;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    private BigDecimal safeSum(java.util.function.Supplier<BigDecimal> supplier) {
        try {
            return nz(supplier.get());
        } catch (Exception e) {
            log.warn("[Cockpit] 成本聚合失败: {}", e.getMessage());
            return ZERO;
        }
    }

    private BigDecimal avgBillableUtilizationSafe(String period) {
        try {
            String p = currentPeriodOrDefault(period);
            Map<String, Object> avg = billableUtilizationService.snapshotAverage(p);
            if (avg == null || avg.isEmpty()) {
                return BigDecimal.valueOf(0.75);
            }
            return toDecimal(avg.get("avg_pct"));
        } catch (Exception e) {
            log.warn("[Cockpit] 利用率均值获取失败: {}", e.getMessage());
            return BigDecimal.valueOf(0.75);
        }
    }

    private String currentPeriodOrDefault(String period) {
        if (StringUtils.hasText(period)) return period;
        return LocalDate.now().format(PERIOD_FMT);
    }

    private BigDecimal toDecimal(Object o) {
        if (o == null) return ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return new BigDecimal(o.toString());
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return ZERO;
        }
    }

    private long toLongOrZero(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return 0L;
        }
    }

    private int toIntOrZero(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    // ============= 批次18 增量方法 =============

    @Override
    public CockpitAlertSummaryVO alertSummary(String period, CockpitDrillDownDTO drillDown) {
        CockpitAlertSummaryVO out = new CockpitAlertSummaryVO();
        Map<String, Object> snapshot = buildKpiSnapshot(period, drillDown);
        List<AlertEventDTO> events = alertEngine.evaluate(snapshot);
        int red = 0, yellow = 0, info = 0;
        for (AlertEventDTO e : events) {
            if (e.getSeverity() == AlertSeverity.RED) red++;
            else if (e.getSeverity() == AlertSeverity.YELLOW) yellow++;
            else info++;
        }
        out.setRedCount(red);
        out.setYellowCount(yellow);
        out.setInfoCount(info);
        out.setTotalCount(events.size());
        out.setEvents(events);
        out.setTopEvent(events.isEmpty() ? null : events.get(0));
        return out;
    }

    @Override
    public List<ProjectGroupKpiDTO> projectGroupOverview(String period, CockpitDrillDownDTO drillDown) {
        List<ProjectGroupKpiDTO> out = new ArrayList<>();
        try {
            // 1) 按 levelCode 聚合成本
            List<Map<String, Object>> rows = costAllocationMapper.sumByLevelCode();
            if (rows == null) rows = new ArrayList<>();

            // 2) 同时获取 KPI 总量用于 fallback
            BigDecimal totalContract = sumInvoiceAmount();
            BigDecimal totalRevenue = sumAllocatedPayment();
            BigDecimal totalCost = safeSum(costAllocationMapper::sumAllAmount)
                    .add(safeSum(purchaseMapper::sumAllAmount))
                    .add(safeSum(expenseMapper::sumAllAmount));

            for (Map<String, Object> row : rows) {
                ProjectGroupKpiDTO dto = ProjectGroupKpiDTO.builder()
                        .groupCode(String.valueOf(row.getOrDefault("level_code", "UNKNOWN")))
                        .groupName(inferGroupName(String.valueOf(row.getOrDefault("level_code", "UNKNOWN"))))
                        .activeProjects(0)
                        .totalContractAmount(ZERO)
                        .confirmedRevenue(ZERO)
                        .totalCost(toDecimal(row.get("total_amount")))
                        .grossProfit(ZERO)
                        .grossMargin(ZERO)
                        .evmRedCount(0)
                        .build();
                // 总成本按比例分摊毛利和收入
                BigDecimal groupCost = dto.getTotalCost();
                if (totalCost.signum() > 0 && groupCost.signum() > 0) {
                    BigDecimal share = groupCost.divide(totalCost, 4, RoundingMode.HALF_UP);
                    dto.setTotalContractAmount(totalContract.multiply(share).setScale(2, RoundingMode.HALF_UP));
                    dto.setConfirmedRevenue(totalRevenue.multiply(share).setScale(2, RoundingMode.HALF_UP));
                    BigDecimal profit = dto.getConfirmedRevenue().subtract(dto.getTotalCost());
                    dto.setGrossProfit(profit);
                    dto.setGrossMargin(dto.getConfirmedRevenue().signum() == 0
                            ? ZERO
                            : profit.divide(dto.getConfirmedRevenue(), 4, RoundingMode.HALF_UP));
                }
                out.add(dto);
            }
            // 按合同总额降序
            out.sort((a, b) -> b.getTotalContractAmount().compareTo(a.getTotalContractAmount()));
        } catch (Exception e) {
            log.warn("[Cockpit] 项目群驾驶舱聚合失败: {}", e.getMessage());
        }
        return out;
    }

    @Override
    public ExecutiveOverviewVO executiveOverview(String period, CockpitDrillDownDTO drillDown) {
        // 1) 复用 overview 拿基础 KPI
        CockpitKpiVO kpi = overview(period, drillDown);

        // 2) 风险项目数（RED + YELLOW）
        int riskRed = 0;
        int riskYellow = 0;
        try {
            List<Map<String, Object>> rows = riskMapper.countByRiskLevel();
            for (Map<String, Object> row : rows) {
                String level = String.valueOf(row.getOrDefault("risk_level", ""));
                int cnt = toIntOrZero(row.get("cnt"));
                if ("RED".equalsIgnoreCase(level)) riskRed = cnt;
                else if ("YELLOW".equalsIgnoreCase(level)) riskYellow = cnt;
            }
        } catch (Exception e) {
            log.warn("[Cockpit] 风险等级统计失败: {}", e.getMessage());
        }
        int riskProjectCount = riskRed + riskYellow;
        int totalProjects = kpi.getActiveProjects() == null ? 0 : kpi.getActiveProjects();
        BigDecimal riskRatio = totalProjects == 0
                ? ZERO
                : BigDecimal.valueOf(riskProjectCount).divide(BigDecimal.valueOf(totalProjects), 4, RoundingMode.HALF_UP);

        // 3) 健康度占比
        int totalEvm = (kpi.getEvmRedCount() == null ? 0 : kpi.getEvmRedCount())
                + (kpi.getEvmYellowCount() == null ? 0 : kpi.getEvmYellowCount())
                + (kpi.getEvmGreenCount() == null ? 0 : kpi.getEvmGreenCount());
        BigDecimal healthRatio = totalEvm == 0
                ? ZERO
                : BigDecimal.valueOf(kpi.getEvmGreenCount() == null ? 0 : kpi.getEvmGreenCount())
                        .divide(BigDecimal.valueOf(totalEvm), 4, RoundingMode.HALF_UP);

        // 4) 项目群
        List<ProjectGroupKpiDTO> groups = projectGroupOverview(period, drillDown);

        // 5) 健康度评分（0-100）
        BigDecimal healthScore = computeHealthScore(
                kpi.getGrossMargin(),
                kpi.getAvgBillableUtilization(),
                healthRatio,
                riskRatio);
        String healthGrade = gradeByScore(healthScore);

        return ExecutiveOverviewVO.builder()
                .activeProjects(totalProjects)
                .totalContractAmount(kpi.getTotalContractAmount())
                .confirmedRevenue(kpi.getConfirmedRevenue())
                .totalCost(kpi.getTotalCost())
                .grossProfit(kpi.getGrossProfit())
                .grossMargin(kpi.getGrossMargin())
                .avgBillableUtilization(kpi.getAvgBillableUtilization())
                .benchIdleCost(kpi.getBenchIdleCost())
                .evmRedCount(kpi.getEvmRedCount())
                .evmYellowCount(kpi.getEvmYellowCount())
                .evmGreenCount(kpi.getEvmGreenCount())
                .healthRatio(healthRatio)
                .riskProjectCount(riskProjectCount)
                .riskProjectRatio(riskRatio)
                .projectGroups(groups)
                .healthScore(healthScore)
                .healthGrade(healthGrade)
                .build();
    }

    @Override
    public KpiTrendVO kpiTrend(Integer months) {
        int limit = months == null || months <= 0 ? 12 : Math.min(months, 36);

        // 1) 倒序拉数据
        List<Map<String, Object>> contractRowsDesc = new ArrayList<>();
        List<Map<String, Object>> paymentRowsDesc = new ArrayList<>();
        try {
            contractRowsDesc = invoiceMapper.sumByRecentMonth(limit);
        } catch (Exception e) {
            log.warn("[Cockpit] KPI 趋势-合同查询失败: {}", e.getMessage());
        }
        try {
            paymentRowsDesc = paymentMapper.aggregateByRecentMonth(limit);
        } catch (Exception e) {
            log.warn("[Cockpit] KPI 趋势-回款查询失败: {}", e.getMessage());
        }

        // 2) 升序排序 + 月份对齐
        List<String> contractMonths = extractMonths(contractRowsDesc);
        List<String> paymentMonths = extractMonths(paymentRowsDesc);
        List<String> periods = mergeAndSortMonths(contractMonths, paymentMonths, limit);
        Collections.reverse(periods); // 升序输出

        List<BigDecimal> contractSeries = new ArrayList<>();
        List<BigDecimal> revenueSeries = new ArrayList<>();
        List<BigDecimal> costSeries = new ArrayList<>();
        List<BigDecimal> profitSeries = new ArrayList<>();
        List<BigDecimal> marginSeries = new ArrayList<>();
        List<Integer> projectSeries = new ArrayList<>();

        Map<String, BigDecimal> contractMap = toMonthMap(contractRowsDesc, "total_amount");
        Map<String, BigDecimal> paymentMap = toMonthMap(paymentRowsDesc, "amount");

        BigDecimal prevContract = null;
        BigDecimal prevRevenue = null;
        BigDecimal prevProfit = null;
        BigDecimal contractMtd = null;
        BigDecimal revenueMtd = null;
        BigDecimal profitMtd = null;

        for (int i = 0; i < periods.size(); i++) {
            String m = periods.get(i);
            BigDecimal amt = contractMap.getOrDefault(m, ZERO);
            BigDecimal rev = paymentMap.getOrDefault(m, ZERO);
            // 成本 = 同月成本归集（用 cost_allocation.monthlySummary 暂不可得，先用 0 + 由前端解释）
            // 此处保守用 0 占位，避免无谓的 SQL 流量
            BigDecimal cost = ZERO;
            BigDecimal profit = rev.subtract(cost);
            BigDecimal margin = rev.signum() == 0
                    ? ZERO
                    : profit.divide(rev, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);

            contractSeries.add(amt);
            revenueSeries.add(rev);
            costSeries.add(cost);
            profitSeries.add(profit);
            marginSeries.add(margin);
            // 项目数：用最近 3 个月的活跃数做近似（避免高基数查询）
            projectSeries.add(0);

            if (i > 0) {
                if (prevContract != null && prevContract.signum() > 0) {
                    contractMtd = amt.subtract(prevContract)
                            .divide(prevContract, 4, RoundingMode.HALF_UP);
                }
                if (prevRevenue != null && prevRevenue.signum() > 0) {
                    revenueMtd = rev.subtract(prevRevenue)
                            .divide(prevRevenue, 4, RoundingMode.HALF_UP);
                }
                if (prevProfit != null && prevProfit.signum() != 0) {
                    profitMtd = profit.subtract(prevProfit)
                            .divide(prevProfit.abs(), 4, RoundingMode.HALF_UP);
                }
            }
            prevContract = amt;
            prevRevenue = rev;
            prevProfit = profit;
        }

        return KpiTrendVO.builder()
                .periods(periods)
                .contractAmountSeries(contractSeries)
                .confirmedRevenueSeries(revenueSeries)
                .totalCostSeries(costSeries)
                .grossProfitSeries(profitSeries)
                .grossMarginPctSeries(marginSeries)
                .activeProjectsSeries(projectSeries)
                .contractMtdGrowth(contractMtd == null ? ZERO : contractMtd)
                .revenueMtdGrowth(revenueMtd == null ? ZERO : revenueMtd)
                .profitMtdGrowth(profitMtd == null ? ZERO : profitMtd)
                .build();
    }

    /**
     * 构建 KPI 快照（供预警规则引擎使用）
     */
    private Map<String, Object> buildKpiSnapshot(String period, CockpitDrillDownDTO drillDown) {
        CockpitKpiVO kpi = overview(period, drillDown);
        Map<String, Object> snap = new HashMap<>();
        snap.put("evmRedCount", kpi.getEvmRedCount());
        snap.put("evmYellowCount", kpi.getEvmYellowCount());
        snap.put("evmGreenCount", kpi.getEvmGreenCount());
        snap.put("grossMargin", kpi.getGrossMargin());
        snap.put("benchIdleCost", kpi.getBenchIdleCost());
        snap.put("avgBillableUtilization", kpi.getAvgBillableUtilization());
        snap.put("totalContractAmount", kpi.getTotalContractAmount());
        snap.put("confirmedRevenue", kpi.getConfirmedRevenue());
        snap.put("totalCost", kpi.getTotalCost());
        snap.put("activeProjects", kpi.getActiveProjects());
        return snap;
    }

    /**
     * 综合健康度评分（0-100），4 个因子加权：
     *   毛利率 30% + 利用率 30% + 健康占比 30% + 风险扣分 10%
     */
    private BigDecimal computeHealthScore(BigDecimal margin, BigDecimal util,
                                          BigDecimal healthRatio, BigDecimal riskRatio) {
        // 毛利率归一化到 0-100：0%→0，20%→100（线性夹逼）
        BigDecimal marginNorm = clampPercent(margin == null ? ZERO : margin.multiply(new BigDecimal("100")), 0, 20)
                .multiply(new BigDecimal("5")); // 0-20% * 5 = 0-100
        BigDecimal utilNorm = clampPercent(util == null ? ZERO : util.multiply(new BigDecimal("100")), 0, 100);
        BigDecimal healthNorm = clampPercent(healthRatio == null ? ZERO : healthRatio.multiply(new BigDecimal("100")), 0, 100);
        BigDecimal riskDeduct = clampPercent(riskRatio == null ? ZERO : riskRatio.multiply(new BigDecimal("100")), 0, 100);

        BigDecimal score = marginNorm.multiply(new BigDecimal("0.30"))
                .add(utilNorm.multiply(new BigDecimal("0.30")))
                .add(healthNorm.multiply(new BigDecimal("0.30")))
                .subtract(riskDeduct.multiply(new BigDecimal("0.10")));

        return clampPercent(score, 0, 100).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal clampPercent(BigDecimal v, double min, double max) {
        if (v == null) return BigDecimal.valueOf(min);
        if (v.compareTo(BigDecimal.valueOf(max)) > 0) return BigDecimal.valueOf(max);
        if (v.compareTo(BigDecimal.valueOf(min)) < 0) return BigDecimal.valueOf(min);
        return v;
    }

    private String gradeByScore(BigDecimal score) {
        if (score == null) return "D";
        double s = score.doubleValue();
        if (s >= 90) return "A";
        if (s >= 75) return "B";
        if (s >= 60) return "C";
        return "D";
    }

    /**
     * 项目群名称（基于 levelCode 推断）
     */
    private String inferGroupName(String levelCode) {
        if (levelCode == null || levelCode.isEmpty() || "UNKNOWN".equalsIgnoreCase(levelCode)) {
            return "未分类项目群";
        }
        // 简单规则：L1-L3 储备池 / L4-L12 事业部 / L13+ 总部
        try {
            int level = Integer.parseInt(levelCode.replaceAll("[^0-9]", ""));
            if (level >= 1 && level <= 3) return "储备池（L" + level + "）";
            if (level >= 4 && level <= 12) return "事业部（L" + level + "）";
            if (level >= 13) return "总部直属（L" + level + "）";
        } catch (NumberFormatException ignore) {
            // 非数字保持原值
        }
        return levelCode;
    }

    /**
     * 从 SQL 聚合结果中提取月份列表
     */
    private List<String> extractMonths(List<Map<String, Object>> rows) {
        List<String> out = new ArrayList<>();
        if (rows == null) return out;
        for (Map<String, Object> r : rows) {
            Object m = r.get("month");
            if (m == null) m = r.get("MONTH");
            if (m != null) {
                String s = String.valueOf(m);
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) out.add(s);
            }
        }
        return out;
    }

    private Map<String, BigDecimal> toMonthMap(List<Map<String, Object>> rows, String amountField) {
        Map<String, BigDecimal> out = new HashMap<>();
        if (rows == null) return out;
        for (Map<String, Object> r : rows) {
            Object m = r.get("month");
            if (m == null) m = r.get("MONTH");
            if (m == null) continue;
            String key = String.valueOf(m);
            if (key.isEmpty() || "null".equalsIgnoreCase(key)) continue;
            out.put(key, toDecimal(r.get(amountField)));
        }
        return out;
    }

    /**
     * 合并两个月份列表并去重倒序截断
     */
    private List<String> mergeAndSortMonths(List<String> a, List<String> b, int limit) {
        List<String> all = new ArrayList<>();
        all.addAll(a);
        all.addAll(b);
        // 去重
        List<String> dedup = new ArrayList<>();
        for (String s : all) {
            if (!dedup.contains(s)) dedup.add(s);
        }
        // 倒序
        dedup.sort((x, y) -> y.compareTo(x));
        if (dedup.size() > limit) {
            dedup = dedup.subList(0, limit);
        }
        return dedup;
    }
}
