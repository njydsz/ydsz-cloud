package com.njydsz.project.api.fallback;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.client.EvmMeasureClient;

import lombok.extern.slf4j.Slf4j;

/**
 * EvmMeasureClient 降级工厂。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class EvmMeasureClientFallback implements FallbackFactory<EvmMeasureClient> {

    @Override
    public EvmMeasureClient create(Throwable cause) {
        log.warn("[EvmMeasureClient] 降级触发：{}", cause.getMessage());
        return new EvmMeasureClient() {
            @Override
            public BaseResponse<Map<String, Object>> getById(String id) {
                return BaseResponse.error("EVM 服务不可用");
            }

            @Override
            public BaseResponse<List<Map<String, Object>>> listByInitiationId(String initiationId) {
                return BaseResponse.error("EVM 服务不可用");
            }
        };
    }
}
