package com.njydsz.common.search.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import javax.sql.DataSource;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.analytics.SearchQualityTracker;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.core.SearchStrategy;
import com.njydsz.common.search.engine.memory.InMemorySearchStrategy;
import com.njydsz.common.search.engine.pg.PgSearchStrategy;
import com.njydsz.common.search.health.SearchHealthIndicator;
import com.njydsz.common.search.metrics.SearchMetrics;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderRegistry;
import com.njydsz.common.search.service.BusinessRanker;
import com.njydsz.common.search.service.IndexRebuildService;
import com.njydsz.common.search.service.IndexSyncService;
import com.njydsz.common.search.service.QueryParser;
import com.njydsz.common.search.service.SearchCacheService;
import com.njydsz.common.search.service.SearchPipeline;
import com.njydsz.common.search.service.SearchTextProcessor;
import com.njydsz.common.search.service.SuggestionService;
import com.njydsz.common.search.service.UnifiedSearchService;
import com.njydsz.common.search.service.ZeroResultHandler;
import com.njydsz.common.search.sync.IndexConsistencyChecker;
import com.njydsz.common.search.sync.IndexSyncListener;
import com.njydsz.common.search.sync.PersistentDeadLetterQueue;
import com.njydsz.common.search.sync.SearchIndexEventBridge;

/**
 * 全文检索自动配置。
 *
 * <p>封装 PostgreSQL 全文检索引擎与内存降级引擎，提供统一搜索入口。
 *
 * <p>通过 {@code ydsz.search.*} 配置主引擎选择、缓存、降级策略等。 默认使用 PostgreSQL tsvector 引擎，不可用时自动降级到内存引擎。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(SearchStrategy.class)
@ConditionalOnProperty(
    prefix = "ydsz.search",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(SearchProperties.class)
public class SearchAutoConfiguration {

  private UnifiedSearchService unifiedSearchServiceInstance;
  private IndexSyncService indexSyncServiceInstance;
  @Autowired private ObjectProvider<PgSearchStrategy> pgSearchStrategyProvider;
  @Autowired private ObjectProvider<IndexRebuildService> indexRebuildServiceProvider;

  // ==================== 引擎策略装配 ====================

  /**
   * PG 引擎 — classpath 有 DataSource + JdbcTemplate 时激活。 *
   *
   * <p>本项目默认引擎（{@code matchIfMissing = true}），未显式指定 {@code ydsz.search.primary} 时即选用 PG，无需额外部署 ES
   * 集群。 索引表与 GIN 索引由独立 SQL 脚本创建，本方法不做 DDL。
   *
   * <p><b>降级策略</b>：{@link DataSource} 不可用时返回 {@link InMemorySearchStrategy} 并打 warn 日志，
   * 保证应用能启动、搜索接口不报错，但结果仅来自内存索引且重启即失效。
   *
   * @param dataSourceProvider 数据源的惰性提供者，缺失时触发内存引擎降级
   * @param properties 搜索配置，其中 {@code pg} 段提供索引表名与字段权重
   * @return PG 搜索策略；数据源缺失时返回内存搜索策略，永不为 {@code null}
   */
  @Configuration
  @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
  static class PgEngineConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "pgSearchStrategy")
    @ConditionalOnProperty(
        prefix = "ydsz.search",
        name = "primary",
        havingValue = "pg",
        matchIfMissing = true)
    public SearchStrategy pgSearchStrategy(
        ObjectProvider<DataSource> dataSourceProvider, SearchProperties properties) {
      DataSource ds = dataSourceProvider.getIfAvailable();
      if (ds == null) {
        log.warn("[SearchAutoConfig] DataSource 不可用，PG 引擎降级");
        return new InMemorySearchStrategy();
      }
      return new PgSearchStrategy(ds, properties.getPg());
    }
  }

  /** Memory 引擎 — 始终可用（降级兜底）。 */
  @Bean
  @ConditionalOnMissingBean(name = "memorySearchStrategy")
  public SearchStrategy memorySearchStrategy() {
    return new InMemorySearchStrategy();
  }

  // ==================== 模块化引擎 Starter 支持 ====================

  /**
   * 引擎模块自动装配注册表 — 支持独立引擎 Starter 自我注册。
   *
   * <p>当需要拆分引擎为独立模块（ydsz-common-search-es、ydsz-common-search-pg 等）时， 每个 Starter 只需声明一个 {@code
   * EngineStarterConfigurer} Bean， 该方法即会被调用以注册引擎的自动配置类。
   *
   * <p>设计原则：
   *
   * <ul>
   *   <li>核心模块仅保留 PG + Memory（覆盖 80% 场景）
   *   <li>各引擎 Starter 自包含：依赖 + Condition + Strategy 一体化
   *   <li>业务按需引入引擎 Starter，减少不必要的依赖
   * </ul>
   */
  @Configuration
  @Import(EngineStarterRegistry.class)
  static class ModularEngineConfiguration {}

  /**
   * 引擎启动器注册表 — 收集所有引擎模块贡献的自动配置。
   *
   * <p>通过 {@link org.springframework.context.annotation.ImportSelector} 机制， 动态发现 classpath 上所有实现了
   * {@link EngineStarterConfigurer} 的模块， 并将其 {@code ImportSelector} 导入 Spring 容器。
   */
  public static class EngineStarterRegistry
      implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
      // ServiceLoader 方式加载各引擎 Starter 的 EngineStarterConfigurer 实现
      List<String> configClasses = new ArrayList<>();
      ServiceLoader<EngineStarterConfigurer> loader =
          ServiceLoader.load(EngineStarterConfigurer.class);
      for (EngineStarterConfigurer configurer : loader) {
        for (ImportSelector selector :
            configurer.getImportSelectors()) {
          for (String className : selector.selectImports(metadata)) {
            configClasses.add(className);
          }
        }
      }
      return configClasses.toArray(new String[0]);
    }
  }

  // ==================== 线程池装配 ====================

  /**
   * 搜索执行线程池 — 供 {@link UnifiedSearchService} 使用。
   *
   * <p>通过 {@code @ConditionalOnMissingBean} 允许业务方注入自定义线程池覆盖。 当 classpath 存在 {@code
   * ydsz-common-thread} 且配置了 {@code ydsz.thread.pools.searchExecutor} 时，业务方应注入对应的统一管理线程池。
   *
   * @param properties 搜索配置
   * @return 搜索执行线程池
   */
  @Bean("searchExecutor")
  @ConditionalOnMissingBean(name = "searchExecutor")
  public ThreadPoolTaskExecutor searchExecutor(SearchProperties properties) {
    return UnifiedSearchService.createDefaultSearchExecutor(properties);
  }

  /**
   * 索引同步线程池 — 供 {@link IndexSyncService} 使用。
   *
   * <p>通过 {@code @ConditionalOnMissingBean} 允许业务方注入自定义线程池覆盖。
   *
   * @param properties 搜索配置
   * @return 索引同步线程池
   */
  @Bean("indexSyncExecutor")
  @ConditionalOnMissingBean(name = "indexSyncExecutor")
  public ThreadPoolTaskExecutor indexSyncExecutor(SearchProperties properties) {
    return IndexSyncService.createDefaultIndexSyncExecutor(properties);
  }

  // ==================== 核心服务装配 ====================

  /**
   * 装配搜索提供者注册表，汇总各业务模块贡献的 {@link SearchProvider}。
   *
   * <p>业务方只需把自己的 Provider 声明为 Bean（如项目、合同、任务）， 即可被自动收集并参与统一检索与索引重建，无需改动本模块。
   *
   * @param providersProvider Provider 列表的惰性提供者；无任何业务 Provider 时为 {@code null}，
   *     注册表将退化为空集合，搜索返回空结果而非报错
   * @return 提供者注册表，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public SearchProviderRegistry searchProviderRegistry(
      ObjectProvider<List<SearchProvider<?>>> providersProvider) {
    List<SearchProvider<?>> providers = providersProvider.getIfAvailable();
    return new SearchProviderRegistry(providers);
  }

  /**
   * 装配搜索引擎注册表，按配置选出主引擎并管理多引擎间的降级顺序。
   *
   * <p>注入的是<b>全部</b>已装配的 {@link SearchStrategy} （PG / Memory 中被条件激活的那些）， 由注册表依据 {@code
   * ydsz.search.primary} 挑选主引擎， 主引擎不可用时按能力回退到内存引擎。
   *
   * @param strategies 所有已装配的搜索策略；内存引擎无条件装配，因此列表至少含 1 个元素
   * @param properties 搜索配置，提供主引擎选择与降级开关
   * @return 引擎注册表，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public SearchEngineRegistry searchEngineRegistry(
      List<SearchStrategy> strategies, SearchProperties properties) {
    return new SearchEngineRegistry(strategies, properties);
  }

  /**
   * 装配搜索结果缓存服务。
   *
   * <p>缓存键由检索请求的关键词、类型、分页、排序与权限过滤条件共同构成， 因此<b>不同用户的权限过滤结果不会互相串数据</b>； 容量与 TTL 由 {@code
   * ydsz.search.cache-*} 配置。
   *
   * <p>本 Bean 作为全局共享的搜索缓存实例，供 {@link UnifiedSearchService}、 健康检查与运维接口统一使用。
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
   * <p>在请求进入引擎前对关键词做归一化，直接影响召回率； 处理结果为空时调用方会保留用户原始输入，避免把查询「洗没了」。
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
   * <p>{@link MeterRegistry} 以惰性方式注入：未引入 Micrometer 时传入 {@code null}， 采集器进入空实现模式，埋点调用变为无副作用的空操作，
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
   * <p>Redis 以惰性方式注入：可用时数据落 Redis（跨节点聚合、重启不丢）， 不可用时自动降级为单节点内存统计（有容量上限、重启即丢）。
   *
   * @param redisProvider {@link StringRedisTemplate} 的惰性提供者，缺失时触发内存降级
   * @return 分析服务实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public SearchAnalyticsService searchAnalyticsService(
      ObjectProvider<StringRedisTemplate> redisProvider) {
    return new SearchAnalyticsService(redisProvider);
  }

  /**
   * 装配业务重排器，在引擎相关性得分之上叠加业务权重。
   *
   * <p>用于把「引擎认为最相关」修正为「业务认为最该看」， 例如按时间衰减、实体类型权重、状态优先级调整最终顺序。 重排在分页截取<b>之后</b>执行，只影响当前页内的排列。
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
   * <p>用于评估召回与排序策略的调整效果，是搜索体验优化的数据基础。 Redis 惰性注入，不可用时降级为内存统计（重启丢失）。
   *
   * @param redisProvider {@link StringRedisTemplate} 的惰性提供者，缺失时触发内存降级
   * @return 质量追踪器实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public SearchQualityTracker searchQualityTracker(
      ObjectProvider<StringRedisTemplate> redisProvider) {
    return new SearchQualityTracker(redisProvider);
  }

  /**
   * 装配统一搜索服务，作为业务方检索的唯一入口。
   *
   * <p>内部会创建独立的搜索信号量限流器， 因此本方法额外把实例缓存到 {@code unifiedSearchServiceInstance} 字段， 供 {@link
   * #destroy()} 在容器关闭时回收线程池—— 由于该 Bean 未声明 {@code destroyMethod}，若不做此缓存线程池将泄漏。
   *
   * @param engineRegistry 引擎注册表，决定实际执行检索的引擎
   * @param providerRegistry 提供者注册表，提供数据权限过滤条件
   * @param properties 搜索配置：分页上限、超时、熔断阈值等
   * @param searchMetrics 指标采集器，上报耗时与命中数
   * @param searchAnalyticsService 行为分析服务，沉淀热门词与零结果词
   * @param searchQualityTracker 搜索质量追踪器，统计 MRR/CTR/零结果率/延迟
   * @param searchTextProcessor 查询文本预处理器
   * @param businessRanker 业务重排器
   * @param searchCacheService 共享搜索缓存服务
   * @return 统一搜索服务实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public UnifiedSearchService unifiedSearchService(
      SearchEngineRegistry engineRegistry,
      SearchProviderRegistry providerRegistry,
      SearchProperties properties,
      SearchMetrics searchMetrics,
      SearchAnalyticsService searchAnalyticsService,
      SearchQualityTracker searchQualityTracker,
      SearchTextProcessor searchTextProcessor,
      BusinessRanker businessRanker,
      SearchCacheService searchCacheService,
      ThreadPoolTaskExecutor searchExecutor) {
    unifiedSearchServiceInstance =
        new UnifiedSearchService(
            engineRegistry,
            providerRegistry,
            properties,
            searchMetrics,
            searchAnalyticsService,
            searchQualityTracker,
            searchTextProcessor,
            businessRanker,
            searchCacheService,
            searchExecutor);
    return unifiedSearchServiceInstance;
  }

  /**
   * 装配索引同步服务，负责业务数据变更到搜索索引的最终一致同步。
   *
   * <p>与 {@link #unifiedSearchService} 同理，实例被缓存到字段中， 以便 {@link #destroy()} 关闭其内部索引线程池，避免线程泄漏。
   *
   * @param engineRegistry 引擎注册表，提供索引写入能力
   * @param providerRegistry 提供者注册表，用于全量重建时按类型加载数据
   * @param properties 搜索配置：重试次数、退避间隔、重建批大小
   * @param searchMetrics 指标采集器，上报索引操作成败
   * @return 索引同步服务实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public IndexSyncService indexSyncService(
      SearchEngineRegistry engineRegistry,
      SearchProviderRegistry providerRegistry,
      SearchProperties properties,
      SearchMetrics searchMetrics,
      ThreadPoolTaskExecutor indexSyncExecutor,
      ObjectProvider<PersistentDeadLetterQueue> persistentDlqProvider) {
    indexSyncServiceInstance =
        new IndexSyncService(
            engineRegistry, providerRegistry, properties, searchMetrics, indexSyncExecutor);
    // 数据源可用时注入 PostgreSQL 持久化死信队列，否则 IndexSyncService 内部回退到纯内存模式
    PersistentDeadLetterQueue dlq = persistentDlqProvider.getIfAvailable();
    if (dlq != null) {
      indexSyncServiceInstance.setPersistentDlq(dlq);
    }
    return indexSyncServiceInstance;
  }

  /**
   * 索引重建线程池 — 供 {@link IndexRebuildService} 使用（单线程，串行重建保证一致性）。
   *
   * <p>通过 {@code @ConditionalOnMissingBean} 允许业务方注入自定义线程池覆盖。
   *
   * @return 索引重建线程池
   */
  @Bean("indexRebuildExecutor")
  @ConditionalOnMissingBean(name = "indexRebuildExecutor")
  public ThreadPoolTaskExecutor indexRebuildExecutor() {
    return IndexRebuildService.createDefaultRebuildExecutor();
  }

  /**
   * 装配索引重建服务，提供运维侧的全量/蓝绿重建入口。
   *
   * <p>该实例的线程池由 {@link #destroy()} 通过 {@code indexRebuildServiceProvider} 惰性取回后关闭。
   *
   * @param indexSyncService 索引同步服务，承担实际的数据回灌
   * @param engineRegistry 引擎注册表，提供清空索引的能力
   * @param providerRegistry 提供者注册表，用于枚举可重建的实体类型
   * @return 索引重建服务实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public IndexRebuildService indexRebuildService(
      IndexSyncService indexSyncService,
      SearchEngineRegistry engineRegistry,
      SearchProviderRegistry providerRegistry,
      ThreadPoolTaskExecutor indexRebuildExecutor) {
    return new IndexRebuildService(
        indexSyncService, engineRegistry, providerRegistry, indexRebuildExecutor);
  }

  /**
   * 装配搜索建议服务，提供自动补全与拼写纠错能力。
   *
   * <p>三层召回策略：引擎前缀建议 → 热门搜索兜底 → Levenshtein 纠错。 依赖主引擎实现 {@code SuggestStrategy}；
   * 引擎不支持建议能力时服务仍可正常装配，只是引擎层查询返回空列表， 仍可从分析服务获取热门词兜底。
   *
   * @param engineRegistry 引擎注册表，从中获取建议策略
   * @param analyticsService 分析服务，提供热门搜索兜底
   * @param properties 搜索配置，提供建议条数上限 {@code suggestLimit}
   * @return 建议服务实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public SuggestionService suggestionService(
      SearchEngineRegistry engineRegistry,
      SearchAnalyticsService analyticsService,
      SearchProperties properties) {
    return new SuggestionService(engineRegistry, analyticsService, properties);
  }

  /**
   * 装配零结果引导处理器，返回「您是不是要找」「热门搜索」「去掉筛选条件」建议。
   *
   * <p>在搜索返回 0 条结果时，通过以下策略引导用户：
   *
   * <ul>
   *   <li>did-you-mean: 基于编辑距离的拼写纠错候选
   *   <li>hot-keywords: 从 Redis 取当前热门搜索词兜底
   *   <li>suggest-remove-filter: 提示用户去掉非必要筛选条件扩大搜索范围
   * </ul>
   *
   * @param suggestionService 建议服务，用于生成纠错候选
   * @param searchAnalyticsService 搜索分析服务
   * @param properties 搜索配置属性
   * @return 零结果引导处理器实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public ZeroResultHandler zeroResultHandler(
      SuggestionService suggestionService,
      SearchAnalyticsService searchAnalyticsService,
      SearchProperties properties) {
    return new ZeroResultHandler(suggestionService, searchAnalyticsService, properties);
  }

  /**
   * 装配搜索文本处理管道（Filter 链模式）。
   *
   * <p>通过可组合的过滤器实现搜索引擎归一化策略：
   *
   * <ol>
   *   <li>Normalizer — 全半角转换、大小写归一、空白压缩
   *   <li>StopWord — 停用词过滤
   *   <li>Synonym — 同义词改写
   *   <li>ChineseToken — 中文分词（jieba/ICU/简单空格）
   *   <li>Pinyin — 拼音首字母提取
   * </ol>
   *
   * <p>过滤器可配置启用/禁用，执行顺序固定；每个 Filter 独立可测试。
   *
   * @param properties 搜索配置，控制各 Filter 的启用开关与词典路径
   * @return 搜索管道实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public SearchPipeline searchPipeline(SearchProperties properties) {
    return SearchPipeline.fromConfig(properties);
  }

  /**
   * 装配 PostgreSQL 持久化死信队列。
   *
   * <p>索引同步失败的操作最终持久化到 {@code ydsz_search_dead_letter} 表， 应用重启不丢失，支持定时任务触发重放。 表结构定义位于 {@code
   * db/ydsz_search_dead_letter.sql}，需由 DBA 或 Flyway 执行。
   *
   * <p>{@link javax.sql.DataSource} 以惰性方式注入，缺失时返回 {@code null}， {@link IndexSyncService}
   * 会检测到并回退到纯内存死信队列。
   *
   * @param dataSourceProvider 数据源的惰性提供者，缺失时返回 {@code null}
   * @return 持久化死信队列；数据源缺失时返回 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public PersistentDeadLetterQueue persistentDeadLetterQueue(
      ObjectProvider<DataSource> dataSourceProvider) {
    DataSource ds = dataSourceProvider.getIfAvailable();
    if (ds == null) {
      log.info("[SearchAutoConfig] DataSource 不可用，持久化死信队列不装配（回退到纯内存模式）");
      return null;
    }
    return new PersistentDeadLetterQueue(Optional.of(ds));
  }

  /**
   * 装配索引同步事件监听器，把领域事件转成索引变更操作。
   *
   * <p>这是业务代码与搜索模块的解耦点：业务只管发事件， 不需要显式调用索引 API，也不会因索引失败而回滚业务事务。
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
   * <p>与 {@link IndexSyncListener} 的分工：监听器负责「收事件」， 桥接器负责「按事件中的类型找到对应 Provider 并生成索引文档」。
   *
   * @param indexSyncService 索引同步服务，执行最终的索引写入
   * @param providerRegistry 提供者注册表，按事件类型定位 Provider
   * @param engineRegistry 引擎注册表，用于判断引擎是否支持索引操作
   * @param searchMetrics 指标采集器，上报桥接与索引结果
   * @return 事件桥接器实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public SearchIndexEventBridge searchIndexEventBridge(
      IndexSyncService indexSyncService,
      SearchProviderRegistry providerRegistry,
      SearchEngineRegistry engineRegistry,
      SearchMetrics searchMetrics) {
    return new SearchIndexEventBridge(
        indexSyncService, providerRegistry, engineRegistry, searchMetrics);
  }

  /**
   * 装配索引一致性校验器，比对数据源与索引之间的文档差异。
   *
   * <p>因为索引同步是异步且失败不回滚的最终一致模型， 长时间运行后可能积累「库里已删索引仍在」或「库里新增索引缺失」的偏差， 需要本校验器定期巡检并触发补偿或重建。
   *
   * @param engineRegistry 引擎注册表，用于读取索引侧文档
   * @param providerRegistry 提供者注册表，用于读取数据源侧文档 ID
   * @return 一致性校验器实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public IndexConsistencyChecker indexConsistencyChecker(
      SearchEngineRegistry engineRegistry, SearchProviderRegistry providerRegistry) {
    return new IndexConsistencyChecker(engineRegistry, providerRegistry);
  }

  /**
   * 装配搜索模块健康指示器，向 Actuator 暴露引擎可用性与缓存状态。
   *
   * <p>由于主引擎不可用时模块会静默降级到内存引擎（接口不报错）， 健康检查是外部感知这一降级的<b>主要手段</b>，应纳入监控告警。
   *
   * @param engineRegistry 引擎注册表，用于探测主引擎可用性
   * @param cacheService 缓存服务，用于上报缓存规模
   * @param searchMetrics 指标采集器，用于上报累计检索统计
   * @return 健康指示器实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public SearchHealthIndicator searchHealthIndicator(
      SearchEngineRegistry engineRegistry,
      SearchCacheService cacheService,
      SearchMetrics searchMetrics) {
    return new SearchHealthIndicator(engineRegistry, cacheService, searchMetrics);
  }

  /**
   * 容器关闭时集中回收搜索模块创建的全部线程池。
   *
   * <p>部分服务（如 {@link IndexSyncService}、{@link IndexRebuildService}） 在外部未注入线程池时会 fallback 创建默认线程池，
   * Spring 无法自动识别其销毁方法，故在此统一关闭，防止线程泄漏 导致 JVM 无法退出。涉及四类线程池：统一搜索、索引同步、 PG 可用性探测、索引重建。
   *
   * <p>每个实例都做了 {@code null} 判断——相关 Bean 可能因条件装配未生效 （如引擎非 PG、业务方自定义了同名 Bean），缺失属正常情况。
   *
   * <p>关闭顺序不做严格保证；各服务自身的 shutdown 均为幂等， 与 Spring 可能触发的其他销毁回调叠加也不会出错。
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
