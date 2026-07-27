package com.njydsz.cronjob.web.controller.job;

import java.util.List;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.cronjob.domain.entity.job.JobDO;
import com.njydsz.cronjob.domain.entity.job.JobHistoryDO;
import com.njydsz.cronjob.server.service.job.JobHistoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 任务配置历史版本 Controller（P1-6 任务版本管理）。
 *
 * <p>提供任务配置历史版本的查询、详情、回滚、对比等 HTTP 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "任务配置历史版本")
@RestController
@RequestMapping("/cronjob/history")
@RequiredArgsConstructor
public class JobHistoryController {

    /** 任务配置历史版本服务 */
    private final JobHistoryService jobHistoryService;

    /**
     * 获取指定任务的版本列表（按版本号降序）。
     *
     * @param jobId 任务 ID
     * @return 统一响应结果，包含历史版本列表
     */
    @Operation(summary = "获取任务版本列表")
    @GetMapping("/versions")
    public BaseResponse<List<JobHistoryDO>> versions(@RequestParam String jobId) {
        return BaseResponse.success(jobHistoryService.listVersions(jobId));
    }

    /**
     * 获取指定任务的指定历史版本详情。
     *
     * @param jobId   任务 ID
     * @param version 版本号
     * @return 统一响应结果，包含历史版本记录
     */
    @Operation(summary = "获取指定版本详情")
    @GetMapping("/detail")
    public BaseResponse<JobHistoryDO> detail(@RequestParam String jobId,
                                        @RequestParam Integer version) {
        return BaseResponse.success(jobHistoryService.getVersion(jobId, version));
    }

    /**
     * 回滚到指定版本。
     *
     * @param jobId   任务 ID
     * @param version 目标版本号
     * @return 统一响应结果，包含回滚后的任务定义
     */
    @Operation(summary = "回滚到指定版本")
    @Idempotent(key = "ydsz:cronjob:JobHistoryController:rollback:lock", ttlSeconds = 5)
    @RateLimit(resource = "cronjob.jobhistory.rollback", threshold = 50)
    @PostMapping("/rollback")
    public BaseResponse<JobDO> rollback(@RequestParam String jobId,
                                   @RequestParam Integer version) {
        return BaseResponse.success(jobHistoryService.rollback(jobId, version));
    }

    /**
     * 对比两个版本的差异。
     *
     * @param jobId 任务 ID
     * @param v1    旧版本号
     * @param v2    新版本号
     * @return 统一响应结果，包含差异字段列表
     */
    @Operation(summary = "对比两个版本差异")
    @GetMapping("/compare")
    public BaseResponse<List<Map<String, Object>>> compare(@RequestParam String jobId,
                                                      @RequestParam("v1") Integer version1,
                                                      @RequestParam("v2") Integer version2) {
        return BaseResponse.success(jobHistoryService.compareVersions(jobId, version1, version2));
    }
}
