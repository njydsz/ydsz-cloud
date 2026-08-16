package com.njydsz.cronjob.web.controller.dag;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.entity.dag.JobDag;
import com.njydsz.cronjob.domain.entity.dag.JobDagInstance;
import com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.infra.mapper.dag.JobDagInstanceMapper;
import com.njydsz.cronjob.infra.mapper.dag.JobDagMapper;
import com.njydsz.cronjob.infra.mapper.dag.JobDagNodeInstanceMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
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
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "任务执行拓扑图", description = "DAG 实例执行拓扑可视化：节点/边/执行状态/历史")
@RestController
@RequestMapping("/api/v1/cronjob/topology")
@RequiredArgsConstructor
public class TaskTopologyController {

  /** DAG 实例 Mapper */
  private final JobDagInstanceMapper dagInstanceMapper;

  /** DAG 节点实例 Mapper */
  private final JobDagNodeInstanceMapper dagNodeInstanceMapper;

  /** DAG 定义 Mapper */
  private final JobDagMapper dagMapper;

  /** DAG 定义 JSON 编解码器 */
  private final DagDefinitionCodec dagDefinitionCodec;

  /** 任务执行日志 Mapper */
  private final JobLogMapper jobLogMapper;

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
  public BaseResponse<Map<String, Object>> getDagInstanceTopology(
      @PathVariable String dagInstanceId) {
    // 1. 加载 DAG 实例
    JobDagInstance instance = dagInstanceMapper.selectById(dagInstanceId);
    if (instance == null) {
      log.debug("[TaskTopology] DAG 实例不存在: dagInstanceId={}", dagInstanceId);
      return BaseResponse.success(null);
    }

    // 2. 加载 DAG 定义（dag_definition JSON 字段 → DagDefinition 对象）
    JobDag dag = dagMapper.selectById(instance.getDagId());
    DagDefinition definition =
        dag != null ? dagDefinitionCodec.fromJson(dag.getDagDefinition()) : DagDefinition.empty();

    // 3. 查询节点实例列表（每个节点的实时执行状态）
    List<JobDagNodeInstance> nodeInstances =
        dagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);

    // 4. 组装拓扑数据（使用 LinkedHashMap 保持 key 顺序）
    Map<String, Object> topology = new LinkedHashMap<>();
    topology.put("dagDefinition", definition);
    topology.put("dagInstance", instance);
    topology.put("nodeInstances", nodeInstances);

    return BaseResponse.success(topology);
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
  public BaseResponse<Map<String, Object>> getDagInstanceCytoscape(
      @PathVariable String dagInstanceId) {
    // 1. 加载 DAG 实例
    JobDagInstance instance = dagInstanceMapper.selectById(dagInstanceId);
    if (instance == null) {
      log.debug("[TaskTopology] DAG 实例不存在: dagInstanceId={}", dagInstanceId);
      return BaseResponse.success(null);
    }

    // 2. 加载 DAG 定义
    JobDag dag = dagMapper.selectById(instance.getDagId());
    DagDefinition definition =
        dag != null ? dagDefinitionCodec.fromJson(dag.getDagDefinition()) : DagDefinition.empty();

    // 3. 查询节点实例并构建状态映射
    List<JobDagNodeInstance> nodeInstances =
        dagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
    Map<String, String> statusMap = new HashMap<>();
    Map<String, Long> durationMap = new HashMap<>();
    for (JobDagNodeInstance ni : nodeInstances) {
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

    return BaseResponse.success(cytoscapeData);
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
  public BaseResponse<List<JobLogVO>> getJobExecutionHistory(@PathVariable String jobKey) {
    // 构造查询条件：jobKey 精确匹配 + 未逻辑删除 + 按 created_at 倒序 + LIMIT 20
    LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .eq(JobLog::getJobKey, jobKey)
        .eq(JobLog::getDeleted, 0)
        .orderByDesc(JobLog::getCreatedAt)
        .last("LIMIT 20");
    // Entity → VO 转换
    return BaseResponse.success(
        CronjobConverter.INSTANT.jobLogListToVO(jobLogMapper.selectList(wrapper)));
  }
}
