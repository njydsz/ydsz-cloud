package com.njydsz.common.queue.service.impl;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.metrics.QueueMetrics;
import com.njydsz.common.queue.rate.ConsumerRateLimiter;
import com.njydsz.common.queue.recovery.ConsumerThreadGuard;
import com.njydsz.common.queue.retry.RetryPolicy;
import com.njydsz.common.queue.service.IMessageHandler;
import com.njydsz.common.queue.service.IMessageSubscriber;
import com.njydsz.common.queue.trace.MessageTracer;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 基于 Redis Stream 的消息订阅者。
 *
 * <p>使用 Redis <b>XREADGROUP</b> 命令以消费组（Consumer Group）模式读取消息， 实现了消息确认（ACK）、自动重试和死信队列（DLQ）三大核心能力。
 *
 * <h3>工作流程</h3>
 *
 * <ol>
 *   <li>初始化时通过 <b>XGROUP CREATE</b> 创建消费组（已存在则忽略 BUSYGROUP）
 *   <li>消费循环中以阻塞方式 <b>XREADGROUP</b> 读取消息，每次读取 {@code batchSize} 条
 *   <li>消息处理成功后 <b>XACK</b> 确认；处理失败则按指数退避策略重试
 *   <li>重试次数超过 {@code retryMax} 后，将消息转入死信队列 Stream 并 ACK 原消息
 * </ol>
 *
 * <h3>顺序消息支持</h3>
 *
 * <p>当消息携带 {@code groupKey} 和 {@code sequence} 字段时，支持同分组内的顺序消费， 通过 {@link
 * QueueMessage#setSequential(String, Long)} 标记顺序属性。
 *
 * <h3>线程安全</h3>
 *
 * <p>通过 {@link AtomicBoolean} 控制运行状态，{@link ConsumerThreadGuard} 守护消费线程 自动恢复意外退出的消费循环。{@link
 * ConsumerRateLimiter} 实现消费端限流。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see IMessageSubscriber
 * @see RedisStreamPublisher
 */
@Slf4j
public class RedisStreamSubscriber implements IMessageSubscriber {

  /** Stream Entry 中存放消息体的字段名 */
  private static final String FIELD_PAYLOAD = "payload";

  /** Stream Entry 中存放已重试次数的字段名 */
  private static final String FIELD_RETRY_COUNT = "retryCount";

  /** Stream Entry 中存放顺序消息分组键的字段名 */
  private static final String FIELD_GROUP_KEY = "groupKey";

  /** Stream Entry 中存放顺序消息序号的字段名 */
  private static final String FIELD_SEQUENCE = "sequence";

  /** 复用 ydsz-common-redis 的 Redis 连接 */
  private final RedisTemplate<String, Object> redisTemplate;

  /** 订阅的 Stream Key（频道名称） */
  private final String channel;

  /** 消费组名称，同组内消息只投递给一个消费者 */
  private final String group;

  /** 消费者名称，格式为 "{配置前缀}-{8位UUID}"，确保多实例唯一 */
  private final String consumer;

  /** 最大重试次数，超过后转入死信队列 */
  private final int retryMax;

  /** XREADGROUP 阻塞读取的超时时间（毫秒） */
  private final int blockMillis;

  /** 每次读取的最大消息条数 */
  private final int batchSize;

  /** 死信队列 Stream Key，格式为 "{channel}{deadLetterSuffix}" */
  private final String dlqChannel;

  /** 消费循环运行状态标志 */
  private final AtomicBoolean running;

  /** 累计成功消费的消息数 */
  private final AtomicLong consumedCount;

  /** 最近一次异常（供外部健康检查读取） */
  private final AtomicReference<Throwable> lastError;

  /** 消费指标采集器（成功/失败/延迟） */
  private final QueueMetrics messageMetrics;

  /** 消费端限流器，防止下游被压垮 */
  private final ConsumerRateLimiter rateLimiter;

  /** 消费线程池，由外部传入以便统一管理生命周期 */
  private final ExecutorService consumerExecutor;

  /** 重试策略（指数退避：初始1s，上限30s） */
  private final RetryPolicy retryPolicy;

  /** 消费线程守护器，监控消费线程存活并在异常退出时自动重启 */
  private volatile ConsumerThreadGuard threadGuard;

  /**
   * 构造 Redis Stream 订阅者。
   *
   * <p>初始化时会自动调用 {@link #ensureGroup()} 创建消费组。如果消费组已存在 （Redis 返回 BUSYGROUP），则静默忽略，保证幂等启动。
   *
   * @param redisTemplate Redis 连接模板，不可为空
   * @param channel Stream Key（频道名称），不可为空
   * @param queueProperties 队列配置（消费组名/消费者名/重试/限流/阻塞等），不可为空
   * @param consumerExecutor 消费线程池，用于异步消费循环
   * @throws IllegalArgumentException redisTemplate / channel / queueProperties 为空时抛出
   */
  public RedisStreamSubscriber(
      RedisTemplate<String, Object> redisTemplate,
      String channel,
      QueueProperties queueProperties,
      ExecutorService consumerExecutor) {
    if (redisTemplate == null) {
      throw new IllegalArgumentException("RedisTemplate 不能为空");
    }
    if (channel == null || channel.isEmpty()) {
      throw new IllegalArgumentException("通道名称不能为空");
    }
    if (queueProperties == null) {
      throw new IllegalArgumentException("队列配置不能为空");
    }
    this.redisTemplate = redisTemplate;
    this.channel = channel;
    this.group = queueProperties.getStreamGroup();
    this.consumer = queueProperties.getStreamConsumer() + "-" + generateShortId();
    this.retryMax = queueProperties.getStreamRetryMax();
    this.blockMillis = Math.toIntExact(queueProperties.getStreamBlockMillis());
    this.batchSize = queueProperties.getStreamBatchSize();
    this.dlqChannel = channel + queueProperties.getStreamDeadLetterSuffix();
    this.running = new AtomicBoolean(false);
    this.consumedCount = new AtomicLong(0);
    this.lastError = new AtomicReference<>();
    this.messageMetrics = new QueueMetrics(channel, "redis-stream");
    this.rateLimiter = new ConsumerRateLimiter(queueProperties.getConsumerRateLimitPerSecond());
    this.consumerExecutor = consumerExecutor;
    this.retryPolicy = RetryPolicy.exponentialBackoff(retryMax, 1000L, 30000L);
    ensureGroup();
    log.info(
        "[RedisStream] 订阅者初始化完成（复用 ydsz-common-redis 连接），channel={}, group={}, consumer={}",
        channel,
        group,
        consumer);
  }

  /**
   * {@inheritDoc}
   *
   * <p>同步拉取单条消息。以阻塞方式 XREADGROUP 读取一条消息后返回其 payload。 如果消息解析失败会自动 ACK 并跳过，避免消息堆积。
   *
   * @return 消息 payload，无消息时返回 {@code null}
   */
  @Override
  public String subscribe() {
    QueueMessage message = poll();
    return message != null ? QueueMessage.toPayload(message) : null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>启动异步消费循环。通过 {@link ConsumerThreadGuard} 守护消费线程， 当消费线程因异常退出时会自动重启。重复调用时仅记录警告并返回已有消费者 ID。
   *
   * @param handler 消息处理回调
   * @return 消费者 ID（格式：{配置前缀}-{8位UUID}）
   */
  @Override
  public String subscribeAsync(IMessageHandler handler) {
    if (!running.compareAndSet(false, true)) {
      log.warn("[RedisStream] 订阅者已在运行中，consumer={}", consumer);
      return consumer;
    }
    threadGuard = new ConsumerThreadGuard("redis-stream-" + consumer, 10, consumerExecutor);
    threadGuard.start(() -> consumeLoop(handler));
    log.info(
        "[RedisStream] 异步消费已启动（ConsumerThreadGuard守护），channel={}, consumer={}", channel, consumer);
    return consumer;
  }

  /**
   * {@inheritDoc}
   *
   * <p>将运行状态置为 false，消费循环在下一次迭代时退出。 同时停止 {@link ConsumerThreadGuard} 线程守护。
   */
  @Override
  public void stop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    if (threadGuard != null) {
      threadGuard.stop();
    }
    log.info("[RedisStream] 收到停止信号，consumer={}", consumer);
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  /**
   * 消费循环核心逻辑。
   *
   * <p>循环执行以下步骤直到 {@link #running} 被置为 false：
   *
   * <ol>
   *   <li>构建 XREADGROUP 读取参数（batchSize + blockMillis 阻塞）
   *   <li>以 {@code ReadOffset.lastConsumed()} 从上次消费位置继续读取
   *   <li>对每条消息先执行限流 {@link ConsumerRateLimiter#acquire()}，再交给 {@link #processEntry} 处理
   * </ol>
   *
   * <p>异常时记录 lastError 并指数退避（上限 30s），防止 Redis 连接故障导致 CPU 空转。
   *
   * @param handler 消息处理回调
   */
  private void consumeLoop(IMessageHandler handler) {
    while (running.get()) {
      try {
        StreamReadOptions readOptions =
            StreamReadOptions.empty().count(batchSize).block(Duration.ofMillis(blockMillis));
        Consumer consumerObj = Consumer.from(group, consumer);
        StreamOffset<String> offset = StreamOffset.create(channel, ReadOffset.lastConsumed());
        List<MapRecord<String, Object, Object>> records =
            redisTemplate.opsForStream().read(consumerObj, readOptions, offset);
        if (records == null || records.isEmpty()) {
          continue;
        }
        for (MapRecord<String, Object, Object> entry : records) {
          rateLimiter.acquire();
          if (!running.get()) {
            break;
          }
          processEntry(entry, handler);
        }
      } catch (Exception ex) {
        lastError.set(ex);
        log.error(
            "[RedisStream] 消费循环异常，channel={}, group={}, consumer={}", channel, group, consumer, ex);
        long backoff = Math.min(1000L * (1L << Math.min(5, 5)), 30000L);
        sleepQuietly(backoff);
      }
    }
    log.info("[RedisStream] 消费循环已退出，consumer={}", consumer);
  }

  /**
   * 同步拉取单条消息（供 {@link #subscribe()} 调用）。
   *
   * <p>以阻塞方式 XREADGROUP 读取一条消息，解析后返回。如果解析失败则自动 ACK 并返回 null，防止毒消息无限阻塞消费组。
   *
   * @return 解析后的 {@link QueueMessage}，无消息或解析失败时返回 {@code null}
   */
  private QueueMessage poll() {
    try {
      StreamReadOptions readOptions =
          StreamReadOptions.empty().count(1).block(Duration.ofMillis(blockMillis));
      Consumer consumerObj = Consumer.from(group, consumer);
      StreamOffset<String> offset = StreamOffset.create(channel, ReadOffset.lastConsumed());
      List<MapRecord<String, Object, Object>> records =
          redisTemplate.opsForStream().read(consumerObj, readOptions, offset);
      if (records == null || records.isEmpty()) {
        return null;
      }
      MapRecord<String, Object, Object> entry = records.get(0);
      QueueMessage message = parseMessage(entry.getValue());
      if (message == null) {
        redisTemplate.opsForStream().acknowledge(channel, group, entry.getId());
        log.warn("[RedisStream] 消息解析失败，已ACK，entryId={}", entry.getId());
        return null;
      }
      return message;
    } catch (Exception ex) {
      lastError.set(ex);
      log.error("[RedisStream] 拉取消息异常，channel={}, consumer={}", channel, consumer, ex);
      return null;
    }
  }

  /**
   * 处理单条 Stream 消息。
   *
   * <p>处理流程：
   *
   * <ol>
   *   <li>解析消息体，解析失败则 ACK 并跳过
   *   <li>注入 MDC traceId 实现全链路日志追踪
   *   <li>调用业务 handler.onMessage() 处理消息
   *   <li>成功则 XACK 确认 + 记录指标；失败则进入 {@link #handleFailedMessage}
   * </ol>
   *
   * @param entry Redis Stream 消息记录
   * @param handler 消息处理回调
   */
  private void processEntry(MapRecord<String, Object, Object> entry, IMessageHandler handler) {
    QueueMessage message = parseMessage(entry.getValue());
    if (message == null) {
      redisTemplate.opsForStream().acknowledge(channel, group, entry.getId());
      log.warn("[RedisStream] 消息解析失败，已ACK，entryId={}", entry.getId());
      return;
    }
    MessageTracer.injectTraceId(message.getTraceId());
    long startMillis = System.currentTimeMillis();
    try {
      if (handler != null) {
        try {
          handler.onMessage(message);
        } catch (Throwable t) {
          throw new RuntimeException(t);
        }
      }
      redisTemplate.opsForStream().acknowledge(channel, group, entry.getId());
      consumedCount.incrementAndGet();
      lastError.set(null);
      long latency = System.currentTimeMillis() - startMillis;
      messageMetrics.recordConsume(true, latency);
      log.debug("[RedisStream] 消息处理成功，channel={}, traceId={}", channel, message.getTraceId());
    } catch (Exception ex) {
      long latency = System.currentTimeMillis() - startMillis;
      messageMetrics.recordConsume(false, latency);
      handleFailedMessage(entry, message, ex);
    } finally {
      MessageTracer.clearTraceId();
    }
  }

  /**
   * 处理消费失败的消息（重试 / 死信）。
   *
   * <p>策略：
   *
   * <ul>
   *   <li>重试次数 ≤ {@code retryMax}：按 {@link RetryPolicy} 计算退避时间， 休眠后将消息重新写入原 Stream 并 ACK
   *       旧消息（避免重复消费）
   *   <li>重试次数 > {@code retryMax}：将消息写入死信队列 Stream（{@code dlqChannel}）， 并 ACK 原消息，防止 PEL 无限堆积
   * </ul>
   *
   * @param entry 原始 Stream 消息记录
   * @param message 解析后的消息对象
   * @param ex 消费异常
   */
  private void handleFailedMessage(
      MapRecord<String, Object, Object> entry, QueueMessage message, Exception ex) {
    int nextRetry = message.incrementRetryCount();
    lastError.set(ex);
    try {
      if (nextRetry > retryMax) {
        writeStream(dlqChannel, message);
        redisTemplate.opsForStream().acknowledge(channel, group, entry.getId());
        log.error(
            "[RedisStream] 消息已转入死信队列，channel={}, dlq={}, traceId={}, retryCount={}",
            channel,
            dlqChannel,
            message.getTraceId(),
            nextRetry,
            ex);
      } else {
        long delayMillis = retryPolicy.getDelayMillis(nextRetry - 1);
        if (delayMillis > 0) {
          sleepQuietly(delayMillis);
        }
        writeStream(channel, message);
        redisTemplate.opsForStream().acknowledge(channel, group, entry.getId());
        log.warn(
            "[RedisStream] 消息将重试，channel={}, traceId={}, retry={}/{}, delay={}ms, error={}",
            channel,
            message.getTraceId(),
            nextRetry,
            retryMax,
            delayMillis,
            ex.getMessage());
      }
    } catch (Exception writeEx) {
      log.error(
          "[RedisStream] 死信处理异常，channel={}, traceId={}", channel, message.getTraceId(), writeEx);
    }
  }

  /**
   * 将消息写入指定的 Redis Stream。
   *
   * <p>用于重试场景（写入原 Stream）和死信场景（写入 DLQ Stream）。 写入的字段包括 payload、retryCount，供消费端解析恢复重试状态。
   *
   * @param streamKey 目标 Stream Key
   * @param message 要写入的消息对象
   */
  private void writeStream(String streamKey, QueueMessage message) {
    Map<String, String> fields = new HashMap<>(4);
    fields.put(FIELD_PAYLOAD, QueueMessage.toPayload(message));
    fields.put(FIELD_RETRY_COUNT, String.valueOf(message.getRetryCount()));
    ObjectRecord<String, Map<String, String>> record =
        StreamRecords.newRecord().ofObject(fields).withStreamKey(streamKey);
    redisTemplate.opsForStream().add(record);
  }

  /**
   * 从 Stream Entry 的字段 Map 中解析出 {@link QueueMessage}。
   *
   * <p>解析顺序：
   *
   * <ol>
   *   <li>从 {@code payload} 字段反序列化消息体（JSON）
   *   <li>恢复 {@code retryCount} 重试计数
   *   <li>恢复 {@code groupKey} + {@code sequence} 顺序消息属性
   * </ol>
   *
   * @param fields Stream Entry 的字段 Map
   * @return 解析后的消息对象，字段为空或解析失败时返回 {@code null}
   */
  private QueueMessage parseMessage(Map<Object, Object> fields) {
    if (fields == null || fields.isEmpty()) {
      return null;
    }
    Object payloadObj = fields.get(FIELD_PAYLOAD);
    String payload = payloadObj != null ? String.valueOf(payloadObj) : null;
    QueueMessage message = QueueMessage.fromPayload(payload);
    if (message == null) {
      message = QueueMessage.of(payload);
    }
    Object retryObj = fields.get(FIELD_RETRY_COUNT);
    if (retryObj != null) {
      try {
        message.setRetryCount(Integer.parseInt(String.valueOf(retryObj)));
      } catch (NumberFormatException ignored) {
        message.setRetryCount(0);
      }
    }

    // 解析顺序消息字段
    Object groupKeyObj = fields.get(FIELD_GROUP_KEY);
    if (groupKeyObj != null) {
      String groupKey = String.valueOf(groupKeyObj);
      Object sequenceObj = fields.get(FIELD_SEQUENCE);
      Long sequence = null;
      if (sequenceObj != null) {
        try {
          sequence = Long.parseLong(String.valueOf(sequenceObj));
        } catch (NumberFormatException ignored) {
          sequence = null;
        }
      }
      message.setMessageGroupKey(groupKey);
      if (sequence != null) {
        message.addHeader("sequence", String.valueOf(sequence));
      }
    }

    return message;
  }

  /**
   * 确保消费组存在。
   *
   * <p>调用 <b>XGROUP CREATE</b> 创建消费组。如果组已存在，Redis 返回 BUSYGROUP 错误， 此处静默忽略以保证幂等启动。其他异常则向上抛出。
   */
  private void ensureGroup() {
    try {
      redisTemplate.opsForStream().createGroup(channel, group);
      log.debug("[RedisStream] 消费组已创建，channel={}, group={}", channel, group);
    } catch (Exception ex) {
      String msg = ex.getMessage();
      if (msg != null && msg.contains("BUSYGROUP")) {
        log.debug("[RedisStream] 消费组已存在，channel={}, group={}", channel, group);
      } else {
        throw ex;
      }
    }
  }

  /**
   * 安全休眠，被中断时恢复中断标志。
   *
   * @param millis 休眠毫秒数
   */
  private void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * 生成 8 位短 ID，用于消费者名称后缀，确保多实例唯一。
   *
   * @return 8 位十六进制字符串
   */
  private String generateShortId() {
    return IdGenerator.nextIdStr().substring(0, 8);
  }
}
