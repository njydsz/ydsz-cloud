package com.njydsz.userinfo.web.controller;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.userinfo.domain.vo.ActiveUserVO;
import com.njydsz.userinfo.domain.vo.AnomalySessionVO;
import com.njydsz.userinfo.domain.vo.DeviceDistributionVO;
import com.njydsz.userinfo.domain.vo.LoginFailDistributionVO;
import com.njydsz.userinfo.domain.vo.LoginSuccessRateVO;
import com.njydsz.userinfo.domain.vo.MfaCoverageVO;
import com.njydsz.userinfo.domain.vo.RiskLevelDistributionVO;
import com.njydsz.userinfo.domain.vo.SecurityDashboardVO;
import com.njydsz.userinfo.domain.vo.SecurityEventVO;
import com.njydsz.userinfo.domain.vo.SessionActivityVO;
import com.njydsz.userinfo.domain.vo.SessionTrendVO;
import com.njydsz.userinfo.server.auth.SecurityDashboardService;
import com.njydsz.userinfo.server.auth.SessionActivityService;

/**
 * 安全仪表盘控制器。
 *
 * <p>为管理员提供安全指标和会话活跃度的可视化数据源，包括：
 *
 * <ul>
 *   <li>仪表盘总览：用户统计、在线会话、MFA覆盖率、今日登录成功率等</li>
 *   <li>登录安全报表：登录成功率趋势、失败原因分布、风险等级分布</li>
 *   <li>会话活跃度：活跃用户排行、会话趋势、设备分布、异常会话检测</li>
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/admin/security}
 *
 * <p><b>权限要求：</b>所有接口需 {@code admin:security:view} 权限。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/security")
@RequiredArgsConstructor
@Tag(name = "安全仪表盘", description = "安全指标和会话活跃度统计")
public class SecurityDashboardController {
  /** 默认统计时间范围（天）：7 天 */
  private static final int DEFAULT_STATS_DAYS = 7;

  private final SecurityDashboardService securityDashboardService;
  private final SessionActivityService sessionActivityService;

  /**
   * 获取仪表盘总览数据。
   *
   * @return 安全仪表盘总览
   */
  @GetMapping("/dashboard")
  @AuthApiPermission(apiCodes = "admin:security:view")
  @Operation(summary = "获取仪表盘总览")
  public YdszResponse<SecurityDashboardVO> getDashboard() {
    return YdszResponse.success(securityDashboardService.getDashboard());
  }

  /**
   * 获取登录成功率趋势。
   *
   * @param start 起始日期（默认 7 天前）
   * @param end 结束日期（默认今天）
   * @return 每日登录成功率列表
   */
  @GetMapping("/login-success-rate")
  @AuthApiPermission(apiCodes = "admin:security:view")
  @Operation(summary = "获取登录成功率趋势")
  public YdszResponse<List<LoginSuccessRateVO>> getLoginSuccessRate(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
    if (start == null) {
      start = LocalDate.now().minusDays(DEFAULT_STATS_DAYS);
    }
    if (end == null) {
      end = LocalDate.now();
    }
    return YdszResponse.success(securityDashboardService.getLoginSuccessRate(start, end));
  }

  /**
   * 获取登录失败原因分布。
   *
   * @param date 统计日期（默认今天）
   * @return 失败原因分布列表
   */
  @GetMapping("/login-fail-distribution")
  @AuthApiPermission(apiCodes = "admin:security:view")
  @Operation(summary = "获取登录失败原因分布")
  public YdszResponse<List<LoginFailDistributionVO>> getLoginFailDistribution(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return YdszResponse.success(securityDashboardService.getLoginFailDistribution(date));
  }

  /**
   * 获取 MFA 覆盖率统计。
   *
   * @return MFA 覆盖率统计
   */
  @GetMapping("/mfa-coverage")
  @AuthApiPermission(apiCodes = "admin:security:view")
  @Operation(summary = "获取 MFA 覆盖率")
  public YdszResponse<MfaCoverageVO> getMfaCoverage() {
    return YdszResponse.success(securityDashboardService.getMfaCoverage());
  }

  /**
   * 获取风险等级分布。
   *
   * @return 风险等级分布
   */
  @GetMapping("/risk-distribution")
  @AuthApiPermission(apiCodes = "admin:security:view")
  @Operation(summary = "获取风险等级分布")
  public YdszResponse<RiskLevelDistributionVO> getRiskLevelDistribution() {
    return YdszResponse.success(securityDashboardService.getRiskLevelDistribution());
  }

  /**
   * 获取最近安全事件。
   *
   * @param limit 返回记录数上限（默认 20，最大 100）
   * @return 最近安全事件列表
   */
  @GetMapping("/recent-events")
  @AuthApiPermission(apiCodes = "admin:security:view")
  @Operation(summary = "获取最近安全事件")
  public YdszResponse<List<SecurityEventVO>> getRecentSecurityEvents(
      @RequestParam(defaultValue = "20") int limit) {
    return YdszResponse.success(securityDashboardService.getRecentSecurityEvents(limit));
  }

  /**
   * 获取会话活跃度概览。
   *
   * @return 活跃度概览数据
   */
  @GetMapping("/session-activity")
  @AuthApiPermission(apiCodes = "admin:security:view")
  @Operation(summary = "获取会话活跃度概览")
  public YdszResponse<SessionActivityVO> getSessionActivity() {
    return YdszResponse.success(sessionActivityService.getActivityOverview());
  }

  /**
   * 获取活跃用户排行。
   *
   * @param limit 返回记录数上限（默认 10，最大 100）
   * @return 活跃用户排行列表
   */
  @GetMapping("/active-user-ranking")
  @AuthApiPermission(apiCodes = "admin:security:view")
  @Operation(summary = "获取活跃用户排行")
  public YdszResponse<List<ActiveUserVO>> getActiveUserRanking(
      @RequestParam(defaultValue = "10") int limit) {
    return YdszResponse.success(sessionActivityService.getActiveUserRanking(limit));
  }

  /**
   * 获取会话趋势。
   *
   * @param start 起始日期（默认 7 天前）
   * @param end 结束日期（默认今天）
   * @return 会话趋势列表
   */
  @GetMapping("/session-trend")
  @AuthApiPermission(apiCodes = "admin:security:view")
  @Operation(summary = "获取会话趋势")
  public YdszResponse<List<SessionTrendVO>> getSessionTrend(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
    if (start == null) {
      start = LocalDate.now().minusDays(DEFAULT_STATS_DAYS);
    }
    if (end == null) {
      end = LocalDate.now();
    }
    return YdszResponse.success(sessionActivityService.getSessionTrend(start, end));
  }

  /**
   * 获取设备分布。
   *
   * @return 设备分布列表
   */
  @GetMapping("/device-distribution")
  @AuthApiPermission(apiCodes = "admin:security:view")
  @Operation(summary = "获取设备分布")
  public YdszResponse<List<DeviceDistributionVO>> getDeviceDistribution() {
    return YdszResponse.success(sessionActivityService.getDeviceDistribution());
  }

  /**
   * 检测异常会话。
   *
   * @return 异常会话列表
   */
  @GetMapping("/anomaly-sessions")
  @AuthApiPermission(apiCodes = "admin:security:view")
  @Operation(summary = "检测异常会话")
  public YdszResponse<List<AnomalySessionVO>> detectAnomalySessions() {
    return YdszResponse.success(sessionActivityService.detectAnomalySessions());
  }
}
