package com.njydsz.project.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.model.Result;
import com.njydsz.project.api.client.ExecutionTimeEntryClient;
import com.njydsz.project.api.client.TimeEntryPageQuery;

import lombok.extern.slf4j.Slf4j;

/**
 * ExecutionTimeEntryClient 降级工厂
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ExecutionTimeEntryClientFallback implements FallbackFactory<ExecutionTimeEntryClient> {

    @Override
    public ExecutionTimeEntryClient create(Throwable cause) {
        log.warn("[ExecutionTimeEntryClient] 降级触发：{}", cause.getMessage());
        return new ExecutionTimeEntryClient() {
            @Override
            public Result<?> page(TimeEntryPageQuery query) {
                log.warn("[ExecutionTimeEntryClient] page 降级：reason=project 服务不可用");
                return Result.fail("工时服务不可用");
            }

            @Override
            public Result<?> getByEmployeeAndDate(String employeeId, String date) {
                log.warn("[ExecutionTimeEntryClient] getByEmployeeAndDate 降级：employeeId={}, date={}, reason=project 服务不可用",
                        employeeId, date);
                return Result.fail("工时服务不可用");
            }
        };
    }
}
