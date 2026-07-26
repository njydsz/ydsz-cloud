package com.njydsz.project.api.client;

import com.njydsz.common.core.model.Result;
import com.njydsz.common.feign.constant.FeignClientConstants;
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
    path = FeignClientConstants.BASE_PATH)
public interface ProjectContractClient {

    @GetMapping("/project/projectcontract/getById")
    Result<?> getById(@RequestParam("id") String id);
    @GetMapping("/project/projectcontract/page")
    Result<?> page(ProjectContractPageQuery query);
    @GetMapping("/project/projectcontract/list")
    Result<?> listByInitiationId(String initiationId);
}
