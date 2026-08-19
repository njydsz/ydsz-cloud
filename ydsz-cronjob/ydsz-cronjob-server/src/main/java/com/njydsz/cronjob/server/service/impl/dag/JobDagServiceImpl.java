package com.njydsz.cronjob.server.service.impl.dag;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.domain.dag.DagInstanceStatus;
import com.njydsz.cronjob.domain.dto.dag.JobDagSaveDTO;
import com.njydsz.cronjob.domain.entity.dag.JobDag;
import com.njydsz.cronjob.domain.entity.dag.JobDagInstance;
import com.njydsz.cronjob.domain.entity.dag.JobDagVersion;
import com.njydsz.cronjob.domain.repository.JobDagInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.repository.JobDagVersionRepository;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.cronjob.server.core.dag.DagEdge;
import com.njydsz.cronjob.server.core.dag.DagFailureStrategy;
import com.njydsz.cronjob.server.core.dag.DagInstanceExecutor;
import com.njydsz.cronjob.server.core.dag.DagNode;
import com.njydsz.cronjob.server.core.dag.DagParser;
import com.njydsz.cronjob.server.service.dag.JobDagService;

/**
 * DAG 工作流定义服务实现（P2 DAG 增强）。
 *
 * <p>核心职责：
 *
 * <ul>
 *   <li>DAG 定义的增删改查与状态流转（启用/禁用）
 *   <li>DAG 定义 JSON 校验（结构校验 + 环检测）
 *   <li>手动触发 DAG 实例创建并异步派发执行
 * </ul>
 *
 * <p>{@link DagInstanceExecutor} 通过 {@link ObjectProvider} 延迟注入， 避免与 {@code TaskDispatcher}
 * 形成循环依赖；当实现类未注册时仅创建实例记录不执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDagServiceImpl implements JobDagService {

  /** DAG 定义 Repository */
  private final JobDagRepository jobDagRepository;

  /** DAG 实例 Repository */
  private final JobDagInstanceRepository jobDagInstanceRepository;

  /** P1-8: DAG 版本历史 Repository */
  private final JobDagVersionRepository jobDagVersionRepository;

  /** DAG 定义编解码器 */
  private final DagDefinitionCodec dagDefinitionCodec;

  /** DAG 解析器（环检测） */
  private final DagParser dagParser;

  /**
   * DAG 实例执行器（延迟注入）。
   *
   * <p>实现类未注册时 {@code getIfAvailable()} 返回 {@code null}， {@link #triggerDag(String, String)}
   * 仅创建实例记录不执行。
   */
  private final ObjectProvider<DagInstanceExecutor> dagInstanceExecutorProvider;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String createDag(JobDagSaveDTO dto) {
    // 校验 dagKey 唯一性
    if (jobDagRepository.selectByDagKey(dto.getDagKey()) != null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_dag_already_exists")
          .params(dto.getDagKey())
          .build();
    }
    // 校验 DAG 定义（结构 + 环检测）
    validateDagDefinition(dto.getDagDefinition());
    // 校验 CRON 触发类型必须提供 cronExpression
    validateCronExpression(dto.getTriggerType(), dto.getCronExpression());

    JobDag dag = new JobDag();
    dag.setDagKey(dto.getDagKey());
    dag.setDagName(dto.getDagName());
    dag.setDagDefinition(dto.getDagDefinition());
    dag.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "DRAFT");
    dag.setTriggerType(StringUtils.hasText(dto.getTriggerType()) ? dto.getTriggerType() : "MANUAL");
    dag.setCronExpression(
        StringUtils.hasText(dto.getCronExpression()) ? dto.getCronExpression() : null);
    dag.setMaxConcurrentInstances(
        dto.getMaxConcurrentInstances() != null ? dto.getMaxConcurrentInstances() : 1);
    dag.setFailStrategy(
        StringUtils.hasText(dto.getFailStrategy())
            ? dto.getFailStrategy()
            : DagFailureStrategy.ABORT.name());
    dag.setDescription(dto.getDescription());
    // 默认值
    dag.setVersion(1);
    dag.setFireCount(0L);
    dag.setSuccessCount(0L);
    dag.setFailCount(0L);
    // CRON 模式计算 nextFireTime
    if ("CRON".equals(dag.getTriggerType()) && StringUtils.hasText(dag.getCronExpression())) {
      dag.setNextFireTime(nextFireTime(dag.getCronExpression()));
    }
    jobDagRepository.insert(dag);
    // P1-8: 保存 V1 版本快照
    saveVersionSnapshot(dag, "初始创建");
    log.info(
        "[JobDag] 创建 DAG: dagId={} dagKey={} dagName={}",
        dag.getId(),
        dag.getDagKey(),
        dag.getDagName());
    return dag.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void updateDag(String dagId, JobDagSaveDTO dto) {
    JobDag exists = jobDagRepository.selectById(dagId);
    if (exists == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.cronjob.msg_dag_not_found_def")
          .params(dagId)
          .build();
    }
    // 校验 dagKey 唯一性（排除自身）
    JobDag byKey = jobDagRepository.selectByDagKey(dto.getDagKey());
    if (byKey != null && !dagId.equals(byKey.getId())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_dag_already_exists")
          .params(dto.getDagKey())
          .build();
    }
    // 校验 DAG 定义
    validateDagDefinition(dto.getDagDefinition());
    validateCronExpression(dto.getTriggerType(), dto.getCronExpression());

    exists.setDagKey(dto.getDagKey());
    exists.setDagName(dto.getDagName());
    exists.setDagDefinition(dto.getDagDefinition());
    if (StringUtils.hasText(dto.getStatus())) {
      exists.setStatus(dto.getStatus());
    }
    if (StringUtils.hasText(dto.getTriggerType())) {
      exists.setTriggerType(dto.getTriggerType());
    }
    exists.setCronExpression(
        StringUtils.hasText(dto.getCronExpression()) ? dto.getCronExpression() : null);
    if (dto.getMaxConcurrentInstances() != null) {
      exists.setMaxConcurrentInstances(dto.getMaxConcurrentInstances());
    }
    if (StringUtils.hasText(dto.getFailStrategy())) {
      exists.setFailStrategy(dto.getFailStrategy());
    }
    if (dto.getDescription() != null) {
      exists.setDescription(dto.getDescription());
    }
    // 重新计算 nextFireTime（CRON 模式）
    if ("CRON".equals(exists.getTriggerType()) && StringUtils.hasText(exists.getCronExpression())) {
      exists.setNextFireTime(nextFireTime(exists.getCronExpression()));
    } else {
      exists.setNextFireTime(null);
    }
    // version + 1（乐观锁）
    exists.setVersion((exists.getVersion() == null ? 0 : exists.getVersion()) + 1);
    jobDagRepository.updateById(exists);
    // P1-8: 保存版本快照
    saveVersionSnapshot(exists, "更新 DAG 定义");
    log.info(
        "[JobDag] 更新 DAG: dagId={} dagKey={} version={}",
        dagId,
        exists.getDagKey(),
        exists.getVersion());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void deleteDag(String dagId) {
    JobDag exists = jobDagRepository.selectById(dagId);
    if (exists == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.cronjob.msg_dag_not_found_def")
          .params(dagId)
          .build();
    }
    jobDagRepository.deleteById(dagId);
    log.info("[JobDag] 删除 DAG: dagId={} dagKey={}", dagId, exists.getDagKey());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void enableDag(String dagId) {
    JobDag exists = jobDagRepository.selectById(dagId);
    if (exists == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.cronjob.msg_dag_not_found_def")
          .params(dagId)
          .build();
    }
    if (!"DRAFT".equals(exists.getStatus()) && !"DISABLED".equals(exists.getStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_dag_status_invalid")
          .params(exists.getStatus())
          .build();
    }
    exists.setStatus("ENABLED");
    // CRON 模式计算 nextFireTime
    if ("CRON".equals(exists.getTriggerType()) && StringUtils.hasText(exists.getCronExpression())) {
      exists.setNextFireTime(nextFireTime(exists.getCronExpression()));
    }
    exists.setVersion((exists.getVersion() == null ? 0 : exists.getVersion()) + 1);
    jobDagRepository.updateById(exists);
    log.info(
        "[JobDag] 启用 DAG: dagId={} dagKey={} nextFireTime={}",
        dagId,
        exists.getDagKey(),
        exists.getNextFireTime());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void disableDag(String dagId) {
    JobDag exists = jobDagRepository.selectById(dagId);
    if (exists == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.cronjob.msg_dag_not_found_def")
          .params(dagId)
          .build();
    }
    if (!"ENABLED".equals(exists.getStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_dag_status_invalid")
          .params(exists.getStatus())
          .build();
    }
    exists.setStatus("DISABLED");
    exists.setNextFireTime(null);
    exists.setVersion((exists.getVersion() == null ? 0 : exists.getVersion()) + 1);
    jobDagRepository.updateById(exists);
    log.info("[JobDag] 禁用 DAG: dagId={} dagKey={}", dagId, exists.getDagKey());
  }

  @Override
  @Transactional(readOnly = true)
  public JobDag getDagById(String dagId) {
    JobDag dag = jobDagRepository.selectById(dagId);
    if (dag == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.cronjob.msg_dag_not_found_def")
          .params(dagId)
          .build();
    }
    return dag;
  }

  @Override
  @Transactional(readOnly = true)
  public JobDag getDagByKey(String dagKey) {
    JobDag dag = jobDagRepository.selectByDagKey(dagKey);
    if (dag == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.cronjob.msg_dag_not_found_def")
          .params(dagKey)
          .build();
    }
    return dag;
  }

  @Override
  @Transactional(readOnly = true)
  public List<JobDag> listEnabledDags() {
    return jobDagRepository.selectEnabledDags();
  }

  @Override
  @Transactional(readOnly = true)
  public List<JobDag> listCronEnabledDags() {
    return jobDagRepository.selectCronEnabledDags();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String triggerDag(String dagKey, String triggerBy) {
    JobDag dag = jobDagRepository.selectByDagKey(dagKey);
    if (dag == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.cronjob.msg_dag_not_found_def")
          .params(dagKey)
          .build();
    }
    if (!"ENABLED".equals(dag.getStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_dag_dag_not_enabled")
          .params(dagKey)
          .build();
    }
    // 校验并发实例数（maxConcurrentInstances=0 表示不限制）
    int maxConcurrent =
        dag.getMaxConcurrentInstances() != null ? dag.getMaxConcurrentInstances() : 1;
    if (maxConcurrent > 0) {
      int active = jobDagInstanceRepository.countActiveInstances(dag.getId());
      if (active >= maxConcurrent) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.cronjob.msg_dag_concurrent_limit")
            .params(maxConcurrent)
            .build();
      }
    }
    // 创建 DAG 实例
    JobDagInstance instance = new JobDagInstance();
    instance.setDagId(dag.getId());
    instance.setDagKey(dag.getDagKey());
    instance.setStatus(DagInstanceStatus.PENDING.name());
    instance.setTriggerType("MANUAL");
    instance.setTriggerBy(triggerBy);
    String triggerTraceId = RequestContext.getTraceId();
    if (triggerTraceId == null || triggerTraceId.isBlank()) {
      triggerTraceId = MDC.get("traceId");
    }
    instance.setTriggerTraceId(triggerTraceId);
    jobDagInstanceRepository.insert(instance);
    log.info(
        "[JobDag] 触发 DAG: dagId={} dagKey={} instanceId={} triggerBy={}",
        dag.getId(),
        dag.getDagKey(),
        instance.getId(),
        triggerBy);

    // 异步派发执行（DagInstanceExecutor 延迟注入，未注册时仅创建实例记录）
    DagInstanceExecutor executor =
        dagInstanceExecutorProvider != null ? dagInstanceExecutorProvider.getIfAvailable() : null;
    if (executor != null) {
      try {
        executor.execute(instance.getId());
      } catch (Exception e) {
        log.error(
            "[JobDag] 异步执行 DAG 失败: instanceId={} reason={}", instance.getId(), e.getMessage(), e);
      }
    } else {
      log.warn("[JobDag] DagInstanceExecutor 未注册, 仅创建实例记录未执行: instanceId={}", instance.getId());
    }
    return instance.getId();
  }

  // ==================== P1-8: 工作流版本管理 ====================

  @Override
  @Transactional(readOnly = true)
  public List<JobDagVersion> listDagVersions(String dagId, int limit) {
    int effectiveLimit = limit > 0 ? limit : 50;
    return jobDagVersionRepository.selectByVersionDesc(dagId, effectiveLimit);
  }

  @Override
  @Transactional(readOnly = true)
  public JobDagVersion getDagVersion(String dagId, int version) {
    JobDagVersion versionDO = jobDagVersionRepository.selectByVersion(dagId, version);
    if (versionDO == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.cronjob.msg_dag_version_not_found")
          .params(dagId, version)
          .build();
    }
    return versionDO;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int rollbackDagVersion(String dagId, int targetVersion, String changedBy) {
    JobDag dag = jobDagRepository.selectById(dagId);
    if (dag == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.cronjob.msg_dag_not_found_def")
          .params(dagId)
          .build();
    }
    JobDagVersion targetVersionDO = jobDagVersionRepository.selectByVersion(dagId, targetVersion);
    if (targetVersionDO == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.cronjob.msg_dag_version_not_found")
          .params(dagId, targetVersion)
          .build();
    }
    // 回滚：将目标版本的 dagDefinition 复制到当前 DAG
    dag.setDagDefinition(targetVersionDO.getDagDefinition());
    dag.setDagName(targetVersionDO.getDagName());
    dag.setTriggerType(targetVersionDO.getTriggerType());
    dag.setCronExpression(targetVersionDO.getCronExpression());
    dag.setFailStrategy(targetVersionDO.getFailStrategy());
    // 重新计算 nextFireTime
    if ("CRON".equals(dag.getTriggerType()) && StringUtils.hasText(dag.getCronExpression())) {
      dag.setNextFireTime(nextFireTime(dag.getCronExpression()));
    } else {
      dag.setNextFireTime(null);
    }
    // version + 1（乐观锁）
    int newVersion = (dag.getVersion() == null ? 0 : dag.getVersion()) + 1;
    dag.setVersion(newVersion);
    jobDagRepository.updateById(dag);
    // 保存回滚版本快照
    saveVersionSnapshot(dag, "回滚到版本 V" + targetVersion);
    log.info(
        "[JobDag] 回滚 DAG 版本: dagId={} fromV={} toV={} newV={} changedBy={}",
        dagId,
        dag.getVersion() - 1,
        targetVersion,
        newVersion,
        changedBy);
    return newVersion;
  }

  /**
   * P1-8: 保存 DAG 版本快照。
   *
   * @param dag DAG 定义
   * @param remark 版本备注
   */
  private void saveVersionSnapshot(JobDag dag, String remark) {
    JobDagVersion versionDO = new JobDagVersion();
    versionDO.setDagId(dag.getId());
    versionDO.setDagKey(dag.getDagKey());
    versionDO.setVersion(dag.getVersion());
    versionDO.setDagDefinition(dag.getDagDefinition());
    versionDO.setDagName(dag.getDagName());
    versionDO.setTriggerType(dag.getTriggerType());
    versionDO.setCronExpression(dag.getCronExpression());
    versionDO.setFailStrategy(dag.getFailStrategy());
    versionDO.setRemark(remark);
    jobDagVersionRepository.insert(versionDO);
  }

  // ==================== 内部辅助方法 ====================

  /**
   * 校验 DAG 定义 JSON：结构校验（由 {@link DagDefinitionCodec} 完成）+ 环检测。
   *
   * @param dagDefinitionJson DAG 定义 JSON
   * @throws SysException 当 JSON 格式无效、节点缺失或存在环依赖时抛出
   */
  private void validateDagDefinition(String dagDefinitionJson) {
    DagDefinition definition = dagDefinitionCodec.fromJson(dagDefinitionJson);
    // 环检测：将 DagEdge 列表转为邻接表，复用 DagParser.hasCycle
    Map<String, List<String>> adj = buildAdjacencyListFromDagDefinition(definition);
    if (dagParser.hasCycle(adj)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_dag_has_cycle")
          .build();
    }
  }

  /**
   * 将 {@link DagDefinition} 的边列表转为邻接表（from → [to1, to2, ...]）。
   *
   * @param definition DAG 定义
   * @return 邻接表
   */
  private Map<String, List<String>> buildAdjacencyListFromDagDefinition(DagDefinition definition) {
    if (definition == null || definition.edges() == null || definition.edges().isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, List<String>> adj = new HashMap<>();
    // 确保所有节点都在邻接表中（即使没有出边）
    for (DagNode node : definition.nodes()) {
      adj.computeIfAbsent(node.jobKey(), k -> new ArrayList<>());
    }
    for (DagEdge edge : definition.edges()) {
      adj.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
      adj.computeIfAbsent(edge.to(), k -> new ArrayList<>());
    }
    return adj;
  }

  /**
   * 校验 CRON 触发类型必须提供 cronExpression。
   *
   * @param triggerType 触发类型
   * @param cronExpression Cron 表达式
   * @throws SysException 当 triggerType=CRON 且 cronExpression 为空时抛出
   */
  private void validateCronExpression(String triggerType, String cronExpression) {
    if ("CRON".equals(triggerType) && !StringUtils.hasText(cronExpression)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_dag_cron_required")
          .build();
    }
  }

  /**
   * 计算 Cron 表达式的下次触发时间。
   *
   * <p>使用 Spring 6 的 {@link CronExpression}（基于 LocalDateTime，无需时区转换）。 解析或计算失败时返回 {@code null}（catch
   * 异常避免抛出）。
   *
   * @param cron Cron 表达式
   * @return 下次触发时间；表达式非法或无法计算时返回 null
   */
  private LocalDateTime nextFireTime(String cron) {
    try {
      CronExpression expr = CronExpression.parse(cron);
      return expr.next(LocalDateTime.now());
    } catch (Exception e) {
      log.warn("[JobDag] 计算 nextFireTime 失败: cron={} err={}", cron, e.getMessage());
      return null;
    }
  }
}
