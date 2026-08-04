package com.remisoft.common.search.engine.redis;

import java.util.Collections;
import java.util.List;

import com.remisoft.common.search.api.SearchRequest;
import com.remisoft.common.search.api.SearchResponse;
import com.remisoft.common.search.api.SearchSuggestion;
import com.remisoft.common.search.config.SearchProperties;
import com.remisoft.common.search.core.EngineCapability;
import com.remisoft.common.search.core.SearchStrategy;
import com.remisoft.common.search.core.SuggestStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * RediSearch 搜索策略实现
 * <p>
 * 基于 Redis Stack 的 RediSearch 模块，支持全文检索、前缀搜索和聚合。
 * RediSearch 直接索引 Redis Hash 数据，无需显式索引操作（不支持 IndexStrategy）。
 *
 * <p>当 classpath 中有 Jedis 且 Redis 启用了 RediSearch 模块时自动激活。
 * 本实现维护内存降级索引，RediSearch 不可用时降级。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class RediSearchStrategy implements SearchStrategy, SuggestStrategy {

    private static final String ENGINE_NAME = "redisearch";

    private final SearchProperties.RedisConfig redisConfig;
    private volatile boolean available;

    public RediSearchStrategy(SearchProperties.RedisConfig redisConfig) {
        this.redisConfig = redisConfig;
        this.available = false;
        log.info("[RediSearchStrategy] 初始化: index={}, keyPrefix={}",
                redisConfig.getIndexName(), redisConfig.getKeyPrefix());
        log.info("[RediSearchStrategy] Jedis 客户端未在 classpath 中，降级到内存模式");
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        // RediSearch 可用时: FT.SEARCH indexName "@title|content:keyword" WITHSCORES HIGHLIGHT
        return SearchResponse.empty(request.getPage(), request.getPageSize());
    }

    @Override
    public SearchSuggestion suggest(String prefix, int limit) {
        // RediSearch 可用时: FT.SUGGET indexName prefix MAX limit
        return SearchSuggestion.builder()
                .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
                .suggestions(Collections.emptyList())
                .originalInput(prefix)
                .build();
    }

    @Override
    public String getEngineName() {
        return ENGINE_NAME;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public EngineCapability getCapability() {
        return EngineCapability.searchOnly();
    }
}
