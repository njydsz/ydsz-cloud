package com.njydsz.agent.infra.llm;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageRole;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.security.DigestUtils;

/**
 * LLM 响应双层缓存（L1 YdszCache + L2 Redis）
 *
 * <p><b>实现说明</b>：本类名为 "Semantic"（语义），但当前实现为<b>精确哈希匹配</b>缓存： 以 (model + system prompt + 最新 user message)
 * 的 SHA-256 作为缓存 key，相同输入才可命中， 未引入 embedding 相似度检索（避免每次查询额外调用 embedding 服务的成本）。如需真正的语义命中，
 * 需引入向量索引，属后续增强项。
 *
 * <p><b>双层缓存架构（P1-10）</b>：
 *
 * <ul>
 *   <li>L1（YdszCache 本地缓存）：进程内高速缓存，最大 200 条，写入后 5 分钟过期。热点 key 亚毫秒级命中，避免 Redis 网络开销
 *   <li>L2（Redis 分布式缓存）：跨进程共享，支持 TTL 过期与 LRU 容量控制（ZSET 索引：命中刷新 score，超容量淘汰最旧条目）
 *   <li>读取策略：L1 → L2，L2 命中后回填 L1；写入策略：同时写入 L1 和 L2
 * </ul>
 *
 * <p>缓存策略：
 *
 * <ul>
 *   <li>以 (model + system prompt + 最新 user message) 的 SHA-256 作为缓存 key
 *   <li>命中缓存时直接返回 JSON 序列化的 {@link CachedLlmResponse}，跳过 LLM 调用
 *   <li>仅对 temperature=0 的确定性请求启用缓存（高 temperature 结果随机性高）
 * </ul>
 *
 * <p><b>线程安全</b>：YdszCache 与 {@link StringRedisTemplate} 均为线程安全实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SemanticLlmCache {

  /** LRU 索引 key（ZSET：member=缓存 key，score=最近访问时间戳毫秒） */
  private static final String LRU_INDEX_KEY = SemanticCacheConfig.CACHE_KEY_PREFIX + "lru-index";

  /** 超容量后一次性多淘汰的条目数（避免每次写入都触发淘汰） */
  private static final int EVICT_MARGIN = 10;

  /** L1 本地缓存最大条目数 */
  private static final int L1_MAX_SIZE = 200;

  /** L1 本地缓存写入后过期时间（分钟） */
  private static final int L1_EXPIRE_MINUTES = 5;

  /** 缓存名称（用于健康检查和监控） */
  private static final String CACHE_NAME = "agent:semantic-llm";

  private final StringRedisTemplate redisTemplate;
  private final Duration ttl;
  private final int maxCacheSize;

  /** L1 本地缓存（YdszCache） — 进程内高速缓存，降低热点 key 的 Redis 网络开销 */
  private final Cache<String, CachedLlmResponse> l1Cache;

  public SemanticLlmCache(StringRedisTemplate redisTemplate, Duration ttl, int maxCacheSize) {
    this.redisTemplate = redisTemplate;
    this.ttl = ttl;
    this.maxCacheSize = maxCacheSize;
    this.l1Cache =
        YdszCache.newBuilder()
            .name(CACHE_NAME)
            .maximumSize(L1_MAX_SIZE)
            .expireAfterWrite(L1_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .recordStats()
            .build();
  }

  /**
   * 尝试获取缓存的 LLM 响应。
   *
   * <p>采用 L1（Caffeine 本地）→ L2（Redis 分布式）双层查询策略： L1 命中直接返回（亚毫秒级）；L1 未命中查 L2，命中后回填 L1。
   *
   * @param model 模型名称
   * @param systemPrompt 系统提示词
   * @param userMessage 最新用户消息
   * @return 缓存的响应；未命中时返回 null
   */
  public CachedLlmResponse get(String model, String systemPrompt, String userMessage) {
    String key = buildKey(model, systemPrompt, userMessage);
    // L1: 本地 Caffeine 缓存查询（无网络开销）
    CachedLlmResponse l1Result = l1Cache.getIfPresent(key);
    if (l1Result != null) {
      log.debug("[SemanticCache] L1 命中: key={}", key.substring(0, 16) + "...");
      return l1Result;
    }
    // L2: Redis 分布式缓存查询
    try {
      String json = redisTemplate.opsForValue().get(key);
      if (json != null) {
        // 命中刷新 LRU 访问时间
        redisTemplate.opsForZSet().add(LRU_INDEX_KEY, key, Instant.now().toEpochMilli());
        CachedLlmResponse result = YdszJson.fromJson(json, CachedLlmResponse.class);
        // L2 命中后回填 L1，加速后续同进程请求
        if (result != null) {
          l1Cache.put(key, result);
        }
        log.debug("[SemanticCache] L2 命中: key={}", key.substring(0, 16) + "...");
        return result;
      }
    } catch (Exception e) {
      log.warn("[SemanticCache] L2 缓存读取失败", e);
    }
    return null;
  }

  /**
   * 将 LLM 响应写入缓存。
   *
   * <p>同时写入 L1（Caffeine 本地）和 L2（Redis 分布式）： L1 提供进程内高速读取，L2 提供跨进程共享与持久化。 写入后维护 LRU 索引：新增/刷新 score，超出 {@link #maxCacheSize} 时淘汰最旧条目。
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
    CachedLlmResponse cached =
        new CachedLlmResponse(response, provider, Instant.now().toEpochMilli());
    // L1: 写入本地 Caffeine 缓存
    l1Cache.put(key, cached);
    // L2: 写入 Redis 分布式缓存
    try {
      String json = YdszJson.toJson(cached);
      redisTemplate.opsForValue().set(key, json, ttl);
      // 维护 LRU 索引并执行容量淘汰（P1 修复：原 maxCacheSize 参数从未使用，属死代码）
      redisTemplate.opsForZSet().add(LRU_INDEX_KEY, key, Instant.now().toEpochMilli());
      evictIfOverCapacity();
      log.debug(
          "[SemanticCache] 缓存写入: key={}, ttl={}min", key.substring(0, 16) + "...", ttl.toMinutes());
    } catch (Exception e) {
      log.warn("[SemanticCache] L2 缓存写入失败", e);
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
      return SemanticCacheConfig.CACHE_KEY_PREFIX + DigestUtils.sha256Hex(raw);
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
        log.info("[SemanticCache] LRU 淘汰完成: 共删除 {} 条", oldest.size());
      }
    } catch (Exception e) {
      log.warn("[SemanticCache] LRU 淘汰失败", e);
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
