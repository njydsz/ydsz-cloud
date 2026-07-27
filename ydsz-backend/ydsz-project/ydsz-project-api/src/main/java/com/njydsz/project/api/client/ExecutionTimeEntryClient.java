package com.njydsz.project.api.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.fallback.ExecutionTimeEntryClientFallback;

/**
 * 工时录入 Feign 接口。
 *
 * <p>路径与 {@code ExecutionTimeEntryController} 的 {@code @RequestMapping("/api/v1/project/execution/time/entry")} 对齐。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "executionTimeEntryClient",
    fallbackFactory = ExecutionTimeEntryClientFallback.class)
public interface ExecutionTimeEntryClient {

    @GetMapping("/api/v1/project/execution/time/entry/{id}")
    BaseResponse<Map<String, Object>> getById(@PathVariable("id") String id);

    @GetMapping("/api/v1/project/execution/time/entry/list")
    BaseResponse<List<Map<String, Object>>> listByEmployeeAndDate(
            @RequestParam("employeeId") String employeeId,
            @RequestParam("date") String date);
}
