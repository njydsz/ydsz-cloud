package com.njydsz.project.api.client;

import com.njydsz.common.core.model.Result;
import com.njydsz.common.feign.constant.FeignClientConstants;
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
    path = FeignClientConstants.BASE_PATH)
public interface ExecutionWbsClient {

    @GetMapping("/project/executionwbs/getById")
    Result<?> getById(@RequestParam("id") String id);
    @GetMapping("/project/executionwbs/list")
    Result<?> listByInitiationId(String initiationId);
    @GetMapping("/project/executionwbs/getTree")
    Result<?> getTree();
}
