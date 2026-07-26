package com.njydsz.project.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.client.FinanceClient;

import lombok.extern.slf4j.Slf4j;

/**
 * FinanceClient 降级工厂
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
            public BaseResponse<?> getProfitSnapshot() {
                log.warn("[FinanceClient] getProfitSnapshot 降级：reason=project 服务不可用");
                return BaseResponse.error("财务服务不可用");
            }

            @Override
            public BaseResponse<?> getCostSummary() {
                log.warn("[FinanceClient] getCostSummary 降级：reason=project 服务不可用");
                return BaseResponse.error("财务服务不可用");
            }

            @Override
            public BaseResponse<?> getRevenueSummary() {
                log.warn("[FinanceClient] getRevenueSummary 降级：reason=project 服务不可用");
                return BaseResponse.error("财务服务不可用");
            }
        };
    }
}
