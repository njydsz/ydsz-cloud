package com.njydsz.project.api.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.fallback.FinanceClientFallback;

/**
 * 财务数据 Feign 接口（利润/成本/发票/回款）。
 *
 * <p>财务数据为复合查询，通过 project-server 的 Controller 直接暴露汇总接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "financeClient",
    fallbackFactory = FinanceClientFallback.class)
public interface FinanceClient {

    @GetMapping("/api/v1/project/finance/profit-snapshot")
    BaseResponse<Map<String, Object>> getProfitSnapshot(@RequestParam(value = "projectId", required = false) String projectId);

    @GetMapping("/api/v1/project/finance/cost-summary")
    BaseResponse<Map<String, Object>> getCostSummary(@RequestParam(value = "projectId", required = false) String projectId);

    @GetMapping("/api/v1/project/finance/revenue-summary")
    BaseResponse<Map<String, Object>> getRevenueSummary(@RequestParam(value = "projectId", required = false) String projectId);
}
