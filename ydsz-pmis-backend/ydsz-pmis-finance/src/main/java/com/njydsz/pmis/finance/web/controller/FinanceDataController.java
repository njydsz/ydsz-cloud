package com.njydsz.pmis.finance.web.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.finance.infra.mapper.InvoiceMapper;
import com.njydsz.pmis.finance.infra.mapper.PaymentMapper;
import com.njydsz.pmis.finance.infra.mapper.ExpenseMapper;
import com.njydsz.pmis.finance.infra.mapper.RevenueMapper;
import com.njydsz.pmis.finance.infra.mapper.ProfitSnapshotMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 财务数据查询 Controller（内部接口）
 *
 * <p>供 PM 模块通过 {@link com.njydsz.pmis.common.feign.FinanceDataClient} 跨域调用，
 * 暴露发票/回款/费用/收入等聚合数据查询能力。
 *
 * <p>所有方法均做 try-catch 容错，查询异常返回零值/空列表，不抛出异常到调用方。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/finance/data")
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
    public Result<BigDecimal> sumInvoiceAmount() {
        try {
            return Result.ok(nz(invoiceMapper.sumInvoicedAmount()));
        } catch (Exception e) {
            log.error("[FinanceData] sumInvoiceAmount 失败: {}", e.getMessage());
            return Result.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/payment/sumAllocated")
    @Operation(summary = "已分配回款总金额")
    public Result<BigDecimal> sumAllocatedPayment() {
        try {
            return Result.ok(nz(paymentMapper.sumAllocatedAmount()));
        } catch (Exception e) {
            log.error("[FinanceData] sumAllocatedPayment 失败: {}", e.getMessage());
            return Result.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/expense/sumAmount")
    @Operation(summary = "费用报销总金额")
    public Result<BigDecimal> sumExpenseAmount() {
        try {
            return Result.ok(nz(expenseMapper.sumAllAmount()));
        } catch (Exception e) {
            log.error("[FinanceData] sumExpenseAmount 失败: {}", e.getMessage());
            return Result.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/invoice/countDistinctInitiation")
    @Operation(summary = "按项目统计独立立项数")
    public Result<Integer> countDistinctInitiation() {
        try {
            return Result.ok(invoiceMapper.countDistinctInitiation());
        } catch (Exception e) {
            log.error("[FinanceData] countDistinctInitiation 失败: {}", e.getMessage());
            return Result.ok(0);
        }
    }

    @GetMapping("/invoice/sumByDepartment")
    @Operation(summary = "按部门统计发票金额")
    public Result<List<Map<String, Object>>> sumByDepartment() {
        try {
            return Result.ok(invoiceMapper.sumByDepartment());
        } catch (Exception e) {
            log.error("[FinanceData] sumByDepartment 失败: {}", e.getMessage());
            return Result.ok(List.of());
        }
    }

    @GetMapping("/invoice/sumByProjectType")
    @Operation(summary = "按项目类型统计发票金额")
    public Result<List<Map<String, Object>>> sumByProjectType() {
        try {
            return Result.ok(invoiceMapper.sumByProjectType());
        } catch (Exception e) {
            log.error("[FinanceData] sumByProjectType 失败: {}", e.getMessage());
            return Result.ok(List.of());
        }
    }

    @GetMapping("/invoice/sumByCustomer")
    @Operation(summary = "按客户统计发票金额")
    public Result<List<Map<String, Object>>> sumByCustomer() {
        try {
            return Result.ok(invoiceMapper.sumByCustomer());
        } catch (Exception e) {
            log.error("[FinanceData] sumByCustomer 失败: {}", e.getMessage());
            return Result.ok(List.of());
        }
    }

    @GetMapping("/invoice/sumByYear")
    @Operation(summary = "按年度统计发票金额")
    public Result<List<Map<String, Object>>> sumByYear() {
        try {
            return Result.ok(invoiceMapper.sumByYear());
        } catch (Exception e) {
            log.error("[FinanceData] sumByYear 失败: {}", e.getMessage());
            return Result.ok(List.of());
        }
    }

    @GetMapping("/invoice/sumByRecentMonth")
    @Operation(summary = "按最近月份统计发票金额")
    public Result<List<Map<String, Object>>> sumByRecentMonth(@RequestParam("limit") Integer limit) {
        try {
            return Result.ok(invoiceMapper.sumByRecentMonth(limit));
        } catch (Exception e) {
            log.error("[FinanceData] sumByRecentMonth 失败: {}", e.getMessage());
            return Result.ok(List.of());
        }
    }

    @GetMapping("/payment/aggregateByRecentMonth")
    @Operation(summary = "按最近月份统计回款金额")
    public Result<List<Map<String, Object>>> aggregatePaymentByRecentMonth(@RequestParam("limit") Integer limit) {
        try {
            return Result.ok(paymentMapper.aggregateByRecentMonth(limit));
        } catch (Exception e) {
            log.error("[FinanceData] aggregatePaymentByRecentMonth 失败: {}", e.getMessage());
            return Result.ok(List.of());
        }
    }

    @GetMapping("/revenue/sumByInitiation")
    @Operation(summary = "按项目查询收入总额")
    public Result<BigDecimal> sumRevenue(
            @RequestParam("initiationId") String initiationId,
            @RequestParam(value = "period", required = false) String period) {
        try {
            return Result.ok(nz(revenueMapper.sumByInitiation(initiationId, period)));
        } catch (Exception e) {
            log.error("[FinanceData] sumRevenue 失败: {}", e.getMessage());
            return Result.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/expense/sumByInitiation")
    @Operation(summary = "按项目查询费用总额")
    public Result<BigDecimal> sumExpense(
            @RequestParam("initiationId") String initiationId,
            @RequestParam(value = "period", required = false) String period) {
        try {
            return Result.ok(nz(expenseMapper.sumByInitiation(initiationId, period)));
        } catch (Exception e) {
            log.error("[FinanceData] sumExpense 失败: {}", e.getMessage());
            return Result.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/profitSnapshot/latest")
    @Operation(summary = "按项目查询利润快照")
    public Result<Map<String, Object>> latestProfitSnapshot(
            @RequestParam("initiationId") String initiationId,
            @RequestParam(value = "period", required = false) String period) {
        try {
            var snapshot = profitSnapshotMapper.selectLatest(initiationId, period);
            if (snapshot == null) {
                return Result.ok(Map.of());
            }
            return Result.ok(Map.of("snapshotId", snapshot.getId(), "grossProfit", snapshot.getGrossProfit()));
        } catch (Exception e) {
            log.error("[FinanceData] latestProfitSnapshot 失败: {}", e.getMessage());
            return Result.ok(Map.of());
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
