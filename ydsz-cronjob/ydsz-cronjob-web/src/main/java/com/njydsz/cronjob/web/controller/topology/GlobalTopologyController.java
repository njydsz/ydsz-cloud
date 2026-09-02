package com.njydsz.cronjob.web.controller.topology;

import java.util.ArrayList;
import java.util.HashMap;
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
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.cronjob.server.core.dag.DagEdge;

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
 * @since 26.09.01
 */
@Slf4j
@Tag(name = "任务全局拓扑", description = "全局任务拓扑图数据：节点/边/统计")
@RestController
@RequestMapping("/api/v1/cronjob/topology")
@RequiredArgsConstructor
public class GlobalTopologyController {
  /** 单次查询最大任务数上限 */
  private static final int MAX_TOPOLOGY_JOBS = 500;

  /** 任务映射初始容量（用于构建 taskKey → JobVO 映射） */
  private static final int JOB_MAP_INITIAL_CAPACITY = 128;

  /** 节点/边列表初始容量（用于拓扑图数据构建） */
  private static final int TOPOLOGY_LIST_INITIAL_CAPACITY = 64;

  /** 统计信息 Map 初始容量（total/running/paused/failed） */
  private static final int STATS_MAP_INITIAL_CAPACITY = 16;

  /** 任务定义 Repository */
  private final JobRepository jobRepository;

  /** DAG 定义 Repository */
  private final JobDagRepository dagRepository;

  /** DAG 定义 JSON 编解码器 */
  private final DagDefinitionCodec dagDefinitionCodec;

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
    // 1. 查询所有任务（使用大分页获取全量）
    JobRepository.PageResult<JobVO> pageResult =
        jobRepository.page(null, null, null, 1, MAX_TOPOLOGY_JOBS);
    List<JobVO> jobs = pageResult.getRecords();

    if (jobs.size() >= MAX_TOPOLOGY_JOBS) {
      log.warn(
          "[GlobalTopology] 任务数量达到上限 {}, 可能存在截断。建议拆分 DAG 或增加筛选条件。",
          MAX_TOPOLOGY_JOBS);
    }

    // 2. 构建任务 KEY → JobVO 映射（用于边构建时查找）
    Map<String, JobVO> jobMap = new HashMap<>(JOB_MAP_INITIAL_CAPACITY);
    for (JobVO job : jobs) {
      if (job.getJobKey() != null) {
        jobMap.put(job.getJobKey(), job);
      }
    }

    // 3. 构建节点列表
    List<Map<String, Object>> nodes = new ArrayList<>(TOPOLOGY_LIST_INITIAL_CAPACITY);
    for (JobVO job : jobs) {
      Map<String, Object> node = new LinkedHashMap<>();
      node.put("id", job.getId());
      node.put("jobKey", job.getJobKey());
      node.put("jobName", job.getJobName());
      node.put("jobGroup", job.getJobGroup());
      node.put("status", job.getStatus());
      node.put("scheduleType", job.getScheduleType());
      node.put("nextFireTime", job.getNextFireTime());
      nodes.add(node);
    }

    // 4. 从 DAG 定义中提取依赖边
    List<Map<String, String>> links = new ArrayList<>(TOPOLOGY_LIST_INITIAL_CAPACITY);
    List<JobDagVO> dags = dagRepository.findEnabledDags();
    for (JobDagVO dag : dags) {
      if (dag.getDagDefinition() == null) {
        continue;
      }
      DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());
      if (definition == null || definition.edges() == null) {
        continue;
      }
      for (DagEdge edge : definition.edges()) {
        // 仅当源和目标任务都存在时才添加边
        if (jobMap.containsKey(edge.from()) && jobMap.containsKey(edge.to())) {
          Map<String, String> link = new LinkedHashMap<>();
          link.put("source", jobMap.get(edge.from()).getId());
          link.put("target", jobMap.get(edge.to()).getId());
          links.add(link);
        }
      }
    }

    // 5. 统计各状态任务数量
    Map<String, Object> topologyData = new LinkedHashMap<>();
    topologyData.put("nodes", nodes);
    topologyData.put("links", links);
    topologyData.put("stats", buildStats(jobs));

    return YdszResponse.success(topologyData);
  }

  /**
   * 构建任务统计信息。
   *
   * @param jobs 任务列表
   * @return 统计 Map（total/running/paused/failed）
   */
  private Map<String, Object> buildStats(List<JobVO> jobs) {
    Map<String, Object> stats = new LinkedHashMap<>(STATS_MAP_INITIAL_CAPACITY);
    long total = jobs.size();
    long running = jobs.stream().filter(j -> "RUNNING".equals(j.getStatus())).count();
    long paused = jobs.stream().filter(j -> "PAUSED".equals(j.getStatus())).count();
    long failed = jobs.stream().filter(j -> "FAILED".equals(j.getStatus()) || "ERROR".equals(j.getStatus())).count();

    stats.put("total", total);
    stats.put("running", running);
    stats.put("paused", paused);
    stats.put("failed", failed);
    return stats;
  }
}
