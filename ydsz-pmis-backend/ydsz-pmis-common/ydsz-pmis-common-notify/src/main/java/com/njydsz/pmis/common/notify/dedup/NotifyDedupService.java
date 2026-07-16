package com.njydsz.pmis.common.notify.dedup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.common.notify.config.NotifyProperties;

/**
 * 通知去重与幂等服务（P3-13）
 *
 * <p>防止相同内容的邮件在短时间内重复发送。
 * 基于 Redis 实现分布式去重，内存降级方案使用本地 ConcurrentHashMap。
 *
 * <p><b>去重策略：</b>
 * <ul>
 *   <li>对 receiver+title+content 计算 SHA-256 指纹</li>
 *   <li>在配置的时间窗口内（默认 5 分钟），相同指纹只发送一次</li>
 *   <li>窗口过期后自动放行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class NotifyDedupService {

	private static final Logger log = LoggerFactory.getLogger(NotifyDedupService.class);

	private final NotifyProperties properties;
	private final StringRedisTemplate redisTemplate;

	/** 内存降级去重缓存：key=fingerprint, value=过期时间戳 */
	private final ConcurrentMap<String, Long> memoryDedup = new ConcurrentHashMap<>();

	public NotifyDedupService(NotifyProperties properties, StringRedisTemplate redisTemplate) {
		this.properties = properties;
		this.redisTemplate = redisTemplate;
	}

	/**
	 * 判断是否启用去重
	 */
	public boolean isDedupEnabled() {
		return properties.getDedup() != null && properties.getDedup().isEnabled();
	}

	/**
	 * 检查是否为重复消息
	 *
	 * @param receiver 接收者
	 * @param title    标题
	 * @param content  内容
	 * @return true 表示是重复消息（应跳过发送）
	 */
	public boolean isDuplicate(String receiver, String title, String content) {
		if (!isDedupEnabled()) {
			return false;
		}
		String fingerprint = computeFingerprint(receiver, title, content);
		int windowSeconds = properties.getDedup().getWindowSeconds();
		String redisKey = properties.getDedup().getRedisKeyPrefix() + fingerprint;

		if (redisTemplate != null) {
			try {
				Boolean absent = redisTemplate.opsForValue().setIfAbsent(
						redisKey, "1", Duration.ofSeconds(windowSeconds));
				if (Boolean.TRUE.equals(absent)) {
					return false;
				}
				log.debug("[NotifyDedupService] 去重命中(Redis): fp={}", fingerprint);
				return true;
			} catch (Exception e) {
				log.debug("[NotifyDedupService] Redis 去重失败，降级为内存: {}", e.getMessage());
			}
		}

		// 内存降级
		long now = System.currentTimeMillis();
		long expireAt = now + windowSeconds * 1000L;
		Long existing = memoryDedup.putIfAbsent(fingerprint, expireAt);
		if (existing != null) {
			if (existing > now) {
				log.debug("[NotifyDedupService] 去重命中(Memory): fp={}", fingerprint);
				return true;
			}
			memoryDedup.put(fingerprint, expireAt);
		}
		return false;
	}

	/**
	 * 手动清除去重记录
	 */
	public void clearDedup(String receiver, String title, String content) {
		String fingerprint = computeFingerprint(receiver, title, content);
		String redisKey = properties.getDedup().getRedisKeyPrefix() + fingerprint;
		if (redisTemplate != null) {
			try {
				redisTemplate.delete(redisKey);
			} catch (Exception ignored) {
			}
		}
		memoryDedup.remove(fingerprint);
	}

	/**
	 * 计算去重指纹
	 */
	private String computeFingerprint(String receiver, String title, String content) {
		try {
			String raw = (receiver != null ? receiver : "") + "|"
					+ (title != null ? title : "") + "|"
					+ (content != null ? content : "");
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash).substring(0, 32);
		} catch (Exception e) {
			return String.valueOf((receiver + title + content).hashCode());
		}
	}
}
