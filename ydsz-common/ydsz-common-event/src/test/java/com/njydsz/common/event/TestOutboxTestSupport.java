package com.njydsz.common.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.gateway.EventPublishGateway;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.model.OutboxStatus;

/**
 * Outbox 测试工具包（E-4）
 *
 * <p>为业务模块提供内存级的 OutboxRepository mock 和 EventPublishGateway mock， 使"领域事件是否正确发布"的单元测试不需要启动真实 DB
 * 或 MQ 中间件。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 1. 创建测试替身
 * InMemoryOutboxStore store = new InMemoryOutboxStore();
 * CollectingGateway gateway = new CollectingGateway();
 *
 * // 2. 注入业务服务
 * OrderService orderService = new OrderService(orderMapper, outboxService);
 *
 * // 3. 执行业务方法
 * orderService.createOrder(new OrderCreateDTO(...));
 *
 * // 4. 断言事件已发布
 * assertThat(store.countPending()).isEqualTo(1);
 * }</pre>
 *
 * <p>生产代码无需引入本工具类（仅在 test classpath 使用）。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public final class TestOutboxTestSupport {

  private TestOutboxTestSupport() {}

  /**
   * 内存 Outbox 存储（测试替身）
   *
   * <p>提供与真实 OutboxRepository 相同的 CAS claim 语义（通过 synchronized 保证原子性）。
   * 适用于单元测试场景。不支持事务和持久化。
   *
   * <p>注意：本存储未实现 OutboxRepository 接口（因 OutboxRepository 是具体类）， 但提供方法签名兼容的 API，可直接在测试中模拟仓储行为。
   */
  public static class InMemoryOutboxStore {

    private final Map<String, OutboxMessage> store = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    /**
     * 保存消息到存储
     *
     * @param message Outbox 消息
     */
    public void save(OutboxMessage message) {
      String id =
          message.getId() != null ? message.getId()
              : String.valueOf(idGenerator.getAndIncrement());
      store.put(id, cloneWithId(message, id));
    }

    /**
     * 批量保存消息
     *
     * @param messages 消息列表
     */
    public void saveBatch(List<OutboxMessage> messages) {
      messages.forEach(this::save);
    }

    /**
     * 查询待投递消息
     *
     * @param limit 最大条数
     * @return 待投递消息列表
     */
    public List<OutboxMessage> findPending(int limit) {
      return store.values().stream()
          .filter(m -> m.getStatus() == OutboxStatus.PENDING)
          .filter(m -> m.getNextRetryAt() == null || !m.getNextRetryAt().isAfter(Instant.now()))
          .sorted((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) {
              return 0;
            }
            return a.getCreatedAt().compareTo(b.getCreatedAt());
          })
          .limit(limit)
          .toList();
    }

    /**
     * 原子 claim 消息（CAS 语义）
     *
     * @param id 消息 ID
     * @return true 表示 claim 成功
     */
    public boolean claimForProcessing(String id) {
      OutboxMessage msg = store.get(id);
      if (msg == null || msg.getStatus() != OutboxStatus.PENDING) {
        return false;
      }
      synchronized (store) {
        msg = store.get(id);
        if (msg == null || msg.getStatus() != OutboxStatus.PENDING) {
          return false;
        }
        store.put(id, withStatus(msg, OutboxStatus.PROCESSING));
        return true;
      }
    }

    /**
     * 批量原子 claim 消息（逐条降级）
     *
     * @param ids 消息 ID 列表
     * @return 成功 claim 的数量
     */
    public int claimBatchForProcessing(List<String> ids) {
      int count = 0;
      for (String id : ids) {
        if (claimForProcessing(id)) {
          count++;
        }
      }
      return count;
    }

    /**
     * 回收超时的 PROCESSING 消息
     *
     * @param thresholdMinutes 超时阈值（分钟）
     * @return 回收的消息数量
     */
    public int reclaimStaleProcessing(int thresholdMinutes) {
      Instant cutoff = Instant.now().minusSeconds(thresholdMinutes * 60L);
      int count = 0;
      for (OutboxMessage msg : store.values()) {
        if (msg.getStatus() == OutboxStatus.PROCESSING
            && msg.getUpdatedAt() != null
            && msg.getUpdatedAt().isBefore(cutoff)) {
          store.put(msg.getId(), withStatus(msg, OutboxStatus.PENDING));
          count++;
        }
      }
      return count;
    }

    /**
     * 标记消息为已投递
     *
     * @param id 消息 ID
     */
    public void markAsSent(String id) {
      OutboxMessage msg = store.get(id);
      if (msg != null) {
        store.put(id, OutboxMessage.builder()
            .id(msg.getId())
            .aggregateId(msg.getAggregateId())
            .aggregateType(msg.getAggregateType())
            .eventType(msg.getEventType())
            .payload(msg.getPayload())
            .status(OutboxStatus.SENT)
            .retryCount(msg.getRetryCount())
            .maxRetries(msg.getMaxRetries())
            .nextRetryAt(msg.getNextRetryAt())
            .createdAt(msg.getCreatedAt())
            .updatedAt(Instant.now())
            .sentAt(Instant.now())
            .errorMessage(null)
            .tenantId(msg.getTenantId())
            .idempotencyKey(msg.getIdempotencyKey())
            .schemaVersion(msg.getSchemaVersion())
            .isCompressed(msg.isCompressed())
            .traceId(msg.getTraceId())
            .build());
      }
    }

    /**
     * 标记消息为失败
     *
     * @param id 消息 ID
     * @param errorMessage 错误信息
     * @param backoffSeconds 退避秒数
     */
    public void markAsFailed(String id, String errorMessage, long backoffSeconds) {
      OutboxMessage msg = store.get(id);
      if (msg != null) {
        long newRetry = msg.getRetryCount() + 1;
        OutboxStatus newStatus = newRetry >= msg.getMaxRetries()
            ? OutboxStatus.DEAD_LETTER : OutboxStatus.PENDING;
        store.put(id, OutboxMessage.builder()
            .id(msg.getId())
            .aggregateId(msg.getAggregateId())
            .aggregateType(msg.getAggregateType())
            .eventType(msg.getEventType())
            .payload(msg.getPayload())
            .status(newStatus)
            .retryCount(newRetry)
            .maxRetries(msg.getMaxRetries())
            .nextRetryAt(Instant.now().plusSeconds(backoffSeconds))
            .createdAt(msg.getCreatedAt())
            .updatedAt(Instant.now())
            .sentAt(msg.getSentAt())
            .errorMessage(errorMessage)
            .tenantId(msg.getTenantId())
            .idempotencyKey(msg.getIdempotencyKey())
            .schemaVersion(msg.getSchemaVersion())
            .isCompressed(msg.isCompressed())
            .traceId(msg.getTraceId())
            .build());
      }
    }

    /**
     * 统计各状态消息数（始终查询实时数据）
     *
     * @param useCache 是否使用缓存（内存存储忽略此参数）
     * @return 状态 → 数量
     */
    public Map<String, Long> countByStatus(boolean useCache) {
      return countByStatusFromDb();
    }

    /**
     * 统计各状态消息数
     *
     * @return 状态 → 数量
     */
    public Map<String, Long> countByStatus() {
      return countByStatusFromDb();
    }

    private Map<String, Long> countByStatusFromDb() {
      Map<String, Long> result = new ConcurrentHashMap<>();
      for (OutboxStatus s : OutboxStatus.values()) {
        result.put(s.name(), 0L);
      }
      for (OutboxMessage m : store.values()) {
        result.merge(m.getStatus().name(), 1L, Long::sum);
      }
      return result;
    }

    /**
     * 删除超期的 SENT 消息
     *
     * @param beforeTime 截止时间
     * @return 删除条数
     */
    public int deleteSentBefore(Instant beforeTime) {
      int count = 0;
      var iterator = store.entrySet().iterator();
      while (iterator.hasNext()) {
        var entry = iterator.next();
        OutboxMessage msg = entry.getValue();
        if (msg.getStatus() == OutboxStatus.SENT
            && msg.getSentAt() != null
            && msg.getSentAt().isBefore(beforeTime)) {
          iterator.remove();
          count++;
        }
      }
      return count;
    }

    /**
     * 检查幂等键是否已存在
     *
     * @param idempotencyKey 幂等去重键
     * @return true 表示已存在
     */
    public boolean existsByIdempotencyKey(String idempotencyKey) {
      if (idempotencyKey == null || idempotencyKey.isBlank()) {
        return false;
      }
      return store.values().stream()
          .anyMatch(m -> idempotencyKey.equals(m.getIdempotencyKey())
              && (m.getStatus() == OutboxStatus.PENDING
                  || m.getStatus() == OutboxStatus.PROCESSING));
    }

    /**
     * 分页查询指定状态的消息
     *
     * @param status 状态
     * @param pageable 分页参数
     * @param eventTypeFilter 事件类型过滤
     * @return 分页结果
     */
    public Page<OutboxMessage> findByStatus(
        OutboxStatus status, Pageable pageable, String eventTypeFilter) {
      List<OutboxMessage> filtered = store.values().stream()
          .filter(m -> m.getStatus() == status)
          .filter(m -> eventTypeFilter == null || eventTypeFilter.equals(m.getEventType()))
          .sorted((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) {
              return 0;
            }
            return b.getCreatedAt().compareTo(a.getCreatedAt());
          })
          .toList();

      int start = (int) Math.min(pageable.getOffset(), filtered.size());
      int end = Math.min(start + pageable.getPageSize(), filtered.size());
      return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    /**
     * CAS 重置为 PENDING
     *
     * @param id 消息 ID
     * @param fromStatus 原始状态
     * @return 成功更新的行数
     */
    public int resetToPending(String id, OutboxStatus fromStatus) {
      OutboxMessage msg = store.get(id);
      if (msg != null && msg.getStatus() == fromStatus) {
        store.put(id, withStatus(msg, OutboxStatus.PENDING));
        return 1;
      }
      return 0;
    }

    /**
     * 批量重置为 PENDING
     *
     * @param fromStatus 原始状态
     * @param eventTypeFilter 事件类型过滤
     * @return 成功更新的行数
     */
    public int resetAllToPending(OutboxStatus fromStatus, String eventTypeFilter) {
      int count = 0;
      for (var entry : store.entrySet()) {
        OutboxMessage msg = entry.getValue();
        if (msg.getStatus() == fromStatus
            && (eventTypeFilter == null || eventTypeFilter.equals(msg.getEventType()))) {
          store.put(entry.getKey(), withStatus(msg, OutboxStatus.PENDING));
          count++;
        }
      }
      return count;
    }

    /**
     * 仅当消息处于终态时删除
     *
     * @param id 消息 ID
     * @param terminalStatuses 终态状态列表
     * @return 成功删除的行数
     */
    public int deleteIfTerminal(String id, Collection<OutboxStatus> terminalStatuses) {
      OutboxMessage msg = store.get(id);
      if (msg != null && terminalStatuses.contains(msg.getStatus())) {
        store.remove(id);
        return 1;
      }
      return 0;
    }

    private OutboxMessage withStatus(OutboxMessage original, OutboxStatus newStatus) {
      return OutboxMessage.builder()
          .id(original.getId())
          .aggregateId(original.getAggregateId())
          .aggregateType(original.getAggregateType())
          .eventType(original.getEventType())
          .payload(original.getPayload())
          .status(newStatus)
          .retryCount(newStatus == OutboxStatus.PENDING ? 0 : original.getRetryCount())
          .maxRetries(original.getMaxRetries())
          .nextRetryAt(newStatus == OutboxStatus.PENDING ? Instant.now()
              : original.getNextRetryAt())
          .createdAt(original.getCreatedAt())
          .updatedAt(Instant.now())
          .sentAt(original.getSentAt())
          .errorMessage(
              newStatus == OutboxStatus.PENDING ? null : original.getErrorMessage())
          .tenantId(original.getTenantId())
          .idempotencyKey(original.getIdempotencyKey())
          .schemaVersion(original.getSchemaVersion())
          .isCompressed(original.isCompressed())
          .traceId(original.getTraceId())
          .build();
    }

    private OutboxMessage cloneWithId(OutboxMessage message, String id) {
      return OutboxMessage.builder()
          .id(id)
          .aggregateId(message.getAggregateId())
          .aggregateType(message.getAggregateType())
          .eventType(message.getEventType())
          .payload(message.getPayload())
          .status(message.getStatus() != null ? message.getStatus() : OutboxStatus.PENDING)
          .retryCount(message.getRetryCount())
          .maxRetries(message.getMaxRetries())
          .nextRetryAt(message.getNextRetryAt())
          .createdAt(message.getCreatedAt() != null ? message.getCreatedAt() : Instant.now())
          .updatedAt(message.getUpdatedAt() != null ? message.getUpdatedAt() : Instant.now())
          .sentAt(message.getSentAt())
          .errorMessage(message.getErrorMessage())
          .tenantId(message.getTenantId())
          .idempotencyKey(message.getIdempotencyKey())
          .schemaVersion(message.getSchemaVersion())
          .isCompressed(message.isCompressed())
          .traceId(message.getTraceId())
          .build();
    }

    // ===== 测试辅助方法 =====

    public long countPending() {
      return store.values().stream()
          .filter(m -> m.getStatus() == OutboxStatus.PENDING).count();
    }

    public long countByStatus(OutboxStatus status) {
      return store.values().stream().filter(m -> m.getStatus() == status).count();
    }

    public List<OutboxMessage> findAll() {
      return new ArrayList<>(store.values());
    }

    public void clear() {
      store.clear();
    }
  }

  /**
   * 收集型网关实现（测试替身）
   *
   * <p>记录所有被投递的消息，方便测试断言消息数量和内容。 支持配置失败次数来模拟 MQ 故障场景。
   */
  public static class CollectingGateway implements EventPublishGateway {

    private final List<OutboxMessage> published = new CopyOnWriteArrayList<>();
    private final AtomicInteger failCountdown = new AtomicInteger(0);

    @Override
    public boolean publish(OutboxMessage message) {
      if (failCountdown.getAndDecrement() > 0) {
        return false;
      }
      published.add(message);
      return true;
    }

    @Override
    public List<Boolean> publishBatch(List<OutboxMessage> messages) {
      List<Boolean> results = new ArrayList<>(messages.size());
      for (OutboxMessage m : messages) {
        results.add(publish(m));
      }
      return results;
    }

    /**
     * 设置前 N 次投递为失败（用于测试重试逻辑）
     *
     * @param count 失败次数
     */
    public void setFailCount(int count) {
      failCountdown.set(count);
    }

    public List<OutboxMessage> getPublished() {
      return new ArrayList<>(published);
    }

    public int getPublishedCount() {
      return published.size();
    }

    public void clear() {
      published.clear();
    }
  }

  /**
   * DomainEvent 构建工厂（测试辅助）
   *
   * <p>简化测试代码中的事件创建。
   *
   * @param type 事件类型
   * @param aggregateId 聚合根 ID
   * @return 构造好的 DomainEvent
   */
  public static DomainEvent buildTestEvent(String type, String aggregateId) {
    return DomainEvent.builder()
        .eventType(type)
        .aggregateId(aggregateId)
        .aggregateType("Test")
        .build();
  }

  /**
   * 创建 UserCreated 测试事件
   *
   * @param userId 用户 ID
   * @return DomainEvent 实例
   */
  public static DomainEvent userCreated(String userId) {
    return DomainEvent.builder()
        .eventType(DomainEventTypes.USER_CREATED)
        .aggregateId(userId)
        .aggregateType("User")
        .build();
  }

  /**
   * 断言工具：等待直到仓储中出现指定类型的消息
   *
   * @param store 内存存储
   * @param eventType 事件类型
   * @param timeoutMs 超时毫秒
   * @return 是否出现
   */
  public static boolean awaitEventType(
      InMemoryOutboxStore store, String eventType, long timeoutMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      boolean found = store.findAll().stream()
          .anyMatch(m -> eventType.equals(m.getEventType()));
      if (found) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
  }
}
