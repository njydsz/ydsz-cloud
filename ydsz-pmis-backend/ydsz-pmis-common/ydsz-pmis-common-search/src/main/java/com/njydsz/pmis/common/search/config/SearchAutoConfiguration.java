package com.njydsz.pmis.common.search.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.pmis.common.search.core.SearchEngine;
import com.njydsz.pmis.common.search.engine.memory.InMemorySearchEngine;
import com.njydsz.pmis.common.search.engine.pg.PgSearchEngine;
import com.njydsz.pmis.common.search.provider.SearchProviderRegistry;
import com.njydsz.pmis.common.search.service.IndexSyncService;
import com.njydsz.pmis.common.search.service.SuggestionService;
import com.njydsz.pmis.common.search.service.UnifiedSearchService;
import com.njydsz.pmis.common.search.sync.IndexSyncListener;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索服务 Spring Boot 自动配置
 *
 * <p>配置前缀：{@code ydsz.search}
 *
 * <pre>
 * ydsz:
 *   search:
 *     engine: pg          # pg / memory / es
 *     highlight: true
 *     fuzzy: true
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(SearchEngine.class)
@EnableConfigurationProperties(SearchProperties.class)
@ConditionalOnProperty(prefix = "ydsz.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SearchAutoConfiguration {

    /**
     * PG 搜索引擎（默认引擎）
     */
    @Configuration
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    @ConditionalOnProperty(prefix = "ydsz.search", name = "engine", havingValue = "pg", matchIfMissing = true)
    static class PgSearchEngineConfiguration {

        @Bean
        @ConditionalOnMissingBean(SearchEngine.class)
        public SearchEngine pgSearchEngine(DataSource dataSource, SearchProperties properties) {
            log.info("[SearchAutoConfiguration] 初始化 PgSearchEngine: highlight={}, fuzzy={}",
                    properties.isHighlight(), properties.isFuzzy());
            return new PgSearchEngine(dataSource);
        }
    }

    /**
     * 内存搜索引擎（测试/降级用）
     */
    @Configuration
    @ConditionalOnProperty(prefix = "ydsz.search", name = "engine", havingValue = "memory")
    static class InMemorySearchEngineConfiguration {

        @Bean
        @ConditionalOnMissingBean(SearchEngine.class)
        public SearchEngine inMemorySearchEngine() {
            log.info("[SearchAutoConfiguration] 初始化 InMemorySearchEngine");
            return new InMemorySearchEngine();
        }
    }

    /**
     * 搜索提供者注册中心
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchProviderRegistry searchProviderRegistry() {
        return new SearchProviderRegistry();
    }

    /**
     * 统一搜索服务
     */
    @Bean
    @ConditionalOnMissingBean
    public UnifiedSearchService unifiedSearchService(SearchEngine searchEngine,
                                                      SearchProviderRegistry providerRegistry,
                                                      SearchProperties properties) {
        return new UnifiedSearchService(searchEngine, providerRegistry, properties);
    }

    /**
     * 索引同步服务
     */
    @Bean
    @ConditionalOnMissingBean
    public IndexSyncService indexSyncService(SearchEngine searchEngine,
                                              SearchProviderRegistry providerRegistry,
                                              SearchProperties properties) {
        return new IndexSyncService(searchEngine, providerRegistry, properties);
    }

    /**
     * 索引同步事件监听器
     */
    @Bean
    @ConditionalOnMissingBean
    public IndexSyncListener indexSyncListener(IndexSyncService indexSyncService) {
        return new IndexSyncListener(indexSyncService);
    }

    /**
     * 搜索建议服务
     */
    @Bean
    @ConditionalOnMissingBean
    public SuggestionService suggestionService(SearchEngine searchEngine,
                                                SearchProperties properties) {
        return new SuggestionService(searchEngine, properties);
    }
}
