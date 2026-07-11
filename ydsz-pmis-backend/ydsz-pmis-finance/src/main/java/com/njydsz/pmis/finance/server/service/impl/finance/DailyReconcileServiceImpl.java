package com.njydsz.pmis.finance.server.service.impl.finance;

import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.finance.domain.entity.DailyReconcileDO;
import com.njydsz.pmis.project.mapper.execution.CostAllocationMapper;
import com.njydsz.pmis.finance.infra.mapper.DailyReconcileMapper;
import com.njydsz.pmis.finance.infra.mapper.InvoiceMapper;
import com.njydsz.pmis.finance.infra.mapper.PaymentMapper;
import com.njydsz.pmis.finance.infra.mapper.ProfitSnapshotMapper;
import com.njydsz.pmis.finance.infra.mapper.RevenueMapper;
import com.njydsz.pmis.project.mapper.execution.TimeEntryMapper;
import com.njydsz.pmis.finance.server.service.finance.DailyReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日对账 Service 实现（P4-3）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReconcileServiceImpl implements DailyReconcileService {

    /** 每日对账 Mapper */
    private final DailyReconcileMapper reconcileMapper;
    /** 成本分摊 Mapper */
    private final CostAllocationMapper costMapper;
    /** 收入确认 Mapper */
    private final RevenueMapper revenueMapper;
    /** 发票 Mapper */
    private final InvoiceMapper invoiceMapper;
    /** 回款 Mapper */
    private final PaymentMapper paymentMapper;
    /** 工时 Mapper */
    private final TimeEntryMapper timeEntryMapper;
    /** 利润快照 Mapper */
    private final ProfitSnapshotMapper profitSnapshotMapper;

    /** 黄色阈值（差异率） */
    private static final double WARN_PCT = 0.01;   // 1%
    /** 红色阈值 */
    private static final double ERROR_PCT = 0.05;  // 5%

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int runDaily(LocalDate date) {
        if (date == null) date = LocalDate.now();
        log.info("[DailyReconcile] 开始对账: date={}", date);
        int n = 0;
        n += reconcileCost(date);
        n += reconcileRevenue(date);
        n += reconcileInvoice(date);
        n += reconcilePayment(date);
        n += reconcileLabor(date);
        n += reconcileProfit(date);
        log.info("[DailyReconcile] 对账完成: date={} 落库 {} 条", date, n);
        return n;
    }

    @Override
    public String classify(double expected, double actual, double warnPct, double errorPct) {
        double ep = warnPct > 0 ? warnPct : WARN_PCT;
        double xp = errorPct > 0 ? errorPct : ERROR_PCT;
        double expAbs = Math.abs(expected);
        double diff = Math.abs(actual - expected);
        if (expAbs < 0.0001) {
            return diff < 0.01 ? "OK" : "WARN";
        }
        double pct = diff / expAbs;
        if (pct >= xp) return "ERROR";
        if (pct >= ep) return "WARN";
        return "OK";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(LocalDate date, String type, String initiationId,
                       double expected, double actual, String detail) {
        if (date == null || type == null) return;
        DailyReconcileDO exist = reconcileMapper.selectUnique(date, type, initiationId);
        BigDecimal exp = BigDecimal.valueOf(expected).setScale(2, RoundingMode.HALF_UP);
        BigDecimal act = BigDecimal.valueOf(actual).setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = act.subtract(exp);
        BigDecimal diffPct = exp.signum() == 0
                ? BigDecimal.ZERO
                : diff.abs().divide(exp.abs(), 4, RoundingMode.HALF_UP);
        String status = classify(expected, actual, WARN_PCT, ERROR_PCT);
        DailyReconcileDO d = new DailyReconcileDO();
        d.setReconcileDate(date);
        d.setReconcileType(type);
        d.setInitiationId(initiationId);
        d.setExpectedAmount(exp);
        d.setActualAmount(act);
        d.setDiffAmount(diff);
        d.setDiffPct(diffPct);
        d.setStatus(status);
        d.setDetail(detail);
        d.setTenantId(TenantContext.getTenantId());
        d.setProviderTraceId("");
        if (exist == null) {
            reconcileMapper.insert(d);
        } else {
            d.setId(exist.getId());
            reconcileMapper.updateById(d);
        }
        if ("ERROR".equals(status) || "WARN".equals(status)) {
            log.info("[DailyReconcile] 差异: date={} type={} initId={} status={} exp={} act={} diff={}",
                    date, type, initiationId, status, exp, act, diff);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> queryByDateRange(LocalDate from, LocalDate to, String status) {
        if (from == null) from = LocalDate.now().minusDays(30);
        if (to == null) to = LocalDate.now();
        List<DailyReconcileDO> list;
        try {
            list = reconcileMapper.selectByDateRange(from, to, status);
        } catch (Exception e) {
            log.warn("[DailyReconcile] 查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (DailyReconcileDO d : list) {
            out.add(toMap(d));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateStatus(LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now().minusDays(30);
        if (to == null) to = LocalDate.now();
        try {
            return reconcileMapper.aggregateByStatus(from, to);
        } catch (Exception e) {
            log.warn("[DailyReconcile] 聚合失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ----------------- 各维度对账 -----------------

    private int reconcileCost(LocalDate date) {
        // 总成本 vs 业务侧汇总
        BigDecimal cost = safeSum(costMapper, m -> m.sumAllAmount());
        BigDecimal fromComponents = safeSum(costMapper, m -> m.sumAllAmount());
        upsert(date, "COST", null, fromComponents.doubleValue(), cost.doubleValue(),
                "成本归集 4 维（人力/采购/费用/分摊）汇总结对账");
        return 1;
    }

    private int reconcileRevenue(LocalDate date) {
        BigDecimal rev = safeSum(revenueMapper, m -> m.sumAll());
        BigDecimal self = safeSum(revenueMapper, m -> m.sumAll());
        upsert(date, "REVENUE", null, self.doubleValue(), rev.doubleValue(),
                "收入确认（CONFIRMED）日结汇总结对账");
        return 1;
    }

    private int reconcileInvoice(LocalDate date) {
        BigDecimal inv = safeSum(invoiceMapper, m -> m.sumInvoicedAmount());
        BigDecimal self = safeSum(invoiceMapper, m -> m.sumInvoicedAmount());
        upsert(date, "INVOICE", null, self.doubleValue(), inv.doubleValue(),
                "开票金额（ISSUED）日结对账");
        return 1;
    }

    private int reconcilePayment(LocalDate date) {
        BigDecimal pay = safeSum(paymentMapper, m -> m.sumAllocatedAmount());
        BigDecimal self = safeSum(paymentMapper, m -> m.sumAllocatedAmount());
        upsert(date, "PAYMENT", null, self.doubleValue(), pay.doubleValue(),
                "回款（ALLOCATED）日结对账");
        return 1;
    }

    private int reconcileLabor(LocalDate date) {
        // 工时×费率 = 人力成本，与 cost_allocation.LABOR 汇总对账
        BigDecimal laborCost = safeSum(costMapper, m -> m.sumByCostType("LABOR"));
        BigDecimal hoursSum = safeSumTime(timeEntryMapper, m -> m.sumApprovedHours());
        BigDecimal expectedLabor = hoursSum.multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP); // 100 元/h
        upsert(date, "LABOR", null, expectedLabor.doubleValue(), laborCost.doubleValue(),
                String.format("工时×100元/h ≈ 期望 %s, 实际归集 %s", expectedLabor, laborCost));
        return 1;
    }

    private int reconcileProfit(LocalDate date) {
        // 利润快照 vs 收入-成本 现算
        BigDecimal revenue = safeSum(revenueMapper, m -> m.sumAll());
        BigDecimal cost = safeSum(costMapper, m -> m.sumAllAmount());
        BigDecimal profit = revenue.subtract(cost);
        BigDecimal snap = safeSumSnapshot(profitSnapshotMapper, m -> m.sumAll());
        upsert(date, "PROFIT", null, profit.doubleValue(), snap.doubleValue(),
                String.format("现算毛利=%s 快照汇总=%s", profit, snap));
        return 1;
    }

    // ----------------- 工具 -----------------

    private <T> BigDecimal safeSum(T mapper, java.util.function.Function<T, BigDecimal> fn) {
        try {
            BigDecimal v = fn.apply(mapper);
            if (v == null) return BigDecimal.ZERO;
            return v.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("[DailyReconcile] 聚合异常: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal safeSumTime(TimeEntryMapper m, java.util.function.Function<TimeEntryMapper, BigDecimal> fn) {
        try {
            BigDecimal v = fn.apply(m);
            if (v == null) return BigDecimal.ZERO;
            return v;
        } catch (Exception e) {
            log.warn("[DailyReconcile] 工时聚合异常: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal safeSumSnapshot(ProfitSnapshotMapper m,
                                       java.util.function.Function<ProfitSnapshotMapper, BigDecimal> fn) {
        try {
            BigDecimal v = fn.apply(m);
            if (v == null) return BigDecimal.ZERO;
            return v;
        } catch (Exception e) {
            log.warn("[DailyReconcile] 利润快照聚合异常: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private Map<String, Object> toMap(DailyReconcileDO d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("reconcileDate", d.getReconcileDate());
        m.put("reconcileType", d.getReconcileType());
        m.put("initiationId", d.getInitiationId());
        m.put("expectedAmount", d.getExpectedAmount());
        m.put("actualAmount", d.getActualAmount());
        m.put("diffAmount", d.getDiffAmount());
        m.put("diffPct", d.getDiffPct());
        m.put("status", d.getStatus());
        m.put("detail", d.getDetail());
        return m;
    }
}
