package com.njydsz.common.event.archive;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * Outbox 事件归档仓储接口（F-4）
 *
 * <p>为已投递（SENT）或已丢弃（DEAD_LETTER）的消息提供归档能力：
 *
 * <ul>
 *   <li>消息由 Outbox 表归档移动到归档表（降低主表存储压力，加速查询）
 *   <li>支持按事件类型、aggregateId、时间范围检索历史消息
 *   <li>归档数据可设置独立的 TTL（如 90 天后清理）
 * </ul>
 *
 * <p><b>归档策略（建议）：</b>
 *
 * <ul>
 *   <li>SENT 消息：投递成功后 7 天自动归档（物理移动到归档表）
 *   <li>DEAD_LETTER 消息：人工处理后立即归档
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public interface OutboxArchiveRepository {

  /**
   * 归档单条消息（INSERT 到归档表 + DELETE 原 Outbox 表）
   *
   * <p>调用方需确保在同一数据库事务中执行，保证一致性。
   *
   * @param message 需要归档的消息
   */
  void archive(OutboxMessage message);

  /**
   * 批量归档消息
   *
   * @param messages 需要归档的消息列表
   */
  void archiveBatch(List<OutboxMessage> messages);

  /**
   * 根据事件 ID 查找归档消息
   *
   * @param messageId OutboxMessage ID
   * @return 归档消息（可能为空）
   */
  Optional<OutboxMessage> findById(String messageId);

  /**
   * 根据 aggregateId 查询归档消息列表
   *
   * @param aggregateId 聚合根 ID
   * @return 归档消息列表（按创建时间倒序）
   */
  List<OutboxMessage> findByAggregateId(String aggregateId);

  /**
   * 分页查询归档消息
   *
   * @param eventType 事件类型（可为 null 表示不过滤）
   * @param startTime 开始时间（可为 null）
   * @param endTime 结束时间（可为 null）
   * @param pageable 分页参数
   * @return 分页归档消息
   */
  Page<OutboxMessage> findArchives(String eventType, Instant startTime, Instant endTime,
      Pageable pageable);

  /**
   * 清理归档表超期数据（定期维护）
   *
   * @param beforeTime 早于此时间的归档将被删除
   * @return 删除条数
   */
  int deleteArchivedBefore(Instant beforeTime);

  /**
   * 检查归档表是否存在（用于懒加载/条件装配）
   *
   * @return true 表示归档表可用
   */
  boolean isArchiveTableAvailable();
}
