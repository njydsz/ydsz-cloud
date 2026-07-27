package com.njydsz.cronjob.web.controller.alert;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.dto.alert.JobSlaSaveDTO;
import com.njydsz.cronjob.domain.entity.alert.JobSlaDO;
import com.njydsz.cronjob.server.service.alert.JobSlaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * SLA 管理 Controller（P2-7 SLA 管理）。
 *
 * <p>提供 SLA 规则的 CRUD 接口与违约检查接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "任务 SLA 管理")
@RestController
@RequestMapping("/cronjob/sla")
@RequiredArgsConstructor
public class JobSlaController {

    /** SLA 管理服务 */
    private final JobSlaService jobSlaService;

    /**
     * 创建 SLA 规则。
     *
     * @param dto SLA 规则保存请求体
     * @return 统一响应结果，包含新增 SLA ID
     */
    @Operation(summary = "创建 SLA 规则")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_SLA_CREATE)
    @Idempotent(key = "ydsz:cronjob:JobSlaController:create:lock", ttlSeconds = 5)
    @Audit(module = "SLA管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'create'")
    @SentinelRateLimit(resource = "cronjob.jobsla.create", threshold = 50)
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody JobSlaSaveDTO dto) {
        return BaseResponse.success(jobSlaService.createSla(dto));
    }

    /**
     * 更新 SLA 规则。
     *
     * @param id  SLA 规则 ID
     * @param dto SLA 规则保存请求体
     * @return 统一响应结果
     */
    @Operation(summary = "更新 SLA 规则")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_SLA_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobSlaController:update:lock", ttlSeconds = 5)
    @Audit(module = "SLA管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'update'")
    @SentinelRateLimit(resource = "cronjob.jobsla.update", threshold = 50)
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody JobSlaSaveDTO dto) {
        jobSlaService.updateSla(id, dto);
        return BaseResponse.success();
    }

    /**
     * 删除 SLA 规则。
     *
     * @param id SLA 规则 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除 SLA 规则")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_SLA_DELETE)
    @Idempotent(key = "ydsz:cronjob:JobSlaController:delete:lock", ttlSeconds = 5)
    @Audit(module = "SLA管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @SentinelRateLimit(resource = "cronjob.jobsla.delete", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        jobSlaService.deleteSla(id);
        return BaseResponse.success();
    }

    /**
     * 查询 SLA 规则详情。
     *
     * @param id SLA 规则 ID
     * @return 统一响应结果，包含 SLA 规则详情
     */
    @Operation(summary = "查询 SLA 规则详情")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_SLA_VIEW)
    @GetMapping("/{id}")
    public BaseResponse<JobSlaDO> getById(@PathVariable String id) {
        return BaseResponse.success(jobSlaService.getSlaById(id));
    }

    /**
     * 查询全部 SLA 规则。
     *
     * @return 统一响应结果，包含 SLA 规则列表
     */
    @Operation(summary = "查询全部 SLA 规则")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_SLA_VIEW)
    @GetMapping("/list")
    public BaseResponse<List<JobSlaDO>> list() {
        return BaseResponse.success(jobSlaService.listSla());
    }

    /**
     * 启用或禁用 SLA 规则。
     *
     * @param id      SLA 规则 ID
     * @param enabled 启用状态（1=启用，0=禁用）
     * @return 统一响应结果
     */
    @Operation(summary = "启用/禁用 SLA 规则")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_SLA_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobSlaController:toggle:lock", ttlSeconds = 5)
    @Audit(module = "SLA管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'toggle'")
    @SentinelRateLimit(resource = "cronjob.jobsla.toggle", threshold = 50)
    @PutMapping("/{id}/toggle")
    public BaseResponse<Void> toggle(@PathVariable String id, @RequestParam Integer enabled) {
        jobSlaService.toggleSla(id, enabled);
        return BaseResponse.success();
    }

    /**
     * 检查任务是否违反 SLA。
     *
     * @param jobId 任务 ID
     * @return 统一响应结果，包含违约记录列表
     */
    @Operation(summary = "检查任务是否违反 SLA")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_SLA_VIEW)
    @GetMapping("/check")
    public BaseResponse<List<JobSlaService.SlaViolation>> checkViolation(@RequestParam String jobId) {
        return BaseResponse.success(jobSlaService.checkViolation(jobId));
    }
}
