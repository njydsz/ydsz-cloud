package com.njydsz.pmis.common.notify.fallback;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.pmis.common.notify.config.NotifyProperties;
import com.njydsz.pmis.common.notify.core.NotifySendResult;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;

/**
 * 渠道降级管理器（P1-6）
 *
 * <p>当主渠道发送失败时，按配置的降级链自动尝试备用渠道发送。
 * 降级链在 {@code ydsz.notify.fallback.chains} 中配置，
 * key 为主渠道枚举，value 为按优先级排序的备用渠道列表。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   notify:
 *     fallback:
 *       enabled: true
 *       chains:
 *         EMAIL:
 *           - SMS
 *           - INSITE
 *         SMS:
 *           - WECOM
 * }</pre>
 *
 * <p>降级策略：
 * <ul>
 *   <li>仅在主渠道发送失败时触发</li>
 *   <li>按降级链顺序依次尝试，直到成功或全部失败</li>
 *   <li>每次降级尝试记录日志，便于追踪降级路径</li>
 *   <li>最多尝试 3 个备用渠道，防止无限降级</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
public class NotifyFallbackManager {

	private static final Logger log = LoggerFactory.getLogger(NotifyFallbackManager.class);

	/** 最大降级尝试次数 */
	private static final int MAX_FALLBACK_ATTEMPTS = 3;

	private final NotifyProperties properties;
	private final Map<NotifyChannel, NotifyChannelStrategy> strategyMap;

	public NotifyFallbackManager(NotifyProperties properties,
								 List<NotifyChannelStrategy> strategies) {
		this.properties = properties;
		this.strategyMap = new ConcurrentHashMap<>();
		for (NotifyChannelStrategy strategy : strategies) {
			strategyMap.put(strategy.getChannel(), strategy);
		}
	}

	/**
	 * 判断降级功能是否启用
	 *
	 * @return true 表示降级已启用且配置了降级链
	 */
	public boolean isFallbackEnabled() {
		return properties.getFallback() != null
				&& properties.getFallback().isEnabled()
				&& properties.getFallback().getChains() != null
				&& !properties.getFallback().getChains().isEmpty();
	}

	/**
	 * 执行渠道降级发送
	 *
	 * <p>当主渠道发送失败后，按降级链依次尝试备用渠道。
	 *
	 * @param primaryChannel 主渠道
	 * @param receiver       接收者
	 * @param title          标题
	 * @param content        内容
	 * @return 降级发送结果（如果所有降级渠道都失败，返回失败结果）
	 */
	public NotifySendResult fallbackSend(NotifyChannel primaryChannel,
										 String receiver, String title, String content) {
		if (!isFallbackEnabled()) {
			return NotifySendResult.failure("降级未启用且主渠道发送失败", primaryChannel.getName());
		}

		List<NotifyChannel> fallbackChain = properties.getFallback().getChains().get(primaryChannel);
		if (fallbackChain == null || fallbackChain.isEmpty()) {
			return NotifySendResult.failure(
					"主渠道[" + primaryChannel.getName() + "]发送失败且无降级链配置",
					primaryChannel.getName());
		}

		int attempts = 0;
		for (NotifyChannel fallbackChannel : fallbackChain) {
			if (attempts >= MAX_FALLBACK_ATTEMPTS) {
				log.warn("[NotifyFallbackManager] 达到最大降级次数({})，停止降级", MAX_FALLBACK_ATTEMPTS);
				break;
			}
			attempts++;

			NotifyChannelStrategy strategy = strategyMap.get(fallbackChannel);
			if (strategy == null || !strategy.isEnabled()) {
				log.debug("[NotifyFallbackManager] 降级渠道[{}]不可用，跳过", fallbackChannel.getName());
				continue;
			}

			log.info("[NotifyFallbackManager] 主渠道[{}]降级到[{}], receiver={}",
					primaryChannel.getName(), fallbackChannel.getName(), receiver);
			try {
				NotifySendResult result = strategy.send(receiver, title, content);
				if (result.isSuccess()) {
					log.info("[NotifyFallbackManager] 降级发送成功: channel={}, receiver={}",
							fallbackChannel.getName(), receiver);
					return result;
				}
				log.warn("[NotifyFallbackManager] 降级渠道[{}]发送失败: {}",
						fallbackChannel.getName(), result.getErrorMessage());
			} catch (Exception e) {
				log.error("[NotifyFallbackManager] 降级渠道[{}]发送异常: {}",
						fallbackChannel.getName(), e.getMessage(), e);
			}
		}

		return NotifySendResult.failure(
				"所有降级渠道均发送失败, 主渠道=" + primaryChannel.getName(),
				primaryChannel.getName());
	}

	/**
	 * 获取指定渠道的降级链
	 *
	 * @param channel 主渠道
	 * @return 降级渠道列表（可能为空）
	 */
	public List<NotifyChannel> getFallbackChain(NotifyChannel channel) {
		if (!isFallbackEnabled()) {
			return List.of();
		}
		return properties.getFallback().getChains().getOrDefault(channel, List.of());
	}
}
