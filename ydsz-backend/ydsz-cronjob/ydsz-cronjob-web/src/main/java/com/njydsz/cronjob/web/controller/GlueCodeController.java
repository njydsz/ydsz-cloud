package com.njydsz.cronjob.web.controller.schedule;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.annotation.RateLimit;
import com.njydsz.cronjob.domain.entity.schedule.GlueCodeDO;
import com.njydsz.cronjob.server.service.schedule.GlueCodeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GLUE 在线编码 Controller（P1-2 GLUE 在线编码）。
 *
 * <p>提供 GLUE 代码的在线保存、查询最新版本、查询版本列表、按版本回滚等 API。
 *
 * <p>P0-5：补齐全部接口的权限/审计/限流/校验注解：
 * <ul>
 *   <li>读接口（latest/versions/template/diff）统一 {@code CRONJOB_GLUE_VIEW} 权限</li>
 *   <li>写接口（save/rollback）统一 {@code CRONJOB_GLUE_MANAGE} 权限 + 审计日志 + 幂等</li>
 *   <li>测试接口（test）独立 {@code CRONJOB_GLUE_TEST} 权限 + 审计日志 + 限流（防止在线执行任意代码被滥用）</li>
 *   <li>所有 {@code @RequestBody} 加 {@code @Valid}，字段加 {@code @NotBlank}/@NotNull 校验</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "GLUE 在线编码")
@RestController
@RequestMapping("/cronjob/glue")
@RequiredArgsConstructor
@Validated
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
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_GLUE_MANAGE)
    @Idempotent(key = "glueCode:save", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/save")
    public BaseResponse<GlueCodeDO> save(@Valid @RequestBody GlueCodeSaveRequest request) {
        return BaseResponse.success(glueCodeService.save(
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
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_GLUE_VIEW)
    @GetMapping("/latest")
    public BaseResponse<GlueCodeDO> latest(@RequestParam String jobId) {
        return BaseResponse.success(glueCodeService.getLatest(jobId));
    }

    /**
     * 获取指定任务的全部版本列表（按版本号降序）。
     *
     * @param jobId 任务 ID
     * @return 统一响应结果，包含版本列表
     */
    @Operation(summary = "获取 GLUE 代码版本列表")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_GLUE_VIEW)
    @GetMapping("/versions")
    public BaseResponse<List<GlueCodeDO>> versions(@RequestParam String jobId) {
        return BaseResponse.success(glueCodeService.listVersions(jobId));
    }

    /**
     * 回滚到指定版本。
     *
     * @param request 回滚请求体
     * @return 统一响应结果，包含新创建的回滚版本
     */
    @Operation(summary = "回滚到指定版本")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_GLUE_MANAGE)
    @Idempotent(key = "glueCode:rollback", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/rollback")
    public BaseResponse<GlueCodeDO> rollback(@Valid @RequestBody GlueCodeRollbackRequest request) {
        return BaseResponse.success(glueCodeService.rollback(request.getJobId(), request.getVersion()));
    }

    /**
     * P1-1: 在线测试 GLUE 代码（不保存版本，直接执行）。
     *
     * <p>业务侧在线编辑器中点击"测试运行"时调用此接口。
     * 代码不持久化，仅在内存中编译执行并返回结果。
     *
     * <p>P0-5：该接口允许在服务端执行任意代码，属于高风险接口，独立分配 {@code CRONJOB_GLUE_TEST}
     * 权限码，并加 {@code @RateLimit} 限流（60s 内最多 10 次），防止滥用导致 CPU/内存被打满。
     *
     * @param request 测试请求体
     * @return 统一响应结果，包含执行结果或错误信息
     */
    @Operation(summary = "在线测试 GLUE 代码")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_GLUE_TEST)
    @RateLimit(key = "glue:test", qps = 10, windowSeconds = 60, message = "在线测试过于频繁，请稍后重试")
    @PostMapping("/test")
    public BaseResponse<Map<String, Object>> test(@Valid @RequestBody GlueTestRequest request) {
        return BaseResponse.success(glueCodeService.testCode(
                request.getSourceCode(),
                request.getLanguage(),
                request.getParamsJson()));
    }

    /**
     * P1-1: 获取代码模板。
     *
     * <p>根据语言返回对应的代码模板，便于业务侧快速开始。
     *
     * @param language 语言（GROOVY / PYTHON / SHELL / JAVASCRIPT）
     * @return 统一响应结果，包含模板代码
     */
    @Operation(summary = "获取代码模板")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_GLUE_VIEW)
    @GetMapping("/template")
    public BaseResponse<Map<String, String>> template(@RequestParam(defaultValue = "GROOVY") String language) {
        return BaseResponse.success(glueCodeService.getCodeTemplate(language));
    }

    /**
     * P1-1: 对比两个版本的差异。
     *
     * @param jobId     任务 ID
     * @param versionA  版本 A
     * @param versionB  版本 B
     * @return 统一响应结果，包含差异信息
     */
    @Operation(summary = "对比版本差异")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_GLUE_VIEW)
    @GetMapping("/diff")
    public BaseResponse<Map<String, Object>> diff(@RequestParam String jobId,
                                             @RequestParam Integer versionA,
                                             @RequestParam Integer versionB) {
        return BaseResponse.success(glueCodeService.diffVersions(jobId, versionA, versionB));
    }

    /**
     * 保存请求体。
     */
    @lombok.Data
    public static class GlueCodeSaveRequest {
        /** 任务 ID */
        @NotBlank(message = "任务 ID 不能为空")
        private String jobId;
        /** 源代码 */
        @NotBlank(message = "源代码不能为空")
        private String sourceCode;
        /** 语言（GROOVY / JAVA） */
        @NotBlank(message = "语言不能为空")
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
        @NotBlank(message = "任务 ID 不能为空")
        private String jobId;
        /** 目标版本号 */
        @NotNull(message = "目标版本号不能为空")
        private Integer version;
    }

    /**
     * P1-1: 在线测试请求体。
     */
    @lombok.Data
    public static class GlueTestRequest {
        /** 源代码 */
        @NotBlank(message = "源代码不能为空")
        private String sourceCode;
        /** 语言（GROOVY / PYTHON / SHELL / JAVASCRIPT） */
        @NotBlank(message = "语言不能为空")
        private String language;
        /** 测试参数（JSON 字符串） */
        private String paramsJson;
    }
}
