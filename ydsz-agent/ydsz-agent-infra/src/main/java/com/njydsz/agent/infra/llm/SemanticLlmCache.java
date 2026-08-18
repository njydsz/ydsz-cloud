package com.njydsz.agent.infra.llm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageRole;
import com.njydsz.common.json.YdszJson;

/**
 * LLM 响应缓存（基于 Redis）
 *
 * <p><b>实现说明（P0/P1 修复）</b>：本类名为 "Semantic"（语义），但当前实现为<b>精确哈希匹配</b>缓存：
 * 以 (model + system prompt + 最新 user message) 的 SHA-256 作为缓存 key，相同输入才可命中， 未引入 embedding
 * 相似度检索（避免每次查询额外调用 embedding 服务的成本）。如需真正的语义命中， 需引入向量索引，属后续增强项。
 *
 * <p>缓存策略：
 *
 * <ul>
 *   <li>以 (model + system prompt + 最新 user message) 的 SHA-256 作为缓存 key
 *   <li>命中缓存时直接从 Redis 返回 JSON 序列化的 {@link CachedLlmResponse}，跳过 LLM 调用
 *   <li>支持 TTL 过期与 LRU 容量控制（Redis ZSET 索引：命中刷新 score，超容量淘汰最旧条目）
 *   <li>仅对 temperature=0 的确定性请求启用缓存（高 temperature 结果随机性高）
 * </ul>
 *
 * <p><b>线程安全</b>：Redis 操作原子，{@link StringRedisTemplate} 线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SemanticLlmCache {

  private static final Logger LOG = LoggerFactory.getLogger(SemanticLlmCache.class);

  /** LRU 索引 key（ZSET：member=缓存 key，score=最近访问时间戳毫秒） */
  private static final String LRU_INDEX_KEY = SemanticCacheConfig.CACHE_KEY_PREFIX + "lru-index";

  /** 超容量后一次性多淘汰的条目数（避免每次写入都触发淘汰） */
  private static final int EVICT_MARGIN = 10;

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
   * <p>生成缓存 key 并从 Redis 反序列化缓存的响应数据。 缓存不存在或反序列化失败时返回 null。
   *
   * @param model 模型名称
   * @param systemPrompt 系统提示词
   * @param userMessage 最新用户消息
   * @return 缓存的响应；未命中时返回 null
   */
  public CachedLlmResponse get(String model, String systemPrompt, String userMessage) {
    String key = buildKey(model, systemPrompt, userMessage);
    try {
      String json = redisTemplate.opsForValue().get(key);
      if (json != null) {
        // 命中刷新 LRU 访问时间
        redisTemplate.opsForZSet().add(LRU_INDEX_KEY, key, Instant.now().toEpochMilli());
        LOG.debug("[SemanticCache] 缓存命中: key={}", key.substring(0, 16) + "...");
        return YdszJson.fromJson(json, CachedLlmResponse.class);
      }
    } catch (Exception e) {
      LOG.warn("[SemanticCache] 缓存读取失败: {}", e.getMessage());
    }
    return null;
  }

  /**
   * 将 LLM 响应写入缓存。
   *
   * <p>写入后维护 LRU 索引：新增/刷新 score，超出 {@link #maxCacheSize} 时淘汰最旧条目。
   *
   * @param model 模型名称
   * @param systemPrompt 系统提示词
   * @param userMessage 用户消息
   * @param response LLM 响应内容
   * @param provider Provider 标识
   */
  public void put(
      String model, String systemPrompt, String userMessage, String response, String provider) {
    String key = buildKey(model, systemPrompt, userMessage);
    try {
      CachedLlmResponse cached =
          new CachedLlmResponse(response, provider, Instant.now().toEpochMilli());
      String json = YdszJson.toJson(cached);
      redisTemplate.opsForValue().set(key, json, ttl);
      // 维护 LRU 索引并执行容量淘汰（P1 修复：原 maxCacheSize 参数从未使用，属死代码）
      redisTemplate.opsForZSet().add(LRU_INDEX_KEY, key, Instant.now().toEpochMilli());
      evictIfOverCapacity();
      LOG.debug(
          "[SemanticCache] 缓存写入: key={}, ttl={}min", key.substring(0, 16) + "...", ttl.toMinutes());
    } catch (Exception e) {
      LOG.warn("[SemanticCache] 缓存写入失败: {}", e.getMessage());
    }
  }

  /**
   * 检查缓存是否可能对指定请求启用。
   *
   * <p>仅当请求为确定性调用（temperature 接近 0）且无 tools 时才启用缓存。
   *
   * @param temperature 采样温度
   * @param hasTools 是否包含工具定义
   * @return true 表示允许缓存
   */
  public static boolean isCacheable(double temperature, boolean hasTools) {
    // temperature=0 的确定性请求才缓存；有工具调用的请求不缓存（结果可能不同）
    return temperature <= 0.01 && !hasTools;
  }

  /**
   * 构建缓存 key。
   *
   * <p>以 model + systemPrompt + userMessage 的 SHA-256 摘要作为 key， 避免超长消息作为 key。
   *
   * <p>可见性为 public：供 {@code CachedLlmClient} 用作缓存击穿防护的互斥键。
   *
   * @param model 模型名称
   * @param systemPrompt 系统提示词
   * @param userMessage 用户消息内容
   * @return Redis 缓存 key
   */
  public String buildKey(String model, String systemPrompt, String userMessage) {
    String raw =
        model
            + SemanticCacheConfig.KEY_SEPARATOR
            + (systemPrompt != null ? systemPrompt : "")
            + SemanticCacheConfig.KEY_SEPARATOR
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
   * <p>P0 修复：原实现以 {@code instanceof Map} 解析消息，但实际消息类型为 {@link ChatMessage} 领域对象，导致永远提取不到内容、缓存 key
   * 恒定、跨会话串流。 现直接遍历 {@link ChatMessage}，按角色提取最后一条 system 与最后一条 user 内容。
   *
   * @param messages 聊天消息列表
   * @return Map-entry 形式：key=systemPrompt, value=userMessage；提取失败时返回 null
   */
  public static Map.Entry<String, String> extractCacheableContent(List<ChatMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return null;
    }
    String systemPrompt = "";
    String latestUserMessage = "";
    for (ChatMessage msg : messages) {
      if (msg == null || msg.getContent() == null) {
        continue;
      }
      if (msg.getRole() == MessageRole.SYSTEM) {
        systemPrompt = msg.getContent();
      } else if (msg.getRole() == MessageRole.USER) {
        latestUserMessage = msg.getContent();
      }
    }
    return Map.entry(systemPrompt, latestUserMessage);
  }

  /**
   * LRU 容量淘汰：当索引条目数超过 {@link #maxCacheSize} 时，删除最旧条目及其对应缓存。
   *
   * <p>通过 ZSET {@code popMin} 原子取出最旧的 (N + {@value #EVICT_MARGIN}) 条 member 并删除缓存 key。
   */
  private void evictIfOverCapacity() {
    if (maxCacheSize <= 0) {
      return;
    }
    try {
      Long size = redisTemplate.opsForZSet().zCard(LRU_INDEX_KEY);
      if (size == null || size <= maxCacheSize) {
        return;
      }
      long toRemove = size - maxCacheSize + EVICT_MARGIN;
      Set<ZSetOperations.TypedTuple<String>> oldest =
          redisTemplate.opsForZSet().popMin(LRU_INDEX_KEY, toRemove);
      if (oldest != null) {
        for (ZSetOperations.TypedTuple<String> tuple : oldest) {
          String member = tuple.getValue();
          if (member != null) {
            redisTemplate.delete(member);
          }
        }
        LOG.info("[SemanticCache] LRU 淘汰完成: 共删除 {} 条", oldest.size());
      }
    } catch (Exception e) {
      LOG.warn("[SemanticCache] LRU 淘汰失败: {}", e.getMessage());
    }
  }

  /**
   * 缓存值对象（存储在 Redis 中的 JSON 结构）。
   *
   * @param content LLM 响应内容
   * @param provider LLM Provider 标识
   * @param cachedAt 缓存时间戳（毫秒）
   */
  public record CachedLlmResponse(String content, String provider, long cachedAt) {}
}
