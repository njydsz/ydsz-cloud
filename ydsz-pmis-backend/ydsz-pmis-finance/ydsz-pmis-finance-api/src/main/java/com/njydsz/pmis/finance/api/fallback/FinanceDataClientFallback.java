package com.njydsz.pmis.finance.api.fallback;
import com.njydsz.pmis.finance.api.client.FinanceDataClient;

import com.njydsz.pmis.common.core.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 财务数据查询 Feign 客户端降级工厂
 *
 * <p>财务服务不可用时返回零值/空列表，避免报表聚合场景级联失败。
 * 降级行为与原有 CockpitReportServiceImpl 中的 try-catch 降级逻辑一致。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Slf4j
@Component
public class FinanceDataClientFallback implements FinanceDataClient {

    @Override
    public BaseResponse<BigDecimal> sumInvoiceAmount() {
        log.warn("[FinanceDataClient] 降级: sumInvoiceAmount 返回零值");
        return BaseResponse.ok(BigDecimal.ZERO);
    }

    @Override
    public BaseResponse<BigDecimal> sumAllocatedPayment() {
        log.warn("[FinanceDataClient] 降级: sumAllocatedPayment 返回零值");
        return BaseResponse.ok(BigDecimal.ZERO);
    }

    @Override
    public BaseResponse<BigDecimal> sumExpenseAmount() {
        log.warn("[FinanceDataClient] 降级: sumExpenseAmount 返回零值");
        return BaseResponse.ok(BigDecimal.ZERO);
    }

    @Override
    public BaseResponse<Integer> countDistinctInitiation() {
        log.warn("[FinanceDataClient] 降级: countDistinctInitiation 返回零值");
        return BaseResponse.ok(0);
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> sumInvoiceByDepartment() {
        log.warn("[FinanceDataClient] 降级: sumInvoiceByDepartment 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> sumInvoiceByProjectType() {
        log.warn("[FinanceDataClient] 降级: sumInvoiceByProjectType 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> sumInvoiceByCustomer() {
        log.warn("[FinanceDataClient] 降级: sumInvoiceByCustomer 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> sumInvoiceByYear() {
        log.warn("[FinanceDataClient] 降级: sumInvoiceByYear 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> sumInvoiceByRecentMonth(Integer limit) {
        log.warn("[FinanceDataClient] 降级: sumInvoiceByRecentMonth 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> aggregatePaymentByRecentMonth(Integer limit) {
        log.warn("[FinanceDataClient] 降级: aggregatePaymentByRecentMonth 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<BigDecimal> sumRevenue(String initiationId, String period) {
        log.warn("[FinanceDataClient] 降级: sumRevenue 返回零值, initiationId={}", initiationId);
        return BaseResponse.ok(BigDecimal.ZERO);
    }

    @Override
    public BaseResponse<BigDecimal> sumExpense(String initiationId, String period) {
        log.warn("[FinanceDataClient] 降级: sumExpense 返回零值, initiationId={}", initiationId);
        return BaseResponse.ok(BigDecimal.ZERO);
    }

    @Override
    public BaseResponse<Map<String, Object>> latestProfitSnapshot(String initiationId, String period) {
        log.warn("[FinanceDataClient] 降级: latestProfitSnapshot 返回空Map, initiationId={}", initiationId);
        return BaseResponse.ok(Collections.emptyMap());
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> profitSnapshotSummaryAll() {
        log.warn("[FinanceDataClient] 降级: profitSnapshotSummaryAll 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> profitSnapshotRank(Integer top, String sortBy, String period) {
        log.warn("[FinanceDataClient] 降级: profitSnapshotRank 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> revenueByInitiation(String initiationId) {
        log.warn("[FinanceDataClient] 降级: revenueByInitiation 返回空列表, initiationId={}", initiationId);
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> revenueSumByPeriod(String initiationId) {
        log.warn("[FinanceDataClient] 降级: revenueSumByPeriod 返回空列表, initiationId={}", initiationId);
        return BaseResponse.ok(Collections.emptyList());
    }
}
