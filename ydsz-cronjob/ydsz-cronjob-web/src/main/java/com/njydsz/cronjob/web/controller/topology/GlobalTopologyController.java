package com.njydsz.cronjob.web.controller.topology;

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
import com.njydsz.cronjob.domain.service.GlobalTopologyQueryService;

/**
 * 任务全局拓扑数据 API Controller（P2-3）。
 *
 * <p>提供全局任务拓扑图数据，包含所有任务节点及其 DAG 依赖边。
 *
 * <p>返回数据结构：
 *
 * <pre>{@code
 * {
 *   "nodes": [
 *     { "id": "job-001", "jobKey": "order-sync", "jobName": "订单同步", "status": "NORMAL", "jobGroup": "trade" }
 *   ],
 *   "links": [
 *     { "source": "job-001", "target": "job-002" }
 *   ],
 *   "stats": { "total": 50, "running": 3, "paused": 2, "failed": 1 }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Tag(name = "任务全局拓扑", description = "全局任务拓扑图数据：节点/边/统计")
@ApiVersion("26.10.01")
@RestController
@RequestMapping("/cronjob/topology")
@RequiredArgsConstructor
public class GlobalTopologyController {
  /** 全局拓扑查询 Service（DDD 分层：Controller → Service → Repository） */
  private final GlobalTopologyQueryService globalTopologyQueryService;

  /**
   * 查询全局任务拓扑图数据。
   *
   * <p>返回所有任务节点（含状态/分组信息）和 DAG 依赖边，供前端 ECharts 力导向图渲染。
   *
   * <p>节点上限 500 个，超过时截断并记录 WARN 日志。
   *
   * @return 拓扑图数据（nodes/links/stats）
   */
  @Operation(summary = "查询全局任务拓扑图数据")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @GetMapping("/global")
  public YdszResponse<Map<String, Object>> getGlobalTopology() {
    // 通过 Service 组装全局拓扑图数据（Service → Repository）
    Map<String, Object> topologyData = globalTopologyQueryService.getGlobalTopology();
    return YdszResponse.success(topologyData);
  }
}
