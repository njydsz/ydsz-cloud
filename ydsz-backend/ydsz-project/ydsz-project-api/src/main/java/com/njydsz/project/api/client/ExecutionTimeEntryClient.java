package com.njydsz.project.api.client;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.project.api.fallback.ExecutionTimeEntryClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工时录入 Feign 接口。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "executionTimeEntryClient",
    fallbackFactory = ExecutionTimeEntryClientFallback.class)
public interface ExecutionTimeEntryClient {

    @GetMapping("/project/executiontimeentry/page")
    BaseResponse<?> page(@RequestParam("query") Object query);
    @GetMapping("/project/executiontimeentry/getByEmployeeAndDate")
    BaseResponse<?> getByEmployeeAndDate(@RequestParam("employeeId") String employeeId, @RequestParam("date") String date);
}
