package com.njydsz.project.api.client;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.project.api.fallback.ProjectContractClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 合同管理 Feign 接口。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "projectContractClient",
    fallbackFactory = ProjectContractClientFallback.class)
public interface ProjectContractClient {

    @GetMapping("/project/projectcontract/getById")
    BaseResponse<?> getById(@RequestParam("id") String id);
    @GetMapping("/project/projectcontract/page")
    BaseResponse<?> page(@RequestParam("query") Object query);
    @GetMapping("/project/projectcontract/list")
    BaseResponse<?> listByInitiationId(@RequestParam("initiationId") String initiationId);
}
