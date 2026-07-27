package com.njydsz.project.api.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.fallback.ProjectInitiationClientFallback;

/**
 * 项目立项 Feign 接口。
 *
 * <p>路径与 {@code ProjectInitiationController} 的 {@code @RequestMapping} 对齐。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "projectInitiationClient",
    fallbackFactory = ProjectInitiationClientFallback.class)
public interface ProjectInitiationClient {

    /**
     * 根据 ID 查询项目立项。
     *
     * @param id 项目立项ID
     * @return 项目立项信息
     */
    @GetMapping("/api/v1/project/initiation/{id}")
    BaseResponse<Map<String, Object>> getById(@PathVariable("id") String id);

    /**
     * 根据项目编号查询项目立项。
     *
     * @param projectCode 项目编号
     * @return 项目立项信息
     */
    @GetMapping("/api/v1/project/initiation/code/{projectCode}")
    BaseResponse<Map<String, Object>> getByCode(@PathVariable("projectCode") String projectCode);

    /**
     * 根据项目经理 ID 查询项目列表。
     *
     * @param pmId 项目经理用户ID
     * @return 项目立项列表
     */
    @GetMapping("/api/v1/project/initiation/pm/{pmId}")
    BaseResponse<List<Map<String, Object>>> listByPmId(@PathVariable("pmId") String pmId);
}
