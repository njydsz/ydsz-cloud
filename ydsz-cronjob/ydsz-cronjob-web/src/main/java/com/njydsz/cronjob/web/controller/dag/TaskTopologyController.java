package com.njydsz.cronjob.web.controller.dag;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.repository.JobDagInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobDagNodeInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.server.core.dag.DagCytoscapeHelper;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;

/**
 * 任务执行拓扑图后端 API Controller（P2-11）。
 *
 * <p>提供任务执行全链路拓扑数据，支持前端可视化展示 DAG 工作流执行状态：
 *
 * <ul>
 *   <li>DAG 工作流节点/边定义 + 每个节点的实时执行状态
 *   <li>任务执行日志关联（每个节点关联最近一次执行日志）
 *   <li>执行时间线（开始/结束/耗时）
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #getDagInstanceTopology} - 查询指定 DAG 实例的完整拓扑图数据
 *   <li>{@link #getJobExecutionHistory} - 查询指定任务的最近 20 次执行历史
 * </ul>
 *
 * <h3>返回数据结构</h3>
 *
 * <pre>{@code
 * {
 *   "dagDefinition": { "nodes": [...], "edges": [...] },
 *   "dagInstance": { "id": "...", "status": "RUNNING", ... },
 *   "nodeInstances": [
 *     { "jobKey": "a", "status": "SUCCESS", "startTime": "...", "endTime": "...", "durationMs": 1234, "logId": "..." },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * <h3>安全</h3>
 *
 * 接口加 {@link AuthApiPermission} 权限控制（{@link PermissionCodes#CRONJOB_JOB_VIEW}）； 只读不写，无需幂等/限流/审计。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Tag(name = "任务执行拓扑图", description = "DAG 实例执行拓扑可视化：节点/边/执行状态/历史")
@RestController
@RequestMapping("/api/v1/cronjob/topology")
@RequiredArgsConstructor
public class TaskTopologyController {
  /** 最近日志条数 */
  private static final int RECENT_LOG_LIMIT = 20;


  /** DAG 实例 Repository（DDD 分层：Controller 通过 Repository 接口访问） */
  private final JobDagInstanceRepository dagInstanceRepository;

  /** DAG 节点实例 Repository */
  private final JobDagNodeInstanceRepository dagNodeInstanceRepository;

  /** DAG 定义 Repository */
  private final JobDagRepository dagRepository;

  /** DAG 定义 JSON 编解码器 */
  private final DagDefinitionCodec dagDefinitionCodec;

  /** 任务执行日志 Repository */
  private final JobLogRepository jobLogRepository;

  /**
   * 查询 DAG 实例的执行拓扑图数据。
   *
   * <p>组装 3 部分数据：
   *
   * <ol>
   *   <li>{@code dagDefinition} - 从 {@code ydsz_job_dag.dag_definition} JSON 字段反序列化为 {@link
   *       DagDefinition}
   *   <li>{@code dagInstance} - DAG 实例主表（{@code ydsz_job_dag_instance}）
   *   <li>{@code nodeInstances} - DAG 节点实例列表（{@code ydsz_job_dag_node_instance}）
   * </ol>
   *
   * <p>当实例不存在时返回 {@code success(null)}（前端据此展示"实例不存在"占位）； 当 DAG 定义丢失时使用 {@link
   * DagDefinition#empty()} 占位。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 拓扑图数据 Map（含 dagDefinition / dagInstance / nodeInstances 三个 key）
   */
  @Operation(summary = "查询DAG实例执行拓扑图")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @GetMapping("/dagInstance/{dagInstanceId}")
  public YdszResponse<Map<String, Object>> getDagInstanceTopology(
      @PathVariable String dagInstanceId) {
    // 1. 加载 DAG 实例（通过 Repository 返回 VO）
    Optional<JobDagInstanceVO> instanceOpt = dagInstanceRepository.findById(dagInstanceId);
    if (instanceOpt.isEmpty()) {
      log.debug("[TaskTopology] DAG 实例不存在: dagInstanceId={}", dagInstanceId);
      return YdszResponse.success(null);
    }
    JobDagInstanceVO instance = instanceOpt.get();

    // 2. 加载 DAG 定义（dag_definition JSON 字段 → DagDefinition 对象）
    Optional<JobDagVO> dagOpt = dagRepository.findById(instance.getDagId());
    DagDefinition definition =
        dagOpt.isPresent() && dagOpt.get().getDagDefinition() != null
            ? dagDefinitionCodec.fromJson(dagOpt.get().getDagDefinition())
            : DagDefinition.empty();

    // 3. 查询节点实例列表（通过 Repository 返回 VO 列表）
    List<JobDagNodeInstanceVO> nodeInstances =
        dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);

    // 4. 组装拓扑数据（使用 LinkedHashMap 保持 key 顺序）
    Map<String, Object> topology = new LinkedHashMap<>(16);
    topology.put("dagDefinition", definition);
    topology.put("dagInstance", instance);
    topology.put("nodeInstances", nodeInstances);

    return YdszResponse.success(topology);
  }

  /**
   * P1-E1: 查询 DAG 实例的 Cytoscape.js 兼容可视化数据。
   *
   * <p>返回可直接用于 Cytoscape.js 渲染的节点/边格式，每个节点包含实时状态颜色和形状信息。 前端无需额外转换即可渲染 DAG 执行拓扑图。
   *
   * <p>输出格式：
   *
   * <pre>{@code
   * {
   *   "nodes": [{"data": {"id":"a","label":"抽取","color":"#28a745","shape":"round-rectangle","status":"SUCCESS"}}],
   *   "edges": [{"data": {"id":"edge_a_b","source":"a","target":"b"}}]
   * }
   * }</pre>
   *
   * @param dagInstanceId DAG 实例 ID
   * @return Cytoscape.js 兼容的节点/边数据；实例不存在时返回 success(null)
   */
  @Operation(summary = "查询DAG实例Cytoscape.js可视化")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @GetMapping("/dagInstance/{dagInstanceId}/cytoscape")
  public YdszResponse<Map<String, Object>> getDagInstanceCytoscape(
      @PathVariable String dagInstanceId) {
    // 1. 加载 DAG 实例（通过 Repository）
    Optional<JobDagInstanceVO> instanceOpt = dagInstanceRepository.findById(dagInstanceId);
    if (instanceOpt.isEmpty()) {
      log.debug("[TaskTopology] DAG 实例不存在: dagInstanceId={}", dagInstanceId);
      return YdszResponse.success(null);
    }
    JobDagInstanceVO instance = instanceOpt.get();

    // 2. 加载 DAG 定义（通过 Repository）
    Optional<JobDagVO> dagOpt = dagRepository.findById(instance.getDagId());
    DagDefinition definition =
        dagOpt.isPresent() && dagOpt.get().getDagDefinition() != null
            ? dagDefinitionCodec.fromJson(dagOpt.get().getDagDefinition())
            : DagDefinition.empty();

    // 3. 查询节点实例并构建状态映射（通过 Repository 返回 VO 列表）
    List<JobDagNodeInstanceVO> nodeInstances =
        dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
    Map<String, String> statusMap = new HashMap<>(16);
    Map<String, Long> durationMap = new HashMap<>(16);
    for (JobDagNodeInstanceVO ni : nodeInstances) {
      if (ni.getJobKey() != null && ni.getNodeStatus() != null) {
        statusMap.put(ni.getJobKey(), ni.getNodeStatus());
      }
      if (ni.getDurationMs() != null && ni.getDurationMs() > 0) {
        durationMap.put(ni.getJobKey(), ni.getDurationMs());
      }
    }

    // 4. 转换为 Cytoscape.js 格式
    Map<String, Object> cytoscapeData =
        DagCytoscapeHelper.toCytoscapeFormat(definition, statusMap, durationMap);

    return YdszResponse.success(cytoscapeData);
  }

  /**
   * 查询任务的执行历史拓扑（最近 20 次执行）。
   *
   * <p>按 {@code created_at} 倒序返回指定任务最近 20 条执行日志（含成功/失败/超时/运行中等所有状态），
   * 供前端"任务执行历史"面板展示时间线/成功率/平均耗时等指标。
   *
   * <p>注意：限制 LIMIT 20 是为了避免大任务历史过多导致响应体过大； 若需分页查询全部历史请使用 {@code JobLogController} 的分页接口。
   *
   * @param jobKey 任务 KEY（{@code ydsz_job.job_key}）
   * @return 执行历史列表（{@link JobLogVO}，最多 20 条）
   */
  @Operation(summary = "查询任务执行历史")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @GetMapping("/jobHistory/{jobKey}")
  public YdszResponse<List<JobLogVO>> getJobExecutionHistory(@PathVariable String jobKey) {
    // 通过 Repository 查询最近 20 条执行日志（LIMIT 20 在 Repository 层控制）
    return YdszResponse.success(jobLogRepository.findByJobKey(jobKey, RECENT_LOG_LIMIT));
  }
}
