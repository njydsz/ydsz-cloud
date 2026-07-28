package com.njydsz.project.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.project.api.fallback.ProjectInfoClientFallback;

/**
 * 项目信息查询 Feign 客户端（供跨服务调用）。
 *
 * <p>提供项目基本信息和状态的远程查询能力。
 * 典型场景：系统管理模块查询项目名称、Agent 服务关联项目上下文等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = FeignClientConstants.PROJECT, contextId = "projectInfoClient",
        fallbackFactory = ProjectInfoClientFallback.class)

/**
 * ProjectInfoClient Feign 客户端接口，声明跨服务远程调用。
 *
 * <p>所属包：{@code com.njydsz.project.api.client}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ProjectInfoClient {

    /**
     * 按项目 ID 查询项目名称。
     *
     * @param projectId 项目 ID
     * @return 项目名称；不存在时返回 null
     */
    @GetMapping(FeignClientConstants.PROJECT_PATH_GET_BY_ID)
    BaseResponse<String> getProjectName(@RequestParam String projectId);

    /**
     * 查询项目当前状态。
     *
     * @param projectId 项目 ID
     * @return 项目状态编码（如 INITIATED/APPROVED/IN_PROGRESS/CLOSED）；不存在时返回 null
     */
    @GetMapping(FeignClientConstants.PROJECT_PATH_GET_STATUS)
    BaseResponse<String> getProjectStatus(@RequestParam String projectId);
}
