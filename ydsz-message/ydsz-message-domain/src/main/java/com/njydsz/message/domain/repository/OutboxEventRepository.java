package com.njydsz.message.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.event.OutboxEvent;

/**
 * Outbox 事件仓储接口（domain 层契约）。
 *
 * <p>领域事件的 Outbox 持久化层，提供：
 * <ul>
 *   <li>业务同事务落库（保证事件不丢失）</li>
 *   <li>扫描待发布事件（供 Outbox 扫描器使用）</li>
 *   <li>标记发布状态（PENDING → PUBLISHING → PUBLISHED）</li>
 *   <li>失败补偿重试</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface OutboxEventRepository {

  /**
   * 保存 Outbox 事件（与业务操作同事务）。
   *
   * @param event Outbox 事件
   * @return 保存成功返回 true
   */
  boolean save(OutboxEvent event);

  /**
   * 根据 ID 查询 Outbox 事件。
   *
   * @param id 事件 ID
   * @return Outbox 事件，不存在返回 Optional.empty()
   */
  Optional<OutboxEvent> findById(String id);

  /**
   * 扫描待发布的事件（按创建时间升序，分页）。
   *
   * @param limit 最大扫描数量
   * @param beforeTime 扫描此时间之前创建的 PENDING 事件
   * @return 待发布事件列表
   */
  List<OutboxEvent> findPending(int limit, LocalDateTime beforeTime);

  /**
   * 标记事件为发布中。
   *
   * @param id 事件 ID
   * @return 标记成功返回 true
   */
  boolean markPublishing(String id);

  /**
   * 标记事件为已发布。
   *
   * @param id 事件 ID
   * @return 标记成功返回 true
   */
  boolean markPublished(String id);

  /**
   * 标记事件为发布失败（增加重试次数）。
   *
   * @param id 事件 ID
   * @param maxRetries 最大重试次数
   * @return 标记成功返回 true
   */
  boolean markFailed(String id, int maxRetries);

  /**
   * 统计各状态事件数量。
   *
   * @return 状态 → 数量映射
   */
  Map<String, Long> countByStatus();

  /**
   * 分页查询 Outbox 事件列表。
   *
   * @param status 状态过滤（可选）
   * @param pageNum 页码
   * @param pageSize 每页大小
   * @return 分页结果
   */
  PageResponse<List<OutboxEvent>> findPage(String status, int pageNum, int pageSize);
}
