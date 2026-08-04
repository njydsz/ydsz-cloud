package com.remisoft.common.search.config;

import java.util.List;

import javax.sql.DataSource;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.remisoft.common.search.analytics.SearchAnalyticsService;
import com.remisoft.common.search.analytics.SearchQualityTracker;
import com.remisoft.common.search.core.SearchEngineRegistry;
import com.remisoft.common.search.core.SearchStrategy;
import com.remisoft.common.search.engine.es.ElasticsearchSearchStrategy;
import com.remisoft.common.search.engine.memory.InMemorySearchStrategy;
import com.remisoft.common.search.engine.opensearch.OpenSearchStrategy;
import com.remisoft.common.search.engine.pg.PgSearchStrategy;
import com.remisoft.common.search.engine.redis.RediSearchStrategy;
import com.remisoft.common.search.engine.solr.SolrSearchStrategy;
import com.remisoft.common.search.health.SearchHealthIndicator;
import com.remisoft.common.search.metrics.SearchMetrics;
import com.remisoft.common.search.provider.SearchProvider;
import com.remisoft.common.search.provider.SearchProviderRegistry;
import com.remisoft.common.search.service.BusinessRanker;
import com.remisoft.common.search.service.IndexRebuildService;
import com.remisoft.common.search.service.IndexSyncService;
import com.remisoft.common.search.service.QueryParser;
import com.remisoft.common.search.service.SearchCacheService;
import com.remisoft.common.search.service.SearchTextProcessor;
import com.remisoft.common.search.service.SuggestionService;
import com.remisoft.common.search.service.UnifiedSearchService;
import com.remisoft.common.search.sync.IndexConsistencyChecker;
import com.remisoft.common.search.sync.IndexSyncListener;
import com.remisoft.common.search.sync.SearchIndexEventBridge;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 全文检索自动配置。
 *
 * <p>封装 Elasticsearch 客户端与索引模板管理：连接池、查询构造器、聚合分析、高亮。
 *
 * <p>通过 {@code remi.search.*} 配置 ES 集群地址、用户名/密码、连接超时等。
 *
 * @author remi-team
 * @since 1.0.0
 */

@Slf4j
@AutoConfiguration
@ConditionalOnClass(SearchStrategy.class)
@ConditionalOnProperty(prefix = "remi.search", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SearchProperties.class)
public class SearchAutoConfiguration {

    private UnifiedSearchService unifiedSearchServiceInstance;
    private IndexSyncService indexSyncServiceInstance;
    @Autowired
    private ObjectProvider<PgSearchStrategy> pgSearchStrategyProvider;
    @Autowired
    private ObjectProvider<IndexRebuildService> indexRebuildServiceProvider;

    // ==================== 引擎策略装配 ====================

    /**
     * PG 引擎 — classpath 有 DataSource + JdbcTemplate 时激活
     */
    @Configuration
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    static class PgEngineConfiguration {
        /**
         * 装配基于 PostgreSQL tsvector 的默认搜索引擎。
         *
         * <p>本项目默认引擎（{@code matchIfMissing = true}），未显式指定
         * {@code remi.search.primary} 时即选用 PG，无需额外部署 ES 集群。
         * 索引表与 GIN 索引由独立 SQL 脚本创建，本方法不做 DDL。
         *
         * <p><b>降级策略</b>：{@link DataSource} 不可用时返回
         * {@link InMemorySearchStrategy} 并打 warn 日志，
         * 保证应用能启动、搜索接口不报错，但结果仅来自内存索引且重启即失效。
         *
         * @param dataSourceProvider 数据源的惰性提供者，缺失时触发内存引擎降级
         * @param properties         搜索配置，其中 {@code pg} 段提供索引表名与字段权重
         * @return PG 搜索策略；数据源缺失时返回内存搜索策略，永不为 {@code null}
         */
        @Bean
        @ConditionalOnMissingBean(name = "pgSearchStrategy")
        @ConditionalOnProperty(prefix = "remi.search", name = "primary", havingValue = "pg", matchIfMissing = true)
        public SearchStrategy pgSearchStrategy(ObjectProvider<DataSource> dataSourceProvider,
                                                 SearchProperties properties) {
            DataSource ds = dataSourceProvider.getIfAvailable();
            if (ds == null) {
                log.warn("[SearchAutoConfig] DataSource 不可用，PG 引擎降级");
                return new InMemorySearchStrategy();
            }
            return new PgSearchStrategy(ds, properties.getPg());
        }
    }

    /**
     * Elasticsearch 引擎 — 需手动配置 remi.search.primary=es
     */
    @Bean
    @ConditionalOnMissingBean(name = "esSearchStrategy")
    @ConditionalOnProperty(prefix = "remi.search", name = "primary", havingValue = "es")
    public SearchStrategy esSearchStrategy(SearchProperties properties) {
        return new ElasticsearchSearchStrategy(properties.getEs());
    }

    /**
     * RediSearch 引擎
     */
    @Bean
    @ConditionalOnMissingBean(name = "rediSearchStrategy")
    @ConditionalOnProperty(prefix = "remi.search", name = "primary", havingValue = "redis")
    public SearchStrategy rediSearchStrategy(SearchProperties properties) {
        return new RediSearchStrategy(properties.getRedis());
    }

    /**
     * Solr 引擎
     */
    @Bean
    @ConditionalOnMissingBean(name = "solrSearchStrategy")
    @ConditionalOnProperty(prefix = "remi.search", name = "primary", havingValue = "solr")
    public SearchStrategy solrSearchStrategy(SearchProperties properties) {
        return new SolrSearchStrategy(properties.getSolr());
    }

    /**
     * OpenSearch 引擎
     */
    @Bean
    @ConditionalOnMissingBean(name = "openSearchStrategy")
    @ConditionalOnProperty(prefix = "remi.search", name = "primary", havingValue = "opensearch")
    public SearchStrategy openSearchStrategy(SearchProperties properties) {
        return new OpenSearchStrategy(properties.getOpensearch());
    }

    /**
     * Memory 引擎 — 始终可用（降级兜底）
     */
    @Bean
    @ConditionalOnMissingBean(name = "memorySearchStrategy")
    public SearchStrategy memorySearchStrategy() {
        return new InMemorySearchStrategy();
    }

    // ==================== 核心服务装配 ====================

    /**
     * 装配搜索提供者注册表，汇总各业务模块贡献的 {@link SearchProvider}。
     *
     * <p>业务方只需把自己的 Provider 声明为 Bean（如项目、合同、任务），
     * 即可被自动收集并参与统一检索与索引重建，无需改动本模块。
     *
     * @param providersProvider Provider 列表的惰性提供者；无任何业务 Provider 时为 {@code null}，
     *                          注册表将退化为空集合，搜索返回空结果而非报错
     * @return 提供者注册表，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchProviderRegistry searchProviderRegistry(ObjectProvider<List<SearchProvider<?>>> providersProvider) {
        List<SearchProvider<?>> providers = providersProvider.getIfAvailable();
        return new SearchProviderRegistry(providers);
    }

    /**
     * 装配搜索引擎注册表，按配置选出主引擎并管理多引擎间的降级顺序。
     *
     * <p>注入的是<b>全部</b>已装配的 {@link SearchStrategy}
     * （PG / ES / Redis / Solr / OpenSearch / Memory 中被条件激活的那些），
     * 由注册表依据 {@code remi.search.primary} 挑选主引擎，
     * 主引擎不可用时按能力回退到内存引擎。
     *
     * @param strategies 所有已装配的搜索策略；内存引擎无条件装配，因此列表至少含 1 个元素
     * @param properties 搜索配置，提供主引擎选择与降级开关
     * @return 引擎注册表，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchEngineRegistry searchEngineRegistry(List<SearchStrategy> strategies,
                                                      SearchProperties properties) {
        return new SearchEngineRegistry(strategies, properties);
    }

    /**
     * 装配搜索结果缓存服务。
     *
     * <p>缓存键由检索请求的关键词、类型、分页、排序与权限过滤条件共同构成，
     * 因此<b>不同用户的权限过滤结果不会互相串数据</b>；
     * 容量与 TTL 由 {@code remi.search.cache-*} 配置。
     *
     * <p>注意 {@link UnifiedSearchService} 内部另行 new 了一个独立缓存实例，
     * 本 Bean 主要供健康检查与运维接口观测/清理使用。
     *
     * @param properties 搜索配置，提供缓存容量与过期时间
     * @return 缓存服务实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchCacheService searchCacheService(SearchProperties properties) {
        return new SearchCacheService(properties);
    }

    /**
     * 装配查询文本预处理器，负责分词、停用词过滤与同义词改写。
     *
     * <p>在请求进入引擎前对关键词做归一化，直接影响召回率；
     * 处理结果为空时调用方会保留用户原始输入，避免把查询「洗没了」。
     *
     * @param properties 搜索配置，提供停用词表与同义词表
     * @return 文本处理器实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchTextProcessor searchTextProcessor(SearchProperties properties) {
        return new SearchTextProcessor(properties);
    }

    /**
     * 装配搜索指标采集器，上报检索耗时、命中数与索引操作成败。
     *
     * <p>{@link MeterRegistry} 以惰性方式注入：未引入 Micrometer 时传入 {@code null}，
     * 采集器进入空实现模式，埋点调用变为无副作用的空操作，
     * 因此业务代码可以无条件调用埋点方法。
     *
     * @param meterRegistryProvider 指标注册表的惰性提供者，缺失时降级为空实现
     * @return 指标采集器实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchMetrics searchMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new SearchMetrics(meterRegistryProvider.getIfAvailable());
    }

    /**
     * 装配搜索行为分析服务，沉淀热门词、零结果词与每日搜索量。
     *
     * <p>Redis 以惰性方式注入：可用时数据落 Redis（跨节点聚合、重启不丢），
     * 不可用时自动降级为单节点内存统计（有容量上限、重启即丢）。
     *
     * @param redisProvider {@link StringRedisTemplate} 的惰性提供者，缺失时触发内存降级
     * @return 分析服务实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchAnalyticsService searchAnalyticsService(ObjectProvider<StringRedisTemplate> redisProvider) {
        return new SearchAnalyticsService(redisProvider);
    }

    /**
     * 装配业务重排器，在引擎相关性得分之上叠加业务权重。
     *
     * <p>用于把「引擎认为最相关」修正为「业务认为最该看」，
     * 例如按时间衰减、实体类型权重、状态优先级调整最终顺序。
     * 重排在分页截取<b>之后</b>执行，只影响当前页内的排列。
     *
     * @param properties 搜索配置，提供各维度的重排权重系数
     * @return 业务重排器实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public BusinessRanker businessRanker(SearchProperties properties) {
        return new BusinessRanker(properties);
    }

    /**
     * 装配查询语法解析器，解析高级检索表达式（AND/OR/NOT、引号短语、字段限定等）。
     *
     * <p>无状态且不依赖任何配置，线程安全，可被所有请求共享。
     *
     * @return 查询解析器实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public QueryParser queryParser() {
        return new QueryParser();
    }

    /**
     * 装配搜索质量追踪器，统计点击率、首位命中率等效果指标。
     *
     * <p>用于评估召回与排序策略的调整效果，是搜索体验优化的数据基础。
     * Redis 惰性注入，不可用时降级为内存统计（重启丢失）。
     *
     * @param redisProvider {@link StringRedisTemplate} 的惰性提供者，缺失时触发内存降级
     * @return 质量追踪器实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchQualityTracker searchQualityTracker(ObjectProvider<StringRedisTemplate> redisProvider) {
        return new SearchQualityTracker(redisProvider);
    }

    /**
     * 装配统一搜索服务，作为业务方检索的唯一入口。
     *
     * <p>内部会创建独立的搜索线程池与信号量限流器，
     * 因此本方法额外把实例缓存到 {@code unifiedSearchServiceInstance} 字段，
     * 供 {@link #destroy()} 在容器关闭时回收线程池——
     * 由于该 Bean 未声明 {@code destroyMethod}，若不做此缓存线程池将泄漏。
     *
     * @param engineRegistry         引擎注册表，决定实际执行检索的引擎
     * @param providerRegistry       提供者注册表，提供数据权限过滤条件
     * @param properties             搜索配置：分页上限、超时、熔断阈值等
     * @param searchMetrics          指标采集器，上报耗时与命中数
     * @param searchAnalyticsService 行为分析服务，沉淀热门词与零结果词
     * @param searchTextProcessor    查询文本预处理器
     * @param businessRanker         业务重排器
     * @return 统一搜索服务实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public UnifiedSearchService unifiedSearchService(SearchEngineRegistry engineRegistry,
                                                      SearchProviderRegistry providerRegistry,
                                                      SearchProperties properties,
                                                      SearchMetrics searchMetrics,
                                                      SearchAnalyticsService searchAnalyticsService,
                                                      SearchTextProcessor searchTextProcessor,
                                                      BusinessRanker businessRanker) {
        unifiedSearchServiceInstance = new UnifiedSearchService(engineRegistry, providerRegistry, properties,
                searchMetrics, searchAnalyticsService, searchTextProcessor, businessRanker);
        return unifiedSearchServiceInstance;
    }

    /**
     * 装配索引同步服务，负责业务数据变更到搜索索引的最终一致同步。
     *
     * <p>与 {@link #unifiedSearchService} 同理，实例被缓存到字段中，
     * 以便 {@link #destroy()} 关闭其内部索引线程池，避免线程泄漏。
     *
     * @param engineRegistry   引擎注册表，提供索引写入能力
     * @param providerRegistry 提供者注册表，用于全量重建时按类型加载数据
     * @param properties       搜索配置：重试次数、退避间隔、重建批大小
     * @param searchMetrics    指标采集器，上报索引操作成败
     * @return 索引同步服务实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public IndexSyncService indexSyncService(SearchEngineRegistry engineRegistry,
                                              SearchProviderRegistry providerRegistry,
                                              SearchProperties properties,
                                              SearchMetrics searchMetrics) {
        indexSyncServiceInstance = new IndexSyncService(engineRegistry, providerRegistry, properties, searchMetrics);
        return indexSyncServiceInstance;
    }

    /**
     * 装配索引重建服务，提供运维侧的全量/蓝绿重建入口。
     *
     * <p>该实例的线程池由 {@link #destroy()} 通过
     * {@code indexRebuildServiceProvider} 惰性取回后关闭。
     *
     * @param indexSyncService 索引同步服务，承担实际的数据回灌
     * @param engineRegistry   引擎注册表，提供清空索引的能力
     * @param providerRegistry 提供者注册表，用于枚举可重建的实体类型
     * @return 索引重建服务实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public IndexRebuildService indexRebuildService(IndexSyncService indexSyncService,
                                                    SearchEngineRegistry engineRegistry,
                                                    SearchProviderRegistry providerRegistry) {
        return new IndexRebuildService(indexSyncService, engineRegistry, providerRegistry);
    }

    /**
     * 装配搜索建议服务，提供自动补全与拼写纠错能力。
     *
     * <p>依赖主引擎实现 {@code SuggestStrategy}；
     * 引擎不支持建议能力时服务仍可正常装配，只是所有查询返回空列表。
     *
     * @param engineRegistry 引擎注册表，从中获取建议策略
     * @param properties     搜索配置，提供建议条数上限 {@code suggestLimit}
     * @return 建议服务实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public SuggestionService suggestionService(SearchEngineRegistry engineRegistry,
                                                SearchProperties properties) {
        return new SuggestionService(engineRegistry, properties);
    }

    /**
     * 装配索引同步事件监听器，把领域事件转成索引变更操作。
     *
     * <p>这是业务代码与搜索模块的解耦点：业务只管发事件，
     * 不需要显式调用索引 API，也不会因索引失败而回滚业务事务。
     *
     * @param indexSyncService 索引同步服务，承接监听到的变更
     * @return 事件监听器实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public IndexSyncListener indexSyncListener(IndexSyncService indexSyncService) {
        return new IndexSyncListener(indexSyncService);
    }

    /**
     * 装配搜索索引事件桥接器，把通用领域事件适配为具体实体的索引文档。
     *
     * <p>与 {@link IndexSyncListener} 的分工：监听器负责「收事件」，
     * 桥接器负责「按事件中的类型找到对应 Provider 并生成索引文档」。
     *
     * @param indexSyncService 索引同步服务，执行最终的索引写入
     * @param providerRegistry 提供者注册表，按事件类型定位 Provider
     * @param engineRegistry   引擎注册表，用于判断引擎是否支持索引操作
     * @param searchMetrics    指标采集器，上报桥接与索引结果
     * @return 事件桥接器实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchIndexEventBridge searchIndexEventBridge(IndexSyncService indexSyncService,
                                                          SearchProviderRegistry providerRegistry,
                                                          SearchEngineRegistry engineRegistry,
                                                          SearchMetrics searchMetrics) {
        return new SearchIndexEventBridge(indexSyncService, providerRegistry, engineRegistry, searchMetrics);
    }

    /**
     * 装配索引一致性校验器，比对数据源与索引之间的文档差异。
     *
     * <p>因为索引同步是异步且失败不回滚的最终一致模型，
     * 长时间运行后可能积累「库里已删索引仍在」或「库里新增索引缺失」的偏差，
     * 需要本校验器定期巡检并触发补偿或重建。
     *
     * @param engineRegistry   引擎注册表，用于读取索引侧文档
     * @param providerRegistry 提供者注册表，用于读取数据源侧文档 ID
     * @return 一致性校验器实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public IndexConsistencyChecker indexConsistencyChecker(SearchEngineRegistry engineRegistry,
                                                             SearchProviderRegistry providerRegistry) {
        return new IndexConsistencyChecker(engineRegistry, providerRegistry);
    }

    /**
     * 装配搜索模块健康指示器，向 Actuator 暴露引擎可用性与缓存状态。
     *
     * <p>由于主引擎不可用时模块会静默降级到内存引擎（接口不报错），
     * 健康检查是外部感知这一降级的<b>主要手段</b>，应纳入监控告警。
     *
     * @param engineRegistry 引擎注册表，用于探测主引擎可用性
     * @param cacheService   缓存服务，用于上报缓存规模
     * @param searchMetrics  指标采集器，用于上报累计检索统计
     * @return 健康指示器实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchHealthIndicator searchHealthIndicator(SearchEngineRegistry engineRegistry,
                                                        SearchCacheService cacheService,
                                                        SearchMetrics searchMetrics) {
        return new SearchHealthIndicator(engineRegistry, cacheService, searchMetrics);
    }

    /**
     * 容器关闭时集中回收搜索模块创建的全部线程池。
     *
     * <p>本模块的服务均由 {@code new} 直接创建并自持线程池，
     * Spring 无法自动识别其销毁方法，故在此统一关闭，防止线程泄漏
     * 导致 JVM 无法退出。涉及四类线程池：统一搜索、索引同步、
     * PG 可用性探测、索引重建。
     *
     * <p>每个实例都做了 {@code null} 判断——相关 Bean 可能因条件装配未生效
     * （如引擎非 PG、业务方自定义了同名 Bean），缺失属正常情况。
     *
     * <p>关闭顺序不做严格保证；各服务自身的 shutdown 均为幂等，
     * 与 Spring 可能触发的其他销毁回调叠加也不会出错。
     */
    @PreDestroy
    public void destroy() {
        if (unifiedSearchServiceInstance != null) {
            unifiedSearchServiceInstance.shutdown();
        }
        if (indexSyncServiceInstance != null) {
            indexSyncServiceInstance.shutdown();
        }
        if (pgSearchStrategyProvider != null) {
            PgSearchStrategy pgStrategy = pgSearchStrategyProvider.getIfAvailable();
            if (pgStrategy != null) {
                pgStrategy.shutdown();
            }
        }
        if (indexRebuildServiceProvider != null) {
            IndexRebuildService rebuildService = indexRebuildServiceProvider.getIfAvailable();
            if (rebuildService != null) {
                rebuildService.shutdown();
            }
        }
    }
}
