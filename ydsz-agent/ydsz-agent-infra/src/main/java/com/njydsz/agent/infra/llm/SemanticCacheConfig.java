package com.njydsz.agent.infra.llm;

/**
 * LLM 语义缓存配置常量
 *
 * <p>定义语义缓存的各项阈值参数，可在 application.yml 中通过
 * {@code ydsz.agent.cache} 前缀覆盖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SemanticCacheConfig {

    /** 私有构造器防止实例化 */
    private SemanticCacheConfig() {
    }

    /** 默认缓存 TTL（分钟） */
    public static final int DEFAULT_TTL_MINUTES = 60;
    /** 默认语义相似度阈值（0.0 ~ 1.0） */
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.95;
    /** 默认最大缓存条目数 */
    public static final int DEFAULT_MAX_CACHE_SIZE = 500;
    /** 缓存 key 拼接分隔符 */
    public static final String KEY_SEPARATOR = "|";
    /** Redis 缓存 key 前缀 */
    public static final String CACHE_KEY_PREFIX = "agent:llm:cache:";
}
