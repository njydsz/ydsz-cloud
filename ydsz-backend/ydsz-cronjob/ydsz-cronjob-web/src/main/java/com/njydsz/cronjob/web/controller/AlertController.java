package com.njydsz.cronjob.web.controller.alert;

import java.time.LocalDateTime;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.dto.alert.AlertRuleSaveDTO;
import com.njydsz.cronjob.domain.entity.job.JobAlertLog;
import com.njydsz.cronjob.domain.entity.job.JobAlertRule;
import com.njydsz.cronjob.server.service.alert.AlertService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.vo.JobAlertLogVO;
import com.njydsz.cronjob.domain.vo.JobAlertRuleVO;
import com.njydsz.cronjob.domain.dto.post.AlertRulePostDTO;
import com.njydsz.cronjob.domain.dto.put.AlertRulePutDTO;

/**
 * 告警规则管理 Controller（P5 告警 + 监控）。
 *
 * <p>提供告警规则的增删改查 API 与告警日志查询 API。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "任务告警规则")
@RestController
@RequestMapping("/api/v1/cronjob/alert")
@RequiredArgsConstructor
public class AlertController {

    /** 告警规则与日志服务 */
    private final AlertService alertService;

    /**
     * 创建告警规则。
     *
     * @param dto 告警规则保存请求体
     * @return 统一响应结果，包含新增规则 ID
     */
    @Operation(summary = "创建告警规则")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_CREATE)
    @Idempotent(key = "ydsz:cronjob:AlertController:createRule:lock", ttlSeconds = 5)
    @Audit(module = "告警管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'createRule'")
    @RateLimit(resource = "cronjob.alert.createRule", threshold = 50)
    @PostMapping("/rule")
    public BaseResponse<String> createRule(@Valid @RequestBody AlertRulePostDTO dto) {
        return BaseResponse.success(alertService.create(toSaveDTO(dto)));
    }

    /**
     * 更新告警规则。
     *
     * @param id  规则 ID
     * @param dto 告警规则保存请求体
     * @return 统一响应结果
     */
    @Operation(summary = "更新告警规则")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_UPDATE)
    @Idempotent(key = "ydsz:cronjob:AlertController:updateRule:lock", ttlSeconds = 5)
    @Audit(module = "告警管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'updateRule'")
    @RateLimit(resource = "cronjob.alert.updateRule", threshold = 50)
    @PutMapping("/rule/{id}")
    public BaseResponse<Void> updateRule(@PathVariable String id, @Valid @RequestBody AlertRulePutDTO dto) {
        alertService.updateRule(id, toSaveDTO(dto));
        return BaseResponse.success();
    }

    /**
     * 删除告警规则。
     *
     * @param id 规则 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除告警规则")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_DELETE)
    @Idempotent(key = "ydsz:cronjob:AlertController:deleteRule:lock", ttlSeconds = 5)
    @Audit(module = "告警管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'deleteRule'")
    @RateLimit(resource = "cronjob.alert.deleteRule", threshold = 50)
    @DeleteMapping("/rule/{id}")
    public BaseResponse<Void> deleteRule(@PathVariable String id) {
        alertService.deleteRule(id);
        return BaseResponse.success();
    }

    /**
     * 查询告警规则详情。
     *
     * @param id 规则 ID
     * @return 统一响应结果，包含告警规则详情
     */
    @Operation(summary = "查询告警规则详情")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_VIEW)
    @GetMapping("/rule/{id}")
    public BaseResponse<JobAlertRuleVO> getRuleById(@PathVariable String id) {
        return BaseResponse.success(CronjobConverter.INSTANT.entityToVO(alertService.getRuleById(id)));
    }

    /**
     * 查询全部告警规则。
     *
     * @return 统一响应结果，包含告警规则列表
     */
    @Operation(summary = "查询全部告警规则")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_VIEW)
    @GetMapping("/rules")
    public BaseResponse<List<JobAlertRuleVO>> listRules() {
        return BaseResponse.success(CronjobConverter.INSTANT.jobAlertRuleListToVO(alertService.listRules()));
    }

    /**
     * 启用或禁用告警规则。
     *
     * @param id      规则 ID
     * @param enabled 启用状态（1=启用，0=禁用）
     * @return 统一响应结果
     */
    @Operation(summary = "启用/禁用告警规则")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_UPDATE)
    @Idempotent(key = "ydsz:cronjob:AlertController:toggleRule:lock", ttlSeconds = 5)
    @Audit(module = "告警管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'toggleRule'")
    @RateLimit(resource = "cronjob.alert.toggleRule", threshold = 50)
    @PutMapping("/rule/{id}/toggle")
    public BaseResponse<Void> toggleRule(@PathVariable String id, @RequestParam Integer enabled) {
        alertService.toggleRule(id, enabled);
        return BaseResponse.success();
    }

    /**
     * 查询任务告警历史日志。
     *
     * @param jobId 任务 ID
     * @param since 起始时间（可选，ISO 8601 格式）
     * @return 统一响应结果，包含告警日志列表
     */
    @Operation(summary = "查询任务告警历史")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_VIEW)
    @GetMapping("/logs/{jobId}")
    public BaseResponse<List<JobAlertLogVO>> queryAlertLogs(
            @PathVariable String jobId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return BaseResponse.success(CronjobConverter.INSTANT.jobAlertLogListToVO(alertService.queryAlertLogs(jobId, since)));
    }
    /**
     * 将 PostDTO 转换为 SaveDTO。
     */
    private AlertRuleSaveDTO toSaveDTO(AlertRulePostDTO dto) {
        AlertRuleSaveDTO saveDTO = new AlertRuleSaveDTO();
        saveDTO.setRuleName(dto.getRuleName());
        saveDTO.setJobId(dto.getJobId());
        saveDTO.setJobKey(dto.getJobKey());
        saveDTO.setAlertType(dto.getAlertType());
        saveDTO.setAlertLevel(dto.getAlertLevel());
        saveDTO.setThreshold(dto.getThreshold());
        saveDTO.setTimeWindowMinutes(dto.getTimeWindowMinutes());
        saveDTO.setChannels(dto.getChannels());
        saveDTO.setReceivers(dto.getReceivers());
        saveDTO.setCooldownMinutes(dto.getCooldownMinutes());
        saveDTO.setEnabled(dto.getEnabled());
        return saveDTO;
    }

    /**
     * 将 PutDTO 转换为 SaveDTO。
     */
    private AlertRuleSaveDTO toSaveDTO(AlertRulePutDTO dto) {
        AlertRuleSaveDTO saveDTO = new AlertRuleSaveDTO();
        saveDTO.setId(dto.getId());
        saveDTO.setRuleName(dto.getRuleName());
        saveDTO.setJobId(dto.getJobId());
        saveDTO.setJobKey(dto.getJobKey());
        saveDTO.setAlertType(dto.getAlertType());
        saveDTO.setAlertLevel(dto.getAlertLevel());
        saveDTO.setThreshold(dto.getThreshold());
        saveDTO.setTimeWindowMinutes(dto.getTimeWindowMinutes());
        saveDTO.setChannels(dto.getChannels());
        saveDTO.setReceivers(dto.getReceivers());
        saveDTO.setCooldownMinutes(dto.getCooldownMinutes());
        saveDTO.setEnabled(dto.getEnabled());
        return saveDTO;
    }
}
