package com.njydsz.pmis.cronjob.controller.schedule;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.cronjob.entity.schedule.GlueCodeDO;
import com.njydsz.pmis.cronjob.service.schedule.GlueCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GLUE 在线编码 Controller（P1-2 GLUE 在线编码）。
 *
 * <p>提供 GLUE 代码的在线保存、查询最新版本、查询版本列表、按版本回滚等 API。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "GLUE 在线编码")
@RestController
@RequestMapping("/cronjob/glue")
@RequiredArgsConstructor
public class GlueCodeController {

    /** GLUE 在线编码服务 */
    private final GlueCodeService glueCodeService;

    /**
     * 保存 GLUE 代码（产生新版本）。
     *
     * @param request 保存请求体
     * @return 统一响应结果，包含新创建的 GLUE 代码版本
     */
    @Operation(summary = "保存 GLUE 代码（新版本）")
    @Idempotent(key = "glueCode:save", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/save")
    public Result<GlueCodeDO> save(@RequestBody GlueCodeSaveRequest request) {
        return Result.ok(glueCodeService.save(
                request.getJobId(),
                request.getSourceCode(),
                request.getLanguage(),
                request.getRemark()));
    }

    /**
     * 获取指定任务的最新版本 GLUE 代码。
     *
     * @param jobId 任务 ID
     * @return 统一响应结果，包含最新版本 GLUE 代码
     */
    @Operation(summary = "获取最新版本 GLUE 代码")
    @GetMapping("/latest")
    public Result<GlueCodeDO> latest(@RequestParam String jobId) {
        return Result.ok(glueCodeService.getLatest(jobId));
    }

    /**
     * 获取指定任务的全部版本列表（按版本号降序）。
     *
     * @param jobId 任务 ID
     * @return 统一响应结果，包含版本列表
     */
    @Operation(summary = "获取 GLUE 代码版本列表")
    @GetMapping("/versions")
    public Result<List<GlueCodeDO>> versions(@RequestParam String jobId) {
        return Result.ok(glueCodeService.listVersions(jobId));
    }

    /**
     * 回滚到指定版本。
     *
     * @param request 回滚请求体
     * @return 统一响应结果，包含新创建的回滚版本
     */
    @Operation(summary = "回滚到指定版本")
    @Idempotent(key = "glueCode:rollback", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/rollback")
    public Result<GlueCodeDO> rollback(@RequestBody GlueCodeRollbackRequest request) {
        return Result.ok(glueCodeService.rollback(request.getJobId(), request.getVersion()));
    }

    /**
     * 保存请求体。
     */
    @lombok.Data
    public static class GlueCodeSaveRequest {
        /** 任务 ID */
        private String jobId;
        /** 源代码 */
        private String sourceCode;
        /** 语言（GROOVY / JAVA） */
        private String language;
        /** 版本备注 */
        private String remark;
    }

    /**
     * 回滚请求体。
     */
    @lombok.Data
    public static class GlueCodeRollbackRequest {
        /** 任务 ID */
        private String jobId;
        /** 目标版本号 */
        private Integer version;
    }
}
