package com.njydsz.project.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.client.ExecutionWbsClient;

import lombok.extern.slf4j.Slf4j;

/**
 * ExecutionWbsClient 降级工厂
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ExecutionWbsClientFallback implements FallbackFactory<ExecutionWbsClient> {

    @Override
    public ExecutionWbsClient create(Throwable cause) {
        log.warn("[ExecutionWbsClient] 降级触发：{}", cause.getMessage());
        return new ExecutionWbsClient() {
            @Override
            public BaseResponse<?> getById(String id) {
                log.warn("[ExecutionWbsClient] getById 降级：id={}, reason=project 服务不可用", id);
                return BaseResponse.error("WBS 服务不可用");
            }

            @Override
            public BaseResponse<?> listByInitiationId(String initiationId) {
                log.warn("[ExecutionWbsClient] listByInitiationId 降级：initiationId={}, reason=project 服务不可用",
                        initiationId);
                return BaseResponse.error("WBS 服务不可用");
            }

            @Override
            public BaseResponse<?> getTree() {
                log.warn("[ExecutionWbsClient] getTree 降级：reason=project 服务不可用");
                return BaseResponse.error("WBS 服务不可用");
            }
        };
    }
}
