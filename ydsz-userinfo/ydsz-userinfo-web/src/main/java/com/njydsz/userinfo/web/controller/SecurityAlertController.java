package com.njydsz.userinfo.web.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.userinfo.domain.alert.SecurityAlert;
import com.njydsz.userinfo.domain.alert.SecurityAlertRepository;

/**
 * 安全告警管理 Controller。
 *
 * <p>为管理员提供安全告警的查询和处理接口，包括：
 *
 * <ul>
 *   <li>分页查询告警列表（支持按状态、风险等级、时间范围过滤）</li>
 *   <li>确认/忽略告警</li>
 *   <li>获取待处理告警数量统计</li>
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/admin/security/alerts}
 *
 * <p><b>权限要求：</b>所有接口需 {@code admin:security:alert} 权限。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/security/alerts")
@RequiredArgsConstructor
@Tag(name = "安全告警管理", description = "安全告警查询与处理")
public class SecurityAlertController {

  private final SecurityAlertRepository alertRepository;

  /**
   * 分页查询安全告警列表。
   *
   * @param status 告警状态（PENDING/ACKNOWLEDGED/RESOLVED/IGNORED），可为空
   * @param riskLevel 风险等级（LOW/MEDIUM/HIGH/CRITICAL），可为空
   * @param start 起始日期，可为空（默认 7 天前）
   * @param end 结束日期，可为空（默认今天）
   * @param pageNum 页码（默认 1）
   * @param pageSize 每页大小（默认 20，最大 100）
   * @return 分页告警列表
   */
  @GetMapping
  @AuthApiPermission(apiCodes = "admin:security:alert")
  @Operation(summary = "分页查询安全告警")
  public YdszResponse<PageResponse<List<SecurityAlert>>> pageAlerts(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String riskLevel,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int pageSize) {
    // 限制每页大小上限
    if (pageSize > 100) {
      pageSize = 100;
    }
    // 转换参数
    SecurityAlert.AlertStatus alertStatus = null;
    if (status != null && !status.isBlank()) {
      try {
        alertStatus = SecurityAlert.AlertStatus.valueOf(status.toUpperCase());
      } catch (IllegalArgumentException e) {
        // 忽略无效的状态值
        log.debug("[SecurityAlert] 忽略无效的告警状态值: status={}", status);
      }
    }
    SecurityAlert.RiskLevel riskLevelEnum = null;
    if (riskLevel != null && !riskLevel.isBlank()) {
      try {
        riskLevelEnum = SecurityAlert.RiskLevel.valueOf(riskLevel.toUpperCase());
      } catch (IllegalArgumentException e) {
        // 忽略无效的风险等级值
        log.debug("[SecurityAlert] 忽略无效的风险等级值: riskLevel={}", riskLevel);
      }
    }
    LocalDateTime startTime = start != null ? start.atStartOfDay() : null;
    LocalDateTime endTime = end != null ? end.atTime(LocalTime.MAX) : null;
    return YdszResponse.success(
        alertRepository.page(alertStatus, riskLevelEnum, startTime, endTime, pageNum, pageSize));
  }

  /**
   * 获取待处理的告警列表。
   *
   * @param riskLevel 风险等级过滤（可为空）
   * @param limit 数量限制（默认 50）
   * @return 待处理告警列表
   */
  @GetMapping("/pending")
  @AuthApiPermission(apiCodes = "admin:security:alert")
  @Operation(summary = "获取待处理告警")
  public YdszResponse<List<SecurityAlert>> getPendingAlerts(
      @RequestParam(required = false) String riskLevel,
      @RequestParam(defaultValue = "50") int limit) {
    SecurityAlert.RiskLevel riskLevelEnum = null;
    if (riskLevel != null && !riskLevel.isBlank()) {
      try {
        riskLevelEnum = SecurityAlert.RiskLevel.valueOf(riskLevel.toUpperCase());
      } catch (IllegalArgumentException e) {
        // 忽略无效的风险等级值
        log.debug("[SecurityAlert] 忽略无效的风险等级值: riskLevel={}", riskLevel);
      }
    }
    return YdszResponse.success(alertRepository.findPendingAlerts(riskLevelEnum, limit));
  }

  /**
   * 确认告警（将状态更新为 ACKNOWLEDGED）。
   *
   * @param id 告警 ID
   * @param note 处理备注（可选）
   * @return 操作结果
   */
  @PutMapping("/{id}/acknowledge")
  @AuthApiPermission(apiCodes = "admin:security:alert")
  @Operation(summary = "确认告警")
  public YdszResponse<Boolean> acknowledgeAlert(
      @PathVariable String id,
      @RequestParam(required = false) String note) {
    return YdszResponse.success(
        alertRepository.updateStatus(id, SecurityAlert.AlertStatus.ACKNOWLEDGED, note));
  }

  /**
   * 处理完成告警（将状态更新为 RESOLVED）。
   *
   * @param id 告警 ID
   * @param note 处理备注（可选）
   * @return 操作结果
   */
  @PutMapping("/{id}/resolve")
  @AuthApiPermission(apiCodes = "admin:security:alert")
  @Operation(summary = "处理完成告警")
  public YdszResponse<Boolean> resolveAlert(
      @PathVariable String id,
      @RequestParam(required = false) String note) {
    return YdszResponse.success(
        alertRepository.updateStatus(id, SecurityAlert.AlertStatus.RESOLVED, note));
  }

  /**
   * 忽略告警（将状态更新为 IGNORED）。
   *
   * @param id 告警 ID
   * @param note 忽略原因（可选）
   * @return 操作结果
   */
  @PutMapping("/{id}/ignore")
  @AuthApiPermission(apiCodes = "admin:security:alert")
  @Operation(summary = "忽略告警")
  public YdszResponse<Boolean> ignoreAlert(
      @PathVariable String id,
      @RequestParam(required = false) String note) {
    return YdszResponse.success(
        alertRepository.updateStatus(id, SecurityAlert.AlertStatus.IGNORED, note));
  }
}
