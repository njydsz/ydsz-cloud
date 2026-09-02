package com.njydsz.cronjob.web.controller.dashboard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.repository.JobRepository;

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
 * @since 26.09.01
 */
@Slf4j
@Tag(name = "运维 Dashboard", description = "Dashboard 聚合统计数据：任务分布/分组排行/调度类型")
@RestController
@RequestMapping("/api/v1/cronjob/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  /** 任务定义 Repository */
  private final JobRepository jobRepository;

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
    Map<String, Object> data = new LinkedHashMap<>(16);

    // 1. 任务状态分布
    Map<String, Long> statusDistribution = new LinkedHashMap<>(16);
    statusDistribution.put("NORMAL", jobRepository.countByStatus("NORMAL"));
    statusDistribution.put("PAUSED", jobRepository.countByStatus("PAUSED"));
    statusDistribution.put("AUTO_PAUSED", jobRepository.countByStatus("AUTO_PAUSED"));
    statusDistribution.put("ERROR", jobRepository.countByStatus("ERROR"));
    data.put("statusDistribution", statusDistribution);

    // 2. 分组任务数量统计
    List<String> groups = jobRepository.listDistinctGroups();
    Map<String, Long> groupStats = new LinkedHashMap<>(16);
    for (String group : groups) {
      groupStats.put(group, jobRepository.countByGroup(group));
    }
    data.put("groupStats", groupStats);

    // 3. 汇总指标
    Map<String, Object> summary = new LinkedHashMap<>(16);
    summary.put("total", jobRepository.countAll());
    summary.put("normalCount", jobRepository.countByStatus("NORMAL"));
    summary.put("pausedCount", jobRepository.countByStatus("PAUSED"));
    summary.put("errorCount", jobRepository.countByStatus("ERROR"));
    data.put("summary", summary);

    return YdszResponse.success(data);
  }
}
