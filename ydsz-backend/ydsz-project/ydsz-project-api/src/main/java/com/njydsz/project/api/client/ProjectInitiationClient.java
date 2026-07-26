package com.njydsz.project.api.client;

import com.njydsz.common.core.model.Result;
import com.njydsz.common.feign.constant.FeignClientConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 项目立项 Feign 接口。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "projectInitiationClient",
    path = FeignClientConstants.BASE_PATH)
public interface ProjectInitiationClient {

    @GetMapping("/project/projectinitiation/getById")
    Result<?> getById(@RequestParam("id") String id);
    @GetMapping("/project/projectinitiation/page")
    Result<?> page(ProjectInitiationPageQuery query);
    @GetMapping("/project/projectinitiation/getByCode")
    Result<?> getByCode(@RequestParam("projectCode") String projectCode);
}
