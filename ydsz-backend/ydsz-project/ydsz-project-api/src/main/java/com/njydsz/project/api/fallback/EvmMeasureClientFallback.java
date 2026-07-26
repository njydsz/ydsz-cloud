package com.njydsz.project.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.model.Result;
import com.njydsz.project.api.client.EvmMeasureClient;

import lombok.extern.slf4j.Slf4j;

/**
 * EvmMeasureClient 降级工厂
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
            public Result<?> getByInitiationId(String initiationId) {
                log.warn("[EvmMeasureClient] getByInitiationId 降级：initiationId={}, reason=project 服务不可用",
                        initiationId);
                return Result.fail("EVM 服务不可用");
            }

            @Override
            public Result<?> getLatestSnapshot() {
                log.warn("[EvmMeasureClient] getLatestSnapshot 降级：reason=project 服务不可用");
                return Result.fail("EVM 服务不可用");
            }
        };
    }
}
