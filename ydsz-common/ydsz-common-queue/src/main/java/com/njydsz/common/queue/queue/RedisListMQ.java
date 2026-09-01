package com.njydsz.common.queue.queue;

import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.queue.service.IMessageSubscriber;
import com.njydsz.common.queue.service.impl.RedisListPublisher;
import com.njydsz.common.queue.service.impl.RedisListSubscriber;

/**
 * 基于 Redis List 实现的消息队列
 *
 * <p>Redis List 队列是最轻量的消息队列实现，基于 LPUSH/BRPOP 命令实现 FIFO（先进先出）语义。 适用于简单的任务队列场景，不支持消息确认、重试、死信等高级特性。
 *
 * <p><b>连接复用：</b>通过 RedisTemplate 复用 ydsz-common-redis 的连接。
 *
 * <p><b>生产环境警告：</b>
 *
 * <ul>
 *   <li>Redis List 不具备消息持久化确认机制，broker 宕机可能丢失未消费消息
 *   <li>不支持消息 ACK / 重试 / 死信队列，不适合关键业务场景
 *   <li>生产环境关键业务请使用 Kafka / RocketMQ / RabbitMQ 等专业消息中间件
 *   <li>仅适用于轻量级任务队列、临时缓冲等非关键场景
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RedisListMQ extends AbstractMessageQueue {

  private final QueueProperties queueProperties;
  private final RedisTemplate<String, Object> redisTemplate;
  private final ExecutorService consumerExecutor;

  /**
   * 基于 RedisTemplate 构造（复用 ydsz-common-redis 连接，推荐）
   *
   * @param redisTemplate Redis 模板
   * @param config 队列配置
   * @param consumerExecutor 异步消费者线程池（可为 null，将退化到裸线程，不推荐）
   */
  public RedisListMQ(
      RedisTemplate<String, Object> redisTemplate,
      QueueProperties config,
      ExecutorService consumerExecutor) {
    super("Redis-List");
    if (config == null) {
      throw BusinessException.builder().key("队列配置不能为空").build();
    }
    this.queueProperties = config;
    this.redisTemplate = redisTemplate;
    this.consumerExecutor = consumerExecutor;
    log.info("[RedisListMQ] 初始化成功（复用 ydsz-common-redis 连接）");
    log.warn(
        "[RedisListMQ] 警告: Redis List 队列不适合生产环境关键业务场景，"
            + "不具备消息 ACK/重试/死信能力，关键业务请使用 Kafka/RocketMQ/RabbitMQ");
  }

  @Override
  public IMessagePublisher createPublisher(String channel) {
    checkNotClosed();
    if (channel == null || channel.isEmpty()) {
      throw BusinessException.builder().key("通道名称不能为空").build();
    }
    return new RedisListPublisher(redisTemplate, channel);
  }

  @Override
  public IMessageSubscriber createSubscriber(String channel) {
    checkNotClosed();
    if (channel == null || channel.isEmpty()) {
      throw BusinessException.builder().key("通道名称不能为空").build();
    }
    return new RedisListSubscriber(
        redisTemplate,
        channel,
        (int) queueProperties.getListBlockTimeoutSeconds(),
        10000,
        queueProperties,
        consumerExecutor);
  }

  @Override
  public String[] getChannels() {
    return new String[0];
  }

  @Override
  protected void doClose() {
    // RedisTemplate 由 ydsz-common-redis 管理，无需关闭
    log.info("[Redis-List] 队列已关闭");
  }
}
