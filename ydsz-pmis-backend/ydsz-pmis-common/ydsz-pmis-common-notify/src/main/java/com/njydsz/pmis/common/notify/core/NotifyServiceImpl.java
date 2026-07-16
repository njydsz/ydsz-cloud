package com.njydsz.pmis.common.notify.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import com.njydsz.pmis.common.notify.ratelimit.NotifyRateLimiterManager;

/**
 * 统一消息通知服务实现。
 *
 * <p>整合邮件、企业微信、钉钉、飞书等多种通知渠道，提供统一的发送接口。
 * 使用策略模式实现渠道自动分发，所有渠道（包括邮件）均通过 {@link NotifyChannelStrategy} 统一处理。
 *
 * <p><b>限流保护：</b>
 * <ul>
 *   <li>每个渠道独立限流，防止单个渠道过载</li>
 *   <li>基于滑动窗口算法，平滑控制请求速率</li>
 *   <li>限流触发时返回失败结果，不阻塞调用方</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
public class NotifyServiceImpl implements NotifyService {

	private static final Logger log = LoggerFactory.getLogger(NotifyServiceImpl.class);

	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

	private final Map<NotifyChannel, NotifyChannelStrategy> channelStrategies;

	/**
	 * 渠道限流管理器（可选依赖）
	 */
	private final NotifyRateLimiterManager rateLimiterManager;

	/**
	 * 并行发送线程池（可选依赖）
	 */
	private final ExecutorService parallelExecutor;

	/** 渠道熔断器注册中心（可选依赖，P0-3） */
	private final NotifyCircuitBreakerRegistry circuitBreakerRegistry;

	public NotifyServiceImpl(List<NotifyChannelStrategy> strategyList) {
		this(strategyList, null, null, null);
	}

	public NotifyServiceImpl(List<NotifyChannelStrategy> strategyList, NotifyRateLimiterManager rateLimiterManager) {
		this(strategyList, rateLimiterManager, null, null);
	}

	public NotifyServiceImpl(List<NotifyChannelStrategy> strategyList, NotifyRateLimiterManager rateLimiterManager,
							 ExecutorService parallelExecutor) {
		this(strategyList, rateLimiterManager, parallelExecutor, null);
	}

	/**
	 * 构造通知服务实现
	 *
	 * @param strategyList           渠道策略列表
	 * @param rateLimiterManager     限流管理器（可选）
	 * @param parallelExecutor       并行线程池（可选）
	 * @param circuitBreakerRegistry 熔断器注册中心（可选，P0-3）
	 */
	public NotifyServiceImpl(List<NotifyChannelStrategy> strategyList, NotifyRateLimiterManager rateLimiterManager,
							 ExecutorService parallelExecutor, NotifyCircuitBreakerRegistry circuitBreakerRegistry) {
		this.channelStrategies = strategyList.stream()
				.filter(NotifyChannelStrategy::isEnabled)
				.collect(Collectors.toMap(NotifyChannelStrategy::getChannel, s -> s));
		this.rateLimiterManager = rateLimiterManager;
		this.parallelExecutor = parallelExecutor;
		this.circuitBreakerRegistry = circuitBreakerRegistry;
		log.info("[NotifyServiceImpl] 初始化完成, strategies={}, rateLimit={}, parallel={}, circuitBreaker={}",
				channelStrategies.size(), rateLimiterManager != null, parallelExecutor != null,
				circuitBreakerRegistry != null);
	}

	@Override
	public NotifySendResult send(NotifyChannel channel, String receiver, String title, String content) {
		// P0-3：熔断检查
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

		NotifySendResult result = strategy.send(receiver, title, content);
		if (result.isSuccess()) {
			recordCircuitSuccess(channel);
		} else {
			recordCircuitFailure(channel);
		}
		return result;
	}

	@Override
	public NotifySendResult sendTemplate(NotifyChannel channel, String receiver, String templateCode, Object templateParams) {
		// P0-3：熔断检查
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

		NotifySendResult result = strategy.sendTemplate(receiver, templateCode, templateParams);
		if (result.isSuccess()) {
			recordCircuitSuccess(channel);
		} else {
			recordCircuitFailure(channel);
		}
		return result;
	}

	/**
	 * 批量发送通知消息到多个接收者
	 *
	 * @param channel   通知渠道
	 * @param receivers 接收者标识列表
	 * @param title     消息标题
	 * @param content   消息内容
	 * @return 发送结果
	 */
	@Override
	public NotifySendResult batchSend(NotifyChannel channel, List<String> receivers, String title, String content) {
		// 限流检查
		if (!tryAcquireRateLimit(channel)) {
			return NotifySendResult.failure("通知渠道限流触发，请稍后重试: " + channel.getName(), channel.getName());
		}

		NotifyChannelStrategy strategy = channelStrategies.get(channel);
		if (strategy == null) {
			return NotifySendResult.failure("通知渠道未配置: " + channel.getName(), channel.getName());
		}
		return strategy.batchSend(receivers, title, content);
	}

	@Override
	public CompletableFuture<NotifySendResult> parallelBatchSend(NotifyChannel channel, List<String> receivers,
																  String title, String content) {
		// 限流检查
		if (!tryAcquireRateLimit(channel)) {
			return CompletableFuture.completedFuture(
				NotifySendResult.failure("通知渠道限流触发，请稍后重试: " + channel.getName(), channel.getName())
			);
		}

		NotifyChannelStrategy strategy = channelStrategies.get(channel);
		if (strategy == null) {
			return CompletableFuture.completedFuture(
				NotifySendResult.failure("通知渠道未配置: " + channel.getName(), channel.getName())
			);
		}

		// 如果没有配置并行线程池，降级为串行发送
		if (parallelExecutor == null) {
			return CompletableFuture.completedFuture(strategy.batchSend(receivers, title, content));
		}

		// 并行发送：为每个接收者创建异步任务
		List<CompletableFuture<NotifySendResult>> futures = receivers.stream()
			.map(receiver -> CompletableFuture.supplyAsync(
				() -> strategy.send(receiver, title, content),
				parallelExecutor
			))
			.collect(Collectors.toList());

		// 等待所有任务完成，汇总结果
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
	 * 尝试获取渠道限流许可
	 *
	 * @param channel 通知渠道
	 * @return true 表示允许发送，false 表示被限流
	 */
	private boolean tryAcquireRateLimit(NotifyChannel channel) {
		if (rateLimiterManager == null) {
			return true; // 未配置限流管理器，直接放行
		}
		return rateLimiterManager.tryAcquire(channel);
	}

	/**
	 * 尝试获取渠道熔断许可（P0-3）
	 *
	 * @param channel 通知渠道
	 * @return true 表示允许通过，false 表示被熔断
	 */
	private boolean tryAcquireCircuitBreaker(NotifyChannel channel) {
		if (circuitBreakerRegistry == null) {
			return true;
		}
		return circuitBreakerRegistry.tryAcquire(channel);
	}

	/**
	 * 记录渠道发送成功（P0-3 熔断器）
	 */
	private void recordCircuitSuccess(NotifyChannel channel) {
		if (circuitBreakerRegistry != null) {
			circuitBreakerRegistry.recordSuccess(channel);
		}
	}

	/**
	 * 记录渠道发送失败（P0-3 熔断器）
	 */
	private void recordCircuitFailure(NotifyChannel channel) {
		if (circuitBreakerRegistry != null) {
			circuitBreakerRegistry.recordFailure(channel);
		}
	}

	/**
	 * 校验邮箱格式是否合法。
	 *
	 * @param email 邮箱地址
	 * @return 格式合法返回 true，否则返回 false
	 */
	public static boolean isValidEmail(String email) {
		if (email == null || email.isBlank()) {
			return false;
		}
		return EMAIL_PATTERN.matcher(email).matches();
	}
}
