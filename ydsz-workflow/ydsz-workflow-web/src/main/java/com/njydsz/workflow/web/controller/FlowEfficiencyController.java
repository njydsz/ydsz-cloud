package com.njydsz.workflow.web.controller;


import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.workflow.domain.vo.FlowApproverEfficiencyVO;
import com.njydsz.workflow.domain.vo.FlowBottleneckVO;
import com.njydsz.workflow.domain.vo.FlowEfficiencyStatsVO;
import com.njydsz.workflow.domain.vo.FlowTrendVO;
import com.njydsz.workflow.server.service.FlowEfficiencyService;

/**
 * 流程效率分析 Controller（P2-2 从 FlowMonitorDashboardController 拆分）。
 *
 * <p>承接原 Dashboard 中 5 个 {@code /efficiency/*} 端点，职责单一：
 * 审批效率统计、节点瓶颈、审批人排名、审批趋势、流程健康度。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@Tag(name = "流程效率分析", description = "审批效率统计 / 瓶颈排名 / 审批人排名 / 趋势 / 健康度")
@RestController
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowEfficiencyController {

  /** 效率分析服务 */
  private final FlowEfficiencyService efficiencyService;

  /**
   * 审批效率统计 — 单量/平均耗时/代批率/超期率。
   *
   * @param startTime 开始时间（可选）
   * @param endTime 结束时间（可选）
   * @return 统计结果
   */
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_VIEW)
  @Operation(summary = "审批效率统计")
  @GetMapping("/efficiency/stats")
  public YdszResponse<FlowEfficiencyStatsVO> efficiencyStats(
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(efficiencyService.efficiencyStats(tenantId, startTime, endTime));
  }

  /**
   * 节点瓶颈排名。
   *
   * @param flowCode 流程编码（可选）
   * @param limit 返回条数上限
   * @return 瓶颈节点列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_VIEW)
  @Operation(summary = "节点瓶颈排名")
  @GetMapping("/efficiency/bottleneck")
  public YdszResponse<List<FlowBottleneckVO>> bottleneckRanking(
      @RequestParam(required = false) String flowCode,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(efficiencyService.bottleneckRanking(tenantId, flowCode, limit));
  }

  /**
   * 审批人效率排名。
   *
   * @param startTime 开始时间（可选）
   * @param endTime 结束时间（可选）
   * @param limit 返回条数上限
   * @return 审批人排名列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_VIEW)
  @Operation(summary = "审批人效率排名")
  @GetMapping("/efficiency/approverRanking")
  public YdszResponse<List<FlowApproverEfficiencyVO>> approverRanking(
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(
        efficiencyService.approverRanking(tenantId, startTime, endTime, limit));
  }

  /**
   * 审批趋势。
   *
   * @param interval 聚合粒度：DAY / WEEK / MONTH
   * @param startTime 开始时间（可选）
   * @param endTime 结束时间（可选）
   * @return 趋势列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_VIEW)
  @Operation(summary = "审批趋势")
  @GetMapping("/efficiency/trend")
  public YdszResponse<List<FlowTrendVO>> approvalTrend(
      @RequestParam(defaultValue = "DAY") String interval,
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(
        efficiencyService.approvalTrend(tenantId, interval, startTime, endTime));
  }

  /**
   * 流程健康度综合评分。
   *
   * @param startTime 开始时间（可空）
   * @param endTime 结束时间（可空）
   * @return 评分结果
   */
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_VIEW)
  @Operation(summary = "流程健康度综合评分")
  @GetMapping("/efficiency/healthScore")
  public YdszResponse<Map<String, Object>> healthScore(
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(efficiencyService.healthScore(tenantId, startTime, endTime));
  }
}
