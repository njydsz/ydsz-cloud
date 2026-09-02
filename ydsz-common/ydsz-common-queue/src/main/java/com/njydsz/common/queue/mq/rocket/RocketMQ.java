package com.njydsz.common.queue.mq.rocket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.queue.queue.AbstractMessageQueue;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.queue.service.IMessageSubscriber;

/**
 * RocketMQ 消息队列
 *
 * <p>RocketMQ 是阿里巴巴开源的分布式消息中间件，具有高可用、高可靠等特点。 支持事务消息、顺序消息、延迟消息等高级特性。
 *
 * <p><b>资源管理：</b>
 *
 * <ul>
 *   <li>构造时创建临时 Producer 验证 NameServer 连通性
 *   <li>每个 Publisher 持有独立的 DefaultMQProducer
 *   <li>{@link #doClose()} 关闭所有已创建的 Producer，防止资源泄漏
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RocketMQ extends AbstractMessageQueue {

  private final RocketMQProperties properties;
  private final List<RocketMQPublisher> publishers = new CopyOnWriteArrayList<>();
  private final List<RocketMQSubscriber> subscribers = new CopyOnWriteArrayList<>();

  public RocketMQ(RocketMQProperties properties) {
    super("RocketMQ");
    if (properties == null) {
      throw BusinessException.builder().key("RocketMQ 配置不能为空").build();
    }
    this.properties = properties;
    validateConnection();
    log.info("[RocketMQ] 初始化成功，namesrvAddr={}", properties.resolvedNamesrvAddr());
  }

  @Override
  public IMessagePublisher createPublisher(String channel) {
    checkNotClosed();
    if (channel == null || channel.isEmpty()) {
      throw BusinessException.builder().key("主题名称不能为空").build();
    }
    RocketMQPublisher publisher = new RocketMQPublisher(properties, channel);
    publishers.add(publisher);
    return publisher;
  }

  @Override
  public IMessageSubscriber createSubscriber(String channel) {
    checkNotClosed();
    if (channel == null || channel.isEmpty()) {
      throw BusinessException.builder().key("主题名称不能为空").build();
    }
    RocketMQSubscriber subscriber = new RocketMQSubscriber(properties, channel);
    subscribers.add(subscriber);
    return subscriber;
  }

  @Override
  protected void doClose() {
    for (RocketMQPublisher publisher : publishers) {
      try {
        publisher.close();
      } catch (Exception e) {
        log.warn("[RocketMQ] 关闭 Publisher 时异常", e);
      }
    }
    publishers.clear();

    for (RocketMQSubscriber subscriber : subscribers) {
      try {
        subscriber.stop();
      } catch (Exception e) {
        log.warn("[RocketMQ] 关闭 Subscriber 时异常", e);
      }
    }
    subscribers.clear();

    log.info("[RocketMQ] 所有资源已释放");
  }

  private void validateConnection() {
    DefaultMQProducer probe = null;
    try {
      probe = new DefaultMQProducer("rocketmq-connectivity-check");
      probe.setNamesrvAddr(properties.resolvedNamesrvAddr());
      probe.setSendMsgTimeout(3000);
      probe.start();
      log.debug("[RocketMQ] 连接验证成功");
    } catch (Exception e) {
      log.error("[RocketMQ] 连接验证失败，namesrvAddr={}", properties.resolvedNamesrvAddr(), e);
      throw BusinessException.builder().key("RocketMQ 连接失败，请检查配置：" + e.getMessage()).build();
    } finally {
      if (probe != null) {
        try {
          probe.shutdown();
        } catch (Exception e) {
          log.debug("[RocketMQ] 连接验证 Producer 关闭异常", e);
        }
      }
    }
  }
}
