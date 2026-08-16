package com.njydsz.agent.infra.llm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.njydsz.common.json.YdszJson;

/**
 * LLM 语义缓存（基于 Redis）
 *
 * <p>缓存策略：
 * <ul>
 *   <li>以 (model + system prompt + 最新 user message) 的 SHA-256 作为缓存 key</li>
 *   <li>命中缓存时直接从 Redis 返回 JSON 序列化的 {@link CachedLlmResponse}，跳过 LLM 调用</li>
 *   <li>支持 TTL 过期与 LRU 容量控制（通过 Redis key 过期 + 定期清理实现）</li>
 *   <li>仅对 temperature=0 的确定性请求启用缓存（高 temperature 结果随机性高）</li>
 * </ul>
 *
 * <p><b>线程安全</b>：Redis 操作原子，{@link StringRedisTemplate} 线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SemanticLlmCache {

    private static final Logger log = LoggerFactory.getLogger(SemanticLlmCache.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final int maxCacheSize;

    public SemanticLlmCache(StringRedisTemplate redisTemplate, Duration ttl, int maxCacheSize) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
        this.maxCacheSize = maxCacheSize;
    }

    /**
     * 尝试获取缓存的 LLM 响应。
     *
     * <p>生成缓存 key 并从 Redis 反序列化缓存的响应数据。
     * 缓存不存在或反序列化失败时返回 null。
     *
     * @param model         模型名称
     * @param systemPrompt  系统提示词
     * @param userMessage   最新用户消息
     * @return 缓存的响应；未命中时返回 null
     */
    public CachedLlmResponse get(String model, String systemPrompt, String userMessage) {
        String key = buildCacheKey(model, systemPrompt, userMessage);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                log.debug("[SemanticCache] 缓存命中: key={}", key.substring(0, 16) + "...");
                return YdszJson.toObject(json, CachedLlmResponse.class);
            }
        } catch (Exception e) {
            log.warn("[SemanticCache] 缓存读取失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 将 LLM 响应写入缓存。
     *
     * @param model        模型名称
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param response     LLM 响应内容
     * @param provider     Provider 标识
     */
    public void put(String model, String systemPrompt, String userMessage,
                    String response, String provider) {
        String key = buildCacheKey(model, systemPrompt, userMessage);
        try {
            CachedLlmResponse cached = new CachedLlmResponse(
                    response, provider, Instant.now().toEpochMilli());
            String json = YdszJson.toJson(cached);
            redisTemplate.opsForValue().set(key, json, ttl);
            log.debug("[SemanticCache] 缓存写入: key={}, ttl={}min",
                    key.substring(0, 16) + "...", ttl.toMinutes());
        } catch (Exception e) {
            log.warn("[SemanticCache] 缓存写入失败: {}", e.getMessage());
        }
    }

    /**
     * 检查缓存是否可能对指定请求启用。
     *
     * <p>仅当请求为确定性调用（temperature 接近 0）且无 tools 时才启用缓存。
     *
     * @param temperature 采样温度
     * @param hasTools    是否包含工具定义
     * @return true 表示允许缓存
     */
    public static boolean isCacheable(double temperature, boolean hasTools) {
        // temperature=0 的确定性请求才缓存；有工具调用的请求不缓存（结果可能不同）
        return temperature <= 0.01 && !hasTools;
    }

    /**
     * 构建缓存 key。
     *
     * <p>以 model + systemPrompt + userMessage 的 SHA-256 摘要作为 key，
     * 避免超长消息作为 key。
     *
     * @param model        模型名称
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息内容
     * @return Redis 缓存 key
     */
    private String buildCacheKey(String model, String systemPrompt, String userMessage) {
        String raw = model + SemanticCacheConfig.KEY_SEPARATOR
                + (systemPrompt != null ? systemPrompt : "") + SemanticCacheConfig.KEY_SEPARATOR
                + (userMessage != null ? userMessage : "");
        return SemanticCacheConfig.CACHE_KEY_PREFIX + sha256(raw);
    }

    /**
     * 计算 SHA-256 摘要。
     *
     * @param input 输入字符串
     * @return 十六进制摘要（64 字符）
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            // SHA-256 在 JDK 标准中必定可用，此处仅为防御
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 从消息列表中提取 system prompt 和最新的 user message。
     *
     * @param messages 聊天消息列表
     * @return Map-entry 形式：key=systemPrompt, value=userMessage；提取失败时返回 null
     */
    public static Map.Entry<String, String> extractCacheableContent(List<?> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        String systemPrompt = "";
        String latestUserMessage = "";
        for (Object msgObj : messages) {
            if (msgObj instanceof Map<?, ?> msg) {
                Object role = msg.get("role");
                Object content = msg.get("content");
                if ("system".equals(role) && content != null) {
                    systemPrompt = content.toString();
                } else if ("user".equals(role) && content != null) {
                    latestUserMessage = content.toString();
                }
            }
        }
        return Map.entry(systemPrompt, latestUserMessage);
    }

    /**
     * 缓存值对象（存储在 Redis 中的 JSON 结构）。
     *
     * @param content      LLM 响应内容
     * @param provider     LLM Provider 标识
     * @param cachedAt     缓存时间戳（毫秒）
     */
    public record CachedLlmResponse(String content, String provider, long cachedAt) {
    }
}
