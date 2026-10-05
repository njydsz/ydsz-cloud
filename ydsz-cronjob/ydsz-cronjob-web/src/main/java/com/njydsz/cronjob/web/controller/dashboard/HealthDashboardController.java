package com.njydsz.cronjob.web.controller.dashboard;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.cronjob.domain.service.HealthDashboardQueryService;

/**
 * 健康仪表盘 Controller（P1-2：前端控制台交互增强）。
 *
 * <p>聚合调度引擎的多维度运行状态，为前端控制台提供一站式健康检查能力：
 *
 * <ul>
 *   <li>系统资源：CPU / 内存 / 线程池使用率
 *   <li>任务概览：总数 / 各状态分布 / 今日执行统计
 *   <li>DAG 工作流：运行中实例数 / 成功率
 *   <li>调度器状态：Leader 节点 / 扫描器状态 / 队列积压
 *   <li>最近异常：失败任务 Top N / 超时任务
 * </ul>
 *
 * <p>适用于运维人员快速定位系统异常、评估集群健康度。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Tag(name = "健康仪表盘", description = "聚合系统资源、任务、DAG、调度器多维度运行状态")
@ApiVersion("26.10.01")
@RestController
@RequestMapping("/cronjob/dashboard/health")
@RequiredArgsConstructor
public class HealthDashboardController {
  /** 健康仪表盘查询 Service（DDD 分层：Controller → Service → Repository） */
  private final HealthDashboardQueryService healthDashboardQueryService;

  /**
   * 获取系统整体健康状态。
   *
   * <p>聚合所有维度的健康指标，返回前端仪表盘所需的完整数据。
   *
   * @return 健康仪表盘数据
   */
  @Operation(summary = "系统整体健康状态")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
  @GetMapping
  public YdszResponse<Map<String, Object>> getHealth() {
    // 通过 Service 聚合健康仪表盘数据（Service → Repository，符合 DDD 分层）
    Map<String, Object> health = healthDashboardQueryService.getHealth();
    return YdszResponse.success(health);
  }
}
