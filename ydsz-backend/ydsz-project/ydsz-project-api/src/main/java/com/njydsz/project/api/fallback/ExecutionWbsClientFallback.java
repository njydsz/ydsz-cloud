package com.njydsz.project.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.model.Result;
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
            public Result<?> getById(String id) {
                log.warn("[ExecutionWbsClient] getById 降级：id={}, reason=project 服务不可用", id);
                return Result.fail("WBS 服务不可用");
            }

            @Override
            public Result<?> listByInitiationId(String initiationId) {
                log.warn("[ExecutionWbsClient] listByInitiationId 降级：initiationId={}, reason=project 服务不可用",
                        initiationId);
                return Result.fail("WBS 服务不可用");
            }

            @Override
            public Result<?> getTree() {
                log.warn("[ExecutionWbsClient] getTree 降级：reason=project 服务不可用");
                return Result.fail("WBS 服务不可用");
            }
        };
    }
}
