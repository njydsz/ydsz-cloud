package com.remisoft.common.notify.tracking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import com.remisoft.common.json.YdszJson;
import com.remisoft.common.notify.config.NotifyProperties;

/**
 * 邮件追踪与已读回执服务（P1-5 + P3-1 增强）
 *
 * <p>在 HTML 邮件末尾注入 1&times;1 透明追踪像素，当收件人打开邮件时，
 * 邮件客户端自动请求追踪 URL，服务端记录打开事件。
 *
 * <p><b>P3-1 增强：</b>支持多种追踪事件类型（SENT/DELIVERED/OPENED/CLICKED/BOUNCED/COMPLAINED），
 * 通过 Webhook 接收第三方服务商的事件推送。
 *
 * <p><b>追踪像素原理：</b>
 * <pre>{@code
 * <img src="https://remi.remi.com/api/notify/track/open?mid=abc123"
 *      width="1" height="1" alt="" style="display:none"/>
 * }</pre>
 *
 * <p>追踪数据存储在 Redis 中，支持以下指标：
 * <ul>
 *   <li>邮件打开次数（总打开 / 唯一打开）</li>
 *   <li>首次打开时间</li>
 *   <li>最近打开时间</li>
 *   <li>打开设备/客户端（通过 User-Agent）</li>
 *   <li>邮件投递状态（P3-1：sent/delivered/bounced/complained）</li>
 *   <li>点击事件（P3-1）</li>
 * </ul>
 *
 * <p>当 Redis 不可用时，降级为内存计数器。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class EmailTrackingService {

	private static final Logger log = LoggerFactory.getLogger(EmailTrackingService.class);

	private static final String REDIS_KEY_OPEN_COUNT = "notify:track:open:count:";
	private static final String REDIS_KEY_OPEN_FIRST = "notify:track:open:first:";
	private static final String REDIS_KEY_OPEN_LAST = "notify:track:open:last:";
	private static final String REDIS_KEY_OPEN_UA = "notify:track:open:ua:";
	private static final String REDIS_KEY_EVENT = "notify:track:event:";
	private static final String REDIS_KEY_CLICK_COUNT = "notify:track:click:count:";

	/** Redis Key 过期时间（30 天） */
	private static final Duration REDIS_TTL = Duration.ofDays(30);

	/** 追踪像素 HTML 片段模板 */
	private static final String PIXEL_HTML_TEMPLATE =
			"<img src=\"%s?mid=%s\" width=\"1\" height=\"1\" alt=\"\" "
					+ "style=\"display:none;border:0;outline:none;\"/>";

	private final NotifyProperties properties;
	private final StringRedisTemplate redisTemplate;

	/** 内存降级计数器 */
	private final ConcurrentMap<String, AtomicLong> memoryOpenCount = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Long> memoryFirstOpen = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Long> memoryLastOpen = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, AtomicLong> memoryClickCount = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> memoryDeliveryStatus = new ConcurrentHashMap<>();

	public EmailTrackingService(NotifyProperties properties, StringRedisTemplate redisTemplate) {
		this.properties = properties;
		this.redisTemplate = redisTemplate;
	}

	/**
	 * 判断追踪功能是否启用
	 *
	 * @return true 表示追踪已启用且配置完整
	 */
	public boolean isTrackingEnabled() {
		NotifyProperties.EmailConfig email = properties.getEmail();
		return email != null
				&& email.getTracking() != null
				&& email.getTracking().isEnabled()
				&& StringUtils.hasText(email.getTracking().getPixelBaseUrl());
	}

	/**
	 * 为邮件内容注入追踪像素
	 *
	 * <p>仅在 HTML 邮件且追踪功能启用时注入。
	 * 像素插入在 {@code </body>} 标签前，若无 body 标签则追加到内容末尾。
	 *
	 * @param content    原始 HTML 邮件内容
	 * @param messageId  邮件唯一标识（用于追踪关联）
	 * @return 注入追踪像素后的 HTML 内容
	 */
	public String injectTrackingPixel(String content, String messageId) {
		if (!isTrackingEnabled() || !StringUtils.hasText(content) || !StringUtils.hasText(messageId)) {
			return content;
		}
		String pixelUrl = properties.getEmail().getTracking().getPixelBaseUrl();
		String trackingId = generateTrackingId(messageId);
		String pixelHtml = String.format(PIXEL_HTML_TEMPLATE, pixelUrl, trackingId);

		if (content.contains("</body>")) {
			return content.replace("</body>", pixelHtml + "</body>");
		}
		return content + pixelHtml;
	}

	/**
	 * 记录邮件打开事件
	 *
	 * @param trackingId 追踪 ID（从像素 URL 参数中获取）
	 * @param userAgent  打开设备的 User-Agent（可为空）
	 */
	public void recordOpen(String trackingId, String userAgent) {
		long now = System.currentTimeMillis();

		if (redisTemplate != null) {
			try {
				redisTemplate.opsForValue().increment(REDIS_KEY_OPEN_COUNT + trackingId);
				redisTemplate.opsForValue().setIfAbsent(REDIS_KEY_OPEN_FIRST + trackingId,
						String.valueOf(now), REDIS_TTL);
				redisTemplate.opsForValue().set(REDIS_KEY_OPEN_LAST + trackingId,
						String.valueOf(now), REDIS_TTL);
				if (StringUtils.hasText(userAgent)) {
					redisTemplate.opsForValue().set(REDIS_KEY_OPEN_UA + trackingId,
							userAgent, REDIS_TTL);
				}
				log.debug("[EmailTrackingService] 打开事件已记录(Redis): trackingId={}, userAgent={}",
						trackingId, userAgent);
				return;
			} catch (Exception e) {
				log.warn("[EmailTrackingService] Redis 记录打开事件失败，降级为内存: {}", e.getMessage());
			}
		}

		// 内存降级
		memoryOpenCount.computeIfAbsent(trackingId, k -> new AtomicLong(0)).incrementAndGet();
		memoryFirstOpen.putIfAbsent(trackingId, now);
		memoryLastOpen.put(trackingId, now);
		log.debug("[EmailTrackingService] 打开事件已记录(Memory): trackingId={}", trackingId);
	}

	/**
	 * 获取邮件打开次数
	 *
	 * @param trackingId 追踪 ID
	 * @return 打开次数（0 表示未打开）
	 */
	public long getOpenCount(String trackingId) {
		if (redisTemplate != null) {
			try {
				String count = redisTemplate.opsForValue().get(REDIS_KEY_OPEN_COUNT + trackingId);
				return count != null ? Long.parseLong(count) : 0;
			} catch (Exception e) {
				log.debug("[EmailTrackingService] Redis 查询打开次数失败: {}", e.getMessage());
			}
		}
		AtomicLong count = memoryOpenCount.get(trackingId);
		return count != null ? count.get() : 0;
	}

	/**
	 * 生成追踪 ID（基于 messageId 的 SHA-256 短摘要）
	 *
	 * @param messageId 邮件消息 ID
	 * @return 16 字符的追踪 ID
	 */
	private String generateTrackingId(String messageId) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(messageId.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash).substring(0, 16);
		} catch (Exception e) {
			return messageId.length() > 16 ? messageId.substring(0, 16) : messageId;
		}
	}

	// ==================== P3-1 增强事件追踪 ====================

	/**
	 * 邮件追踪事件类型（P3-1）
	 */
	public enum TrackingEvent {
		SENT, DELIVERED, OPENED, CLICKED, BOUNCED, COMPLAINED, UNSUBSCRIBED
	}

	/**
	 * 记录追踪事件（P3-1）
	 *
	 * <p>支持通过 Webhook 接收第三方邮件服务商的事件推送。
	 *
	 * @param trackingId 追踪 ID
	 * @param event      事件类型
	 * @param userAgent  User-Agent（可为 null）
	 * @param metadata   附加元数据（可为 null）
	 */
	public void recordEvent(String trackingId, TrackingEvent event, String userAgent,
							Map<String, String> metadata) {
		if (trackingId == null || event == null) {
			return;
		}
		long now = System.currentTimeMillis();

		switch (event) {
			case OPENED -> recordOpen(trackingId, userAgent);
			case CLICKED -> recordClick(trackingId);
			case BOUNCED, COMPLAINED, UNSUBSCRIBED -> recordDeliveryStatus(trackingId, event.name());
			case DELIVERED -> recordDeliveryStatus(trackingId, "DELIVERED");
			case SENT -> recordDeliveryStatus(trackingId, "SENT");
		}

		// 存储完整事件记录
		if (redisTemplate != null) {
			try {
				String eventJson = buildEventJson(event, now, userAgent, metadata);
				redisTemplate.opsForList().rightPush(REDIS_KEY_EVENT + trackingId, eventJson);
				redisTemplate.expire(REDIS_KEY_EVENT + trackingId, REDIS_TTL);
			} catch (Exception e) {
				log.debug("[EmailTrackingService] Redis 存储事件失败: {}", e.getMessage());
			}
		}

		log.info("[EmailTrackingService] 追踪事件记录: trackingId={}, event={}", trackingId, event);
	}

	/**
	 * 记录点击事件（P3-1）
	 *
	 * @param trackingId 追踪 ID
	 */
	public void recordClick(String trackingId) {
		if (redisTemplate != null) {
			try {
				redisTemplate.opsForValue().increment(REDIS_KEY_CLICK_COUNT + trackingId);
				return;
			} catch (Exception e) {
				log.debug("[EmailTrackingService] Redis 记录点击失败: {}", e.getMessage());
			}
		}
		memoryClickCount.computeIfAbsent(trackingId, k -> new AtomicLong(0)).incrementAndGet();
	}

	/**
	 * 获取邮件点击次数（P3-1）
	 *
	 * @param trackingId 追踪 ID
	 * @return 点击次数
	 */
	public long getClickCount(String trackingId) {
		if (redisTemplate != null) {
			try {
				String count = redisTemplate.opsForValue().get(REDIS_KEY_CLICK_COUNT + trackingId);
				return count != null ? Long.parseLong(count) : 0;
			} catch (Exception e) {
				log.debug("[EmailTrackingService] Redis 查询点击次数失败: {}", e.getMessage());
			}
		}
		AtomicLong count = memoryClickCount.get(trackingId);
		return count != null ? count.get() : 0;
	}

	/**
	 * 记录投递状态（P3-1）
	 *
	 * @param trackingId 追踪 ID
	 * @param status     投递状态
	 */
	private void recordDeliveryStatus(String trackingId, String status) {
		if (redisTemplate != null) {
			try {
				redisTemplate.opsForValue().set(REDIS_KEY_EVENT + "status:" + trackingId, status, REDIS_TTL);
				return;
			} catch (Exception e) {
				log.debug("[EmailTrackingService] Redis 记录投递状态失败: {}", e.getMessage());
			}
		}
		memoryDeliveryStatus.put(trackingId, status);
	}

	/**
	 * 获取投递状态（P3-1）
	 *
	 * @param trackingId 追踪 ID
	 * @return 投递状态，未记录返回 null
	 */
	public String getDeliveryStatus(String trackingId) {
		if (redisTemplate != null) {
			try {
				return redisTemplate.opsForValue().get(REDIS_KEY_EVENT + "status:" + trackingId);
			} catch (Exception e) {
				log.debug("[EmailTrackingService] Redis 查询投递状态失败: {}", e.getMessage());
			}
		}
		return memoryDeliveryStatus.get(trackingId);
	}

	/**
	 * 构建事件 JSON 字符串
	 */
	private String buildEventJson(TrackingEvent event, long timestamp, String userAgent,
								Map<String, String> metadata) {
		Map<String, Object> eventMap = new HashMap<>();
		eventMap.put("event", event.name());
		eventMap.put("timestamp", timestamp);
		if (userAgent != null) {
			eventMap.put("userAgent", userAgent);
		}
		if (metadata != null && !metadata.isEmpty()) {
			eventMap.put("metadata", metadata);
		}
		return YdszJson.toJson(eventMap);
	}
}
