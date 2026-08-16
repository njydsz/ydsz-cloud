package com.njydsz.common.queue.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.queue.manager.QueueManager;

/**
 * 消息队列指标到 Micrometer 的桥接器
 *
 * <p>将 {@link MessageMetrics} 统计信息注册为 Micrometer 指标， 支持与 Prometheus、Grafana 等可观测性平台集成。
 *
 * <p>注册的指标：
 *
 * <ul>
 *   <li>{@code queue.publish.success} - 发布成功总数（FunctionCounter）
 *   <li>{@code queue.publish.fail} - 发布失败总数（FunctionCounter）
 *   <li>{@code queue.publish.qps} - 平均发布 QPS（Gauge）
 *   <li>{@code queue.publish.latency.avg} - 平均发布延迟毫秒（Gauge）
 *   <li>{@code queue.publish.latency.max} - 最大发布延迟毫秒（Gauge）
 *   <li>{@code queue.consume.success} - 消费成功总数（FunctionCounter）
 *   <li>{@code queue.consume.fail} - 消费失败总数（FunctionCounter）
 *   <li>{@code queue.consume.qps} - 平均消费 QPS（Gauge）
 *   <li>{@code queue.consume.latency.avg} - 平均消费延迟毫秒（Gauge）
 *   <li>{@code queue.consume.latency.max} - 最大消费延迟毫秒（Gauge）
 *   <li>{@code queue.backlog} - 当前消息积压量（Gauge）
 *   <li>{@code queue.uptime} - 队列运行时长秒（Gauge）
 * </ul>
 *
 * <p>指标标签：
 *
 * <ul>
 *   <li>{@code queue_name} - 队列名称
 *   <li>{@code queue_type} - 队列类型
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class QueueMetricsBinder implements MeterBinder {

  private static final Logger LOG = LoggerFactory.getLogger(QueueMetricsBinder.class);

  private static final String METRIC_PREFIX = "queue";
  private static final String TAG_QUEUE_NAME = "queue_name";
  private static final String TAG_QUEUE_TYPE = "queue_type";

  private final QueueManager queueManager;
  private final ConcurrentMap<String, Boolean> registeredQueues = new ConcurrentHashMap<>();

  public QueueMetricsBinder(QueueManager queueManager) {
    this.queueManager = queueManager;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    bindExistingQueues(registry);
  }

  /**
   * 绑定 QueueManager 中所有已注册队列的指标
   *
   * @param registry MeterRegistry 实例
   */
  public void bindExistingQueues(MeterRegistry registry) {
    if (queueManager == null) {
      return;
    }
    for (String queueName : queueManager.getAllQueueNames()) {
      bindQueueMetrics(registry, queueName);
    }
    LOG.info("[QueueMetricsBinder] 已绑定 {} 个队列的 Micrometer 指标", registeredQueues.size());
  }

  /**
   * 动态注册新创建队列的指标
   *
   * @param registry MeterRegistry 实例
   * @param queueName 队列名称
   */
  public void bindQueueMetrics(MeterRegistry registry, String queueName) {
    if (queueName == null || queueName.isEmpty()) {
      return;
    }
    if (registeredQueues.containsKey(queueName)) {
      return;
    }

    MessageMetrics metrics = queueManager.getMetrics(queueName);
    if (metrics == null) {
      return;
    }

    Iterable<Tag> tags = Tags.of(TAG_QUEUE_NAME, queueName, TAG_QUEUE_TYPE, metrics.getQueueType());

    registerFunctionCounters(registry, metrics, tags);
    registerGauges(registry, metrics, tags);

    registeredQueues.put(queueName, Boolean.TRUE);
    LOG.debug("[QueueMetricsBinder] 已注册队列指标: {}", queueName);
  }

  private void registerFunctionCounters(
      MeterRegistry registry, MessageMetrics metrics, Iterable<Tag> tags) {
    FunctionCounter.builder(
            METRIC_PREFIX + ".publish.success", metrics, MessageMetrics::getPublishSuccessCount)
        .tags(tags)
        .description("消息发布成功总数")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".publish.fail", metrics, MessageMetrics::getPublishFailCount)
        .tags(tags)
        .description("消息发布失败总数")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".consume.success", metrics, MessageMetrics::getConsumeSuccessCount)
        .tags(tags)
        .description("消息消费成功总数")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".consume.fail", metrics, MessageMetrics::getConsumeFailCount)
        .tags(tags)
        .description("消息消费失败总数")
        .register(registry);
  }

  private void registerGauges(MeterRegistry registry, MessageMetrics metrics, Iterable<Tag> tags) {
    Gauge.builder(METRIC_PREFIX + ".publish.qps", metrics, MessageMetrics::getAvgPublishQps)
        .tags(tags)
        .description("平均发布 QPS")
        .register(registry);

    Gauge.builder(
            METRIC_PREFIX + ".publish.latency.avg", metrics, MessageMetrics::getAvgPublishLatency)
        .tags(tags)
        .description("平均发布延迟（毫秒）")
        .register(registry);

    Gauge.builder(
            METRIC_PREFIX + ".publish.latency.max", metrics, MessageMetrics::getMaxPublishLatency)
        .tags(tags)
        .description("最大发布延迟（毫秒）")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".consume.qps", metrics, MessageMetrics::getAvgConsumeQps)
        .tags(tags)
        .description("平均消费 QPS")
        .register(registry);

    Gauge.builder(
            METRIC_PREFIX + ".consume.latency.avg", metrics, MessageMetrics::getAvgConsumeLatency)
        .tags(tags)
        .description("平均消费延迟（毫秒）")
        .register(registry);

    Gauge.builder(
            METRIC_PREFIX + ".consume.latency.max", metrics, MessageMetrics::getMaxConsumeLatency)
        .tags(tags)
        .description("最大消费延迟（毫秒）")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".backlog", metrics, MessageMetrics::getBacklogCount)
        .tags(tags)
        .description("当前消息积压量")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".uptime", metrics, MessageMetrics::getElapsedSeconds)
        .tags(tags)
        .description("队列运行时长（秒）")
        .register(registry);
  }
}
