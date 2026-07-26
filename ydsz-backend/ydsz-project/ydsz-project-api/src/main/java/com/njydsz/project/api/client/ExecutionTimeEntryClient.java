package com.njydsz.project.api.client;

import com.njydsz.common.core.model.Result;
import com.njydsz.common.feign.constant.FeignClientConstants;
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
    path = FeignClientConstants.BASE_PATH)
public interface ExecutionTimeEntryClient {

    @GetMapping("/project/executiontimeentry/page")
    Result<?> page(TimeEntryPageQuery query);
    @GetMapping("/project/executiontimeentry/getByEmployeeAndDate")
    Result<?> getByEmployeeAndDate(@RequestParam("employeeId") String employeeId, @RequestParam("date") String date);
}
