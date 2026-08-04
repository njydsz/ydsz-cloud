package com.njydsz.message.server.service.impl;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-14: 用户时区感知 DND（Do Not Disturb）服务。
 *
 * <p>支持用户设置免打扰时段，在该时段内非紧急消息将被延迟到窗口结束后发送。
 *
 * <p>功能特性：
 * <ul>
 *   <li>用户级 DND 时段配置（如 22:00-08:00）</li>
 *   <li>时区感知（根据用户时区计算当前时间）</li>
 *   <li>紧急（URGENT）消息不受 DND 限制</li>
 *   <li>DND 期间消息标记为 SCHEDULED，延迟到窗口结束后投递</li>
 * </ul>
 *
 * <p>Redis Key 格式：{@code dnd:{userId}} → "22:00-08:00 Asia/Shanghai"
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DndService {

    private final RedisService redisService;

    /** DND Key 前缀 */
    private static final String DND_KEY_PREFIX = "dnd:";

    /** 默认时区 */
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /** 本地缓存（减少 Redis 访问） */
    private final ConcurrentMap<String, CachedDndConfig> configCache = new ConcurrentHashMap<>();

    /** D-3: 缓存过期时间（毫秒），默认 5 分钟 */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    /**
     * 检查消息是否应被 DND 延迟。
     *
     * @param userId   用户 ID
     * @param priority 消息优先级
     * @return true 表示在 DND 时段内且应延迟，false 表示放行
     */
    public boolean shouldDelay(String userId, String priority) {
        // 紧急消息不受 DND 限制
        if ("URGENT".equalsIgnoreCase(priority)) {
            return false;
        }
        DndConfig config = getDndConfig(userId);
        if (config == null) {
            return false;
        }
        // 根据用户时区计算当前时间
        ZoneId zoneId = ZoneId.of(config.timezone);
        LocalTime now = ZonedDateTime.now(zoneId).toLocalTime();
        boolean inDndWindow = isInWindow(now, config.startTime, config.endTime);
        if (inDndWindow) {
            log.info("[DND] 消息在免打扰时段内,延迟发送: userId={} now={} window={}~{} tz={}",
                    userId, now, config.startTime, config.endTime, config.timezone);
        }
        return inDndWindow;
    }

    /**
     * 设置用户 DND 配置。
     *
     * @param userId    用户 ID
     * @param startTime 开始时间（HH:mm）
     * @param endTime   结束时间（HH:mm）
     * @param timezone  时区 ID
     */
    public void setDnd(String userId, String startTime, String endTime, String timezone) {
        String value = startTime + "-" + endTime + " " + (timezone != null ? timezone : DEFAULT_TIMEZONE);
        redisService.set(DND_KEY_PREFIX + userId, value);
        // 更新本地缓存
        configCache.put(userId, new CachedDndConfig(parseConfig(value), System.currentTimeMillis()));
        log.info("[DND] 用户免打扰配置已设置: userId={} window={}~{} tz={}",
                userId, startTime, endTime, timezone);
    }

    /**
     * 移除用户 DND 配置。
     *
     * @param userId 用户 ID
     */
    public void removeDnd(String userId) {
        redisService.delete(DND_KEY_PREFIX + userId);
        configCache.remove(userId);
        log.info("[DND] 用户免打扰配置已移除: userId={}", userId);
    }

    /**
     * 获取用户 DND 配置（带本地缓存）。
     *
     * @param userId 用户 ID
     * @return DND 配置，null 表示未设置
     */
    private DndConfig getDndConfig(String userId) {
        CachedDndConfig cached = configCache.get(userId);
        // D-3: 检查缓存是否过期
        if (cached != null && (System.currentTimeMillis() - cached.cachedAt) < CACHE_TTL_MS) {
            return cached.config;
        }
        // 缓存过期或不存在，从 Redis 加载
        String value = redisService.get(DND_KEY_PREFIX + userId, String.class);
        if (value == null || value.isBlank()) {
            configCache.remove(userId);
            return null;
        }
        DndConfig config = parseConfig(value);
        if (config != null) {
            configCache.put(userId, new CachedDndConfig(config, System.currentTimeMillis()));
        }
        return config;
    }

    /**
     * 解析 DND 配置字符串。
     *
     * @param value 配置字符串（格式：{@code HH:mm-HH:mm timezone}）
     * @return DND 配置对象
     */
    private DndConfig parseConfig(String value) {
        try {
            String[] parts = value.split(" ");
            String[] times = parts[0].split("-");
            String tz = parts.length > 1 ? parts[1] : DEFAULT_TIMEZONE;
            return new DndConfig(
                    LocalTime.parse(times[0]),
                    LocalTime.parse(times[1]),
                    tz
            );
        } catch (Exception e) {
            log.warn("[DND] 配置解析失败: value={} err={}", value, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 检查当前时间是否在 DND 窗口内。
     *
     * <p>支持跨天窗口（如 22:00-08:00）。
     *
     * @param now       当前时间
     * @param startTime 窗口开始时间
     * @param endTime   窗口结束时间
     * @return true 表示在窗口内
     */
    private boolean isInWindow(LocalTime now, LocalTime startTime, LocalTime endTime) {
        if (startTime.isBefore(endTime)) {
            // 同一天内（如 09:00-18:00）
            return !now.isBefore(startTime) && now.isBefore(endTime);
        } else {
            // 跨天（如 22:00-08:00）
            return !now.isBefore(startTime) || now.isBefore(endTime);
        }
    }

    /** DND 配置内部类 */
    private record DndConfig(LocalTime startTime, LocalTime endTime, String timezone) {}

    /** D-3: 带时间戳的缓存包装类 */
    private record CachedDndConfig(DndConfig config, long cachedAt) {}
}
