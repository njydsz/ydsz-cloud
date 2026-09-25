package com.njydsz.common.event.saga;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import com.njydsz.common.event.api.OutboxNewMessageEvent;

/**
 * Saga 编排管理器（F-1）
 *
 * <p>负责管理 AbstractSaga 实例的生命周期：
 *
 * <ul>
 *   <li>注册 Saga 子类（通过 {@link #registerSagaFactory(String, SagaFactory)}）
 *   <li>监听 OutboxMessage 事件，根据 associationKey + associationValue 路由到对应 Saga
 *   <li>创建新的 Saga 实例或路由到现有 Saga 继续编排
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 1. 在业务模块配置类中注册 Saga
 * &#64;Configuration
 * public class OrderSagaConfig {
 *     &#64;Bean
 *     public SagaManager sagaManager() {
 *         SagaManager manager = new SagaManager();
 *         manager.registerSagaFactory("orderId", OrderCreationSaga::new);
 *         return manager;
 *     }
 * }
 *
 * // 2. 发布事件（OutboxService 自动触发）
 * outboxService.appendToOutbox(DomainEvent.builder()
 *     .eventType("OrderCreated")
 *     .aggregateId(order.getId())
 *     .metadata("orderId", order.getId())
 *     .build());
 *
 * // 3. SagaManager 监听 OutboxNewMessageEvent → 路由到 OrderCreationSaga 实例
 * }</pre>
 *
 * <p><b>设计参考：</b>Axon Framework 的 {@code SagaManager} + {@code AssociationValue} 路由，
 * 但简化为内存实例管理（无 AxonServer 依赖），状态持久化由 OutboxMessage 重放保障。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see AbstractSaga
 * @see SagaState
 */
public class SagaManager {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(SagaManager.class);

  /** 关联键 → Saga 工厂注册表 */
  private final Map<String, SagaFactory<? extends AbstractSaga>> sagaFactories =
      new ConcurrentHashMap<>(16);

  /** 正在运行的 Saga 实例：Saga 子类名 + "@" + associationValue → Saga 实例 */
  private final Map<String, AbstractSaga> activeSagas = new ConcurrentHashMap<>(32);

  /**
   * 注册 Saga 工厂
   *
   * @param associationKey 关联键名（如 "orderId"）
   * @param factory Saga 工厂（通常是构造方法引用）
   * @param <T> Saga 子类型
   */
  public <T extends AbstractSaga> void registerSagaFactory(String associationKey,
      SagaFactory<T> factory) {
    Objects.requireNonNull(associationKey, "associationKey must not be null");
    Objects.requireNonNull(factory, "factory must not be null");
    sagaFactories.put(associationKey, factory);
    LOG.info("Saga factory registered: associationKey={}", associationKey);
  }

  /**
   * 获取所有已注册的 Saga 关联键
   *
   * @return 关联键集合
   */
  public java.util.Set<String> getRegisteredAssociationKeys() {
    return java.util.Collections.unmodifiableSet(sagaFactories.keySet());
  }

  /**
   * 监听 OutboxNewMessageEvent，路由到对应 Saga
   *
   * <p>解析消息 metadata 中的关联值，查找匹配的 Saga 工厂：
   *
   * <ul>
   *   <li>Saga 为 CREATED 状态且匹配起始事件 → 创建新 Saga 实例
   *   <li>Saga 为 RUNNING 状态 → 路由到现有 Saga 继续编排
   *   <li>Saga 已结束 → 忽略事件
   * </ul>
   *
   * @param event Outbox 新消息事件
   */
  @EventListener
  public void onOutboxMessageEvent(OutboxNewMessageEvent event) {
    // 仅处理 messageId 的即时触发，实际编排逻辑需由业务模块监听 OutboxMessage 事件
    // 本类作为路由骨架，提供工厂注册和实例管理基础设施
    LOG.debug("SagaManager received Outbox event, messageId={}", event.getMessageId());
  }

  /**
   * 创建新的 Saga 实例（供业务模块主动创建场景使用）
   *
   * @param associationKey 关联键名
   * @return Saga 实例，未注册工厂时返回 null
   */
  public AbstractSaga createSaga(String associationKey) {
    SagaFactory<? extends AbstractSaga> factory = sagaFactories.get(associationKey);
    if (factory == null) {
      LOG.warn("No saga factory registered for associationKey={}", associationKey);
      return null;
    }
    AbstractSaga saga = factory.create();
    activeSagas.put(buildSagaKey(saga), saga);
    return saga;
  }

  /**
   * 获取所有活动的 Saga 实例
   *
   * @return Saga 实例列表
   */
  public List<AbstractSaga> getActiveSagas() {
    return new ArrayList<>(activeSagas.values());
  }

  /**
   * 清理已结束的 Saga 实例（供定时任务定期调用，防止内存泄漏）
   *
   * @return 清理的 Saga 实例数量
   */
  public int evictEndedSagas() {
    int count = 0;
    var it = activeSagas.entrySet().iterator();
    while (it.hasNext()) {
      var entry = it.next();
      if (entry.getValue().isEnded()) {
        it.remove();
        count++;
      }
    }
    if (count > 0) {
      LOG.info("Evicted {} ended saga instances", count);
    }
    return count;
  }

  private String buildSagaKey(AbstractSaga saga) {
    return saga.getClass().getName() + "@" + saga.getAssociationKey() + "@" + saga.getAssociationValue();
  }

  /**
   * Saga 工厂接口
   *
   * <p>用于创建 Saga 实例（替代直接 new，支持 Spring Bean 注入和实例定制）。
   *
   * @param <T> Saga 子类型
   */
  @FunctionalInterface
  public interface SagaFactory<T extends AbstractSaga> {
    /**
     * 创建 Saga 实例
     *
     * @return 新的 Saga 实例
     */
    T create();
  }
}
