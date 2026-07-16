package com.njydsz.common.notify.core;

import java.time.Duration;
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
 * <p>整合邮件、企业微信、钉钉、飞书等多种通知渠道，提供统一的发送接口。
 * 使用策略模式实现渠道自动分发，所有渠道（包括邮件）均通过 {@link NotifyChannelStrategy} 统一处理。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>限流保护：每个渠道独立限流，基于滑动窗口算法</li>
 *   <li>熔断保护：连续失败超过阈值自动熔断，半开探测恢复</li>
 *   <li>渠道降级：主渠道失败时按降级链尝试备用渠道</li>
 *   <li>去重保护：相同内容在时间窗口内只发送一次（所有渠道生效）</li>
 *   <li>指标埋点：所有渠道发送量/失败率/延迟统一上报 Micrometer</li>
 *   <li>审计日志：每条通知发送记录结构化审计日志</li>
 *   <li>用户偏好：免打扰时段、渠道开关、类型开关</li>
 *   <li>消息聚合：低优先级消息在时间窗口内聚合为摘要</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NotifyServiceImpl implements NotifyService {

	private static final Logger log = LoggerFactory.getLogger(NotifyServiceImpl.class);

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

	/** 消息聚合器（可选依赖，P0-7） */
	private final NotificationAggregator aggregator;

	public NotifyServiceImpl(List<NotifyChannelStrategy> strategyList) {
		this(strategyList, null, null, null, null, null, null, null, null, null);
	}

	public NotifyServiceImpl(List<NotifyChannelStrategy> strategyList,
							 NotifyRateLimiterManager rateLimiterManager) {
		this(strategyList, rateLimiterManager, null, null, null, null, null, null, null, null);
	}

	public NotifyServiceImpl(List<NotifyChannelStrategy> strategyList,
							 NotifyRateLimiterManager rateLimiterManager,
							 ExecutorService parallelExecutor) {
		this(strategyList, rateLimiterManager, parallelExecutor, null, null, null, null, null, null, null);
	}

	public NotifyServiceImpl(List<NotifyChannelStrategy> strategyList,
							 NotifyRateLimiterManager rateLimiterManager,
							 ExecutorService parallelExecutor,
							 NotifyCircuitBreakerRegistry circuitBreakerRegistry) {
		this(strategyList, rateLimiterManager, parallelExecutor, circuitBreakerRegistry,
				null, null, null, null, null, null);
	}

	/**
	 * 构造通知服务实现（全参数）
	 *
	 * @param strategyList           渠道策略列表
	 * @param rateLimiterManager     限流管理器（可选）
	 * @param parallelExecutor       并行线程池（可选）
	 * @param circuitBreakerRegistry 熔断器注册中心（可选）
	 * @param fallbackManager        降级管理器（可选，P0-1）
	 * @param auditService           审计服务（可选，P0-2）
	 * @param metrics                指标服务（可选，P0-4）
	 * @param preferenceManager      偏好管理器（可选，P0-5）
	 * @param dedupService           去重服务（可选，P0-6）
	 * @param aggregator             聚合器（可选，P0-7）
	 */
	public NotifyServiceImpl(List<NotifyChannelStrategy> strategyList,
							 NotifyRateLimiterManager rateLimiterManager,
							 ExecutorService parallelExecutor,
							 NotifyCircuitBreakerRegistry circuitBreakerRegistry,
							 NotifyFallbackManager fallbackManager,
							 NotifyAuditService auditService,
							 NotifyMetrics metrics,
							 NotifyPreferenceManager preferenceManager,
							 NotifyDedupService dedupService,
							 NotificationAggregator aggregator) {
		this.channelStrategies = strategyList.stream()
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
		log.info("[NotifyServiceImpl] 初始化完成, strategies={}, rateLimit={}, parallel={}, circuitBreaker={}, "
						+ "fallback={}, audit={}, metrics={}, preference={}, dedup={}, aggregator={}",
				channelStrategies.size(), rateLimiterManager != null, parallelExecutor != null,
				circuitBreakerRegistry != null, fallbackManager != null, auditService != null,
				metrics != null, preferenceManager != null, dedupService != null, aggregator != null);
	}

	@Override
	public NotifySendResult send(NotifyChannel channel, String receiver, String title, String content) {
		return doSend(channel, receiver, title, content, null, null, NotifyType.TEXT);
	}

	@Override
	public NotifySendResult send(NotifyRequest request) {
		if (request == null) {
			return NotifySendResult.failure("通知请求为空", "unknown");
		}
		NotifyChannel channel = request.getChannel();

		// P0-5: 用户偏好检查（P0_CRITICAL 跳过）
		if (preferenceManager != null && request.getUserId() != null
				&& request.getPriority() != NotifyPriority.P0_CRITICAL) {
			try {
				NotifyType type = request.isTemplateRequest() ? NotifyType.TEMPLATE : NotifyType.TEXT;
				if (!preferenceManager.isAllowed(request.getUserId(), channel, type)) {
					log.debug("[NotifyServiceImpl] 用户偏好检查不通过，跳过发送: userId={}, channel={}",
							request.getUserId(), channel.getName());
					return NotifySendResult.failure("用户通知偏好不允许此渠道", channel.getName());
				}
			} catch (Exception e) {
				log.debug("[NotifyServiceImpl] 偏好检查异常，继续发送: {}", e.getMessage());
			}
		}

		// P0-7: 消息聚合检查（P0_CRITICAL 跳过）
		if (aggregator != null && request.getPriority() != NotifyPriority.P0_CRITICAL) {
			String templateCode = request.isTemplateRequest() ? request.getTemplateCode() : null;
			if (aggregator.offer(request.getReceiver(), channel, templateCode,
					request.getTitle(), request.getContent(), request.getPriority())) {
				log.debug("[NotifyServiceImpl] 消息已加入聚合缓冲区: channel={}, receiver={}",
						channel.getName(), request.getReceiver());
				return NotifySendResult.success("aggregated", channel.getName());
			}
		}

		// 执行发送
		if (request.isTemplateRequest()) {
			return doSendTemplate(channel, request.getReceiver(),
					request.getTemplateCode(), request.getTemplateParams(),
					request.getTitle());
		}
		return doSend(channel, request.getReceiver(), request.getTitle(), request.getContent(),
				request.getUserId(), null, NotifyType.TEXT);
	}

	@Override
	public NotifySendResult sendTemplate(NotifyChannel channel, String receiver,
										  String templateCode, Object templateParams) {
		return doSendTemplate(channel, receiver, templateCode, templateParams, null);
	}

	@Override
	public NotifySendResult batchSend(NotifyChannel channel, List<String> receivers,
									   String title, String content) {
		// P1-1: 熔断检查
		if (!tryAcquireCircuitBreaker(channel)) {
			return NotifySendResult.failure("通知渠道[" + channel.getName() + "]已熔断，请稍后重试", channel.getName());
		}

		if (!tryAcquireRateLimit(channel)) {
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

	@Override
	public CompletableFuture<NotifySendResult> parallelBatchSend(NotifyChannel channel, List<String> receivers,
																  String title, String content) {
		// P1-1: 熔断检查
		if (!tryAcquireCircuitBreaker(channel)) {
			return CompletableFuture.completedFuture(
				NotifySendResult.failure("通知渠道[" + channel.getName() + "]已熔断，请稍后重试", channel.getName())
			);
		}

		if (!tryAcquireRateLimit(channel)) {
			return CompletableFuture.completedFuture(
				NotifySendResult.failure("通知渠道限流触发，请稍后重试: " + channel.getName(), channel.getName())
			);
		}

		NotifyChannelStrategy strategy = channelStrategies.get(channel);
		if (strategy == null) {
			recordCircuitFailure(channel);
			return CompletableFuture.completedFuture(
				NotifySendResult.failure("通知渠道未配置: " + channel.getName(), channel.getName())
			);
		}

		if (parallelExecutor == null) {
			return CompletableFuture.completedFuture(strategy.batchSend(receivers, title, content));
		}

		List<CompletableFuture<NotifySendResult>> futures = receivers.stream()
			.map(receiver -> CompletableFuture.supplyAsync(
				() -> strategy.send(receiver, title, content),
				parallelExecutor
			))
			.collect(Collectors.toList());

		CompletableFuture<Void> allFutures = CompletableFuture.allOf(
			futures.toArray(new CompletableFuture<?>[0])
		);
		return allFutures.thenApply(v -> {
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
					"并行批量发送部分失败: 成功" + successCount + "/" + receivers.size(),
					channel.getName()
				);
			});
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
			String[] parts = entry.getKey().split("\\|", 3);
			if (parts.length < 2) {
				continue;
			}
			String receiver = parts[0];
			NotifyChannel channel;
			try {
				channel = NotifyChannel.valueOf(parts[1]);
			} catch (IllegalArgumentException e) {
				continue;
			}
			NotificationAggregator.AggregatedMessage msg = entry.getValue();
			NotifyChannelStrategy strategy = channelStrategies.get(channel);
			if (strategy != null) {
				strategy.send(receiver, msg.getTitle(), msg.getContent());
				sent++;
			}
		}
		if (sent > 0) {
			log.info("[NotifyServiceImpl] 聚合消息刷新完成, 发送 {} 条聚合消息", sent);
		}
		return sent;
	}

	// ==================== 内部发送逻辑 ====================

	/**
	 * 执行单条通知发送（含去重、熔断、限流、指标、审计、降级全链路）
	 */
	private NotifySendResult doSend(NotifyChannel channel, String receiver, String title,
									 String content, String userId, String templateCode,
									 NotifyType notifyType) {
		// P0-6: 去重检查
		if (isDuplicate(receiver, title, content)) {
			log.debug("[NotifyServiceImpl] 去重命中，跳过发送: receiver={}, title={}", receiver, title);
			return NotifySendResult.success("dedup-skipped", channel.getName());
		}

		// 熔断检查
		if (!tryAcquireCircuitBreaker(channel)) {
			return NotifySendResult.failure("通知渠道[" + channel.getName() + "]已熔断，请稍后重试", channel.getName());
		}

		// 限流检查
		if (!tryAcquireRateLimit(channel)) {
			return NotifySendResult.failure("通知渠道限流触发，请稍后重试: " + channel.getName(), channel.getName());
		}

		NotifyChannelStrategy strategy = channelStrategies.get(channel);
		if (strategy == null) {
			recordCircuitFailure(channel);
			return NotifySendResult.failure("通知渠道未配置: " + channel.getName(), channel.getName());
		}

		long startTime = System.nanoTime();
		NotifySendResult result;
		try {
			result = strategy.send(receiver, title, content);
		} catch (Exception e) {
			result = NotifySendResult.failure(e.getMessage(), channel.getName());
		}

		long durationNanos = System.nanoTime() - startTime;
		recordCircuitResult(channel, result.isSuccess());
		recordMetrics(channel, result.isSuccess(), durationNanos, templateCode);
		auditLog(channel, receiver, templateCode, result, durationNanos);

		// P0-1: 失败降级
		if (!result.isSuccess() && fallbackManager != null) {
			log.info("[NotifyServiceImpl] 主渠道[{}]发送失败，尝试降级", channel.getName());
			result = fallbackManager.fallbackSend(channel, receiver, title, content);
		}

		return result;
	}

	/**
	 * 执行模板通知发送（含熔断、限流、指标、审计、降级全链路）
	 */
	private NotifySendResult doSendTemplate(NotifyChannel channel, String receiver,
											 String templateCode, Object templateParams,
											 String title) {
		// 熔断检查
		if (!tryAcquireCircuitBreaker(channel)) {
			return NotifySendResult.failure("通知渠道[" + channel.getName() + "]已熔断，请稍后重试", channel.getName());
		}

		// 限流检查
		if (!tryAcquireRateLimit(channel)) {
			return NotifySendResult.failure("通知渠道限流触发，请稍后重试: " + channel.getName(), channel.getName());
		}

		NotifyChannelStrategy strategy = channelStrategies.get(channel);
		if (strategy == null) {
			recordCircuitFailure(channel);
			return NotifySendResult.failure("通知渠道未配置: " + channel.getName(), channel.getName());
		}

		long startTime = System.nanoTime();
		NotifySendResult result;
		try {
			result = strategy.sendTemplate(receiver, templateCode, templateParams);
		} catch (Exception e) {
			result = NotifySendResult.failure(e.getMessage(), channel.getName());
		}

		long durationNanos = System.nanoTime() - startTime;
		recordCircuitResult(channel, result.isSuccess());
		recordMetrics(channel, result.isSuccess(), durationNanos, templateCode);
		auditLog(channel, receiver, templateCode, result, durationNanos);

		// P0-1: 失败降级
		if (!result.isSuccess() && fallbackManager != null) {
			String fallbackTitle = title != null ? title : templateCode;
			String fallbackContent = templateParams != null ? String.valueOf(templateParams) : "";
			result = fallbackManager.fallbackSend(channel, receiver, fallbackTitle, fallbackContent);
		}

		return result;
	}

	// ==================== 辅助方法 ====================

	private boolean tryAcquireRateLimit(NotifyChannel channel) {
		if (rateLimiterManager == null) {
			return true;
		}
		return rateLimiterManager.tryAcquire(channel);
	}

	private boolean tryAcquireCircuitBreaker(NotifyChannel channel) {
		if (circuitBreakerRegistry == null) {
			return true;
		}
		return circuitBreakerRegistry.tryAcquire(channel);
	}

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

	private void recordCircuitFailure(NotifyChannel channel) {
		recordCircuitResult(channel, false);
	}

	/**
	 * P0-4: 记录渠道发送指标（所有渠道统一）
	 */
	private void recordMetrics(NotifyChannel channel, boolean success, long durationNanos, String templateCode) {
		if (metrics == null) {
			return;
		}
		try {
			metrics.recordChannelSend(channel.getName(), success, templateCode);
			if (!success) {
				metrics.recordEmailFailure(channel.getName(), "send_error", "send_failure");
			}
		} catch (Exception e) {
			log.debug("[NotifyServiceImpl] 指标记录异常: {}", e.getMessage());
		}
	}

	/**
	 * P0-2: 审计日志记录
	 */
	private void auditLog(NotifyChannel channel, String receiver, String templateCode,
						   NotifySendResult result, long durationNanos) {
		if (auditService == null) {
			return;
		}
		try {
			auditService.audit(channel, receiver, null, result,
					Duration.ofNanos(durationNanos).toMillis(), templateCode);
		} catch (Exception e) {
			log.debug("[NotifyServiceImpl] 审计日志记录异常: {}", e.getMessage());
		}
	}

	/**
	 * P0-6: 去重检查
	 */
	private boolean isDuplicate(String receiver, String title, String content) {
		if (dedupService == null) {
			return false;
		}
		try {
			return dedupService.isDuplicate(receiver, title, content);
		} catch (Exception e) {
			log.debug("[NotifyServiceImpl] 去重检查异常: {}", e.getMessage());
			return false;
		}
	}
}
