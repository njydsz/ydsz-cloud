package com.njydsz.cronjob.domain.repository.outbox;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.vo.OutboxEventVO;

/**
 * Outbox 事件仓储接口（P0-2：事务性 Outbox 事件模式）。
 *
 * <p>定义事件写入、查询、状态变更的契约。实现层位于 infra 模块，保证事件写入可与业务操作共用同一事务。
 *
 * <p>调用方通过 {@link #save(OutboxEventVO)} 写入事件（在业务事务内），再由
 * {@link com.njydsz.cronjob.server.core.outbox.OutboxPublisher} 异步扫描并发布。
 *
 * <p>所有方法返回领域 VO（{@link OutboxEventVO}），禁止泄露 infra 实体。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface OutboxEventRepository {

  /**
   * 写入一条 Outbox 事件。
   *
   * <p>应在业务操作的事务内调用，保证业务数据与事件的原子性。
   *
   * @param event 待写入的事件（非空）
   * @return 写入后的事件（含生成的 ID）
   */
  OutboxEventVO save(OutboxEventVO event);

  /**
   * 批量写入 Outbox 事件。
   *
   * <p>应在业务操作的事务内调用，保证批量业务数据与事件的原子性。
   *
   * @param events 待写入的事件列表（非空）
   * @return 写入后的事件列表（含生成的 ID）
   */
  List<OutboxEventVO> saveAll(List<OutboxEventVO> events);

  /**
   * 查询待发布的事件（下次重试时间已到，且重试次数未超限）。
   *
   * <p>按创建时间升序，限制 batchSize 条。
   *
   * @param now      当前时间
   * @param maxRetry 最大重试次数
   * @param batchSize 批次大小
   * @return 待发布事件列表
   */
  List<OutboxEventVO> findPending(LocalDateTime now, int maxRetry, int batchSize);

  /**
   * 将事件标记为已发布（CAS 语义：仅 PENDING 状态可更新）。
   *
   * @param id 事件 ID
   * @return true 表示更新成功
   */
  boolean markPublished(Long id);

  /**
   * 将事件标记为死亡信（重试耗尽）。
   *
   * @param id 事件 ID
   * @return true 表示更新成功
   */
  boolean markDead(Long id);

  /**
   * 递增重试计数并更新下次重试时间（指数退避）。
   *
   * @param id        事件 ID
   * @param nextRetry 下次重试时间
   * @return true 表示更新成功
   */
  boolean incrementRetry(Long id, LocalDateTime nextRetry);

  /**
   * 删除已发布且超过保留期限的事件（清理历史数据）。
   *
   * @param beforeTime 保留期限（删除此时间之前的数据）
   * @return 删除行数
   */
  int deletePublishedBefore(LocalDateTime beforeTime);
}
