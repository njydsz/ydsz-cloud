package com.njydsz.cronjob.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.infra.entity.job.JobNode;

/**
 * 调度节点 Repository。
 *
 * <p>封装 {@code ydsz_job_node} 表的数据访问，提供业务语义化的查询方法。
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
  List<String> selectStaleOnlineNodeIds(LocalDateTime staleThreshold);

  /**
   * 删除长期 OFFLINE 的节点记录。
   *
   * @param offlineThreshold 离线阈值（早于此时间未恢复的 OFFLINE 节点将被删除）
   * @return 受影响行数
   */
  int deleteStaleOfflineNodes(LocalDateTime offlineThreshold);
}
