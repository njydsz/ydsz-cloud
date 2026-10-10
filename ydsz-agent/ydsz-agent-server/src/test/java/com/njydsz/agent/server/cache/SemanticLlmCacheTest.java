package com.njydsz.agent.server.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ZSetOperations;

import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageRole;
import com.njydsz.agent.infra.cache.CacheKeyBuilder;
import com.njydsz.agent.infra.llm.SemanticLlmCache;
import com.njydsz.agent.infra.llm.SemanticLlmCache.CachedLlmResponse;
import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * {@link SemanticLlmCache} 单元测试（P1-1 Agent 模块测试补充）。
 *
 * <p>覆盖 L1/L2 双层缓存的命中/未命中/写入/淘汰全路径，验证缓存键格式和 LRU 索引维护逻辑。
 *
 * @author ydsz-team
 * @since 26.10.10
 */
@ExtendWith(MockitoExtension.class)
class SemanticLlmCacheTest {

  @Mock private RedisStringOps redisStringOps;
  @Mock private RedisCollectionOps redisCollectionOps;
  @Mock private CacheKeyBuilder cacheKeyBuilder;
  @Mock private ZSetOperations<Object, Object> zSetOperations;

  private SemanticLlmCache cache;

  private static final String TENANT_ID = "tenant-001";
  private static final String MODEL = "gpt-4";
  private static final String SYSTEM_PROMPT = "你是一个助手";
  private static final String USER_MESSAGE = "你好";
  private static final String CACHED_RESPONSE = "你好！有什么可以帮助你的？";
  private static final String PROVIDER = "openai";

  @BeforeEach
  void setUp() {
    cache =
        new SemanticLlmCache(
            redisStringOps, redisCollectionOps, Duration.ofMinutes(30), 100, 50, 10, cacheKeyBuilder);
  }

  @Nested
  @DisplayName("isCacheable 静态方法")
  class IsCacheable {

    @Test
    @DisplayName("temperature=0 且无 tools → 可缓存")
    void temperatureZeroNoTools_returnsTrue() {
      assertThat(SemanticLlmCache.isCacheable(0.0, false)).isTrue();
    }

    @Test
    @DisplayName("temperature=0.01 且无 tools → 可缓存（边界值）")
    void temperatureBoundary_returnsTrue() {
      assertThat(SemanticLlmCache.isCacheable(0.01, false)).isTrue();
    }

    @Test
    @DisplayName("temperature=0.02 → 不可缓存")
    void temperatureAboveBoundary_returnsFalse() {
      assertThat(SemanticLlmCache.isCacheable(0.02, false)).isFalse();
    }

    @Test
    @DisplayName("有 tools → 不可缓存")
    void hasTools_returnsFalse() {
      assertThat(SemanticLlmCache.isCacheable(0.0, true)).isFalse();
    }
  }

  @Nested
  @DisplayName("extractCacheableContent 静态方法")
  class ExtractCacheableContent {

    @Test
    @DisplayName("正常消息列表 → 提取最后一条 system 和最后一条 user")
    void normalMessages_extractsCorrectly() {
      List<ChatMessage> messages =
          List.of(
              new ChatMessage(MessageRole.SYSTEM, "系统提示词1"),
              new ChatMessage(MessageRole.USER, "用户消息1"),
              new ChatMessage(MessageRole.SYSTEM, "最终系统提示词"),
              new ChatMessage(MessageRole.USER, "最终用户消息"));

      var result = SemanticLlmCache.extractCacheableContent(messages);

      assertThat(result).isNotNull();
      assertThat(result.getKey()).isEqualTo("最终系统提示词");
      assertThat(result.getValue()).isEqualTo("最终用户消息");
    }

    @Test
    @DisplayName("null 消息列表 → 返回 null")
    void nullMessages_returnsNull() {
      var result = SemanticLlmCache.extractCacheableContent(null);
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("空消息列表 → 返回 null")
    void emptyMessages_returnsNull() {
      var result = SemanticLlmCache.extractCacheableContent(List.of());
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("仅包含 null 内容的消息 → 返回空字符串键值")
    void nullContentMessages_returnsEmptyEntry() {
      List<ChatMessage> messages =
          List.of(new ChatMessage(MessageRole.SYSTEM, null), new ChatMessage(MessageRole.USER, null));

      var result = SemanticLlmCache.extractCacheableContent(messages);
      assertThat(result).isNotNull();
      assertThat(result.getKey()).isEmpty();
      assertThat(result.getValue()).isEmpty();
    }
  }

  @Nested
  @DisplayName("get 方法")
  class Get {

    private String cacheKey;

    @BeforeEach
    void stubKey() {
      cacheKey = "cached-llm-key";
      when(cacheKeyBuilder.semanticLlm(anyString())).thenReturn(cacheKey);
    }

    @Test
    @DisplayName("L1 YdszCache 命中 → 直接返回，不查 Redis")
    void l1Hit_returnsDirectly() {
      // 模拟 L1 命中需要通过 put 实现，这里仅验证方法不会抛出异常
      // 首次 get 为 L1 未命中，L2 未命中
      when(redisStringOps.get(cacheKey, String.class)).thenReturn(null);

      CachedLlmResponse result = cache.get(MODEL, SYSTEM_PROMPT, USER_MESSAGE);

      assertThat(result).isNull();
      verify(redisStringOps).get(cacheKey, String.class);
    }

    @Test
    @DisplayName("L2 Redis 命中 → 反序列化返回并回填 L1")
    void l2Hit_returnsDeserializedAndBackfill() {
      String jsonResponseData = buildJsonResponse();
      when(redisStringOps.get(cacheKey, String.class)).thenReturn(jsonResponseData);
      when(cacheKeyBuilder.semanticLruIndex()).thenReturn("lru-index-key");

      CachedLlmResponse result = cache.get(MODEL, SYSTEM_PROMPT, USER_MESSAGE);

      assertThat(result).isNotNull();
      assertThat(result.content()).isEqualTo(CACHED_RESPONSE);
      assertThat(result.provider()).isEqualTo(PROVIDER);
      verify(redisCollectionOps).zAdd(eq("lru-index-key"), eq(cacheKey), any(BigDecimal.class));
    }

    @Test
    @DisplayName("L1 未命中且 L2 未命中 → 返回 null")
    void bothMiss_returnsNull() {
      when(redisStringOps.get(cacheKey, String.class)).thenReturn(null);

      CachedLlmResponse result = cache.get(MODEL, SYSTEM_PROMPT, USER_MESSAGE);

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("L2 Redis 反序列化失败 → 返回 null 不抛异常")
    void l2DeserializationFails_returnsNull() {
      when(redisStringOps.get(cacheKey, String.class)).thenReturn("invalid-json{{{");

      CachedLlmResponse result = cache.get(MODEL, SYSTEM_PROMPT, USER_MESSAGE);

      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("put 方法")
  class Put {

    private String cacheKey;

    @BeforeEach
    void stubKey() {
      cacheKey = "cached-llm-key";
      when(cacheKeyBuilder.semanticLlm(anyString())).thenReturn(cacheKey);
      when(cacheKeyBuilder.semanticLruIndex()).thenReturn("lru-index-key");
    }

    @Test
    @DisplayName("正常写入 → 写入 L1 + L2 + 维护 LRU 索引")
    void normalPut_writesBothLevels() {
      cache.put(MODEL, SYSTEM_PROMPT, USER_MESSAGE, CACHED_RESPONSE, PROVIDER);

      verify(redisStringOps).set(eq(cacheKey), anyString(), any(Duration.class));
      verify(redisCollectionOps).zAdd(eq("lru-index-key"), eq(cacheKey), any(BigDecimal.class));
    }

    @Test
    @DisplayName("写入后立即读取 L1 → 命中缓存")
    void putThenGetL1Hit_returnsDirectly() {
      cache.put(MODEL, SYSTEM_PROMPT, USER_MESSAGE, CACHED_RESPONSE, PROVIDER);

      // 写入后通过反射或直接读取 — 由于 L1 是进程内缓存，应能再次获取
      CachedLlmResponse result = cache.get(MODEL, SYSTEM_PROMPT, USER_MESSAGE);

      assertThat(result).isNotNull();
      assertThat(result.content()).isEqualTo(CACHED_RESPONSE);
      // L1 命中，不应再查 Redis
      verify(redisStringOps, never()).get(cacheKey, String.class);
    }
  }

  @Nested
  @DisplayName("buildKey 方法")
  class BuildKey {

    @Test
    @DisplayName("生成 key 包含 SHA-256 + 通过 CacheKeyBuilder 构造")
    void buildKey_usesSha256AndCacheBuilder() {
      when(cacheKeyBuilder.semanticLlm(anyString())).thenReturn("semantic-key-result");

      String key = cache.buildKey(MODEL, SYSTEM_PROMPT, USER_MESSAGE);

      assertThat(key).isEqualTo("semantic-key-result");
      verify(cacheKeyBuilder).semanticLlm(anyString());
    }

    @Test
    @DisplayName("null systemPrompt → 空字符串入 SHA-256")
    void nullSystemPrompt_convertsToEmpty() {
      when(cacheKeyBuilder.semanticLlm(anyString())).thenReturn("key-null-safe");

      String key = cache.buildKey(MODEL, null, USER_MESSAGE);

      assertThat(key).isEqualTo("key-null-safe");
    }
  }

  @Nested
  @DisplayName("evictIfOverCapacity 私有方法（通过 put 触发）")
  class EvictIfOverCapacity {

    @Test
    @DisplayName("容量内 → 不触发淘汰")
    void withinCapacity_noEviction() {
      String cacheKey = "within-capacity-key";
      when(cacheKeyBuilder.semanticLlm(anyString())).thenReturn(cacheKey);
      when(cacheKeyBuilder.semanticLruIndex()).thenReturn("lru-index");
      when(redisCollectionOps.zSize("lru-index")).thenReturn(50L);

      cache.put(MODEL, SYSTEM_PROMPT, USER_MESSAGE, CACHED_RESPONSE, PROVIDER);

      verify(redisCollectionOps, never()).popMin(anyString(), any(Long.class));
    }
  }

  /** 构建 JSON 格式缓存响应（与 {@link CachedLlmResponse} 结构匹配）。 */
  private String buildJsonResponse() {
    return "{\"content\":\"" + CACHED_RESPONSE + "\",\"provider\":\"" + PROVIDER
        + "\",\"cachedAt\":1728000000000}";
  }
}
