package com.njydsz.common.notify.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.notify.aggregate.NotificationAggregator;
import com.njydsz.common.notify.audit.NotifyAuditService;
import com.njydsz.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.common.notify.dedup.NotifyDedupService;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.enums.NotifyPriority;
import com.njydsz.common.notify.enums.NotifyType;
import com.njydsz.common.notify.fallback.NotifyFallbackManager;
import com.njydsz.common.notify.metrics.NotifyMetrics;
import com.njydsz.common.notify.preference.NotifyPreferenceManager;
import com.njydsz.common.notify.ratelimit.NotifyRateLimiterManager;

/**
 * 统一消息通知服务实现。
 *
 * <p>整合邮件、企业微信、钉钉、飞书等多种通知渠道，提供统一的发送接口。 使用策略模式实现渠道自动分发，所有渠道（包括邮件）均通过 {@link NotifyChannelStrategy}
 * 统一处理。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>限流保护：每个渠道独立限流，基于滑动窗口算法
 *   <li>熔断保护：连续失败超过阈值自动熔断，半开探测恢复
 *   <li>渠道降级：主渠道失败时按降级链尝试备用渠道
 *   <li>去重保护：相同内容在时间窗口内只发送一次（所有渠道生效）
 *   <li>指标埋点：所有渠道发送量/失败率/延迟统一上报 Micrometer
 *   <li>审计日志：每条通知发送记录结构化审计日志
 *   <li>用户偏好：免打扰时段、渠道开关、类型开关
 *   <li>消息聚合：低优先级消息在时间窗口内聚合为摘要
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NotifyServiceImpl implements NotifyService {

  private static final Logger LOG = LoggerFactory.getLogger(NotifyServiceImpl.class);

  /** 纳秒到毫秒的转换系数 */
  private static final long NANOS_TO_MILLIS = 1_000_000L;

  private final Map<NotifyChannel, NotifyChannelStrategy> channelStrategies;

  /** 渠道限流管理器（可选依赖） */
  private final NotifyRateLimiterManager rateLimiterManager;

  /** 并行发送线程池（可选依赖） */
  private final ExecutorService parallelExecutor;

  /** 渠道熔断器注册中心（可选依赖） */
  private final NotifyCircuitBreakerRegistry circuitBreakerRegistry;

  /** 渠道降级管理器（可选依赖，P0-1） */
  private final NotifyFallbackManager fallbackManager;

  /** 审计日志服务（可选依赖，P0-2） */
  private final NotifyAuditService auditService;

  /** 指标埋点服务（可选依赖，P0-4） */
  private final NotifyMetrics metrics;

  /** 用户偏好管理器（可选依赖，P0-5） */
  private final NotifyPreferenceManager preferenceManager;

  /** 去重服务（可选依赖，P0-6） */
  private final NotifyDedupService dedupService;

  /** 消息聚合器（可选依赖） */
  private final NotificationAggregator aggregator;

  /** 通知回执追踪器（可选依赖，骨架实现） */
  private NotifyReceiptTracker receiptTracker = new InMemoryNotifyReceiptTracker();

  /**
   * 创建构建器（流式 API）
   *
   * @param strategyList 渠道策略列表
   * @return 构建器实例
   */
  public static NotifyServiceImplBuilder builder(List<NotifyChannelStrategy> strategyList) {
    return NotifyServiceImplBuilder.builder(strategyList);
  }

  public NotifyServiceImpl(List<NotifyChannelStrategy> strategyList) {
    this(strategyList, null, null, null, null, null, null, null, null, null);
  }

  public NotifyServiceImpl(
      List<NotifyChannelStrategy> strategyList, NotifyRateLimiterManager rateLimiterManager) {
    this(strategyList, rateLimiterManager, null, null, null, null, null, null, null, null);
  }

  public NotifyServiceImpl(
      List<NotifyChannelStrategy> strategyList,
      NotifyRateLimiterManager rateLimiterManager,
      ExecutorService parallelExecutor) {
    this(
        strategyList,
        rateLimiterManager,
        parallelExecutor,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public NotifyServiceImpl(
      List<NotifyChannelStrategy> strategyList,
      NotifyRateLimiterManager rateLimiterManager,
      ExecutorService parallelExecutor,
      NotifyCircuitBreakerRegistry circuitBreakerRegistry) {
    this(
        strategyList,
        rateLimiterManager,
        parallelExecutor,
        circuitBreakerRegistry,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * 构造通知服务实现（全参数）
   *
   * @param strategyList 渠道策略列表
   * @param rateLimiterManager 限流管理器（可选）
   * @param parallelExecutor 并行线程池（可选）
   * @param circuitBreakerRegistry 熔断器注册中心（可选）
   * @param fallbackManager 降级管理器（可选，P0-1）
   * @param auditService 审计服务（可选，P0-2）
   * @param metrics 指标服务（可选，P0-4）
   * @param preferenceManager 偏好管理器（可选，P0-5）
   * @param dedupService 去重服务（可选，P0-6）
   * @param aggregator 聚合器（可选，P0-7）
   */
  public NotifyServiceImpl(
      List<NotifyChannelStrategy> strategyList,
      NotifyRateLimiterManager rateLimiterManager,
      ExecutorService parallelExecutor,
      NotifyCircuitBreakerRegistry circuitBreakerRegistry,
      NotifyFallbackManager fallbackManager,
      NotifyAuditService auditService,
      NotifyMetrics metrics,
      NotifyPreferenceManager preferenceManager,
      NotifyDedupService dedupService,
      NotificationAggregator aggregator) {
    this.channelStrategies =
        strategyList.stream()
            .filter(NotifyChannelStrategy::isEnabled)
            .collect(Collectors.toMap(NotifyChannelStrategy::getChannel, s -> s));
    this.rateLimiterManager = rateLimiterManager;
    this.parallelExecutor = parallelExecutor;
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.fallbackManager = fallbackManager;
    this.auditService = auditService;
    this.metrics = metrics;
    this.preferenceManager = preferenceManager;
    this.dedupService = dedupService;
    this.aggregator = aggregator;
    LOG.info(
        "[NotifyServiceImpl] 初始化完成, strategies={}, rateLimit={}, parallel={}, circuitBreaker={}, "
            + "fallback={}, audit={}, metrics={}, preference={}, dedup={}, aggregator={}",
        channelStrategies.size(),
        rateLimiterManager != null,
        parallelExecutor != null,
        circuitBreakerRegistry != null,
        fallbackManager != null,
        auditService != null,
        metrics != null,
        preferenceManager != null,
        dedupService != null,
        aggregator != null);
  }

  /**
   * 设置回执追踪器（可选，用于替换默认的内存实现）
   *
   * @param receiptTracker 回执追踪器
   */
  public void setReceiptTracker(NotifyReceiptTracker receiptTracker) {
    if (receiptTracker != null) {
      this.receiptTracker = receiptTracker;
    }
  }

  /**
   * 获取回执追踪器（供业务方查询回执）
   *
   * @return 回执追踪器
   */
  public NotifyReceiptTracker getReceiptTracker() {
    return receiptTracker;
  }

  /**
   * 发送通知（统一入口）。
   *
   * <p>支持以下全链路增强：
   *
   * <ul>
   *   <li>用户偏好检查（免打扰时段、渠道开关、类型开关），P0_CRITICAL 跳过
   *   <li>消息聚合检查（低优先级消息在时间窗口内聚合为摘要），P0_CRITICAL 跳过
   *   <li>去重检查（相同内容在时间窗口内只发送一次）
   *   <li>熔断检查（连续失败超过阈值自动熔断）
   *   <li>限流检查（每个渠道独立限流，基于滑动窗口算法）
   *   <li>渠道降级（主渠道失败时按降级链尝试备用渠道）
   *   <li>指标埋点（所有渠道发送量/失败率/延迟统一上报 Micrometer）
   *   <li>审计日志（每条通知发送记录结构化审计日志）
   * </ul>
   *
   * @param request 通知请求（包含渠道、接收方、标题、内容、模板信息、优先级等）
   * @return 发送结果（aggregated 表示已加入聚合缓冲区，success 表示发送成功，failure 表示发送失败）
   */
  @Override
  public NotifySendResult send(NotifyRequest request) {
    if (request == null) {
      return NotifySendResult.failure("通知请求为空", "unknown");
    }
    NotifyChannel channel = request.getChannel();

    // P0-5: 用户偏好检查（P0_CRITICAL 跳过）
    if (preferenceManager != null
        && request.getUserId() != null
        && request.getPriority() != NotifyPriority.P0_CRITICAL) {
      try {
        NotifyType type = request.isTemplateRequest() ? NotifyType.TEMPLATE : NotifyType.TEXT;
        if (!preferenceManager.isAllowed(request.getUserId(), channel, type)) {
          LOG.debug(
              "[NotifyServiceImpl] 用户偏好检查不通过，跳过发送: userId={}, channel={}",
              request.getUserId(),
              channel.getName());
          return NotifySendResult.failure("用户通知偏好不允许此渠道", channel.getName());
        }
      } catch (Exception e) {
        LOG.debug("[NotifyServiceImpl] 偏好检查异常，继续发送: {}", e.getMessage());
      }
    }

    // P0-7: 消息聚合检查（P0_CRITICAL 跳过）
    if (aggregator != null && request.getPriority() != NotifyPriority.P0_CRITICAL) {
      String templateCode = request.isTemplateRequest() ? request.getTemplateCode() : null;
      if (aggregator.offer(
          request.getReceiver(),
          channel,
          templateCode,
          request.getTitle(),
          request.getContent(),
          request.getPriority())) {
        LOG.debug(
            "[NotifyServiceImpl] 消息已加入聚合缓冲区: channel={}, receiver={}",
            channel.getName(),
            request.getReceiver());
        return NotifySendResult.success("aggregated", channel.getName());
      }
    }

    // 执行发送
    if (request.isTemplateRequest()) {
      return doSendTemplate(
          channel,
          request.getReceiver(),
          request.getTemplateCode(),
          request.getTemplateParams(),
          request.getTitle(),
          request.getTenantId());
    }
    return doSend(
        channel,
        request.getReceiver(),
        request.getTitle(),
        request.getContent(),
        request.getUserId(),
        null,
        NotifyType.TEXT,
        request.getTenantId());
  }

  /**
   * 批量发送通知（同步阻塞）。
   *
   * <p>先进行熔断检查和限流检查，然后调用渠道策略的 {@code batchSend} 方法。 批量发送不支持降级（因为批量操作难以确定哪些接收方需要降级）。
   *
   * @param channel 通知渠道
   * @param receivers 接收方列表
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  @Override
  public NotifySendResult batchSend(
      NotifyChannel channel, List<String> receivers, String title, String content) {
    // P1-1: 熔断检查
    if (!tryAcquireCircuitBreaker(channel)) {
      return NotifySendResult.failure(
          "通知渠道[" + channel.getName() + "]已熔断，请稍后重试", channel.getName());
    }

    if (!tryAcquireRateLimit(channel, null)) {
      return NotifySendResult.failure("通知渠道限流触发，请稍后重试: " + channel.getName(), channel.getName());
    }

    NotifyChannelStrategy strategy = channelStrategies.get(channel);
    if (strategy == null) {
      recordCircuitFailure(channel);
      return NotifySendResult.failure("通知渠道未配置: " + channel.getName(), channel.getName());
    }
    NotifySendResult result = strategy.batchSend(receivers, title, content);
    recordCircuitResult(channel, result.isSuccess());
    return result;
  }

  /**
   * 批量发送通知（异步并行）。
   *
   * <p>使用线程池并行调用单条发送方法，每个接收方独立处理， 最终统计成功/失败数量返回汇总结果。
   *
   * @param channel 通知渠道
   * @param receivers 接收方列表
   * @param title 消息标题
   * @param content 消息内容
   * @return 异步发送结果 Future
   */
  @Override
  public CompletableFuture<NotifySendResult> parallelBatchSend(
      NotifyChannel channel, List<String> receivers, String title, String content) {
    // P1-1: 熔断检查
    if (!tryAcquireCircuitBreaker(channel)) {
      return CompletableFuture.completedFuture(
          NotifySendResult.failure("通知渠道[" + channel.getName() + "]已熔断，请稍后重试", channel.getName()));
    }

    if (!tryAcquireRateLimit(channel, null)) {
      return CompletableFuture.completedFuture(
          NotifySendResult.failure("通知渠道限流触发，请稍后重试: " + channel.getName(), channel.getName()));
    }

    NotifyChannelStrategy strategy = channelStrategies.get(channel);
    if (strategy == null) {
      recordCircuitFailure(channel);
      return CompletableFuture.completedFuture(
          NotifySendResult.failure("通知渠道未配置: " + channel.getName(), channel.getName()));
    }

    if (parallelExecutor == null) {
      return CompletableFuture.completedFuture(strategy.batchSend(receivers, title, content));
    }

    List<CompletableFuture<NotifySendResult>> futures =
        receivers.stream()
            .map(
                receiver ->
                    CompletableFuture.supplyAsync(
                        () -> strategy.send(receiver, title, content), parallelExecutor))
            .collect(Collectors.toList());

    CompletableFuture<Void> allFutures =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    return allFutures.thenApply(
        v -> {
          int successCount = 0;
          int failureCount = 0;
          for (CompletableFuture<NotifySendResult> future : futures) {
            try {
              NotifySendResult result = future.get();
              if (result.isSuccess()) {
                successCount++;
              } else {
                failureCount++;
              }
            } catch (Exception e) {
              failureCount++;
            }
          }
          recordCircuitResult(channel, failureCount == 0);
          if (failureCount == 0) {
            return NotifySendResult.success("parallel-batch:" + successCount, channel.getName());
          }
          return NotifySendResult.failure(
              "并行批量发送部分失败: 成功" + successCount + "/" + receivers.size(), channel.getName());
        });
  }

  /**
   * 并行批量发送（返回结构化明细）
   *
   * <p>与 {@link #parallelBatchSend} 逻辑一致，但结果包含每个接收者的发送明细。
   *
   * @param channel 通知渠道
   * @param receivers 接收方列表
   * @param title 消息标题
   * @param content 消息内容
   * @return 异步批量发送结构化结果
   */
  @Override
  public CompletableFuture<BatchSendResultDTO> parallelBatchSendDetailed(
      NotifyChannel channel, List<String> receivers, String title, String content) {
    if (!tryAcquireCircuitBreaker(channel)) {
      return CompletableFuture.completedFuture(
          buildBatchErrorResult(
              receivers, "通知渠道[" + channel.getName() + "]已熔断，请稍后重试", channel.getName()));
    }
    if (!tryAcquireRateLimit(channel, null)) {
      return CompletableFuture.completedFuture(
          buildBatchErrorResult(
              receivers, "通知渠道限流触发，请稍后重试: " + channel.getName(), channel.getName()));
    }
    NotifyChannelStrategy strategy = channelStrategies.get(channel);
    if (strategy == null) {
      recordCircuitFailure(channel);
      return CompletableFuture.completedFuture(
          buildBatchErrorResult(receivers, "通知渠道未配置: " + channel.getName(), channel.getName()));
    }
    if (parallelExecutor == null) {
      NotifySendResult batchResult = strategy.batchSend(receivers, title, content);
      return CompletableFuture.completedFuture(buildBatchResultFromSingle(receivers, batchResult));
    }

    List<CompletableFuture<BatchSendResultDTO.ReceiverSendResult>> futures =
        receivers.stream()
            .map(
                receiver ->
                    CompletableFuture.supplyAsync(
                        () ->
                            new BatchSendResultDTO.ReceiverSendResult(
                                receiver, strategy.send(receiver, title, content)),
                        parallelExecutor))
            .collect(Collectors.toList());

    CompletableFuture<Void> allFutures =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    return allFutures.thenApply(
        v -> {
          List<BatchSendResultDTO.ReceiverSendResult> details = new ArrayList<>();
          int successCount = 0;
          int failureCount = 0;
          for (CompletableFuture<BatchSendResultDTO.ReceiverSendResult> future : futures) {
            try {
              BatchSendResultDTO.ReceiverSendResult item = future.get();
              details.add(item);
              if (item.result().isSuccess()) {
                successCount++;
              } else {
                failureCount++;
              }
            } catch (Exception e) {
              failureCount++;
            }
          }
          recordCircuitResult(channel, failureCount == 0);
          return new BatchSendResultDTO(receivers.size(), successCount, failureCount, details);
        });
  }

  /**
   * 构建批量错误结果（所有接收者标记为同一错误）
   *
   * @param receivers 接收者列表
   * @param errorMessage 错误信息
   * @param channelName 渠道名称
   * @return 批量错误结果
   */
  private BatchSendResultDTO buildBatchErrorResult(
      List<String> receivers, String errorMessage, String channelName) {
    List<BatchSendResultDTO.ReceiverSendResult> details =
        receivers.stream()
            .map(
                r ->
                    new BatchSendResultDTO.ReceiverSendResult(
                        r, NotifySendResult.failure(errorMessage, channelName)))
            .collect(Collectors.toList());
    return new BatchSendResultDTO(receivers.size(), 0, receivers.size(), details);
  }

  /**
   * 从单条批量发送结果构建批量结果（parallelExecutor 不可用时的降级路径）
   *
   * @param receivers 接收者列表
   * @param batchResult 批量发送结果
   * @return 批量结构化结果
   */
  private BatchSendResultDTO buildBatchResultFromSingle(
      List<String> receivers, NotifySendResult batchResult) {
    List<BatchSendResultDTO.ReceiverSendResult> details =
        receivers.stream()
            .map(r -> new BatchSendResultDTO.ReceiverSendResult(r, batchResult))
            .collect(Collectors.toList());
    int successCount = batchResult.isSuccess() ? receivers.size() : 0;
    int failureCount = batchResult.isSuccess() ? 0 : receivers.size();
    return new BatchSendResultDTO(receivers.size(), successCount, failureCount, details);
  }

  /**
   * 刷新聚合缓冲区，发送所有待聚合消息（由定时任务调用）
   *
   * @return 本次刷新发送的消息数量
   */
  public int flushAggregatedMessages() {
    if (aggregator == null) {
      return 0;
    }
    Map<String, NotificationAggregator.AggregatedMessage> pending = aggregator.flushAll();
    if (pending == null || pending.isEmpty()) {
      return 0;
    }
    int sent = 0;
    for (Map.Entry<String, NotificationAggregator.AggregatedMessage> entry : pending.entrySet()) {
      NotificationAggregator.AggregatedMessage msg = entry.getValue();

      // 优先使用 AggregatedMessage 中携带的渠道信息
      NotifyChannel channel = msg.getChannel();
      String receiver;

      if (channel != null) {
        receiver = extractReceiverFromKey(entry.getKey(), channel);
      } else {
        // 兼容旧格式：从 key 中解析渠道
        String[] parts = entry.getKey().split("\\|", 3);
        if (parts.length < 2) {
          continue;
        }
        receiver = parts[0];
        try {
          channel = NotifyChannel.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
          continue;
        }
      }

      if (receiver == null || channel == null) {
        continue;
      }

      NotifyChannelStrategy strategy = channelStrategies.get(channel);
      if (strategy != null) {
        strategy.send(receiver, msg.getTitle(), msg.getContent());
        sent++;
      }
    }
    if (sent > 0) {
      LOG.info("[NotifyServiceImpl] 聚合消息刷新完成, 发送 {} 条聚合消息", sent);
    }
    return sent;
  }

  /**
   * 从聚合 key 中提取接收者（当渠道已确定时）
   *
   * @param key 聚合 key
   * @param channel 已知渠道
   * @return 接收者，解析失败返回 null
   */
  private String extractReceiverFromKey(String key, NotifyChannel channel) {
    if (key == null || key.isEmpty()) {
      return null;
    }
    // key 格式：receiver|channel|template 或 receiver|channel 或 receiver
    if (key.contains("|")) {
      return key.substring(0, key.indexOf('|'));
    }
    return key;
  }

  // ==================== 内部发送逻辑 ====================

  /**
   * 执行单条通知发送（含去重、熔断、限流、指标、审计、降级全链路）。
   *
   * <p>处理链路：去重 → 熔断 → 限流 → 执行 → 指标 → 审计 → 降级。
   *
   * @param channel 通知渠道
   * @param receiver 接收方
   * @param title 消息标题
   * @param content 消息内容
   * @param userId 用户 ID（可选，用于偏好检查和审计）
   * @param templateCode 模板编码（可选，用于指标）
   * @param notifyType 通知类型（TEXT/TEMPLATE）
   * @param tenantId 租户 ID（可选，用于多租户限流隔离）
   * @return 发送结果
   */
  private NotifySendResult doSend(
      NotifyChannel channel,
      String receiver,
      String title,
      String content,
      String userId,
      String templateCode,
      NotifyType notifyType,
      String tenantId) {
    SendContext ctx =
        SendContext.forSend(
            channel, receiver, title, content, userId, templateCode, notifyType, tenantId);
    NotifySendResult result = executeSendPipeline(ctx);
    updateReceipt(result);
    return result;
  }

  /**
   * 执行模板通知发送（含熔断、限流、指标、审计、降级全链路）。
   *
   * <p>处理链路：熔断 → 限流 → 执行 → 指标 → 审计 → 降级。
   *
   * @param channel 通知渠道
   * @param receiver 接收方
   * @param templateCode 模板编码
   * @param templateParams 模板参数
   * @param title 消息标题（可选，用于降级）
   * @param tenantId 租户 ID（可选，用于多租户限流隔离）
   * @return 发送结果
   */
  private NotifySendResult doSendTemplate(
      NotifyChannel channel,
      String receiver,
      String templateCode,
      Object templateParams,
      String title,
      String tenantId) {
    SendContext ctx =
        SendContext.forTemplate(channel, receiver, templateCode, templateParams, title, tenantId);
    NotifySendResult result = executeTemplateSendPipeline(ctx);
    updateReceipt(result);
    return result;
  }

  // ==================== 发送处理链路（内联自 SendChain） ====================

  /**
   * 执行文本发送链路
   *
   * <p>步骤：去重 → 熔断 → 限流 → 执行 → 后置处理（指标、审计、降级）。
   *
   * @param ctx 发送上下文
   * @return 发送结果
   */
  private NotifySendResult executeSendPipeline(SendContext ctx) {
    // Step 1: 去重检查
    SendContext context = applyDedup(ctx);
    if (context.hasResult()) {
      return context.sendResult();
    }

    // Step 2: 熔断检查
    context = applyCircuitBreaker(context);
    if (context.hasResult()) {
      return context.sendResult();
    }

    // Step 3: 限流检查
    context = applyRateLimit(context);
    if (context.hasResult()) {
      return context.sendResult();
    }

    // Step 4: 执行发送
    context = executeChannelSend(context);

    // Step 5: 后置处理（指标、审计、降级）
    context = applyPostProcess(context);

    return context.sendResult();
  }

  /**
   * 执行模板发送链路
   *
   * <p>步骤：熔断 → 限流 → 执行 → 后置处理（指标、审计、降级）。
   *
   * @param ctx 发送上下文
   * @return 发送结果
   */
  private NotifySendResult executeTemplateSendPipeline(SendContext ctx) {
    SendContext context = ctx;

    // Step 1: 熔断检查
    context = applyCircuitBreaker(context);
    if (context.hasResult()) {
      return context.sendResult();
    }

    // Step 2: 限流检查
    context = applyRateLimit(context);
    if (context.hasResult()) {
      return context.sendResult();
    }

    // Step 3: 执行模板发送
    context = executeChannelTemplateSend(context);

    // Step 4: 后置处理
    context = applyPostProcess(context);

    return context.sendResult();
  }

  /** 去重检查步骤 */
  private SendContext applyDedup(SendContext ctx) {
    if (dedupService == null) {
      return ctx;
    }
    if (dedupService.isDuplicate(ctx.receiver(), ctx.title(), ctx.content())) {
      LOG.debug(
          "[NotifyServiceImpl] 去重命中，跳过发送: receiver={}, title={}", ctx.receiver(), ctx.title());
      return ctx.withResult(NotifySendResult.success("dedup-skipped", ctx.channel().getName()));
    }
    return ctx;
  }

  /** 熔断检查步骤 */
  private SendContext applyCircuitBreaker(SendContext ctx) {
    if (circuitBreakerRegistry == null || circuitBreakerRegistry.tryAcquire(ctx.channel())) {
      return ctx;
    }
    return ctx.withResult(
        NotifySendResult.failure(
            "通知渠道[" + ctx.channel().getName() + "]已熔断，请稍后重试", ctx.channel().getName()));
  }

  /** 限流检查步骤 */
  private SendContext applyRateLimit(SendContext ctx) {
    if (rateLimiterManager == null
        || rateLimiterManager.tryAcquire(ctx.channel(), ctx.tenantId())) {
      return ctx;
    }
    return ctx.withResult(
        NotifySendResult.failure(
            "通知渠道限流触发，请稍后重试: " + ctx.channel().getName(), ctx.channel().getName()));
  }

  /** 执行渠道文本发送步骤 */
  private SendContext executeChannelSend(SendContext ctx) {
    NotifyChannelStrategy strategy = channelStrategies.get(ctx.channel());
    if (strategy == null) {
      recordCircuitFailure(ctx.channel());
      return ctx.withResult(
          NotifySendResult.failure("通知渠道未配置: " + ctx.channel().getName(), ctx.channel().getName()));
    }

    long startTime = System.nanoTime();
    NotifySendResult result;
    try {
      result = strategy.send(ctx.receiver(), ctx.title(), ctx.content());
    } catch (Exception e) {
      result = NotifySendResult.failure(e.getMessage(), ctx.channel().getName());
    }
    long durationNanos = System.nanoTime() - startTime;

    return ctx.withResult(result).withTiming(startTime, durationNanos);
  }

  /** 执行渠道模板发送步骤 */
  private SendContext executeChannelTemplateSend(SendContext ctx) {
    NotifyChannelStrategy strategy = channelStrategies.get(ctx.channel());
    if (strategy == null) {
      recordCircuitFailure(ctx.channel());
      return ctx.withResult(
          NotifySendResult.failure("通知渠道未配置: " + ctx.channel().getName(), ctx.channel().getName()));
    }

    long startTime = System.nanoTime();
    NotifySendResult result;
    try {
      result = strategy.sendTemplate(ctx.receiver(), ctx.templateCode(), ctx.templateParams());
    } catch (Exception e) {
      result = NotifySendResult.failure(e.getMessage(), ctx.channel().getName());
    }
    long durationNanos = System.nanoTime() - startTime;

    return ctx.withResult(result).withTiming(startTime, durationNanos);
  }

  /** 后置处理步骤：熔断记录、指标、审计、降级 */
  private SendContext applyPostProcess(SendContext ctx) {
    if (ctx.sendResult() == null) {
      return ctx;
    }

    // 记录熔断结果
    recordCircuitResult(ctx.channel(), ctx.sendResult().isSuccess());

    // 指标采集
    if (metrics != null) {
      metrics.recordChannelSend(
          ctx.channel().getName(), ctx.sendResult().isSuccess(), ctx.templateCode());
      if (!ctx.sendResult().isSuccess()) {
        metrics.recordEmailFailure(ctx.channel().getName(), "send_error", "send_failure");
      }
    }

    // 审计日志
    if (auditService != null) {
      auditService.audit(
          ctx.channel(),
          ctx.receiver(),
          ctx.title(),
          ctx.sendResult(),
          ctx.durationNanos() / NANOS_TO_MILLIS,
          ctx.templateCode());
    }

    // 失败降级
    if (!ctx.sendResult().isSuccess() && fallbackManager != null) {
      LOG.info("[NotifyServiceImpl] 主渠道[{}]发送失败，尝试降级", ctx.channel().getName());
      String fallbackTitle = ctx.title() != null ? ctx.title() : ctx.templateCode();
      String fallbackContent =
          ctx.content() != null
              ? ctx.content()
              : (ctx.templateParams() != null ? String.valueOf(ctx.templateParams()) : "");
      NotifySendResult fallbackResult =
          fallbackManager.fallbackSend(
              ctx.channel(), ctx.receiver(), fallbackTitle, fallbackContent);
      return ctx.withResult(fallbackResult);
    }

    return ctx;
  }

  /** 根据发送结果更新回执状态 */
  private void updateReceipt(NotifySendResult result) {
    if (receiptTracker == null || result.getMessageId() == null) {
      return;
    }
    try {
      if (result.isSuccess()) {
        receiptTracker.markDelivered(result.getMessageId());
      } else if (result.getErrorMessage() != null) {
        receiptTracker.markFailed(result.getMessageId(), result.getErrorMessage());
      }
    } catch (Exception e) {
      LOG.debug("[NotifyServiceImpl] 回执更新异常: {}", e.getMessage());
    }
  }

  // ==================== 辅助方法 ====================

  /**
   * 尝试获取限流令牌。
   *
   * @param channel 通知渠道
   * @param tenantId 租户 ID（可为 null）
   * @return 是否获取成功（未配置限流管理器时默认成功）
   */
  private boolean tryAcquireRateLimit(NotifyChannel channel, String tenantId) {
    if (rateLimiterManager == null) {
      return true;
    }
    return rateLimiterManager.tryAcquire(channel, tenantId);
  }

  /**
   * 尝试获取熔断器通行证。
   *
   * @param channel 通知渠道
   * @return 是否获取成功（未配置熔断器注册中心时默认成功）
   */
  private boolean tryAcquireCircuitBreaker(NotifyChannel channel) {
    if (circuitBreakerRegistry == null) {
      return true;
    }
    return circuitBreakerRegistry.tryAcquire(channel);
  }

  /**
   * 记录熔断器结果。
   *
   * @param channel 通知渠道
   * @param success 是否成功
   */
  private void recordCircuitResult(NotifyChannel channel, boolean success) {
    if (circuitBreakerRegistry == null) {
      return;
    }
    if (success) {
      circuitBreakerRegistry.recordSuccess(channel);
    } else {
      circuitBreakerRegistry.recordFailure(channel);
    }
  }

  /**
   * 记录熔断器失败。
   *
   * @param channel 通知渠道
   */
  private void recordCircuitFailure(NotifyChannel channel) {
    recordCircuitResult(channel, false);
  }

  /**
   * P0-4: 记录渠道发送指标（所有渠道统一）。
   *
   * @param channel 通知渠道
   * @param success 是否发送成功
   * @param durationNanos 耗时（纳秒）
   * @param templateCode 模板编码（可选）
   */
  private void recordMetrics(
      NotifyChannel channel, boolean success, long durationNanos, String templateCode) {
    if (metrics == null) {
      return;
    }
    try {
      metrics.recordChannelSend(channel.getName(), success, templateCode);
      if (!success) {
        metrics.recordEmailFailure(channel.getName(), "send_error", "send_failure");
      }
    } catch (Exception e) {
      LOG.debug("[NotifyServiceImpl] 指标记录异常: {}", e.getMessage());
    }
  }

  /**
   * P0-2: 审计日志记录。
   *
   * @param channel 通知渠道
   * @param receiver 接收方
   * @param templateCode 模板编码（可选）
   * @param result 发送结果
   * @param durationNanos 耗时（纳秒）
   */
  private void auditLog(
      NotifyChannel channel,
      String receiver,
      String templateCode,
      NotifySendResult result,
      long durationNanos) {
    if (auditService == null) {
      return;
    }
    try {
      auditService.audit(
          channel,
          receiver,
          null,
          result,
          Duration.ofNanos(durationNanos).toMillis(),
          templateCode);
    } catch (Exception e) {
      LOG.debug("[NotifyServiceImpl] 审计日志记录异常: {}", e.getMessage());
    }
  }

  /**
   * P0-6: 去重检查。
   *
   * @param receiver 接收方
   * @param title 消息标题
   * @param content 消息内容
   * @return 是否重复（true 表示应跳过发送）
   */
  private boolean isDuplicate(String receiver, String title, String content) {
    if (dedupService == null) {
      return false;
    }
    try {
      return dedupService.isDuplicate(receiver, title, content);
    } catch (Exception e) {
      LOG.debug("[NotifyServiceImpl] 去重检查异常: {}", e.getMessage());
      return false;
    }
  }
}
