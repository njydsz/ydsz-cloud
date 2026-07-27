package com.njydsz.project.api.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.fallback.ExecutionWbsClientFallback;

/**
 * WBS 任务执行 Feign 接口。
 *
 * <p>路径与 {@code ExecutionWbsTaskController} 的 {@code @RequestMapping("/api/v1/project/execution/wbs/task")} 对齐。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "executionWbsClient",
    fallbackFactory = ExecutionWbsClientFallback.class)
public interface ExecutionWbsClient {

    @GetMapping("/api/v1/project/execution/wbs/task/{id}")
    BaseResponse<Map<String, Object>> getById(@PathVariable("id") String id);

    @GetMapping("/api/v1/project/execution/wbs/task/list")
    BaseResponse<List<Map<String, Object>>> listByInitiationId(@RequestParam("initiationId") String initiationId);
}
