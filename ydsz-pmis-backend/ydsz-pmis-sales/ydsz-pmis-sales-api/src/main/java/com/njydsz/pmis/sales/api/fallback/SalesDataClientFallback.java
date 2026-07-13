package com.njydsz.pmis.sales.api.fallback;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.sales.api.client.SalesDataClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 商务数据查询 Feign 客户端降级工厂
 *
 * <p>销售服务不可用时返回零值/空列表，避免报表聚合场景级联失败。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Slf4j
@Component
public class SalesDataClientFallback implements SalesDataClient {

    @Override
    public BaseResponse<BigDecimal> sumContractAmount() {
        log.warn("[SalesDataClient] 降级: sumContractAmount 返回零值");
        return BaseResponse.ok(BigDecimal.ZERO);
    }

    @Override
    public BaseResponse<BigDecimal> sumContractAmountByInitiation(String initiationId) {
        log.warn("[SalesDataClient] 降级: sumContractAmountByInitiation 返回零值, initiationId={}", initiationId);
        return BaseResponse.ok(BigDecimal.ZERO);
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> sumContractByCustomer() {
        log.warn("[SalesDataClient] 降级: sumContractByCustomer 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> sumContractByYear() {
        log.warn("[SalesDataClient] 降级: sumContractByYear 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> sumContractByRecentMonth(Integer limit) {
        log.warn("[SalesDataClient] 降级: sumContractByRecentMonth 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }

    @Override
    public BaseResponse<Integer> countOpportunities() {
        log.warn("[SalesDataClient] 降级: countOpportunities 返回零值");
        return BaseResponse.ok(0);
    }

    @Override
    public BaseResponse<List<Map<String, Object>>> sumContractByProjectType() {
        log.warn("[SalesDataClient] 降级: sumContractByProjectType 返回空列表");
        return BaseResponse.ok(Collections.emptyList());
    }
}
