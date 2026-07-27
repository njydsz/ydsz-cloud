package com.njydsz.project.api.client;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.project.api.fallback.ExecutionWbsClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * WBS 任务执行 Feign 接口。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "executionWbsClient",
    fallbackFactory = ExecutionWbsClientFallback.class)
public interface ExecutionWbsClient {

    @GetMapping("/project/executionwbs/getById")
    BaseResponse<?> getById(@RequestParam("id") String id);
    @GetMapping("/project/executionwbs/list")
    BaseResponse<?> listByInitiationId(@RequestParam("initiationId") String initiationId);
    @GetMapping("/project/executionwbs/getTree")
    BaseResponse<?> getTree();
}
