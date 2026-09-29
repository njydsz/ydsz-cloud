package com.njydsz.agent.infra.cache;

import org.springframework.stereotype.Component;

import com.njydsz.common.cache.support.AbstractModuleCacheKeyBuilder;

/**
 * Agent 模块缓存键构造器（P1-1 整改：继承公共基类 {@link AbstractModuleCacheKeyBuilder}）。
 *
 * <p>为 Agent 模块的本地缓存实例（YdszCache）和分布式缓存键提供租户感知的统一生成能力，
 * 替代原来自建的字符串拼接（{@code "agent:llm:cache:"}、{@code "ydsz:agent:memory:"} 等）。
 *
 * <p><b>统一格式：</b>{@code ydsz:{tenantId}:agent:{entity}:{id}}
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Component
 * public class SemanticLlmCache {
 *   private final CacheKeyBuilder cacheKeyBuilder;
 *
 *   public String buildKey(String model, String prompt, String msg) {
 *     return cacheKeyBuilder.semanticLlm(model, sha256(prompt + msg));
 *   }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.29
 */
@Component("agentCacheKeyBuilder")
public class CacheKeyBuilder extends AbstractModuleCacheKeyBuilder {

  /** 模块标识：Agent 引擎 */
  private static final String MODULE = "agent";

  /** 构造 Agent 模块缓存键构造器。 */
  public CacheKeyBuilder() {
    super(MODULE);
  }

  // ============================== 对话记忆缓存 key ==============================

  /**
   * 生成「会话记忆 List」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:agent:memory:{conversationId}}
   *
   * @param conversationId 会话 ID
   * @return 租户隔离的缓存键
   */
  public String conversationMemory(String conversationId) {
    return buildKey("memory", conversationId);
  }

  // ============================== LLM 语义缓存 key ==============================

  /**
   * 生成「LLM 语义响应」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:agent:llm:cache:{sha256Hex}}
   *
   * @param sha256Hex model+prompt+message 的 SHA-256 摘要
   * @return 租户隔离的缓存键
   */
  public String semanticLlm(String sha256Hex) {
    return buildKey("llm:cache", sha256Hex);
  }

  /**
   * 生成「LLM 语义缓存 LRU 索引」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:agent:llm:cache:lru-index}
   *
   * @return 租户隔离的 LRU 索引缓存键
   */
  public String semanticLruIndex() {
    return buildKey("llm:cache:lru-index", "");
  }

  // ============================== LLM 请求去重 key ==============================

  /**
   * 生成「LLM 请求去重」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:agent:llm:dedup:{sha256Hex}}
   *
   * @param sha256Hex 请求指纹摘要
   * @return 租户隔离的缓存键
   */
  public String llmDedup(String sha256Hex) {
    return buildKey("llm:dedup", sha256Hex);
  }

  // ============================== JWT 失效通知 channel ==============================

  /**
   * 生成「JWT 失效广播」Redis Pub/Sub channel 名。
   *
   * <p>格式：{@code ydsz:{tenantId}:agent:jwt:invalidate}
   *
   * @return 租户隔离的 channel 名
   */
  public String jwtInvalidateChannel() {
    return buildKey("jwt:invalidate", "");
  }

  // ============================== 健康检查 key ==============================

  /**
   * 生成「健康检查」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:agent:memory:health-check}
   *
   * @return 租户隔离的健康检查缓存键
   */
  public String healthCheck() {
    return buildKey("memory:health-check", "");
  }
}
