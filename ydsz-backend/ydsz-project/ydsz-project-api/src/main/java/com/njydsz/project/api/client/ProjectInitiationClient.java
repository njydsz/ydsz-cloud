package com.njydsz.project.api.client;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.constant.FeignClientConstants;
import com.njydsz.project.api.fallback.ProjectInitiationClientFallback;
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
    path = FeignClientConstants.BASE_PATH,
    fallbackFactory = ProjectInitiationClientFallback.class)
public interface ProjectInitiationClient {

    @GetMapping("/project/projectinitiation/getById")
    BaseResponse<?> getById(@RequestParam("id") String id);
    @GetMapping("/project/projectinitiation/page")
    BaseResponse<?> page(@RequestParam("query") Object query);
    @GetMapping("/project/projectinitiation/getByCode")
    BaseResponse<?> getByCode(@RequestParam("projectCode") String projectCode);
}
