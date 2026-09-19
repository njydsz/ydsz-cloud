package com.njydsz.common.queue.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.queue.queue.IMessageQueue;
import com.njydsz.common.queue.queue.IMessageQueueProvider;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.queue.service.IMessageSubscriber;

/**
 * 多实例引擎路由器（P1-13）
 *
 * <p>声明 Bean：在自动配置中通过 {@link QueueConfiguration} 注册。当用户通过 {@code ydsz.queue.engines[*]} 配置多个引擎时，
 * 注入本组件并使用 {@link #publisher(String)} / {@link #subscriber(String)} 按名称取得引擎专属的发布者/订阅者。
 *
 * <p>Bean 按需创建（首次访问时构建实例并缓存），不阻塞应用启动（与 lazy-init 配合）。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Component
 * public class OrderEventHandler {
 *   private final IMessagePublisher kafkaPublisher;
 *
 *   public OrderEventHandler(QueueEngines engines) {
 *     this.kafkaPublisher = engines.publisher("order");
 *   }
 * }
 * }</pre>
 *
 * <p>配置文件：
 *
 * <pre>{@code
 * ydsz:
 *   queue:
 *     type: KAFKA
 *     engines:
 *       - name: order
 *         type: KAFKA
 *         topic: order-events
 *       - name: notify
 *         type: STREAM
 *         topic: notif-stream
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Component
@Slf4j
public class QueueEngines {

  private final IMessageQueueProvider queueProvider;
  private final Map<String, QueueProperties.EngineDefinition> engineDefs;
  private final ConcurrentHashMap<String, IMessagePublisher> publisherCache =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, IMessageSubscriber> subscriberCache =
      new ConcurrentHashMap<>();

  public QueueEngines(ObjectProvider<IMessageQueueProvider> queueProviderProvider,
      QueueProperties queueProperties) {
    IMessageQueueProvider provider = queueProviderProvider.getIfAvailable();
    this.queueProvider = provider != null ? provider : createNoOpProvider();
    this.engineDefs = buildEngineDefs(queueProperties);
    log.info("[QueueEngines] 注册多实例引擎路由：{}", engineDefs.keySet());
  }

  /**
   * 按引擎名称获取引擎专属的发布者。
   *
   * @param engineName 引擎名称（对应 {@code ydsz.queue.engines[].name}）
   * @return 引擎专属 IMessagePublisher，首次调用时懒构建并缓存
   * @throws IllegalArgumentException 当引擎名称不存在时抛出
   */
  public IMessagePublisher publisher(String engineName) {
    IMessagePublisher publisher = publisherCache.get(engineName);
    if (publisher != null) {
      return publisher;
    }
    QueueProperties.EngineDefinition def = getEngineDef(engineName);
    publisher =
        publisherCache.computeIfAbsent(engineName,
            k -> {
              IMessageQueue queue = queueProvider.createMessageQueue(def.getType());
              return queue.createPublisher(def.getTopic());
            });

    log.debug("[QueueEngines] 已为引擎 {} 创建发布者，topic={}", engineName, def.getTopic());
    return publisher;
  }

  /**
   * 按引擎名称获取引擎专属的订阅者。
   *
   * <p>返回的订阅者使用由 {@link com.njydsz.common.thread.factory.InternalExecutorFactory} 创建的
   * 容器托管线程池执行异步消费（由 {@code queueConsumerExecutor} Bean 提供），
   * {@link com.njydsz.common.queue.recovery.ConsumerThreadGuard} 负责故障恢复。
   *
   * @param engineName 引擎名称
   * @return 引擎专属 IMessageSubscriber，首次调用时懒构建并缓存
   * @throws IllegalArgumentException 当引擎名称不存在时抛出
   */
  public IMessageSubscriber subscriber(String engineName) {
    IMessageSubscriber subscriber = subscriberCache.get(engineName);
    if (subscriber != null) {
      return subscriber;
    }
    QueueProperties.EngineDefinition def = getEngineDef(engineName);
    subscriber =
        subscriberCache.computeIfAbsent(engineName,
            k -> {
              IMessageQueue queue = queueProvider.createMessageQueue(def.getType());
              return queue.createSubscriber(def.getTopic());
            });

    log.debug("[QueueEngines] 已为引擎 {} 创建订阅者，topic={}", engineName, def.getTopic());
    return subscriber;
  }

  /**
   * 检查是否存在已注册的引擎名称。
   *
   * @param engineName 引擎名称
   * @return true 如果存在
   */
  public boolean contains(String engineName) {
    return engineDefs.containsKey(engineName);
  }

  /**
   * 获取所有已注册的引擎名称集合（不可变）。
   *
   * @return 引擎名称 Set
   */
  public java.util.Set<String> engineNames() {
    return Collections.unmodifiableSet(engineDefs.keySet());
  }

  /**
   * 全局路由表：名称 → EngineDefinition 视图（用于监控 / 调试）。
   *
   * @return 不可变路由表
   */
  public Map<String, QueueProperties.EngineDefinition> routeTable() {
    return Collections.unmodifiableMap(engineDefs);
  }

  private QueueProperties.EngineDefinition getEngineDef(String engineName) {
    QueueProperties.EngineDefinition def = engineDefs.get(engineName);
    if (def == null) {
      throw new IllegalArgumentException("未注册的队列引擎名称：" + engineName
          + "，已注册引擎：" + engineDefs.keySet());
    }
    return def;
  }

  private Map<String, QueueProperties.EngineDefinition> buildEngineDefs(QueueProperties props) {
    Map<String, QueueProperties.EngineDefinition> result = new LinkedHashMap<>();
    for (QueueProperties.EngineDefinition def : props.getEngines()) {
      String name = def.getName();
      if (name == null || name.isBlank() || def.getType() == null) {
        log.warn("[QueueEngines] 跳过无效的引擎配置：name={}, type={}", name, def.getType());
        continue;
      }
      result.put(name, def);
    }
    return result;
  }

  /** 当无任何 QueueProvider 可用时的兜底（用于极端降级场景，不应在正常场景触发）。 */
  @SuppressWarnings("checkstyle:MissingJavadocMethod")
  private IMessageQueueProvider createNoOpProvider() {
    return new IMessageQueueProvider() {
      @Override
      public IMessageQueue createMessageQueue(QueueType queueType, String... args) {
        throw new UnsupportedOperationException(
            "IMessageQueueProvider 不可用，无法创建引擎：" + queueType);
      }

      @Override
      public void close() {
        // no-op
      }
    };
  }
}
