package com.njydsz.cronjob.server.service.impl.dag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.domain.repository.JobDagInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobDagNodeInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.cronjob.server.core.dag.DagEdge;
import com.njydsz.cronjob.server.core.dag.DagNode;
import com.njydsz.cronjob.server.service.dag.JobDagInstanceService;
import com.njydsz.cronjob.server.vo.DagInstanceVisualizationVO;

/**
 * DAG 任务实例服务实现。
 *
 * <p>管理 DAG 任务实例 ({@code ydsz_job_dag_instance} / {@code ydsz_job_dag_node_instance})：
 *
 * <p>DAG 版本快照加载、节点入度计算、并行执行调度、失败重试、状态回滚。
 *
 * <p>支持分布式锁 + 抢占式调度（Leader / Leaderless 双模式）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDagInstanceServiceImpl implements JobDagInstanceService {
  /** 默认查询条数上限 */
  private static final int DEFAULT_LIMIT = 20;

  /** StringBuilder 初始容量 */
  private static final int STRING_BUILDER_INITIAL_CAPACITY = 256;


  /** DAG 实例 Repository */
  private final JobDagInstanceRepository jobDagInstanceRepository;

  /** DAG 节点实例 Repository */
  private final JobDagNodeInstanceRepository jobDagNodeInstanceRepository;

  /** DAG 定义 Repository（用于查询 DAG 定义 JSON） */
  private final JobDagRepository jobDagRepository;

  /** DAG 定义 JSON 编解码器 */
  private final DagDefinitionCodec dagDefinitionCodec;

  @Override
  @Transactional(readOnly = true)
  public JobDagInstanceVO getInstanceById(String instanceId) {
    return jobDagInstanceRepository.findById(instanceId)
        .orElseThrow(() -> SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.cronjob.msg_dag_instance_not_found")
            .params(instanceId)
            .build());
  }

  @Override
  @Transactional(readOnly = true)
  public List<JobDagInstanceVO> listByDagId(String dagId, int limit) {
    int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;
    return jobDagInstanceRepository.findByDagId(dagId, safeLimit);
  }

  @Override
  @Transactional(readOnly = true)
  public List<JobDagInstanceVO> listByStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return List.of();
    }
    return jobDagInstanceRepository.findByStatus(status);
  }

  @Override
  @Transactional(readOnly = true)
  public List<JobDagNodeInstanceVO> listNodes(String dagInstanceId) {
    return dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void pauseInstance(String instanceId) {
    getInstanceById(instanceId);
    int rows = jobDagInstanceRepository.markPaused(instanceId);
    if (rows == 0) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_dag_instance_not_running")
          .params(instanceId)
          .build();
    }
    log.info("[JobDagInstance] 暂停 DAG 实例: instanceId={}", instanceId);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void resumeInstance(String instanceId) {
    getInstanceById(instanceId);
    int rows = jobDagInstanceRepository.markResumed(instanceId);
    if (rows == 0) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_dag_instance_not_running")
          .params(instanceId)
          .build();
    }
    log.info("[JobDagInstance] 恢复 DAG 实例: instanceId={}", instanceId);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void cancelInstance(String instanceId) {
    getInstanceById(instanceId);
    JobDagInstanceVO dagInstance = getInstanceById(instanceId);
    LocalDateTime now = LocalDateTime.now();
    long durationMs = dagInstance.getStartedAt() != null
        ? java.time.temporal.ChronoUnit.MILLIS.between(dagInstance.getStartedAt(), now)
        : 0;
    int rows = jobDagInstanceRepository.markCanceled(instanceId, now, durationMs);
    if (rows == 0) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_dag_instance_not_running")
          .params(instanceId)
          .build();
    }
    log.info("[JobDagInstance] 取消 DAG 实例: instanceId={}", instanceId);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void updateContext(String instanceId, String contextJson) {
    getInstanceById(instanceId);
    jobDagInstanceRepository.updateContext(instanceId, contextJson);
    log.info("[JobDagInstance] 更新 DAG 实例上下文: instanceId={}", instanceId);
  }

  @Override
  @Transactional(readOnly = true)
  public DagInstanceVisualizationVO getVisualization(String instanceId) {
    // 1. 查询 DAG 实例（不存在时抛 SysException）
    JobDagInstanceVO instance = getInstanceById(instanceId);

    // 2. 查询 DAG 定义（通过实例.dagId 关联）
    JobDagVO dag = jobDagRepository.findById(instance.getDagId()).orElse(null);
    if (dag == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.cronjob.msg_dag_not_found_def")
          .params(instance.getDagId())
          .build();
    }

    // 3. 解析 DAG 定义 JSON（非法时抛 SysException）
    DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());

    // 4. 查询节点实例执行状态
    List<JobDagNodeInstanceVO> nodeInstances = listNodes(instanceId);

    // 5. 组装可视化数据 VO
    DagInstanceVisualizationVO vo = new DagInstanceVisualizationVO();
    vo.setInstance(instance);
    vo.setDefinition(definition);
    vo.setNodeInstances(nodeInstances);
    return vo;
  }

  @Override
  @Transactional(readOnly = true)
  public String getMermaidDiagram(String instanceId) {
    // 1. 获取可视化数据（复用现有逻辑）
    DagInstanceVisualizationVO visualization = getVisualization(instanceId);
    DagDefinition definition = visualization.getDefinition();
    List<JobDagNodeInstanceVO> nodeInstances = visualization.getNodeInstances();

    // 2. 构建 jobKey → nodeStatus 映射
    Map<String, String> statusMap = new HashMap<>(nodeInstances.size());
    for (JobDagNodeInstanceVO ni : nodeInstances) {
      if (ni.getJobKey() != null && ni.getNodeStatus() != null) {
        statusMap.put(ni.getJobKey(), ni.getNodeStatus());
      }
    }

    // 3. 生成 Mermaid graph TD 文本
    StringBuilder sb = new StringBuilder(STRING_BUILDER_INITIAL_CAPACITY);
    sb.append("```mermaid\ngraph TD\n");

    // 3a. 节点定义（含样式）
    for (DagNode node : definition.nodes()) {
      String jobKey = node.jobKey();
      String label = node.label() != null ? node.label() : jobKey;
      // Mermaid 节点 ID 只允许字母数字下划线，替换特殊字符
      String mermaidId = sanitizeMermaidId(jobKey);
      sb.append("    ").append(mermaidId).append("[\"").append(escapeMermaid(label)).append("\"]\n");
    }

    // 3b. 边定义
    for (DagEdge edge : definition.edges()) {
      String fromId = sanitizeMermaidId(edge.from());
      String toId = sanitizeMermaidId(edge.to());
      sb.append("    ").append(fromId).append(" --> ").append(toId).append("\n");
    }

    // 3c. 节点状态样式
    for (DagNode node : definition.nodes()) {
      String jobKey = node.jobKey();
      String status = statusMap.getOrDefault(jobKey, "PENDING");
      String color = resolveStatusColor(status);
      String mermaidId = sanitizeMermaidId(jobKey);
      sb.append("    style ").append(mermaidId).append(" fill:").append(color).append("\n");
    }

    sb.append("```");
    return sb.toString();
  }

  /** 将 jobKey 转换为 Mermaid 安全的节点 ID（仅保留字母数字下划线） */
  private String sanitizeMermaidId(String jobKey) {
    if (jobKey == null || jobKey.isBlank()) {
      return "node_unknown";
    }
    String sanitized = jobKey.replaceAll("[^a-zA-Z0-9_]", "_");
    // 确保不以数字开头
    if (Character.isDigit(sanitized.charAt(0))) {
      sanitized = "n_" + sanitized;
    }
    return sanitized;
  }

  /** 转义 Mermaid 节点标签中的特殊字符 */
  private String escapeMermaid(String label) {
    if (label == null) {
      return "";
    }
    return label.replace("\"", "'");
  }

  /** 根据节点状态返回对应的颜色 */
  private String resolveStatusColor(String status) {
    if (status == null) {
      return "#9e9e9e";
    }
    switch (status) {
      case "SUCCESS":
        return "#4caf50";
      case "RUNNING":
      case "RETRYING":
        return "#ff9800";
      case "FAILED":
        return "#f44336";
      case "SKIPPED":
        return "#9e9e9e";
      case "PENDING":
      default:
        return "#e0e0e0";
    }
  }
}
