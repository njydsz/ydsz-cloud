package com.njydsz.pmis.project.web.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.project.domain.entity.ProfitSnapshotDO;
import com.njydsz.pmis.project.infra.mapper.ExpenseMapper;
import com.njydsz.pmis.project.infra.mapper.InvoiceMapper;
import com.njydsz.pmis.project.infra.mapper.PaymentMapper;
import com.njydsz.pmis.project.infra.mapper.ProfitSnapshotMapper;
import com.njydsz.pmis.project.infra.mapper.RevenueMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 财务数据查询 Controller（内部接口）
 *
 * <p>供 PM 模块通过 {@link com.njydsz.pmis.project.api.client.FinanceDataClient} 跨域调用，
 * 暴露发票/回款/费用/收入等聚合数据查询能力。
 *
 * <p>所有方法均做 try-catch 容错，查询异常返回零值/空列表，不抛出异常到调用方。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/project/finance/data")
@RequiredArgsConstructor
@Tag(name = "财务数据查询", description = "内部跨域数据查询接口")
public class FinanceDataController {

    private final InvoiceMapper invoiceMapper;
    private final PaymentMapper paymentMapper;
    private final ExpenseMapper expenseMapper;
    private final RevenueMapper revenueMapper;
    private final ProfitSnapshotMapper profitSnapshotMapper;

    @GetMapping("/invoice/sumAmount")
    @Operation(summary = "发票总金额")
    public BaseResponse<BigDecimal> sumInvoiceAmount() {
        try {
            return BaseResponse.ok(nz(invoiceMapper.sumInvoicedAmount()));
        } catch (Exception e) {
            log.error("[FinanceData] sumInvoiceAmount 失败: {}", e.getMessage());
            return BaseResponse.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/payment/sumAllocated")
    @Operation(summary = "已分配回款总金额")
    public BaseResponse<BigDecimal> sumAllocatedPayment() {
        try {
            return BaseResponse.ok(nz(paymentMapper.sumAllocatedAmount()));
        } catch (Exception e) {
            log.error("[FinanceData] sumAllocatedPayment 失败: {}", e.getMessage());
            return BaseResponse.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/expense/sumAmount")
    @Operation(summary = "费用报销总金额")
    public BaseResponse<BigDecimal> sumExpenseAmount() {
        try {
            return BaseResponse.ok(nz(expenseMapper.sumAllAmount()));
        } catch (Exception e) {
            log.error("[FinanceData] sumExpenseAmount 失败: {}", e.getMessage());
            return BaseResponse.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/invoice/countDistinctInitiation")
    @Operation(summary = "按项目统计独立立项数")
    public BaseResponse<Integer> countDistinctInitiation() {
        try {
            return BaseResponse.ok(invoiceMapper.countDistinctInitiation());
        } catch (Exception e) {
            log.error("[FinanceData] countDistinctInitiation 失败: {}", e.getMessage());
            return BaseResponse.ok(0);
        }
    }

    @GetMapping("/invoice/sumByDepartment")
    @Operation(summary = "按部门统计发票金额")
    public BaseResponse<List<Map<String, Object>>> sumByDepartment() {
        try {
            return BaseResponse.ok(invoiceMapper.sumByDepartment());
        } catch (Exception e) {
            log.error("[FinanceData] sumByDepartment 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/invoice/sumByProjectType")
    @Operation(summary = "按项目类型统计发票金额")
    public BaseResponse<List<Map<String, Object>>> sumByProjectType() {
        try {
            return BaseResponse.ok(invoiceMapper.sumByProjectType());
        } catch (Exception e) {
            log.error("[FinanceData] sumByProjectType 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/invoice/sumByCustomer")
    @Operation(summary = "按客户统计发票金额")
    public BaseResponse<List<Map<String, Object>>> sumByCustomer() {
        try {
            return BaseResponse.ok(invoiceMapper.sumByCustomer());
        } catch (Exception e) {
            log.error("[FinanceData] sumByCustomer 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/invoice/sumByYear")
    @Operation(summary = "按年度统计发票金额")
    public BaseResponse<List<Map<String, Object>>> sumByYear() {
        try {
            return BaseResponse.ok(invoiceMapper.sumByYear());
        } catch (Exception e) {
            log.error("[FinanceData] sumByYear 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/invoice/sumByRecentMonth")
    @Operation(summary = "按最近月份统计发票金额")
    public BaseResponse<List<Map<String, Object>>> sumByRecentMonth(@RequestParam("limit") Integer limit) {
        try {
            return BaseResponse.ok(invoiceMapper.sumByRecentMonth(limit));
        } catch (Exception e) {
            log.error("[FinanceData] sumByRecentMonth 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/payment/aggregateByRecentMonth")
    @Operation(summary = "按最近月份统计回款金额")
    public BaseResponse<List<Map<String, Object>>> aggregatePaymentByRecentMonth(@RequestParam("limit") Integer limit) {
        try {
            return BaseResponse.ok(paymentMapper.aggregateByRecentMonth(limit));
        } catch (Exception e) {
            log.error("[FinanceData] aggregatePaymentByRecentMonth 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/revenue/sumByInitiation")
    @Operation(summary = "按项目查询收入总额")
    public BaseResponse<BigDecimal> sumRevenue(
            @RequestParam("initiationId") String initiationId,
            @RequestParam(value = "period", required = false) String period) {
        try {
            return BaseResponse.ok(nz(revenueMapper.sumByInitiation(initiationId, period)));
        } catch (Exception e) {
            log.error("[FinanceData] sumRevenue 失败: {}", e.getMessage());
            return BaseResponse.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/expense/sumByInitiation")
    @Operation(summary = "按项目查询费用总额")
    public BaseResponse<BigDecimal> sumExpense(
            @RequestParam("initiationId") String initiationId,
            @RequestParam(value = "period", required = false) String period) {
        try {
            return BaseResponse.ok(nz(expenseMapper.sumByInitiation(initiationId, period)));
        } catch (Exception e) {
            log.error("[FinanceData] sumExpense 失败: {}", e.getMessage());
            return BaseResponse.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/profitSnapshot/latest")
    @Operation(summary = "按项目查询利润快照")
    public BaseResponse<Map<String, Object>> latestProfitSnapshot(
            @RequestParam("initiationId") String initiationId,
            @RequestParam(value = "period", required = false) String period) {
        try {
            var snapshot = profitSnapshotMapper.selectLatest(initiationId, period);
            if (snapshot == null) {
                return BaseResponse.ok(Map.of());
            }
            return BaseResponse.ok(Map.of("snapshotId", snapshot.getId(), "grossProfit", snapshot.getGrossProfit()));
        } catch (Exception e) {
            log.error("[FinanceData] latestProfitSnapshot 失败: {}", e.getMessage());
            return BaseResponse.ok(Map.of());
        }
    }

    @GetMapping("/profitSnapshot/summaryAll")
    @Operation(summary = "利润快照汇总")
    public BaseResponse<List<Map<String, Object>>> profitSnapshotSummaryAll() {
        try {
            var wrapper = new LambdaQueryWrapper<ProfitSnapshotDO>();
            wrapper.orderByDesc(ProfitSnapshotDO::getSnapshotAt).last("LIMIT 200");
            var snaps = profitSnapshotMapper.selectList(wrapper);
            if (snaps == null) return BaseResponse.ok(List.of());
            List<Map<String, Object>> result = new ArrayList<>();
            for (var s : snaps) {
                Map<String, Object> m = new HashMap<>();
                m.put("initiationId", s.getInitiationId());
                m.put("period", s.getPeriod());
                m.put("totalCost", s.getTotalCost());
                m.put("grossMargin", s.getGrossMargin());
                result.add(m);
            }
            return BaseResponse.ok(result);
        } catch (Exception e) {
            log.error("[FinanceData] profitSnapshotSummaryAll 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/profitSnapshot/rank")
    @Operation(summary = "利润排名")
    public BaseResponse<List<Map<String, Object>>> profitSnapshotRank(
            @RequestParam("top") Integer top,
            @RequestParam("sortBy") String sortBy,
            @RequestParam(value = "period", required = false) String period) {
        try {
            var wrapper = new LambdaQueryWrapper<ProfitSnapshotDO>();
            if (period != null && !period.isEmpty()) {
                wrapper.eq(ProfitSnapshotDO::getPeriod, period);
            }
            var all = profitSnapshotMapper.selectList(wrapper);
            if (all == null) return BaseResponse.ok(List.of());
            // Deduplicate by initiationId, keeping latest snapshot
            Map<String, ProfitSnapshotDO> latest = new HashMap<>();
            for (var s : all) {
                if (s == null || s.getInitiationId() == null) continue;
                var prev = latest.get(s.getInitiationId());
                if (prev == null || (s.getSnapshotAt() != null && (prev.getSnapshotAt() == null || s.getSnapshotAt().isAfter(prev.getSnapshotAt())))) {
                    latest.put(s.getInitiationId(), s);
                }
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (var s : latest.values()) {
                Map<String, Object> row = new HashMap<>();
                row.put("initiationId", s.getInitiationId());
                row.put("period", s.getPeriod());
                row.put("contractAmount", s.getContractAmount());
                row.put("recognizedRevenue", s.getRecognizedRevenue());
                row.put("totalCost", s.getTotalCost());
                row.put("grossProfit", s.getGrossProfit());
                row.put("grossMargin", s.getGrossMargin());
                row.put("progressPct", s.getProgressPct());
                row.put("snapshotAt", s.getSnapshotAt());
                rows.add(row);
            }
            // Sort
            String dim = sortBy != null ? sortBy : "grossMargin";
            Comparator<Map<String, Object>> cmp = switch (dim) {
                case "grossProfit" -> Comparator.comparing(m -> toDecimal(m.get("grossProfit")));
                case "contractAmount" -> Comparator.comparing(m -> toDecimal(m.get("contractAmount")));
                default -> Comparator.comparing(m -> toDecimal(m.get("grossMargin")));
            };
            rows.sort(cmp.reversed());
            int limit = top <= 0 ? 10 : top;
            if (rows.size() > limit) rows = rows.subList(0, limit);
            return BaseResponse.ok(rows);
        } catch (Exception e) {
            log.error("[FinanceData] profitSnapshotRank 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/revenue/selectByInitiation")
    @Operation(summary = "按项目查询收入明细列表")
    public BaseResponse<List<Map<String, Object>>> revenueByInitiation(@RequestParam("initiationId") String initiationId) {
        try {
            var revs = revenueMapper.selectByInitiation(initiationId);
            if (revs == null) return BaseResponse.ok(List.of());
            List<Map<String, Object>> result = new ArrayList<>();
            for (var r : revs) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r.getId());
                m.put("status", r.getStatus());
                m.put("amount", r.getAmount());
                m.put("period", r.getPeriod());
                result.add(m);
            }
            return BaseResponse.ok(result);
        } catch (Exception e) {
            log.error("[FinanceData] revenueByInitiation 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/revenue/sumByPeriod")
    @Operation(summary = "按项目查询收入期间汇总")
    public BaseResponse<List<Map<String, Object>>> revenueSumByPeriod(@RequestParam("initiationId") String initiationId) {
        try {
            return BaseResponse.ok(revenueMapper.sumByPeriod(initiationId));
        } catch (Exception e) {
            log.error("[FinanceData] revenueSumByPeriod 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    private BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
