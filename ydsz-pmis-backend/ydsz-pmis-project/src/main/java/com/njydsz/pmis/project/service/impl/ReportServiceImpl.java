package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.project.entity.CostAllocationDO;
import com.njydsz.pmis.project.entity.ExpenseDO;
import com.njydsz.pmis.project.entity.ProfitSnapshotDO;
import com.njydsz.pmis.project.entity.PurchaseDO;
import com.njydsz.pmis.project.entity.RevenueDO;
import com.njydsz.pmis.project.mapper.CostAllocationMapper;
import com.njydsz.pmis.project.mapper.ExpenseMapper;
import com.njydsz.pmis.project.mapper.ProfitSnapshotMapper;
import com.njydsz.pmis.project.mapper.PurchaseMapper;
import com.njydsz.pmis.project.mapper.RevenueMapper;
import com.njydsz.pmis.project.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础报表服务实现
 *
 * <p>聚合 RevenueDO + InvoiceDO 计算收入，聚合 Labor/Purchase/Expense 分摊计算成本，
 * 提供项目利润报表、成本明细报表、回款台账与全生命周期台账。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final ProfitSnapshotMapper profitSnapshotMapper;
    private final CostAllocationMapper costAllocationMapper;
    private final ExpenseMapper expenseMapper;
    private final PurchaseMapper purchaseMapper;
    private final RevenueMapper revenueMapper;

    @Override
    public Map<String, Object> projectProfitReport(String initiationId, String period) {
        Map<String, Object> report = new HashMap<>();
        if (initiationId == null) {
            report.put("error", "initiationId 不能为空");
            return report;
        }
        // 1) 优先用 ProfitSnapshot
        ProfitSnapshotDO snap = latestSnapshot(initiationId, period);
        // 2) 累计收入
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

        if (snap != null) {
            report.put("snapshotId", snap.getId());
            report.put("snapshotPeriod", snap.getPeriod());
            report.put("snapshotAt", snap.getSnapshotAt());
            report.put("contractAmount", snap.getContractAmount());
            report.put("recognizedRevenue", snap.getRecognizedRevenue());
            report.put("billedAmount", snap.getBilledAmount());
            report.put("receivedAmount", snap.getReceivedAmount());
            report.put("progressPct", snap.getProgressPct());
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
        List<RevenueDO> revs = revenueMapper.selectByInitiation(initiationId);
        BigDecimal totalRevenue = BigDecimal.ZERO;
        if (revs != null) {
            for (RevenueDO r : revs) {
                if ("CONFIRMED".equals(r.getStatus())) {
                    totalRevenue = totalRevenue.add(r.getAmount() == null ? BigDecimal.ZERO : r.getAmount());
                }
            }
        }
        List<Map<String, Object>> byMonth = revenueMapper.sumByPeriod(initiationId);
        report.put("initiationId", initiationId);
        report.put("totalRevenue", totalRevenue);
        report.put("revenueByPeriod", byMonth);
        return report;
    }

    @Override
    public Map<String, Object> projectLifecycleReport(String initiationId) {
        Map<String, Object> report = new HashMap<>();
        report.put("initiationId", initiationId);
        // 跨模块的台账通常会通过 Feign 聚合；这里返回当前模块范围内的关键节点
        report.put("costSummary", sumCostDetail(initiationId));
        report.put("revenueSummary", sumRevenue(initiationId, null));
        return report;
    }

    @Override
    public List<Map<String, Object>> profitSummaryAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            LambdaQueryWrapper<ProfitSnapshotDO> w = new LambdaQueryWrapper<>();
            w.orderByDesc(ProfitSnapshotDO::getSnapshotAt).last("LIMIT 200");
            List<ProfitSnapshotDO> snaps = profitSnapshotMapper.selectList(w);
            if (snaps != null) {
                for (ProfitSnapshotDO s : snaps) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("initiationId", s.getInitiationId());
                    m.put("period", s.getPeriod());
                    m.put("totalCost", s.getTotalCost());
                    m.put("grossMargin", s.getGrossMargin());
                    m.put("healthScore", null); // ProfitSnapshotDO doesn't carry health score
                    result.add(m);
                }
            }
        } catch (Exception e) { log.error("生成利润快照报表失败: {}", e.getMessage(), e); }
        return result;
    }

    @Override
    public List<Map<String, Object>> profitRank(int top, String sortBy, String period) {
        int limit = top <= 0 ? 10 : top;
        String dim = StringUtils.hasText(sortBy) ? sortBy : "grossMargin";
        Map<String, ProfitSnapshotDO> latestByInitiation = new HashMap<>();
        try {
            LambdaQueryWrapper<ProfitSnapshotDO> w = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(period)) {
                w.eq(ProfitSnapshotDO::getPeriod, period);
            }
            List<ProfitSnapshotDO> all = profitSnapshotMapper.selectList(w);
            for (ProfitSnapshotDO s : all) {
                if (s == null || s.getInitiationId() == null) continue;
                ProfitSnapshotDO prev = latestByInitiation.get(s.getInitiationId());
                if (prev == null
                        || (s.getSnapshotAt() != null
                            && (prev.getSnapshotAt() == null
                                || s.getSnapshotAt().isAfter(prev.getSnapshotAt())))) {
                    latestByInitiation.put(s.getInitiationId(), s);
                }
            }
        } catch (Exception e) {
            log.error("[Report] profitRank 快照查询失败: {}", e.getMessage());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProfitSnapshotDO s : latestByInitiation.values()) {
            Map<String, Object> row = new HashMap<>();
            row.put("initiationId", s.getInitiationId());
            row.put("period", s.getPeriod());
            row.put("contractAmount", nz(s.getContractAmount()));
            row.put("recognizedRevenue", nz(s.getRecognizedRevenue()));
            row.put("billedAmount", nz(s.getBilledAmount()));
            row.put("receivedAmount", nz(s.getReceivedAmount()));
            row.put("totalCost", nz(s.getTotalCost()));
            row.put("grossProfit", nz(s.getGrossProfit()));
            row.put("grossMargin", nz(s.getGrossMargin()));
            row.put("progressPct", nz(s.getProgressPct()));
            row.put("snapshotAt", s.getSnapshotAt());
            // 健康度简易派生：毛利率 >= 0.30 = 绿；0.10-0.30 = 黄；< 0.10 = 红
            BigDecimal margin = nz(s.getGrossMargin());
            String health;
            if (margin.compareTo(new BigDecimal("0.30")) >= 0) {
                health = "GREEN";
            } else if (margin.compareTo(new BigDecimal("0.10")) >= 0) {
                health = "YELLOW";
            } else {
                health = "RED";
            }
            row.put("healthLevel", health);
            rows.add(row);
        }

        Comparator<Map<String, Object>> cmp;
        switch (dim) {
            case "grossProfit":
                cmp = Comparator.comparing((Map<String, Object> m) ->
                        (BigDecimal) m.get("grossProfit"));
                break;
            case "contractAmount":
                cmp = Comparator.comparing((Map<String, Object> m) ->
                        (BigDecimal) m.get("contractAmount"));
                break;
            case "grossMargin":
            default:
                cmp = Comparator.comparing((Map<String, Object> m) ->
                        (BigDecimal) m.get("grossMargin"));
                break;
        }
        rows.sort(cmp.reversed());
        if (rows.size() > limit) {
            return new ArrayList<>(rows.subList(0, limit));
        }
        return rows;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private ProfitSnapshotDO latestSnapshot(String initiationId, String period) {
        try {
            if (StringUtils.hasText(period)) {
                return profitSnapshotMapper.selectByInitiationAndPeriod(initiationId, period);
            }
            List<ProfitSnapshotDO> list = profitSnapshotMapper.selectByInitiation(initiationId);
            if (list == null || list.isEmpty()) return null;
            // 找最近的（按 snapshotAt 倒序）
            list.sort((a, b) -> {
                if (a.getSnapshotAt() == null) return 1;
                if (b.getSnapshotAt() == null) return -1;
                return b.getSnapshotAt().compareTo(a.getSnapshotAt());
            });
            return list.get(0);
        } catch (Exception e) {
            log.warn("[ReportServiceImpl] 获取最近快照失败 initiationId={} period={}: {}", initiationId, period, e.getMessage());
            return null;
        }
    }

    private BigDecimal sumRevenue(String initiationId, String period) {
        BigDecimal sum = BigDecimal.ZERO;
        List<RevenueDO> list = revenueMapper.selectByInitiation(initiationId);
        if (list == null) return sum;
        for (RevenueDO r : list) {
            if (!"CONFIRMED".equals(r.getStatus())) continue;
            if (StringUtils.hasText(period) && !period.equals(r.getPeriod())) continue;
            sum = sum.add(r.getAmount() == null ? BigDecimal.ZERO : r.getAmount());
        }
        return sum;
    }

    private BigDecimal sumCost(String initiationId, String period, String costType) {
        BigDecimal sum = BigDecimal.ZERO;
        try {
            List<CostAllocationDO> list = costAllocationMapper.selectByInitiationAndPeriod(initiationId, period);
            if (list == null) return sum;
            for (CostAllocationDO c : list) {
                if (costType != null && !costType.equalsIgnoreCase(c.getCostType())) continue;
                sum = sum.add(c.getAmount() == null ? BigDecimal.ZERO : c.getAmount());
            }
        } catch (Exception e) { log.error("汇总成本分配失败: {}", e.getMessage(), e); }
        return sum;
    }

    private BigDecimal sumPurchase(String initiationId, String period) {
        BigDecimal sum = BigDecimal.ZERO;
        try {
            LambdaQueryWrapper<PurchaseDO> w = new LambdaQueryWrapper<>();
            w.eq(PurchaseDO::getInitiationId, initiationId);
            List<PurchaseDO> list = purchaseMapper.selectList(w);
            if (list != null) {
                for (PurchaseDO p : list) {
                    if (!"APPROVED".equals(p.getStatus())) continue;
                    if (StringUtils.hasText(period) && p.getPurchaseDate() != null
                            && !period.equals(p.getPurchaseDate().toString().substring(0, 7))) continue;
                    sum = sum.add(p.getAmount() == null ? BigDecimal.ZERO : p.getAmount());
                }
            }
        } catch (Exception e) { log.error("汇总采购金额失败: {}", e.getMessage(), e); }
        return sum;
    }

    private BigDecimal sumExpense(String initiationId, String period) {
        BigDecimal sum = BigDecimal.ZERO;
        try {
            LambdaQueryWrapper<ExpenseDO> w = new LambdaQueryWrapper<>();
            w.eq(ExpenseDO::getInitiationId, initiationId);
            List<ExpenseDO> list = expenseMapper.selectList(w);
            if (list != null) {
                for (ExpenseDO e : list) {
                    if (!"APPROVED".equals(e.getStatus())) continue;
                    if (StringUtils.hasText(period) && e.getExpenseDate() != null
                            && !period.equals(e.getExpenseDate().toString().substring(0, 7))) continue;
                    sum = sum.add(e.getAmount() == null ? BigDecimal.ZERO : e.getAmount());
                }
            }
        } catch (Exception e) { log.error("汇总报销金额失败: {}", e.getMessage(), e); }
        return sum;
    }

    private Map<String, BigDecimal> sumCostDetail(String initiationId) {
        Map<String, BigDecimal> m = new HashMap<>();
        m.put("labor", sumCost(initiationId, null, "LABOR"));
        m.put("purchase", sumPurchase(initiationId, null));
        m.put("expense", sumExpense(initiationId, null));
        m.put("allocated", sumCost(initiationId, null, "ALLOCATED"));
        BigDecimal total = m.get("labor").add(m.get("purchase"))
                .add(m.get("expense")).add(m.get("allocated"));
        m.put("total", total);
        return m;
    }
}
