package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

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
}
