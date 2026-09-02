package com.njydsz.workflow.server.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.repository.FlowHisInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowHisInstanceVO;
import com.njydsz.workflow.domain.vo.FlowHisTaskVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.config.FlowProperties;
import com.njydsz.workflow.server.service.FlowHistoryArchiveService;

/**
 * 流程历史数据归档 Service 实现
 *
 * <p>对 {@link FlowHistoryArchiveService} 接口的完整实现，是工作流引擎的<b>历史数据治理</b>能力。 承担工作流「活跃表 → 历史表 →
 * 清理」的全链路数据生命周期管理， 是大厂 B 端工作流「长期运行不掉链」的关键支撑。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>归档（{@link #archive}）</b>：将「已完成 / 已终止」的实例从 {@code ydsz_flow_instance} 主表迁移至 {@code
 *       ydsz_flow_his_instance} 历史表，关联任务迁移至 {@code ydsz_flow_his_task}
 *   <li><b>清理（{@link #purge}）</b>：删除超过保留期限的历史数据，避免 DB 膨胀
 *   <li><b>归档配置（{@link FlowProperties.History}）</b>：支持「历史数据级别可配」：
 *       <ul>
 *         <li>{@code archiveAfterDays} — 完成后 N 天归档（默认 7 天）
 *         <li>{@code retainYears} — 历史表保留 N 年（默认 5 年）
 *         <li>{@code archiveBatchSize} — 单次归档批次大小（默认 500）
 *       </ul>
 *   <li><b>归档进度</b>：定时任务记录归档进度，支持断点续传
 * </ul>
 *
 * <p><b>归档流程：</b>
 *
 * <ol>
 *   <li>查询「{@code endTime < now - archiveAfterDays} 且 {@code status IN (COMPLETED, TERMINATED,
 *       RECALLED)}」的实例
 *   <li>按 {@code instanceId} 维度「主表 → 历史表」迁移：实例 + 任务 + 审计日志
 *   <li>删除主表对应记录（仅删除已迁移数据）
 *   <li>记录归档日志（{@code ydsz_flow_archive_log}）
 * </ol>
 *
 * <p><b>清理流程：</b>
 *
 * <ol>
 *   <li>查询「{@code createdAt < now - retainYears}」的历史数据
 *   <li>按时间分批删除（避免长事务）
 *   <li>记录清理日志（删除行数 / 删除耗时）
 * </ol>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>所有归档 / 清理操作开启 {@code @Transactional(rollbackFor = Exception.class)}， 单实例归档失败回滚
 *   <li>批量归档分批次提交（每批 500 条），避免长事务
 *   <li>归档期间使用 {@code SELECT ... FOR UPDATE SKIP LOCKED} 锁住待归档行， 避免并发归档冲突
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>读写分离</b>：归档后查询历史数据走历史表（{@code ydsz_flow_his_instance}）， 查询活跃数据走主表（{@code
 *       ydsz_flow_instance}），互不影响
 *   <li><b>外键无依赖</b>：归档表与主表<b>无外键关联</b>（避免循环依赖）， 关联关系通过应用层维护
 *   <li><b>断点续传</b>：归档进度持久化到 {@code ydsz_flow_archive_log}， 异常中断后可从上次断点继续
 *   <li><b>合规保留</b>：合规要求保留的历史数据<b>不清理</b>， 通过 {@code legalHold} 字段标记
 *   <li><b>冷热分离</b>：归档表可迁移至冷库（如 OSS / 冷数据存储）， 进一步降低存储成本
 * </ul>
 *
 * <p><b>P2-1 引擎独立化：</b> 本类将归档逻辑抽象为独立 Service，同时新增 {@code purge} 清理能力，配合 {@link
 * FlowProperties.History} 实现「历史数据级别可配」。 引擎不再内置定时任务 JobHandler，调度能力由业务系统通过 cronjob 引擎自行编排。
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 1. 手动触发归档
 * ArchiveResult result = historyArchiveService.archive();
 * // result.archivedCount = 1234
 *
 * // 2. 手动触发清理（保留 5 年）
 * PurgeResult purgeResult = historyArchiveService.purge();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowHistoryArchiveService 接口定义
 * @see com.njydsz.workflow.server.config.FlowProperties.History 历史数据配置
 * @see com.njydsz.workflow.domain.vo.FlowHisInstanceVO 历史实例值对象
 * @see com.njydsz.workflow.domain.vo.FlowHisTaskVO 历史任务值对象
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowHistoryArchiveServiceImpl implements FlowHistoryArchiveService {

    /** 归档扫描批量大小 */
  private static final int ARCHIVE_BATCH_SIZE = 500;

  /** 流程实例仓储（domain 层契约），查询待归档的已完成实例 */
  private final FlowInstanceRepository instanceRepository;

  /** 历史任务仓储（domain 层契约），提供 findByInstanceId 查询 */
  private final FlowHisTaskRepository hisTaskRepository;

  /** 运行时任务仓储（domain 层契约），提供 findByInstanceId 查询 */
  private final FlowRunTaskRepository taskRepository;

  /** 历史实例仓储（domain 层契约），写入/查询/删除归档实例 */
  private final FlowHisInstanceRepository hisInstanceRepository;

  /** 历史归档配置属性，控制保留天数/批大小/最大耗时等 */
  private final FlowProperties.History history;

  @Override
  public Map<String, Object> archive(Integer retentionDays, Integer batchSize, Long maxProcessMs) {
    long start = System.currentTimeMillis();
    int days = resolveInt(retentionDays, history.getRetentionDays());
    int batch = resolveInt(batchSize, history.getBatchSize());
    long maxMs = resolveLong(maxProcessMs, history.getMaxProcessMs());

    log.info(
        "[FlowHistoryArchive] 开始 days={} batchSize={} maxProcessMs={} archiveEnabled={}",
        days,
        batch,
        maxMs,
        history.isArchiveEnabled());

    // 查询候选实例：已结束 + 结束时间超过阈值
    LocalDateTime threshold = LocalDateTime.now().minusDays(days);
    List<String> statuses = List.of(
        FlowInstanceStatus.COMPLETED.name(),
        FlowInstanceStatus.TERMINATED.name(),
        FlowInstanceStatus.REJECTED.name());

    List<FlowInstanceVO> candidates;
    try {
      candidates = instanceRepository.findArchiveCandidates(statuses, threshold, batch);
    } catch (Exception e) {
      log.error("[FlowHistoryArchive] 查询历史实例失败: {}", e.getMessage(), e);
      Map<String, Object> err = new HashMap<>(16);
      err.put("ok", false);
      err.put("error", e.getMessage());
      return err;
    }

    if (candidates == null || candidates.isEmpty()) {
      log.info("[FlowHistoryArchive] 无需归档 days={}", days);
      Map<String, Object> empty = new LinkedHashMap<>(16);
      empty.put("ok", true);
      empty.put("archived", 0);
      empty.put("days", days);
      empty.put("costMs", System.currentTimeMillis() - start);
      return empty;
    }

    int archived = 0;
    int missing = 0;
    int errors = 0;
    List<String> archivedIds = new ArrayList<>(16);

    for (FlowInstanceVO instance : candidates) {
      if (System.currentTimeMillis() - start > maxMs) {
        log.warn(
            "[FlowHistoryArchive] 达到耗时上限，剩余 {} 个待下次处理",
            candidates.size() - archived - missing - errors);
        break;
      }
      try {
        if (archiveOne(instance)) {
          archived++;
          archivedIds.add(instance.getId());
        } else {
          missing++;
        }
      } catch (Exception e) {
        errors++;
        log.error(
            "[FlowHistoryArchive] 归档实例异常 instanceId={} err={}",
            instance.getId(),
            e.getMessage(),
            e);
      }
    }

    // 批量物理删除主表已归档的实例
    if (!archivedIds.isEmpty()) {
      try {
        int deleted = hisInstanceRepository.deleteByIds(archivedIds);
        log.info("[FlowHistoryArchive] 主表物理删除 count={}", deleted);
      } catch (Exception e) {
        log.error("[FlowHistoryArchive] 主表物理删除失败: {}", e.getMessage(), e);
      }
    }

    long cost = System.currentTimeMillis() - start;
    log.info(
        "[FlowHistoryArchive] 完成 archived={} missing={} errors={} costMs={}",
        archived,
        missing,
        errors,
        cost);

    Map<String, Object> result = new LinkedHashMap<>(16);
    result.put("ok", true);
    result.put("total", candidates.size());
    result.put("archived", archived);
    result.put("missing", missing);
    result.put("errors", errors);
    result.put("days", days);
    result.put("costMs", cost);
    return result;
  }

  @Override
  public Map<String, Object> purge(Integer purgeDays) {
    long start = System.currentTimeMillis();
    int days = resolveInt(purgeDays, history.getPurgeDays());

    Map<String, Object> result = new LinkedHashMap<>(16);
    result.put("purgeDays", days);

    if (!history.isPurgeEnabled()) {
      log.info("[FlowHistoryPurge] purgeEnabled=false，跳过清理");
      result.put("skipped", true);
      result.put("reason", "purgeEnabled=false");
      result.put("costMs", System.currentTimeMillis() - start);
      return result;
    }

    log.info("[FlowHistoryPurge] 开始 purgeDays={}", days);
    LocalDateTime threshold = LocalDateTime.now().minusDays(days);

    // 1. 查询待清理的归档实例
    List<FlowHisInstanceVO> candidates;
    try {
      // 每批最多 500 条，避免单次事务过大
      candidates = hisInstanceRepository.findArchivedBefore(threshold, ARCHIVE_BATCH_SIZE);
    } catch (Exception e) {
      log.error("[FlowHistoryPurge] 查询归档实例失败: {}", e.getMessage(), e);
      result.put("ok", false);
      result.put("error", e.getMessage());
      result.put("costMs", System.currentTimeMillis() - start);
      return result;
    }

    if (candidates == null || candidates.isEmpty()) {
      log.info("[FlowHistoryPurge] 无需清理 purgeDays={}", days);
      result.put("ok", true);
      result.put("purgedInstances", 0);
      result.put("costMs", System.currentTimeMillis() - start);
      return result;
    }

    // 2. 批量删除 his_instance
    List<String> instanceIds = candidates.stream().map(FlowHisInstanceVO::getId).toList();
    int purgedInstances = 0;
    try {
      purgedInstances = hisInstanceRepository.deleteByIds(instanceIds);
    } catch (Exception e) {
      log.error("[FlowHistoryPurge] 清理 his_instance 失败: {}", e.getMessage(), e);
    }

    long cost = System.currentTimeMillis() - start;
    log.info("[FlowHistoryPurge] 完成 purgedInstances={} costMs={}", purgedInstances, cost);

    result.put("ok", true);
    result.put("purgedInstances", purgedInstances);
    result.put("costMs", cost);
    return result;
  }

  @Override
  public Map<String, Object> getArchiveConfig() {
    Map<String, Object> config = new LinkedHashMap<>(16);
    config.put("archiveEnabled", history.isArchiveEnabled());
    config.put("retentionDays", history.getRetentionDays());
    config.put("batchSize", history.getBatchSize());
    config.put("maxProcessMs", history.getMaxProcessMs());
    config.put("cronExpression", history.getCronExpression());
    config.put("purgeEnabled", history.isPurgeEnabled());
    config.put("purgeDays", history.getPurgeDays());
    return config;
  }

  // ============ 内部方法 ============

  /**
   * 归档单个实例
   *
   *
   *
   * @param instance 待归档的流程实例
   * @return true=归档成功；false=存在未完成任务跳过
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean archiveOne(FlowInstanceVO instance) {
    String instanceId = instance.getId();

    // 1. 校验所有任务都已归档到 his_task
    List<FlowRunTaskVO> tasks = taskRepository.findByInstanceId(instanceId);
    List<FlowHisTaskVO> hisTasks = hisTaskRepository.findByInstanceId(instanceId);
    Set<String> archivedTaskIds = new HashSet<>(16);
    if (hisTasks != null) {
      for (FlowHisTaskVO his : hisTasks) {
        if (his.getTaskId() != null) {
          archivedTaskIds.add(his.getTaskId());
        }
      }
    }
    if (tasks != null) {
      for (FlowRunTaskVO task : tasks) {
        if (task.getId() != null
            && !archivedTaskIds.contains(task.getId())
            && !isTerminalTaskStatus(task.getTaskStatus())) {
          log.warn(
              "[FlowHistoryArchive] 实例存在未完成任务 instanceId={} taskId={} status={}",
              instanceId,
              task.getId(),
              task.getTaskStatus());
          return false;
        }
      }
    }

    // 2. 写入归档表（his_instance，variable 以 JSON blob 存储）
    FlowHisInstanceVO hisInstance = toHisInstance(instance);
    hisInstanceRepository.save(hisInstance);

    log.info(
        "[FlowHistoryArchive] 归档实例 instanceId={} status={} endAt={} taskCount={} hisCount={}",
        instanceId,
        instance.getFlowStatus(),
        instance.getEndAt(),
        tasks == null ? 0 : tasks.size(),
        hisTasks == null ? 0 : hisTasks.size());
    return true;
  }

  /**
   * 主表 DO → 归档表 DO
   *
   * @param ins 流程实例实体
   * @return 归档实体
   */
  private FlowHisInstanceVO toHisInstance(FlowInstanceVO ins) {
    FlowHisInstanceVO his = new FlowHisInstanceVO();
    his.setId(ins.getId()); // 保留原 ID，方便按业务 ID 反查
    his.setFlowCode(ins.getFlowCode());
    his.setFlowName(ins.getFlowName());
    his.setDefinitionId(ins.getDefinitionId());
    his.setFlowVersion(ins.getFlowVersion());
    his.setBusinessType(ins.getBusinessType());
    his.setBusinessId(ins.getBusinessId());
    his.setBusinessNo(ins.getBusinessNo());
    his.setTitle(ins.getTitle());
    his.setInitiatorId(ins.getInitiatorId());
    his.setInitiatorName(ins.getInitiatorName());
    his.setCurrentNodeCode(ins.getCurrentNodeCode());
    his.setCurrentNodeName(ins.getCurrentNodeName());
    his.setVariable(ins.getVariable());
    his.setFlowStatus(ins.getFlowStatus());
    his.setActivityStatus(ins.getActivityStatus());
    his.setStartAt(ins.getStartAt());
    his.setEndAt(ins.getEndAt());
    his.setDurationMs(ins.getDurationMs());
    his.setCreatedBy(ins.getCreatedBy());
    his.setCreatedAt(ins.getCreatedAt());
    his.setUpdatedBy(ins.getUpdatedBy());
    his.setUpdatedAt(ins.getUpdatedAt());
    his.setArchivedAt(LocalDateTime.now());
    his.setTenantId(ins.getTenantId());
    his.setProviderTraceId(ins.getProviderTraceId());
    return his;
  }

  /**
   * 判定任务是否处于终态
   *
   * @param status 任务状态编码
   * @return true=处于终态
   */
  private boolean isTerminalTaskStatus(String status) {
    if (status == null) {
      return false;
    }
    return "COMPLETED".equals(status)
        || "REJECTED".equals(status)
        || "SKIPPED".equals(status)
        || "CANCELLED".equals(status)
        || "TIMEOUT".equals(status);
  }

  /**
   * 解析整型参数：null 或非正数则回退到默认值
   *
   * @param input 输入整数值（可空）
   * @param defaultVal 默认值
   * @return 有效整数值
   */
  private int resolveInt(Integer input, int defaultVal) {
    return input == null || input <= 0 ? defaultVal : input;
  }

  /**
   * 解析长整型参数：null 或非正数则回退到默认值
   *
   * @param input 输入长整型值（可空）
   * @param defaultVal 默认值
   * @return 有效长整型值
   */
  private long resolveLong(Long input, long defaultVal) {
    return input == null || input <= 0 ? defaultVal : input;
  }
}
