package com.njydsz.project.api.fallback;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.client.ExecutionTimeEntryClient;

import lombok.extern.slf4j.Slf4j;

/**
 * ExecutionTimeEntryClient 降级工厂。
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
            public BaseResponse<Map<String, Object>> getById(String id) {
                return BaseResponse.error("工时服务不可用");
            }

            @Override
            public BaseResponse<List<Map<String, Object>>> listByEmployeeAndDate(String employeeId, String date) {
                return BaseResponse.error("工时服务不可用");
            }
        };
    }
}
