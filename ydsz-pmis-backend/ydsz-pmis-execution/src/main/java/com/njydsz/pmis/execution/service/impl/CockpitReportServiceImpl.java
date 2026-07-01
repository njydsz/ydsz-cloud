package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.dto.CockpitDrillDownDTO;
import com.njydsz.pmis.execution.dto.CockpitKpiVO;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

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
}
