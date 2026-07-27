package com.njydsz.project.api.fallback;

import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.client.FinanceClient;

import lombok.extern.slf4j.Slf4j;

/**
 * FinanceClient 降级工厂。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class FinanceClientFallback implements FallbackFactory<FinanceClient> {

    @Override
    public FinanceClient create(Throwable cause) {
        log.warn("[FinanceClient] 降级触发：{}", cause.getMessage());
        return new FinanceClient() {
            @Override
            public BaseResponse<Map<String, Object>> getProfitSnapshot(String projectId) {
                return BaseResponse.error("财务服务不可用");
            }

            @Override
            public BaseResponse<Map<String, Object>> getCostSummary(String projectId) {
                return BaseResponse.error("财务服务不可用");
            }

            @Override
            public BaseResponse<Map<String, Object>> getRevenueSummary(String projectId) {
                return BaseResponse.error("财务服务不可用");
            }
        };
    }
}
