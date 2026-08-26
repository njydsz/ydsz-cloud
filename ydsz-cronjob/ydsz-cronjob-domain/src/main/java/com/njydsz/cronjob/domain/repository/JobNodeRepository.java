package com.njydsz.cronjob.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobNodeVO;

/**
 * 调度节点 Repository（domain 层契约）。
 *
 * <p>定义调度节点心跳与状态管理的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobNodeRepository {

  /**
   * 标记过期的 ONLINE 节点为 OFFLINE。
   *
   * @param staleThreshold 过期阈值（心跳早于此时间的节点视为过期）
   * @return 受影响行数
   */
  int markStaleOnlineAsOffline(LocalDateTime staleThreshold);

  /**
   * 查询过期的 ONLINE 节点 ID 列表。
   *
   * @param staleThreshold 过期阈值
   * @return 过期节点 ID 列表
   */
  List<String> findStaleOnlineNodeIds(LocalDateTime staleThreshold);

  /**
   * 删除长期 OFFLINE 的节点记录。
   *
   * @param offlineThreshold 离线阈值（早于此时间未恢复的 OFFLINE 节点将被删除）
   * @return 受影响行数
   */
  int deleteStaleOfflineNodes(LocalDateTime offlineThreshold);

  /**
   * 查询所有 ONLINE 状态的节点列表。
   *
   * @return ONLINE 节点列表
   */
  List<JobNodeVO> findOnlineNodes();

  /**
   * 根据条件查询节点列表。
   *
   * @param status 状态过滤（可为空）
   * @return 节点列表
   */
  List<JobNodeVO> findByStatus(String status);

  /**
   * 根据节点 ID 查询节点。
   *
   * @param nodeId 节点 ID
   * @return 节点 VO
   */
  Optional<JobNodeVO> findById(String nodeId);

  /**
   * 插入新节点记录。
   *
   * @param node 节点 VO
   */
  void insert(JobNodeVO node);

  /**
   * 更新节点记录（按 nodeId 匹配）。
   *
   * @param node 节点 VO
   * @return 受影响行数
   */
  int updateByNodeId(JobNodeVO node);

  /**
   * 更新节点心跳与运行指标。
   *
   * @param nodeId 节点 ID
   * @param lastHeartbeat 最后心跳时间
   * @param runningCount 运行中任务数
   * @param cpuUsage CPU 使用率
   * @param memUsagePct 内存使用率
   * @param status 节点状态
   * @return 受影响行数
   */
  int updateHeartbeat(
      String nodeId,
      LocalDateTime lastHeartbeat,
      int runningCount,
      BigDecimal cpuUsage,
      BigDecimal memUsagePct,
      String status);

  /**
   * 更新节点状态。
   *
   * @param nodeId 节点 ID
   * @param status 目标状态
   * @param lastHeartbeat 最后心跳时间
   * @return 受影响行数
   */
  int updateStatus(String nodeId, String status, LocalDateTime lastHeartbeat);

  // ===== P1-1: 节点健康检查 =====

  /**
   * 更新节点加权响应时长（EMA）。
   *
   * @param nodeId 节点 ID
   * @param responseTimeMs 加权平均响应时长（毫秒）
   */
  void updateResponseTime(String nodeId, long responseTimeMs);

  /**
   * 更新节点连续失败次数。
   *
   * @param nodeId 节点 ID
   * @param consecutiveFailures 连续失败次数
   */
  void updateConsecutiveFailures(String nodeId, int consecutiveFailures);

  /**
   * 重置节点连续失败次数（健康检查成功时调用）。
   *
   * @param nodeId 节点 ID
   */
  void resetConsecutiveFailures(String nodeId);
}
