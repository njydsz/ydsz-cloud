package com.njydsz.pmis.common.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.search.api.SearchRequest;
import com.njydsz.pmis.common.search.api.SearchResponse;
import com.njydsz.pmis.common.search.config.SearchProperties;

/**
 * SearchCacheService 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@DisplayName("SearchCacheService 测试")
class SearchCacheServiceTest {

    private SearchCacheService cacheService;
    private SearchProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SearchProperties();
        properties.getCache().setEnabled(true);
        properties.getCache().setTtl(60);
        properties.getCache().setMaxSize(100);
        cacheService = new SearchCacheService(properties);
    }

    @Test
    @DisplayName("缓存未命中返回 null")
    void get_miss_returnsNull() {
        SearchRequest request = SearchRequest.of("test");
        assertThat(cacheService.get(request)).isNull();
    }

    @Test
    @DisplayName("缓存写入后可命中")
    void put_thenGet_hit() {
        SearchRequest request = SearchRequest.of("test");
        SearchResponse response = SearchResponse.empty(1, 20);
        cacheService.put(request, response);

        SearchResponse cached = cacheService.get(request);
        assertThat(cached).isNotNull();
    }

    @Test
    @DisplayName("禁用缓存时 get 返回 null")
    void get_disabled_returnsNull() {
        properties.getCache().setEnabled(false);
        SearchRequest request = SearchRequest.of("test");
        cacheService.put(request, SearchResponse.empty(1, 20));
        assertThat(cacheService.get(request)).isNull();
    }

    @Test
    @DisplayName("禁用缓存时 put 不写入")
    void put_disabled_noOp() {
        properties.getCache().setEnabled(false);
        SearchRequest request = SearchRequest.of("test");
        cacheService.put(request, SearchResponse.empty(1, 20));
        assertThat(cacheService.size()).isZero();
    }

    @Test
    @DisplayName("清空缓存")
    void clear() {
        SearchRequest request = SearchRequest.of("test");
        cacheService.put(request, SearchResponse.empty(1, 20));
        assertThat(cacheService.size()).isEqualTo(1);

        cacheService.clear();
        assertThat(cacheService.size()).isZero();
    }

    @Test
    @DisplayName("不同关键词生成不同缓存键")
    void get_differentKeywords_differentCacheEntries() {
        SearchRequest request1 = SearchRequest.of("keyword1");
        SearchRequest request2 = SearchRequest.of("keyword2");

        cacheService.put(request1, SearchResponse.empty(1, 20));
        cacheService.put(request2, SearchResponse.empty(1, 20));

        assertThat(cacheService.size()).isEqualTo(2);
    }
}
