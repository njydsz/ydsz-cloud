package com.njydsz.agent.infra.llm;

/**
 * LLM 缓存配置常量
 *
 * <p>定义缓存 key 与默认参数，可在 application.yml 中通过 {@code ydsz.agent.cache} 前缀覆盖。
 *
 * <p><b>命名说明</b>：类名保留 "Semantic" 以兼容历史引用，但当前缓存实现为<b>精确哈希匹配</b> （见
 * {@link SemanticLlmCache} 类注释），未启用语义相似度匹配。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class SemanticCacheConfig {

  /** 私有构造器防止实例化 */
  private SemanticCacheConfig() {}

  /** 默认缓存 TTL（分钟） */
  public static final int DEFAULT_TTL_MINUTES = 60;

  /** 缓存 key 拼接分隔符 */
  public static final String KEY_SEPARATOR = "|";

  /** Redis 缓存 key 前缀 */
  public static final String CACHE_KEY_PREFIX = "agent:llm:cache:";
}
