package com.njydsz.cronjob.web.controller.alert;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.dto.alert.AlertRuleSaveDTO;
import com.njydsz.cronjob.domain.dto.post.AlertRulePostDTO;
import com.njydsz.cronjob.domain.dto.put.AlertRulePutDTO;
import com.njydsz.cronjob.domain.vo.JobAlertLogVO;
import com.njydsz.cronjob.domain.vo.JobAlertRuleVO;
import com.njydsz.cronjob.server.service.alert.AlertService;

/**
 * 告警规则管理 Controller（P5 告警 + 监控）。
 *
 * <p>提供任务告警的全生命周期管理 REST API：
 *
 * <ul>
 *   <li>规则 CRUD：创建 / 更新 / 删除 / 详情 / 列表
 *   <li>规则状态：启用 / 禁用
 *   <li>告警日志：按任务 ID + 时间范围查询告警历史
 * </ul>
 *
 * <h3>告警通道</h3>
 *
 * 告警通过 {@link AlertChannel} SPI 派发，支持邮件/短信/企业微信/钉钉/飞书/Webhook。 告警冷却（cooldown）防止短时间内重复轰炸。
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>所有写操作 {@link Idempotent} 防重（5s TTL）
 *   <li>权限按告警维度细分（CRONJOB_ALERT_CREATE/UPDATE/DELETE/VIEW）
 *   <li>所有变更 {@link Audit} 异步落库审计日志
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "任务告警规则", description = "告警规则 CRUD、启停、告警日志查询")
@RestController
@RequestMapping("/api/v1/cronjob/alert")
@RequiredArgsConstructor
public class AlertController {

  /** 告警规则与日志服务 */
  private final AlertService alertService;

  /**
   * 创建告警规则。
   *
   * <p>为指定任务/任务组绑定告警规则。规则匹配后由 {@code AlertScanner} 周期性扫描触发告警。 同一 jobId+alertType 只能有一条规则（唯一索引约束）。
   *
   * @param dto 告警规则保存请求体
   * @return 新规则 ID
   */
  @Operation(summary = "创建告警规则")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_CREATE)
  @Idempotent(key = "ydsz:cronjob:AlertController:createRule:lock", ttlSeconds = 5)
  @Audit(
      module = "告警管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'createRule'")
  @RateLimit(resource = "cronjob.alert.createRule", threshold = 50)
  @PostMapping("/rule")
  public BaseResponse<String> createRule(@Valid @RequestBody AlertRulePostDTO dto) {
    return BaseResponse.success(alertService.createRule(toSaveDTO(dto)));
  }

  /**
   * 更新告警规则。
   *
   * <p>修改阈值/通道/接收人等配置。规则 ID 不可变更；变更后下一次扫描立即生效。
   *
   * @param id 规则 ID
   * @param dto 告警规则保存请求体
   * @return 统一响应结果
   */
  @Operation(summary = "更新告警规则")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_UPDATE)
  @Idempotent(key = "ydsz:cronjob:AlertController:updateRule:lock", ttlSeconds = 5)
  @Audit(
      module = "告警管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'updateRule'")
  @RateLimit(resource = "cronjob.alert.updateRule", threshold = 50)
  @PutMapping("/rule/{id}")
  public BaseResponse<Void> updateRule(
      @PathVariable String id, @Valid @RequestBody AlertRulePutDTO dto) {
    alertService.updateRule(id, toSaveDTO(dto));
    return BaseResponse.success();
  }

  /**
   * 删除告警规则。
   *
   * <p>逻辑删除（status 置为 DELETED）。历史告警日志保留，仅规则不再生效。
   *
   * @param id 规则 ID
   * @return 统一响应结果
   */
  @Operation(summary = "删除告警规则")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_DELETE)
  @Idempotent(key = "ydsz:cronjob:AlertController:deleteRule:lock", ttlSeconds = 5)
  @Audit(
      module = "告警管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'deleteRule'")
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
   * @return 告警规则详情 VO
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
   * <p>用于规则管理页面与规则下拉选择器。
   *
   * @return 告警规则列表
   */
  @Operation(summary = "查询全部告警规则")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_VIEW)
  @GetMapping("/rules")
  public BaseResponse<List<JobAlertRuleVO>> listRules() {
    return BaseResponse.success(
        CronjobConverter.INSTANT.jobAlertRuleListToVO(alertService.listRules()));
  }

  /**
   * 启用或禁用告警规则。
   *
   * <p>提供"软启停"能力，避免频繁增删。enabled=1 启用，enabled=0 禁用。
   *
   * @param id 规则 ID
   * @param enabled 启用状态（1=启用，0=禁用）
   * @return 统一响应结果
   */
  @Operation(summary = "启用/禁用告警规则")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_UPDATE)
  @Idempotent(key = "ydsz:cronjob:AlertController:toggleRule:lock", ttlSeconds = 5)
  @Audit(
      module = "告警管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'toggleRule'")
  @RateLimit(resource = "cronjob.alert.toggleRule", threshold = 50)
  @PutMapping("/rule/{id}/toggle")
  public BaseResponse<Void> toggleRule(@PathVariable String id, @RequestParam Integer enabled) {
    alertService.toggleRule(id, enabled);
    return BaseResponse.success();
  }

  /**
   * 查询任务告警历史日志。
   *
   * <p>按告警时间倒序返回所有历史告警记录（含发送状态、通道、接收人、错误信息等）。 配合前端告警面板用于排查"为什么没收到告警"等问题。
   *
   * @param jobId 任务 ID
   * @param since 起始时间（可选，ISO 8601 格式）
   * @return 告警日志列表
   */
  @Operation(summary = "查询任务告警历史")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_ALERT_VIEW)
  @GetMapping("/logs/{jobId}")
  public BaseResponse<List<JobAlertLogVO>> queryAlertLogs(
      @PathVariable String jobId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime since) {
    return BaseResponse.success(
        CronjobConverter.INSTANT.jobAlertLogListToVO(alertService.queryAlertLogs(jobId, since)));
  }

  /** 将 PostDTO 转换为 SaveDTO。 */
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

  /** 将 PutDTO 转换为 SaveDTO。 */
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
