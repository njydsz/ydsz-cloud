package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.dto.CockpitDrillDownDTO;
import com.njydsz.pmis.execution.dto.CockpitKpiVO;
import com.njydsz.pmis.execution.mapper.CostAllocationMapper;
import com.njydsz.pmis.execution.mapper.EvmMeasureMapper;
import com.njydsz.pmis.execution.mapper.ExpenseMapper;
import com.njydsz.pmis.execution.mapper.InvoiceMapper;
import com.njydsz.pmis.execution.mapper.PaymentMapper;
import com.njydsz.pmis.execution.mapper.PurchaseMapper;
import com.njydsz.pmis.execution.mapper.RiskMapper;
import com.njydsz.pmis.execution.service.CockpitReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final BigDecimal ZERO = BigDecimal.ZERO;

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
        BigDecimal laborCost = nz(costAllocationMapper.sumAllAmount());
        BigDecimal purchaseCost = nz(purchaseMapper.sumAllAmount());
        BigDecimal expenseCost = nz(expenseMapper.sumAllAmount());
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

        // 9) 可计费利用率均值
        kpi.setAvgBillableUtilization(BigDecimal.valueOf(0.75)); // 默认基准，可由 scheduler 重算

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
        out.put("avgBillable", BigDecimal.valueOf(0.75));
        out.put("overloaded", 0);
        out.put("underutilized", 0);
        out.put("normal", 0);
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
}
