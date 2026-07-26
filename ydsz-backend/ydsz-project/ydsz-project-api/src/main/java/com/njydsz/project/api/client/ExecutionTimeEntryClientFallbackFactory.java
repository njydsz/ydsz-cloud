package com.njydsz.project.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.model.Result;
import com.njydsz.common.exception.custom.SysException;

/**
 * ExecutionTimeEntryClient 降级工厂。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class ExecutionTimeEntryClientFallbackFactory implements FallbackFactory<ExecutionTimeEntryClient> {

    @Override
    public ExecutionTimeEntryClient create(Throwable cause) {
        log.error("[Feign降级] ExecutionTimeEntryClient 调用失败", cause);
        return new ExecutionTimeEntryClient() {
            @Override
            public Result<?> getById(String id) {
                throw new SysException("项目服务暂不可用，请稍后重试");
            }
        };
    }
}
