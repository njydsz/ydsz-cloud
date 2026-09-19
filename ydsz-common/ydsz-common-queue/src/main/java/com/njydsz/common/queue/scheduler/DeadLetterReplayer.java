package com.njydsz.common.queue.scheduler;

/**
 * 死信消息引擎级回放 SPI
 *
 * <p>实现类按 MQ 引擎提供「真正的语义重放」能力，替代默认的"重新发布到 topic 末尾"语义：
 *
 * <ul>
 *   <li>Kafka：{@code consumer.seek(TopicPartition, offset)} 实现同分区回溯消费，避免重复投递所有后续消息
 *   <li>RocketMQ：{@code consumer.setConsumeTimestamp(String)} 按时间回放；或结合 {@code reConsumerTime} 做精确回溯
 * </ul>
 *
 * <p>实现通过 {@link DeadLetterReplayerRegistry} 单点注册；各引擎的 queue-on-message 模块在启动时向注册器注入自己实现的 replayer。
 *
 * <p>约定：
 *
 * <ul>
 *   <li>{@link #supports(String engineType)} 判定本 replayer 是否支持该引擎类型
 *   <li>{@link #replay(DeadLetterEntry)} 实现重放语义；返回 true 表示已由回放路径处理完毕（调用方不再做默认的重新发布）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public interface DeadLetterReplayer {

  /**
   * 是否支持该引擎类型
   *
   * @param engineType 引擎类型字符串（来自 {@link
   *     com.njydsz.common.queue.enums.QueueType#getValue()}）
   * @return true 表示本 replayer 可以处理该类型的死信回放
   */
  boolean supports(String engineType);

  /**
   * 执行该死信消息的回放语义。
   *
   * <p>即使本次无法回放（例如 broker 不可达），实现也应在内部记录降级原因并返回 false，
   * 让调用方走兜底的"重新发布"逻辑。不应抛出运行时异常。
   *
   * @param entry 死信消息条目，含引擎特定的回放元数据（parse partition/offset/timestamp 等）
   * @return true 表示回放路径已由本 replayer 处理（调用方跳过默认重发）；false 表示需要降级
   */
  boolean replay(DeadLetterEntry entry);

  /**
   * replayer 的注册名称（用于日志和监控区分）。默认为类名简称。
   *
   * @return 名称
   */
  default String name() {
    return this.getClass().getSimpleName();
  }
}
