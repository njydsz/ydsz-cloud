package com.njydsz.project.api.client;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.project.api.fallback.FinanceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 财务数据 Feign 接口（利润/成本/发票/回款）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "financeClient",
    fallbackFactory = FinanceClientFallback.class)
public interface FinanceClient {

    @GetMapping("/project/finance/getProfitSnapshot")
    BaseResponse<?> getProfitSnapshot();
    @GetMapping("/project/finance/getCostSummary")
    BaseResponse<?> getCostSummary();
    @GetMapping("/project/finance/getRevenueSummary")
    BaseResponse<?> getRevenueSummary();
}
