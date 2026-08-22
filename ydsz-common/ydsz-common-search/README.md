# ydsz-common-search

> 统一搜索服务框架（L5 业务服务层）— 策略模式多引擎抽象 + 索引同步 + 搜索建议 + 业务重排 + 质量分析

基于**策略模式**封装统一搜索能力，通过 SPI 抽象屏蔽底层引擎差异，默认提供 PostgreSQL tsvector（zhparser 中文分词）与内存引擎两种实现，并支持通过独立 Starter 扩展其他引擎。提供主引擎 + 降级链、Provider 业务接入、索引同步事件桥接、搜索建议、业务重排、搜索分析与质量追踪等完整能力，是所有业务模块全文检索的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供统一搜索 SPI、多引擎策略、索引同步、搜索建议、业务重排、搜索分析与质量追踪能力 |
| **依赖** | common-core、common-json、common-domain、common-util、common-exception；可选依赖 common-jdbc、common-cache、common-redis、common-docs、spring-context、spring-web、spring-jdbc、spring-boot-autoconfigure、spring-boot-actuator、spring-boot-health、micrometer-core、jakarta.validation-api、resilience4j-circuitbreaker、apm-toolkit-trace、swagger-annotations-jakarta |
| **版本** | 1.0.0 |

## 核心能力

### 1. 核心 SPI（策略模式抽象）

| 类 | 说明 |
|---|---|
| `SearchStrategy` | 搜索策略 SPI — 所有引擎必须实现（`search` / `getEngineName` / `isAvailable` / `getCapability`） |
| `IndexStrategy` | 索引维护 SPI — 需显式索引的引擎实现（`index` / `bulkIndex` / `deleteIndex` / `deleteAllIndices` / `count` / `getAllDocumentIds`） |
| `SuggestStrategy` | 搜索建议 SPI — 支持自动补全的引擎实现（`suggest`） |
| `EngineCapability` | 引擎能力描述（record：全文/模糊/高亮/聚合/游标/建议/索引），提供 `full()` / `searchOnly()` / `minimal()` 工厂 |
| `SearchEngineRegistry` | 引擎注册中心 — 主引擎 + 降级链，主引擎失败自动降级，`getIndexStrategy()` / `getSuggestStrategy()` 返回 Optional |
| `IndexDocument` | 索引文档模型（引擎无关），含 id/type/title/subtitle/content/snippet/tags/status/path/tenantId/metadata 等 |
| `IndexOperation` | 索引操作（`UPSERT` / `DELETE` / `BULK`），提供 `upsert` / `delete` / `bulkUpsert` 静态工厂 |
| `SearchField` | 搜索字段定义（引擎无关），声明权重/高亮/聚合/排序/分析器，`FieldType` 含 TEXT/KEYWORD/NUMERIC/DATE/BOOLEAN/TAG |

### 2. 业务数据提供者 SPI

| 类 | 说明 |
|---|---|
| `SearchProvider<T>` | 业务数据提供者 SPI — 各业务模块实现，将实体注册到统一搜索（`getType` / `toIndexDocument` / `toSearchHit` / `getFilters` / `loadAll`） |
| `SearchProviderContext` | Provider 上下文（userId / tenantId / roles / deptId / admin），供 Provider 构建权限过滤条件 |
| `SearchProviderRegistry` | Provider 注册中心 — 自动发现 Spring 容器中所有 `SearchProvider` Bean，按类型查找 |
| `ProviderTypeBridge` | Provider 类型桥接工具 — 通过泛型方法签名实现类型擦除安全转换，避免使用 `@SuppressWarnings("unchecked")` |

### 3. 引擎策略实现

| 类 | 说明 |
|---|---|
| `PgSearchStrategy` | PostgreSQL tsvector 引擎 — zhparser/jieba 中文分词、GIN 索引、字段权重、时间衰减；同时实现 `IndexStrategy` + `SuggestStrategy`；不可用时降级到内存引擎 |
| `InMemorySearchStrategy` | 内存引擎（降级兜底）— 最小能力，始终可用 |

> **扩展其他引擎**：通过独立 Starter（如 `ydsz-common-search-es`）实现 `EngineStarterConfigurer`，核心模块会自动发现并注册。

### 4. 核心服务

| 类 | 说明 |
|---|---|
| `UnifiedSearchService` | 统一搜索入口 — 多类型聚合并发检索、Semaphore 并发限流、超时控制、Resilience4j 熔断降级、结果缓存、业务重排、Provider 权限过滤、"您是不是要找" |
| `IndexSyncService` | 索引同步服务 — 异步索引 / 删除、指数退避重试、死信队列（上限 10000）、全量重建 |
| `IndexRebuildService` | 索引重建服务 — 全量重建、蓝绿重建、异步重建、进度跟踪（`getProgressPercent`） |
| `SuggestionService` | 搜索建议服务 — 三层召回：引擎前缀建议 → 热门搜索兜底 → Levenshtein 纠错（按词长自适应最大编辑距离） |
| `SearchCacheService` | 搜索缓存 — MD5 缓存键（过滤条件排序保证顺序无关）、空结果哨兵防穿透（TTL ÷ 3）、惰性淘汰过期条目 |
| `SearchTextProcessor` | 文本处理 — 规范化 + 停用词（通过内部 SearchPipeline）+ 同义词扩展（Trie 树多模式匹配）+ 拼音转换（词典文件加载） |
| `BusinessRanker` | 业务排序器 — 标题命中加权（精确/前缀/包含）、标签命中加权、时间新鲜度加权、类型加权（project/wiki/user/config） |
| `QueryParser` | 查询解析器 |

### 5. 索引同步事件

| 类 | 说明 |
|---|---|
| `IndexSyncListener` | 索引同步事件监听器 — `@EventListener` + `@Async` 异步处理 `IndexOperationEvent`（内部静态事件类，提供 `upsert` / `delete` 工厂） |
| `SearchIndexEventBridge` | 索引事件桥接器 — 业务模块通过此桥接器同步数据，无需直接依赖 `IndexSyncService`（`indexUpsert` / `indexDelete` / `indexSync`） |
| `IndexConsistencyChecker` | 索引一致性巡检器 — 对比 DB 与索引文档数，`ConsistencyReport`（record）报告丢失/冗余，`autoRepair` 自动修复 |

### 6. 内容索引

| 类 | 说明 |
|---|---|
| `ContentIndexer` | 内容索引器 — 文件内容解析后填充 `IndexDocument.content`，超长内容截断（100KB），自动生成摘要（前 200 字符） |
| `ContentExtractor` | 内容提取 SPI — 文档内容解析接口，由业务模块实现（如 PDF/Word/Excel 解析） |

### 7. 搜索分析与质量

| 类 | 说明 |
|---|---|
| `SearchAnalyticsService` | 搜索分析服务 — 热门词 / 零结果词（Redis Sorted Set）、每日搜索量（Redis Hash），Redis 不可用时降级到内存（上限 1000，LRU 淘汰） |
| `SearchQualityTracker` | 搜索质量追踪器 — MRR（平均倒数排名）、CTR（点击率）、零结果率、平均延迟，Redis + 内存双写降级，`QualityReport`（record） |
| `SearchPipeline` | 文本处理管道（Filter 链模式：Normalizer → StopWord → Synonym → ChineseToken → Pinyin），每步可配置启用/禁用 |
| `ZeroResultHandler` | 零结果引导处理器，返回“您是不是要找”、“热门搜索”、“去掉筛选条件”建议 |
| `PersistentDeadLetterQueue` | 基于 PostgreSQL 的持久化死信队列（`ydsz_search_dead_letter` 表），数据源不可用时回退到纯内存模式 |
| `AdvancedQueryParser` | 高级查询解析器，支持 AND/OR/NOT 逻辑组合、引号短语、字段限定等复杂检索表达式 |
| `ChineseTokenizer` | 中文分词器（jieba/ICU/简单空格策略可选） |

### 8. 可观测性

| 类 | 说明 |
|---|---|
| `SearchMetrics` | 搜索指标收集 — Micrometer（Counter / Timer / Gauge）+ 内存计数器降级；`bindGauges` 注册零结果率 / 索引失败率 / 缓存大小 / 熔断器状态 Gauge |
| `SearchHealthIndicator` | 搜索健康检查 — 暴露主引擎状态、能力、所有引擎列表、缓存大小、搜索与索引指标 |

### 9. API 模型

| 类 | 说明 |
|---|---|
| `SearchRequest` | 统一搜索请求（keyword / types / page / pageSize / sortBy / filters / aggregations / highlight / fuzzy / tenantId / userId / roles / deptId / admin / titleOnly / cursor） |
| `SearchResponse` | 统一搜索响应（hits / total / page / pageSize / tookMs / aggregations / suggestion / engine / degraded / nextCursor） |
| `SearchHit` | 搜索命中（id / type / title / subtitle / snippet / highlight / score / path / status / tags / metadata / createdAt / updatedAt） |
| `SearchFilter` | 过滤条件（field / values / operator：EQ/NE/IN/NOT_IN/GT/LT/GTE/LTE/BETWEEN） |
| `SearchAggregation` | 聚合分面结果（field / label / buckets：key/count） |
| `SearchSuggestion` | 搜索建议（type：AUTOCOMPLETE/DID_YOU_MEAN，suggestions，originalInput） |

## 接入方式

### 1. 添加 POM 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-search</artifactId>
</dependency>
```

PG 引擎（默认）还需引入 `ydsz-common-jdbc` 与 `spring-jdbc`；搜索分析/质量追踪需 `ydsz-common-redis`。

### 2. 配置启用

```yaml
ydsz:
  search:
    enabled: true
    primary: pg                    # 主引擎（pg / memory）
    page-size: 20
    search-timeout: 5
    cache:
      enabled: true
      ttl: 60
    pg:
      search-config: search_zh
      index-table: ydsz_search_index
      field-weights:
        title: 1.0
        subtitle: 0.7
        content: 0.4
        tags: 0.2
      time-decay-days: 0
    text-processor:
      synonym-enabled: false
      synonym-file: classpath:synonyms.txt
      pinyin-enabled: false
      pinyin-file: classpath:pinyin.txt
```

自动配置类 `SearchAutoConfiguration` 在 `ydsz.search.enabled=true`（默认缺失视为 true）且 classpath 存在 `SearchStrategy` 时激活，根据 `ydsz.search.primary` 装配对应引擎 Bean，并自动注册 `UnifiedSearchService` / `IndexSyncService` / `IndexRebuildService` / `SuggestionService` / `SearchHealthIndicator` 等核心组件。

### 3. 基础使用

```java
@Resource
private UnifiedSearchService searchService;

public SearchResponse search(String keyword) {
    SearchRequest request = SearchRequest.builder()
            .keyword(keyword)
            .highlight(true)
            .tenantId(TenantContext.getTenantId())
            .userId(UserContext.getUserId())
            .build();
    return searchService.search(request);
}
```

## 配置项

### 通用配置

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.search.enabled` | `true` | 是否启用搜索服务 |
| `ydsz.search.primary` | `pg` | 主引擎名称（pg / memory） |
| `ydsz.search.fallbacks` | `["memory"]` | 降级链（主引擎失败时依次降级的引擎列表） |
| `ydsz.search.page-size` | `20` | 默认每页大小（1~100） |
| `ydsz.search.suggest-limit` | `10` | 搜索建议最大返回数（1~50） |
| `ydsz.search.search-timeout` | `5` | 搜索超时时间秒（1~60） |
| `ydsz.search.max-page-size` | `100` | 最大每页大小（深分页保护，1~500） |
| `ydsz.search.max-page-depth` | `5000` | 最大翻页深度（page × pageSize 上限） |
| `ydsz.search.highlight` | `true` | 是否启用高亮 |
| `ydsz.search.fuzzy` | `true` | 是否启用模糊匹配 |
| `ydsz.search.highlight-pre-tag` | `<em>` | 高亮前置标签 |
| `ydsz.search.highlight-post-tag` | `</em>` | 高亮后置标签 |
| `ydsz.search.highlight-fragment-size` | `120` | 高亮片段最大长度（10~1000） |
| `ydsz.search.fuzzy-min-similarity` | `0.3` | 模糊匹配最小相似度 |

### 缓存配置

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.search.cache.enabled` | `true` | 是否启用搜索结果缓存 |
| `ydsz.search.cache.ttl` | `60` | 缓存存活时间（秒），空结果自动取 1/3 防穿透 |
| `ydsz.search.cache.max-size` | `1000` | 缓存最大条目数 |

### 索引配置

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.search.index.batch-size` | `100` | 批量索引大小 |
| `ydsz.search.index.rebuild-batch-size` | `500` | 全量重建批量大小 |
| `ydsz.search.index.thread-pool-size` | `4` | 索引同步线程池大小 |
| `ydsz.search.index.max-retries` | `3` | 索引操作最大重试次数（0~10） |
| `ydsz.search.index.retry-interval-ms` | `1000` | 重试间隔（毫秒，指数退避） |

### 熔断配置

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.search.circuit-breaker.enabled` | `true` | 是否启用熔断器 |
| `ydsz.search.circuit-breaker.failure-threshold` | `5` | 触发熔断的连续失败次数 |
| `ydsz.search.circuit-breaker.wait-duration` | `30` | 熔断等待时长（秒） |
| `ydsz.search.circuit-breaker.half-open-requests` | `3` | 半开状态探测请求数 |

### 文本处理配置

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.search.text-processor.synonym-enabled` | `false` | 是否启用同义词扩展 |
| `ydsz.search.text-processor.synonym-file` | `classpath:synonyms.txt` | 同义词词典路径（每行一组，逗号分隔） |
| `ydsz.search.text-processor.pinyin-enabled` | `false` | 是否启用拼音转换 |
| `ydsz.search.text-processor.pinyin-file` | `classpath:pinyin.txt` | 拼音词典路径（每行 汉字=拼音） |

### PG 引擎配置

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.search.pg.search-config` | `search_zh` | PG tsvector 搜索配置（search_zh / simple） |
| `ydsz.search.pg.index-table` | `ydsz_search_index` | PG 索引表名 |
| `ydsz.search.pg.field-weights` | title=1.0/subtitle=0.7/content=0.4/tags=0.2 | PG 字段权重 |
| `ydsz.search.pg.time-decay-days` | `0` | 时间衰减半衰期（天，0 表示不衰减） |

## 使用示例

### 示例 1：实现 SearchProvider 接入业务实体

```java
@Component
public class ProjectSearchProvider implements SearchProvider<Project> {
    @Override
    public String getType() {
        return "project";
    }

    @Override
    public IndexDocument toIndexDocument(Project entity) {
        return IndexDocument.builder()
                .id(entity.getId())
                .type("project")
                .title(entity.getProjectName())
                .subtitle(entity.getCustomerName())
                .content(entity.getDescription())
                .tags(entity.getTags())
                .tenantId(entity.getTenantId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @Override
    public List<Project> loadAll(String tenantId) {
        return projectMapper.selectAll(tenantId);
    }

    @Override
    public List<SearchFilter> getFilters(SearchProviderContext context) {
        if (context.isAdmin()) {
            return Collections.emptyList();
        }
        return List.of(SearchFilter.builder()
                .field("tenant_id")
                .values(List.of(context.getTenantId()))
                .operator(SearchFilter.Operator.EQ)
                .build());
    }
}
```

### 示例 2：执行带权限过滤的搜索

```java
@Resource
private UnifiedSearchService searchService;

public SearchResponse searchProjects(String keyword, int page) {
    SearchRequest request = SearchRequest.builder()
            .keyword(keyword)
            .types(List.of("project"))
            .page(page)
            .pageSize(20)
            .highlight(true)
            .fuzzy(true)
            .tenantId(TenantContext.getTenantId())
            .userId(UserContext.getUserId())
            .roles(UserContext.getRoles())
            .build();
    return searchService.search(request);
}
```

### 示例 3：通过事件桥接器同步索引

```java
@RequiredArgsConstructor
public class ProjectServiceImpl {
    private final SearchIndexEventBridge searchIndexBridge;

    public String save(Project dto) {
        Project entity = projectRepository.save(dto);
        searchIndexBridge.indexUpsert("project", entity);
        return entity.getId();
    }

    public boolean removeById(String id) {
        projectRepository.deleteById(id);
        searchIndexBridge.indexDelete("project", id);
        return true;
    }
}
```

### 示例 4：搜索建议与"您是不是要找"

```java
@Resource
private SuggestionService suggestionService;

public List<String> autocomplete(String prefix) {
    return suggestionService.autocomplete(prefix);
}

public List<String> didYouMean(String keyword) {
    return suggestionService.didYouMean(keyword);
}
```

### 示例 5：索引重建与一致性巡检

```java
@Resource
private IndexRebuildService indexRebuildService;
@Resource
private IndexConsistencyChecker consistencyChecker;

public int rebuild(String type, String tenantId) {
    return indexRebuildService.rebuildAll(type, tenantId);
}

public void repairIfNeeded(String tenantId) {
    IndexConsistencyChecker.ConsistencyReport report = consistencyChecker.check(tenantId);
    if (!report.isConsistent()) {
        consistencyChecker.autoRepair(tenantId);
    }
}
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `SearchStrategy` | 搜索引擎策略 — 所有引擎必须实现 | 框架内置 2 种实现（PG/Memory），可通过独立 Starter 扩展 |
| `IndexStrategy` | 索引维护策略 — 需显式索引的引擎实现 | PG 引擎实现 |
| `SuggestStrategy` | 搜索建议策略 — 支持自动补全的引擎实现 | PG 引擎实现 |
| `SearchProvider<T>` | 业务数据提供者 — 将业务实体注册到统一搜索 | 各业务模块实现（如 ProjectSearchProvider / WikiSearchProvider） |
| `ContentExtractor` | 文档内容提取 — 解析文件正文填充索引 | 业务模块实现（如 PDF/Word/Excel 解析器），无实现时仅索引元数据 |

## 健康检查

`SearchHealthIndicator` 自动注册到 Spring Boot Actuator，端点 `GET /actuator/health` 中包含 `search` 指标：

| 暴露字段 | 说明 |
|---|---|
| `primaryEngine` | 主引擎名称（如 `pg`） |
| `available` | 主引擎是否可用 |
| `capability` | 主引擎能力描述（`EngineCapability.toString()`） |
| `engines` | 所有已注册引擎列表及状态（如 `pg(up)`、`memory(up)`） |
| `cacheSize` | 搜索缓存当前条目数 |
| `totalSearches` | 总搜索次数 |
| `zeroResultRate` | 零结果率（百分比字符串） |
| `totalIndexOps` | 总索引操作次数 |
| `indexFailureRate` | 索引操作失败率（百分比字符串） |

健康状态：主引擎可用返回 `UP`，不可用或未配置引擎返回 `DOWN`。

## 注意事项

1. **PG 引擎依赖扩展**：`PgSearchStrategy` 默认使用 `search_zh` 中文分词配置，需在 PG 安装 `zhparser` 或 `jieba` 扩展；若未安装，构造时会自动降级到 `simple` 配置。索引表 DDL 见 `deploy/sql/modules/V1.4.0_search.sql`，需手动执行。
2. **引擎降级**：主引擎不可用或搜索异常时，自动降级到内存引擎，降级结果会标记 `degraded=true`；`memory` 引擎始终可用作兜底。
3. **熔断器保护**：`UnifiedSearchService` 内置 Resilience4j 熔断器，连续失败达 `circuit-breaker.failure-threshold` 次触发熔断（OPEN），等待 `wait-duration` 秒后进入半开（HALF_OPEN）探测，探测成功恢复 CLOSED。
4. **深分页保护**：翻页深度（page × pageSize）超过 `max-page-depth`（默认 5000）时抛出 `IllegalArgumentException`，防止深分页拖垮引擎；支持游标分页的字段可通过 `SearchRequest.cursor` + `SearchResponse.nextCursor` 实现。
5. **缓存防穿透**：空结果使用更短 TTL（正常 TTL ÷ 3）+ 哨兵值，区分"缓存命中空结果"与"缓存未命中"，避免缓存穿透。
6. **同义词与拼音默认关闭**：`text-processor.synonym-enabled` 和 `text-processor.pinyin-enabled` 默认 `false`，需提供词典文件（同义词每行一组逗号分隔；拼音每行 `汉字=拼音`）；词典加载失败不影响搜索功能。
7. **搜索分析需 Redis**：`SearchAnalyticsService` 和 `SearchQualityTracker` 优先使用 Redis 持久化（热门词用 Sorted Set，每日量用 Hash），Redis 不可用时自动降级到内存（上限 1000 条，LRU 淘汰）。
8. **索引同步异步执行**：`SearchIndexEventBridge.indexUpsert` / `indexDelete` 走线程池异步执行，`indexSync` 为同步调用；`IndexSyncService` 内置指数退避重试与死信队列（上限 10000），重试耗尽后可调用 `retryDeadLetterQueue` 重新处理。
9. **类型安全转换**：`ProviderTypeBridge.cast` 通过泛型方法签名实现 Provider 类型转换，避免在业务代码中使用 `@SuppressWarnings("unchecked")`，类型安全性由调用方保证。
10. **模块化引擎扩展**：通过实现 `EngineStarterConfigurer` 并打包为独立 Starter（如 `ydsz-common-search-es`），核心模块会自动发现并注册引擎，无需修改核心代码。

## 变更记录

- **v1.0.2**（2026-08-16）：补充 `SearchPipeline` / `ZeroResultHandler` / `PersistentDeadLetterQueue` / `AdvancedQueryParser` / `ChineseTokenizer` 等组件文档；对齐 `SearchAutoConfiguration` 实际 Bean 清单（22+ 个 Bean）
- **v1.0.1**（2026-08-16）：精简架构 — 移除 ES/Solr/OpenSearch/RediSearch 空壳引擎实现，合并三重内存索引，统一搜索缓存为共享 Bean，合并 SuggestionService 与 EnhancedSuggestionService，清理 Pipeline 占位符 Filter，简化 SearchProvider SPI（合并 getAllDocumentIds + loadById 为 loadAll），重写 README 与代码对齐
- **v1.0.0**（2026-08-02）：初始版本
