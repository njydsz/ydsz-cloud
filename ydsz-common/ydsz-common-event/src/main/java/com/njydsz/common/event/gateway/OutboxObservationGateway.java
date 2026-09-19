package com.njydsz.common.event.gateway;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * Outbox 投递 Observation 装饰器（O-1）
 *
 * <p>当 Spring Boot 3.x 在 classpath 上且 ObservationRegistry Bean 存在时， 此网关自动为每次投递创建 Observation，同时产出 Trace +
 * Metrics：
 *
 * <ul>
 *   <li>Observation.name = "ydsz.outbox.publish"
 *   <li>自动 high-cardinality key: eventType, outcome
 *   <li>底层 Metrics 产出与手写 Counter/Timer/Gauge 兼容（同名替换业务无感知）
 * </ul>
 *
 * <p><b>条件装配：</b>通过 {@code @ConditionalOnClass(Observation.class)} 和 {@code @Bean} 控制加载， 无 micrometer-observation
 * 依赖时不加载此 Bean（业务代码零改动）。
 *
 * <p><b>编码规范遵循：</b>YDIZ-WARN-001（不使用 @SuppressWarnings，所有告警正面解决）
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class OutboxObservationGateway implements EventPublishGateway {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(OutboxObservationGateway.class);

  /** 底层网关委托 */
  private final EventPublishGateway delegate;

  /** ObservationRegistry 提供者（延迟解析，避免编译期强依赖） */
  private final ObjectProvider<Object> observationRegistryProvider;

  /** 是否成功初始化 Observation */
  private final boolean isObservationAvailable;

  /**
   * 构造 Observation 网关装饰器
   *
   * @param delegate 底层网关委托
   * @param observationRegistryProvider ObservationRegistry 提供者（可选）
   */
  public OutboxObservationGateway(EventPublishGateway delegate,
      ObjectProvider<Object> observationRegistryProvider) {
    this.delegate = delegate;
    this.observationRegistryProvider = observationRegistryProvider;
    Object registry = observationRegistryProvider.getIfAvailable();
    this.isObservationAvailable = registry != null;
    if (!isObservationAvailable) {
      LOG.info("ObservationRegistry not available. OutboxObservationGateway using delegate only.");
    }
  }

  @Override
  public boolean publish(OutboxMessage message) throws Throwable {
    if (!isObservationAvailable) {
      return delegate.publish(message);
    }
    return invokeObserve(message, false);
  }

  @Override
  public List<Boolean> publishBatch(List<OutboxMessage> messages) throws Throwable {
    if (!isObservationAvailable) {
      return delegate.publishBatch(messages);
    }
    return invokeObserveBatch(messages);
  }

  /**
   * 使用 Observation API 包装单条投递
   *
   * @param message 消息
   * @param isBatch 是否批量模式
   * @return 投递是否成功
   */
  private boolean invokeObserve(OutboxMessage message, boolean isBatch) throws Throwable {
    try {
      Object registry = observationRegistryProvider.getIfAvailable();
      if (registry == null) {
        return delegate.publish(message);
      }

      // 反射调用 Observation.createNotStarted("ydsz.outbox.publish", registry).start()
      Class<?> observationClass = Class.forName("io.micrometer.observation.Observation");
      Object observation = observationClass.getMethod("createNotStarted", String.class,
          Object.class).invoke(null, "ydsz.outbox.publish", registry);
      observationClass.getMethod("contextName", String.class).invoke(observation, "OutboxPublish");
      observation = observationClass.getMethod("start").invoke(observation);

      // 添加 tag
      observationClass.getMethod("highCardinalityKeyValue", String.class, String.class)
          .invoke(observation, "eventType", message.getEventType());
      observationClass.getMethod("highCardinalityKeyValue", String.class, String.class)
          .invoke(observation, "messageId", message.getId());

      try {
        boolean result = delegate.publish(message);
        observationClass.getMethod("highCardinalityKeyValue", String.class, String.class)
            .invoke(observation, "outcome", result ? "success" : "failure");
        return result;
      } catch (Throwable e) {
        observationClass.getMethod("error", Throwable.class).invoke(observation, e);
        observationClass.getMethod("stop").invoke(observation);
        throw e;
      } finally {
        observationClass.getMethod("stop").invoke(observation);
      }
    } catch (ClassNotFoundException e) {
      // micrometer-observation 不在 classpath，降级
      if (LOG.isDebugEnabled()) {
        LOG.debug("Observation class not found, fallback to direct publish");
      }
      return delegate.publish(message);
    } catch (NoSuchMethodException e) {
      LOG.warn("Observation API mismatch, fallback: {}", e.getMessage());
      return delegate.publish(message);
    }
  }

  /**
   * 使用 Observation API 包装批量投递
   *
   * @param messages 消息列表
   * @return 投递结果列表
   */
  private List<Boolean> invokeObserveBatch(List<OutboxMessage> messages) throws Throwable {
    try {
      Object registry = observationRegistryProvider.getIfAvailable();
      if (registry == null) {
        return delegate.publishBatch(messages);
      }

      Class<?> observationClass = Class.forName("io.micrometer.observation.Observation");
      Object observation = observationClass.getMethod("createNotStarted", String.class,
          Object.class).invoke(null, "ydsz.outbox.publish.batch", registry);
      observation = observationClass.getMethod("start").invoke(observation);

      observationClass.getMethod("highCardinalityKeyValue", String.class, String.class)
          .invoke(observation, "batchSize", String.valueOf(messages.size()));

      try {
        List<Boolean> results = delegate.publishBatch(messages);
        observationClass.getMethod("highCardinalityKeyValue", String.class, String.class)
            .invoke(observation, "outcome", "success");
        return results;
      } catch (Throwable e) {
        observationClass.getMethod("error", Throwable.class).invoke(observation, e);
        throw e;
      } finally {
        observationClass.getMethod("stop").invoke(observation);
      }
    } catch (ClassNotFoundException e) {
      return delegate.publishBatch(messages);
    } catch (NoSuchMethodException e) {
      LOG.warn("Observation API mismatch for batch, fallback: {}", e.getMessage());
      return delegate.publishBatch(messages);
    }
  }

  /**
   * 检查 Observation 是否可用
   *
   * @return true 表示 Observation 可用
   */
  public boolean isObservationAvailable() {
    return isObservationAvailable;
  }
}
