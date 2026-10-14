package com.njydsz.workflow.server.process;

import java.util.List;

/**
 * 流程挖掘分析服务骨架。
 *
 * <p>从已执行的 FlowInstance 日志中挖掘：
 *
 * <ul>
 *   <li>频繁路径（最常执行的节点序列）
 *   <li>瓶颈节点（平均耗时超阈值的节点）
 *   <li>返工循环（节点反复回退）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.14
 */
public interface ProcessMiningService {

  /**
   * 挖掘指定流程定义的频繁路径 Top N。
   *
   * @param processDefinitionId 流程定义 ID
   * @param topN 返回前 N 条路径
   * @return 路径列表（节点 ID 序列 + 出现频率）
   */
  List<FrequentPath> mineFrequentPaths(String processDefinitionId, int topN);

  /**
   * 检测瓶颈节点（平均耗时超过阈值的节点）。
   *
   * @param processDefinitionId 流程定义 ID
   * @param thresholdMs 耗时阈值（毫秒）
   * @return 瓶颈节点列表
   */
  List<BottleneckNode> detectBottlenecks(String processDefinitionId, long thresholdMs);

  /**
   * 挖掘结果 PO：频繁路径。
   *
   * @param nodeSequence 节点编码序列（有序）
   * @param occurrence 出现次数
   * @param percentage 占总实例百分比（0.0 ~ 100.0）
   */
  record FrequentPath(List<String> nodeSequence, int occurrence, double percentage) {}

  /**
   * 挖掘结果 PO：瓶颈节点。
   *
   * @param nodeId 节点编码
   * @param nodeName 节点名称
   * @param avgDurationMs 平均耗时（毫秒）
   * @count 样本数量
   */
  record BottleneckNode(String nodeId, String nodeName, double avgDurationMs, int count) {}
}
