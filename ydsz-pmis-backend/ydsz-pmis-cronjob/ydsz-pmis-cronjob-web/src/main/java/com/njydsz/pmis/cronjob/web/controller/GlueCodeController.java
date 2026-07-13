package com.njydsz.pmis.cronjob.web.controller.schedule;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.cronjob.domain.entity.schedule.GlueCodeDO;
import com.njydsz.pmis.cronjob.server.service.schedule.GlueCodeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    public BaseResponse<GlueCodeDO> save(@RequestBody GlueCodeSaveRequest request) {
        return BaseResponse.ok(glueCodeService.save(
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
    public BaseResponse<GlueCodeDO> latest(@RequestParam String jobId) {
        return BaseResponse.ok(glueCodeService.getLatest(jobId));
    }

    /**
     * 获取指定任务的全部版本列表（按版本号降序）。
     *
     * @param jobId 任务 ID
     * @return 统一响应结果，包含版本列表
     */
    @Operation(summary = "获取 GLUE 代码版本列表")
    @GetMapping("/versions")
    public BaseResponse<List<GlueCodeDO>> versions(@RequestParam String jobId) {
        return BaseResponse.ok(glueCodeService.listVersions(jobId));
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
    public BaseResponse<GlueCodeDO> rollback(@RequestBody GlueCodeRollbackRequest request) {
        return BaseResponse.ok(glueCodeService.rollback(request.getJobId(), request.getVersion()));
    }

    /**
     * P1-1: 在线测试 GLUE 代码（不保存版本，直接执行）。
     *
     * <p>业务侧在线编辑器中点击"测试运行"时调用此接口。
     * 代码不持久化，仅在内存中编译执行并返回结果。
     *
     * @param request 测试请求体
     * @return 统一响应结果，包含执行结果或错误信息
     */
    @Operation(summary = "在线测试 GLUE 代码")
    @PostMapping("/test")
    public BaseResponse<Map<String, Object>> test(@RequestBody GlueTestRequest request) {
        return BaseResponse.ok(glueCodeService.testCode(
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
    @GetMapping("/template")
    public BaseResponse<Map<String, String>> template(@RequestParam(defaultValue = "GROOVY") String language) {
        return BaseResponse.ok(glueCodeService.getCodeTemplate(language));
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
    @GetMapping("/diff")
    public BaseResponse<Map<String, Object>> diff(@RequestParam String jobId,
                                             @RequestParam Integer versionA,
                                             @RequestParam Integer versionB) {
        return BaseResponse.ok(glueCodeService.diffVersions(jobId, versionA, versionB));
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

    /**
     * P1-1: 在线测试请求体。
     */
    @lombok.Data
    public static class GlueTestRequest {
        /** 源代码 */
        private String sourceCode;
        /** 语言（GROOVY / PYTHON / SHELL / JAVASCRIPT） */
        private String language;
        /** 测试参数（JSON 字符串） */
        private String paramsJson;
    }
}
