package com.njydsz.pmis.project.api.fallback;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.project.api.client.InitiationFeignClient;
import com.njydsz.pmis.project.api.dto.InitiationCreateDTO;

import lombok.extern.slf4j.Slf4j;

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
            public BaseResponse<Void> markProcessing(String initiationId) {
                return BaseResponse.failed(StandardResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public BaseResponse<Void> markApproved(String initiationId) {
                return BaseResponse.failed(StandardResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public BaseResponse<Void> markRejected(String initiationId, String reason) {
                return BaseResponse.failed(StandardResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public BaseResponse<String> create(InitiationCreateDTO dto) {
                return BaseResponse.failed(StandardResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
