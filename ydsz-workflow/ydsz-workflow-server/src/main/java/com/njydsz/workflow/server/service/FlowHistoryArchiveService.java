package com.njydsz.workflow.server.service;

import java.util.Map;

/**
 * 流程历史归档服务。
 *
 * <p>归档已完成实例到历史表。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowHistoryArchiveService {

  /**
   * 执行历史实例归档
   *
   * <p>扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过 {@code retentionDays} 的实例， 将其迁移到 {@code
   * ydsz_flow_his_instance} 冷存储表，并从主表物理删除。
   *
   * <p>参数为 null 时使用 {@code FlowProperties.History} 配置的默认值，便于 JobHandler 调用时 仅以 paramsJson
   * 覆盖部分参数（如临时归档 90 天前的数据）。
   *
   * @param retentionDays 归档阈值天数（null 则使用配置默认值）
   * @param batchSize 单次批量大小（null 则使用配置默认值）
   * @param maxProcessMs 单次最大耗时毫秒（null 则使用配置默认值）
   * @return 执行结果摘要：total/archived/missing/errors/days/costMs
   */
  Map<String, Object> archive(Integer retentionDays, Integer batchSize, Long maxProcessMs);

  /**
   * 清理归档表中的过期冷数据
   *
   * <p>删除 {@code ydsz_flow_his_instance} 与 {@code ydsz_flow_his_variable} 中 {@code archived_at} 早于
   * {@code now - purgeDays} 的记录，回收存储空间。
   *
   * <p>仅在 {@code FlowHistoryProperties.purgeEnabled=true} 时生效； 参数为 null 时使用配置默认 purgeDays。
   *
   * @param purgeDays 清理阈值天数（null 则使用配置默认值）
   * @return 执行结果摘要：purgedInstances/purgedVariables/purgeDays/costMs/skipped
   */
  Map<String, Object> purge(Integer purgeDays);

  /**
   * 查询当前归档配置（用于运维查看生效配置）
   *
   * @return 配置项
   *     Map：archiveEnabled/retentionDays/batchSize/maxProcessMs/cronExpression/purgeEnabled/purgeDays
   */
  Map<String, Object> getArchiveConfig();
}
