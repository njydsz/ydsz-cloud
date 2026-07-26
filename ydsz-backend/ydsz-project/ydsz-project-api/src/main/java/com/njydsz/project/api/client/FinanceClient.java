package com.njydsz.project.api.client;

import com.njydsz.common.core.model.Result;
import com.njydsz.common.feign.constant.FeignClientConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 财务数据 Feign 接口（利润/成本/发票/回款）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "financeClient",
    path = FeignClientConstants.BASE_PATH)
public interface FinanceClient {

    @GetMapping("/project/finance/getProfitSnapshot")
    Result<?> getProfitSnapshot();
    @GetMapping("/project/finance/getCostSummary")
    Result<?> getCostSummary();
    @GetMapping("/project/finance/getRevenueSummary")
    Result<?> getRevenueSummary();
}
