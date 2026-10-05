package com.njydsz.cronjob.web.controller.dashboard;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.cronjob.domain.service.DashboardQueryService;

/**
 * Dashboard 数据 API Controller（P2-6）。
 *
 * <p>提供运维 Dashboard 所需的聚合统计数据：
 *
 * <ul>
 *   <li>任务总数/各状态分布
 *   <li>分组任务数量排行
 *   <li>调度类型分布
 * </ul>
 *
 * <p>数据通过 ECharts 按需引入方式在前端渲染，减少首屏加载体积。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Tag(name = "运维 Dashboard", description = "Dashboard 聚合统计数据：任务分布/分组排行/调度类型")
@ApiVersion("26.10.01")
@RestController
@RequestMapping("/cronjob/dashboard")
@RequiredArgsConstructor
public class DashboardController {
  /** Dashboard 查询 Service（DDD 分层：Controller → Service → Repository） */
  private final DashboardQueryService dashboardQueryService;

  /**
   * 查询 Dashboard 概览数据。
   *
   * <p>返回任务状态分布、分组统计、调度类型分布等聚合数据，供前端 ECharts 图表渲染。
   *
   * @return Dashboard 数据（statusDistribution / groupStats / scheduleTypeStats / summary）
   */
  @Operation(summary = "查询Dashboard概览数据")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
  @GetMapping("/overview")
  public YdszResponse<Map<String, Object>> getOverview() {
    // 通过 Service 聚合 Dashboard 数据（Service → Repository，符合 DDD 分层）
    Map<String, Object> data = dashboardQueryService.getOverview();
    return YdszResponse.success(data);
  }
}
