package com.remisoft.common.notify.preference;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.notify.enums.NotifyChannel;
import com.remisoft.common.notify.enums.NotifyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 通知偏好管理器（P3-12）
 *
 * <p>管理用户通知偏好的存储、查询和缓存。
 * Redis 持久化 + 本地缓存双层架构。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class NotifyPreferenceManager {

	private static final Logger log = LoggerFactory.getLogger(NotifyPreferenceManager.class);

	private static final String REDIS_KEY_PREFIX = "notify:preference:";
	private static final Duration CACHE_TTL = Duration.ofHours(24);

	private final StringRedisTemplate redisTemplate;
	private final ConcurrentMap<String, NotifyPreference> localCache = new ConcurrentHashMap<>();

	/**
	 * 构造通知偏好管理器
	 *
	 * @param redisTemplate Redis 模板（可为 null，降级为本地缓存）
	 */
	public NotifyPreferenceManager(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	/**
	 * 获取用户通知偏好
	 *
	 * @param userId 用户ID
	 * @return 偏好配置，不存在时返回默认配置
	 */
	public NotifyPreference getPreference(String userId) {
		if (userId == null || userId.isEmpty()) {
			return new NotifyPreference();
		}
		// 先查本地缓存
		NotifyPreference cached = localCache.get(userId);
		if (cached != null) {
			return cached;
		}
		// 查 Redis
		if (redisTemplate != null) {
			try {
				String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + userId);
				if (json != null) {
					NotifyPreference pref = RemiJson.fromJson(json, NotifyPreference.class);
					localCache.put(userId, pref);
					return pref;
				}
			} catch (Exception e) {
				log.debug("[NotifyPreferenceManager] Redis 查询偏好失败: {}", e.getMessage());
			}
		}
		// 返回默认配置
		NotifyPreference defaultPref = new NotifyPreference(userId);
		localCache.put(userId, defaultPref);
		return defaultPref;
	}

	/**
	 * 保存用户通知偏好
	 *
	 * @param preference 偏好配置
	 */
	public void savePreference(NotifyPreference preference) {
		if (preference == null || preference.getUserId() == null) {
			return;
		}
		localCache.put(preference.getUserId(), preference);
		if (redisTemplate != null) {
			try {
				String json = RemiJson.toJson(preference);
				redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + preference.getUserId(), json, CACHE_TTL);
			} catch (Exception e) {
				log.warn("[NotifyPreferenceManager] Redis 保存偏好失败: {}", e.getMessage());
			}
		}
	}

	/**
	 * 检查是否允许发送
	 *
	 * @param userId  用户ID
	 * @param channel 渠道
	 * @param type    通知类型
	 * @return true 表示允许发送
	 */
	public boolean isAllowed(String userId, NotifyChannel channel, NotifyType type) {
		NotifyPreference pref = getPreference(userId);
		if (pref.isDoNotDisturb()) {
			log.debug("[NotifyPreferenceManager] 用户[{}]在免打扰时段", userId);
			return false;
		}
		return pref.isAllowed(channel, type);
	}

	/**
	 * 清除本地缓存
	 */
	public void evictCache(String userId) {
		localCache.remove(userId);
	}
}
