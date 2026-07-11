package com.njydsz.pmis.project.server.service.impl;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.FinanceDataClient;
import com.njydsz.pmis.project.domain.entity.CostAllocationDO;
import com.njydsz.pmis.project.domain.entity.PurchaseDO;
import com.njydsz.pmis.project.infra.mapper.CostAllocationMapper;
import com.njydsz.pmis.project.infra.mapper.PurchaseMapper;
import com.njydsz.pmis.project.server.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础报表服务实现
 *
 * <p>聚合 PM 域成本数据（Labor/Purchase）+ 财务域数据（Revenue/Expense/ProfitSnapshot），
 * 提供项目利润报表、成本明细报表、回款台账与全生命周期台账。
 *
 * <p>跨域财务数据通过 {@link FinanceDataClient} Feign 调用获取，失败时降级返回零值。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@com.baomidou.dynamic.datasource.annotation.DS(com.njydsz.pmis.common.datasource.DataSourceConstants.SLAVE)
public class ReportServiceImpl implements ReportService {

    /** 成本分摊 Mapper */
    private final CostAllocationMapper costAllocationMapper;
    /** 采购成本 Mapper */
    private final PurchaseMapper purchaseMapper;
    /** 财务数据 Feign 客户端（跨域查询收入/费用/利润快照） */
    private final FinanceDataClient financeDataClient;

    @Override
    public Map<String, Object> projectProfitReport(String initiationId, String period) {
        Map<String, Object> report = new HashMap<>();
        if (initiationId == null) {
            report.put("error", "initiationId 不能为空");
            return report;
        }
        // 1) 优先用 ProfitSnapshot（跨域 Feign 调用财务服务）
        Map<String, Object> snap = latestSnapshot(initiationId, period);
        // 2) 累计收入（跨域 Feign）
        BigDecimal totalRevenue = sumRevenue(initiationId, period);
        // 3) 累计成本
        BigDecimal laborCost = sumCost(initiationId, period, "LABOR");
        BigDecimal purchaseCost = sumPurchase(initiationId, period);
        BigDecimal expenseCost = sumExpense(initiationId, period);
        BigDecimal allocated = sumCost(initiationId, period, "ALLOCATED");
        BigDecimal totalCost = laborCost.add(purchaseCost).add(expenseCost).add(allocated);

        BigDecimal grossProfit = totalRevenue.subtract(totalCost);
        BigDecimal grossMargin = totalRevenue.signum() == 0
                ? BigDecimal.ZERO
                : grossProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP);

        if (snap != null && !snap.isEmpty()) {
            report.putAll(snap);
        }
        report.put("initiationId", initiationId);
        report.put("period", period);
        report.put("revenue", totalRevenue);
        report.put("laborCost", laborCost);
        report.put("purchaseCost", purchaseCost);
        report.put("expenseCost", expenseCost);
        report.put("allocatedCost", allocated);
        report.put("totalCost", totalCost);
        report.put("grossProfit", grossProfit);
        report.put("grossMargin", grossMargin);
        return report;
    }

    @Override
    public Map<String, Object> costDetailReport(String initiationId, String period) {
        Map<String, Object> report = new HashMap<>();
        if (initiationId == null) {
            report.put("error", "initiationId 不能为空");
            return report;
        }
        BigDecimal labor = sumCost(initiationId, period, "LABOR");
        BigDecimal purchase = sumPurchase(initiationId, period);
        BigDecimal expense = sumExpense(initiationId, period);
        BigDecimal allocated = sumCost(initiationId, period, "ALLOCATED");
        BigDecimal total = labor.add(purchase).add(expense).add(allocated);
        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("labor", labor);
        breakdown.put("purchase", purchase);
        breakdown.put("expense", expense);
        breakdown.put("allocated", allocated);

        Map<String, Object> ratio = new HashMap<>();
        if (total.signum() > 0) {
            ratio.put("labor", labor.divide(total, 4, RoundingMode.HALF_UP));
            ratio.put("purchase", purchase.divide(total, 4, RoundingMode.HALF_UP));
            ratio.put("expense", expense.divide(total, 4, RoundingMode.HALF_UP));
            ratio.put("allocated", allocated.divide(total, 4, RoundingMode.HALF_UP));
        } else {
            ratio.put("labor", BigDecimal.ZERO);
            ratio.put("purchase", BigDecimal.ZERO);
            ratio.put("expense", BigDecimal.ZERO);
            ratio.put("allocated", BigDecimal.ZERO);
        }
        report.put("initiationId", initiationId);
        report.put("period", period);
        report.put("total", total);
        report.put("breakdown", breakdown);
        report.put("ratio", ratio);

        // 人员维度：每个员工的成本
        List<Map<String, Object>> byEmployee = new ArrayList<>();
        try {
            List<CostAllocationDO> allocations = costAllocationMapper.selectByInitiationAndPeriod(initiationId, period);
            if (allocations != null) {
                Map<String, BigDecimal> empTotals = new HashMap<>();
                for (CostAllocationDO c : allocations) {
                    if (c.getEmployeeId() == null) continue;
                    empTotals.merge(c.getEmployeeId(), c.getAmount() == null ? BigDecimal.ZERO : c.getAmount(), BigDecimal::add);
                }
                for (Map.Entry<String, BigDecimal> e : empTotals.entrySet()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("employeeId", e.getKey());
                    m.put("amount", e.getValue());
                    byEmployee.add(m);
                }
            }
        } catch (Exception e) { log.error("生成员工维度报表失败: {}", e.getMessage(), e); }
        report.put("byEmployee", byEmployee);
        return report;
    }

    @Override
    public Map<String, Object> paymentLedgerReport(String initiationId) {
        Map<String, Object> report = new HashMap<>();
        if (initiationId == null) {
            report.put("error", "initiationId 不能为空");
            return report;
        }
        // 跨域 Feign 调用财务服务获取收入明细
        BigDecimal totalRevenue = BigDecimal.ZERO;
        try {
            Result<List<Map<String, Object>>> resp = financeDataClient.revenueByInitiation(initiationId);
            if (resp != null && resp.getData() != null) {
                for (Map<String, Object> r : resp.getData()) {
                    if ("CONFIRMED".equals(String.valueOf(r.get("status")))) {
                        totalRevenue = totalRevenue.add(toDecimal(r.get("amount")));
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Report] paymentLedgerReport 收入查询失败: {}", e.getMessage());
        }
        // 跨域 Feign 调用获取期间汇总
        List<Map<String, Object>> byMonth = new ArrayList<>();
        try {
            Result<List<Map<String, Object>>> resp = financeDataClient.revenueSumByPeriod(initiationId);
            if (resp != null && resp.getData() != null) {
                byMonth = resp.getData();
            }
        } catch (Exception e) {
            log.error("[Report] paymentLedgerReport 期间汇总查询失败: {}", e.getMessage());
        }
        report.put("initiationId", initiationId);
        report.put("totalRevenue", totalRevenue);
        report.put("revenueByPeriod", byMonth);
        return report;
    }

    @Override
    public Map<String, Object> projectLifecycleReport(String initiationId) {
        Map<String, Object> report = new HashMap<>();
        report.put("initiationId", initiationId);
        report.put("costSummary", sumCostDetail(initiationId));
        report.put("revenueSummary", sumRevenue(initiationId, null));
        return report;
    }

    @Override
    public List<Map<String, Object>> profitSummaryAll() {
        try {
            Result<List<Map<String, Object>>> resp = financeDataClient.profitSnapshotSummaryAll();
            if (resp != null && resp.getData() != null) {
                return resp.getData();
            }
        } catch (Exception e) { log.error("[Report] profitSummaryAll 查询失败: {}", e.getMessage(), e); }
        return new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> profitRank(int top, String sortBy, String period) {
        try {
            Result<List<Map<String, Object>>> resp = financeDataClient.profitSnapshotRank(top, sortBy, period);
            if (resp != null && resp.getData() != null) {
                List<Map<String, Object>> rows = new ArrayList<>(resp.getData());
                // 健康度简易派生：毛利率 >= 0.30 = 绿；0.10-0.30 = 黄；< 0.10 = 红
                for (Map<String, Object> row : rows) {
                    BigDecimal margin = toDecimal(row.get("grossMargin"));
                    String health;
                    if (margin.compareTo(new BigDecimal("0.30")) >= 0) {
                        health = "GREEN";
                    } else if (margin.compareTo(new BigDecimal("0.10")) >= 0) {
                        health = "YELLOW";
                    } else {
                        health = "RED";
                    }
                    row.put("healthLevel", health);
                }
                return rows;
            }
        } catch (Exception e) {
            log.error("[Report] profitRank 查询失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    // ------------------ 私有辅助 ------------------

    private Map<String, Object> latestSnapshot(String initiationId, String period) {
        try {
            Result<Map<String, Object>> resp = financeDataClient.latestProfitSnapshot(initiationId, period);
            if (resp != null && resp.getData() != null && !resp.getData().isEmpty()) {
                return resp.getData();
            }
        } catch (Exception e) {
            log.error("[Report] 利润快照查询失败: {}", e.getMessage());
        }
        return null;
    }

    private BigDecimal sumRevenue(String initiationId, String period) {
        try {
            Result<BigDecimal> resp = financeDataClient.sumRevenue(initiationId, period);
            return resp != null && resp.getData() != null ? resp.getData() : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("[Report] 收入汇总查询失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal sumExpense(String initiationId, String period) {
        try {
            Result<BigDecimal> resp = financeDataClient.sumExpense(initiationId, period);
            return resp != null && resp.getData() != null ? resp.getData() : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("[Report] 费用汇总查询失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal sumCost(String initiationId, String period, String category) {
        try {
            List<CostAllocationDO> list = costAllocationMapper.selectByInitiationAndPeriod(initiationId, period);
            if (list == null) return BigDecimal.ZERO;
            BigDecimal sum = BigDecimal.ZERO;
            for (CostAllocationDO c : list) {
                if (category == null || category.equals(c.getCostType())) {
                    sum = sum.add(c.getAmount() == null ? BigDecimal.ZERO : c.getAmount());
                }
            }
            return sum;
        } catch (Exception e) {
            log.error("[Report] 成本汇总查询失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal sumPurchase(String initiationId, String period) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PurchaseDO> w =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            w.eq(PurchaseDO::getInitiationId, initiationId);
            if (StringUtils.hasText(period)) {
                w.like(PurchaseDO::getPurchaseDate, period);
            }
            List<PurchaseDO> list = purchaseMapper.selectList(w);
            if (list == null) return BigDecimal.ZERO;
            return list.stream()
                    .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.error("[Report] 采购成本查询失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private Map<String, Object> sumCostDetail(String initiationId) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("labor", sumCost(initiationId, null, "LABOR"));
        detail.put("purchase", sumPurchase(initiationId, null));
        detail.put("expense", sumExpense(initiationId, null));
        detail.put("allocated", sumCost(initiationId, null, "ALLOCATED"));
        return detail;
    }

    private BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
