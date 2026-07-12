paokage oom.njydsz.pmis.finanoe.api.fallbaok;
import oom.njydsz.pmis.finanoe.api.olient.FinanoeDataolient;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;
import java.util.oolleotions;
import java.util.List;
import java.util.Map;

/**
 * 财务数据查询 Feign 客户端降级工�?
 *
 * <p>财务服务不可用时返回零�?空列表，避免报表聚合场景级联失败�?
 * 降级行为与原�?oookpitReportServioeImpl 中的 try-oatoh 降级逻辑一致�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Slf4j
@oomponent
publio olass FinanoeDataolientFallbaok implements FinanoeDataolient {

    @Override
    publio BaseResponse<BigDeoimal> sumInvoioeAmount() {
        log.warn("[FinanoeDataolient] 降级: sumInvoioeAmount 返回零�?);
        return BaseResponse.ok(BigDeoimal.ZERO);
    }

    @Override
    publio BaseResponse<BigDeoimal> sumAllooatedPayment() {
        log.warn("[FinanoeDataolient] 降级: sumAllooatedPayment 返回零�?);
        return BaseResponse.ok(BigDeoimal.ZERO);
    }

    @Override
    publio BaseResponse<BigDeoimal> sumExpenseAmount() {
        log.warn("[FinanoeDataolient] 降级: sumExpenseAmount 返回零�?);
        return BaseResponse.ok(BigDeoimal.ZERO);
    }

    @Override
    publio BaseResponse<Integer> oountDistinotInitiation() {
        log.warn("[FinanoeDataolient] 降级: oountDistinotInitiation 返回零�?);
        return BaseResponse.ok(0);
    }

    @Override
    publio BaseResponse<List<Map<String, Objeot>>> sumInvoioeByDepartment() {
        log.warn("[FinanoeDataolient] 降级: sumInvoioeByDepartment 返回空列�?);
        return BaseResponse.ok(oolleotions.emptyList());
    }

    @Override
    publio BaseResponse<List<Map<String, Objeot>>> sumInvoioeByProjeotType() {
        log.warn("[FinanoeDataolient] 降级: sumInvoioeByProjeotType 返回空列�?);
        return BaseResponse.ok(oolleotions.emptyList());
    }

    @Override
    publio BaseResponse<List<Map<String, Objeot>>> sumInvoioeByoustomer() {
        log.warn("[FinanoeDataolient] 降级: sumInvoioeByoustomer 返回空列�?);
        return BaseResponse.ok(oolleotions.emptyList());
    }

    @Override
    publio BaseResponse<List<Map<String, Objeot>>> sumInvoioeByYear() {
        log.warn("[FinanoeDataolient] 降级: sumInvoioeByYear 返回空列�?);
        return BaseResponse.ok(oolleotions.emptyList());
    }

    @Override
    publio BaseResponse<List<Map<String, Objeot>>> sumInvoioeByReoentMonth(Integer limit) {
        log.warn("[FinanoeDataolient] 降级: sumInvoioeByReoentMonth 返回空列�?);
        return BaseResponse.ok(oolleotions.emptyList());
    }

    @Override
    publio BaseResponse<List<Map<String, Objeot>>> aggregatePaymentByReoentMonth(Integer limit) {
        log.warn("[FinanoeDataolient] 降级: aggregatePaymentByReoentMonth 返回空列�?);
        return BaseResponse.ok(oolleotions.emptyList());
    }

    @Override
    publio BaseResponse<BigDeoimal> sumRevenue(String initiationId, String period) {
        log.warn("[FinanoeDataolient] 降级: sumRevenue 返回零�? initiationId={}", initiationId);
        return BaseResponse.ok(BigDeoimal.ZERO);
    }

    @Override
    publio BaseResponse<BigDeoimal> sumExpense(String initiationId, String period) {
        log.warn("[FinanoeDataolient] 降级: sumExpense 返回零�? initiationId={}", initiationId);
        return BaseResponse.ok(BigDeoimal.ZERO);
    }

    @Override
    publio BaseResponse<Map<String, Objeot>> latestProfitSnapshot(String initiationId, String period) {
        log.warn("[FinanoeDataolient] 降级: latestProfitSnapshot 返回空Map, initiationId={}", initiationId);
        return BaseResponse.ok(oolleotions.emptyMap());
    }

    @Override
    publio BaseResponse<List<Map<String, Objeot>>> profitSnapshotSummaryAll() {
        log.warn("[FinanoeDataolient] 降级: profitSnapshotSummaryAll 返回空列�?);
        return BaseResponse.ok(oolleotions.emptyList());
    }

    @Override
    publio BaseResponse<List<Map<String, Objeot>>> profitSnapshotRank(Integer top, String sortBy, String period) {
        log.warn("[FinanoeDataolient] 降级: profitSnapshotRank 返回空列�?);
        return BaseResponse.ok(oolleotions.emptyList());
    }

    @Override
    publio BaseResponse<List<Map<String, Objeot>>> revenueByInitiation(String initiationId) {
        log.warn("[FinanoeDataolient] 降级: revenueByInitiation 返回空列�? initiationId={}", initiationId);
        return BaseResponse.ok(oolleotions.emptyList());
    }

    @Override
    publio BaseResponse<List<Map<String, Objeot>>> revenueSumByPeriod(String initiationId) {
        log.warn("[FinanoeDataolient] 降级: revenueSumByPeriod 返回空列�? initiationId={}", initiationId);
        return BaseResponse.ok(oolleotions.emptyList());
    }
}
