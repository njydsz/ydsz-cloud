package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * InitiationFeignClient 降级工厂。
 *
 * <p>项目服务不可用时返回 SERVICE_UNAVAILABLE 占位结果，保证调用方主流程不被阻塞。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class InitiationFeignClientFallbackFactory implements FallbackFactory<InitiationFeignClient> {

    @Override
    public InitiationFeignClient create(Throwable cause) {
        log.warn("[InitiationFeignClient] Feign fallback triggered: {}",
                cause == null ? "null" : cause.getMessage());
        return new InitiationFeignClient() {
            @Override
            public Result<Void> markProcessing(Long initiationId) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<Void> markApproved(Long initiationId) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<Void> markRejected(Long initiationId, String reason) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
