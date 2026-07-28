package com.njydsz.message.server.service.impl;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-17: Push Token 生命周期管理。
 *
 * <p>管理移动端推送 Token 的注册、更新、查询和失效，支持多设备多平台。
 *
 * <p>功能特性：
 * <ul>
 *   <li>多设备支持：一个用户可关联多个设备 Token</li>
 *   <li>多平台支持：iOS (APNs) / Android (FCM/华为/小米/OPPO/VIVO)</li>
 *   <li>自动失效：Token 超过 30 天未活跃自动清除</li>
 *   <li>失效标记：推送失败时标记 Token 为无效，后续不再推送</li>
 * </ul>
 *
 * <p>Redis 数据结构：
 * <ul>
 *   <li>Hash: {@code push:tokens:{userId}} → field=deviceId, value=token:platform</li>
 *   <li>Set: {@code push:invalid:{userId}} → 已失效的 Token 集合</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushTokenManager {

    private final RedisService redisService;

    private static final String TOKENS_KEY_PREFIX = "push:tokens:";
    private static final String INVALID_KEY_PREFIX = "push:invalid:";
    private static final long TOKEN_TTL_DAYS = 30L;

    /**
     * 注册/更新 Push Token。
     *
     * @param userId   用户 ID
     * @param deviceId 设备 ID
     * @param token    推送 Token
     * @param platform 平台
     */
    public void registerToken(String userId, String deviceId, String token, String platform) {
        String key = TOKENS_KEY_PREFIX + userId;
        // OD-6: 改用 JSON 存储，消除 token:platform 字符串拼接脆弱性
        Map<String, String> tokenInfo = new HashMap<>();
        tokenInfo.put("token", token);
        tokenInfo.put("platform", platform != null ? platform : "UNKNOWN");
        redisService.hSet(key, deviceId, YdszJson.toJson(tokenInfo));
        redisService.expire(key, Duration.ofDays(TOKEN_TTL_DAYS));
        redisService.sRem(INVALID_KEY_PREFIX + userId, token);
        log.info("[PushToken] Token 注册: userId={} deviceId={} platform={}", userId, deviceId, platform);
    }

    /**
     * 获取用户所有有效 Token。
     *
     * @param userId 用户 ID
     * @return deviceId → token:platform 的 Map
     */
    public Map<String, String> getValidTokens(String userId) {
        String key = TOKENS_KEY_PREFIX + userId;
        Map<String, String> raw = redisService.hGetAll(key, String.class);
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Set<String> invalidTokens = redisService.sMembers(INVALID_KEY_PREFIX + userId, String.class);
        Map<String, String> result = new HashMap<>();
        raw.forEach((deviceId, tokenInfoJson) -> {
            // OD-6: 从 JSON 解析 token 和 platform
            try {
                Map<String, Object> info = YdszJson.fromJsonToMap(String.valueOf(tokenInfoJson), String.class, Object.class);
                String token = info.get("token") != null ? String.valueOf(info.get("token")) : "";
                if (invalidTokens == null || !invalidTokens.contains(token)) {
                    result.put(deviceId.toString(), String.valueOf(tokenInfoJson));
                }
            } catch (Exception e) {
                log.warn("[PushToken] JSON 解析失败: deviceId={} err={}", deviceId, e.getMessage());
            }
        });
        return result;
    }

    /**
     * 标记 Token 为无效。
     *
     * @param userId 用户 ID
     * @param token  失效的 Token
     */
    public void markInvalid(String userId, String token) {
        redisService.sAdd(INVALID_KEY_PREFIX + userId, token);
        log.warn("[PushToken] Token 标记无效: userId={} token={}...",
                userId, token != null && token.length() > 20 ? token.substring(0, 20) : token);
    }

    /**
     * 移除设备 Token。
     *
     * @param userId   用户 ID
     * @param deviceId 设备 ID
     */
    public void removeToken(String userId, String deviceId) {
        String key = TOKENS_KEY_PREFIX + userId;
        redisService.opsForHash().delete(key, deviceId);
        log.info("[PushToken] Token 移除: userId={} deviceId={}", userId, deviceId);
    }

    /**
     * 获取用户在线设备数。
     *
     * @param userId 用户 ID
     * @return 有效 Token 数
     */
    public int getDeviceCount(String userId) {
        return getValidTokens(userId).size();
    }
}
