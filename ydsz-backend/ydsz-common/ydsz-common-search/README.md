# ydsz-common-search

统一搜索服务框架 — 基于**策略模式**的多搜索引擎抽象层。

## 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                   业务模块 (nextwiki / workflow / ...)    │
│                   实现 SearchProvider<T>                 │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│              UnifiedSearchService                        │
│    (搜索入口 / 缓存 / 熔断 / 多类型聚合 / 并发限流)        │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│           SearchEngineRegistry                           │
│    (主引擎 + 降级链 / 能力查询)                           │
└──────────────────────┬──────────────────────────────────┘
                       │
          ┌────────────┼────────────┬────────────┬────────────┐
          ▼            ▼            ▼            ▼            ▼
   ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
   │ PgSearch │ │  ES      │ │ RediSrch │ │  Solr    │ │ Memory   │
   │ Strategy │ │ Strategy │ │ Strategy │ │ Strategy │ │ Strategy │
   └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘
```

## 核心接口

| 接口 | 职责 |
|------|------|
| `SearchStrategy` | 搜索 SPI — 所有引擎必须实现 |
| `IndexStrategy` | 索引维护 SPI — 需显式索引的引擎实现 |
| `SuggestStrategy` | 搜索建议 SPI — 支持自动补全的引擎实现 |
| `EngineCapability` | 引擎能力描述（record） |
| `SearchEngineRegistry` | 引擎注册中心 — 主引擎 + 降级链 |
| `SearchProvider<T>` | 业务数据提供者 SPI — 各业务模块实现 |

## 支持的搜索引擎

| 引擎 | 策略类 | 能力 | 激活条件 |
|------|--------|------|----------|
| PostgreSQL | `PgSearchStrategy` | 全能力 | `ydsz.search.primary=pg`（默认） |
| Memory | `InMemorySearchStrategy` | 最小能力 | 始终可用（降级兜底） |
| Elasticsearch | `ElasticsearchSearchStrategy` | 全能力 | `ydsz.search.primary=es` |
| RediSearch | `RediSearchStrategy` | 搜索+建议 | `ydsz.search.primary=redis` |
| Apache Solr | `SolrSearchStrategy` | 全能力 | `ydsz.search.primary=solr` |
| OpenSearch | `OpenSearchStrategy` | 全能力 | `ydsz.search.primary=opensearch` |

## 配置示例

```yaml
ydsz:
  search:
    enabled: true
    primary: pg                    # 主引擎
    fallbacks: [memory]            # 降级链
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
    es:
      host: localhost
      port: 9200
      index-name: ydsz_search
```

## 业务模块接入

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-search</artifactId>
</dependency>
```

### 2. 实现 SearchProvider

```java
@Component
public class ProjectSearchProvider implements SearchProvider<ProjectDO> {
    @Override
    public String getType() { return "project"; }

    @Override
    public IndexDocument toIndexDocument(ProjectDO entity) {
        return IndexDocument.builder()
            .id(entity.getId())
            .type("project")
            .title(entity.getProjectName())
            .subtitle(entity.getCustomerName())
            .content(entity.getDescription())
            .tenantId(entity.getTenantId())
            .build();
    }

    @Override
    public List<SearchFilter> getFilters(SearchProviderContext context) {
        // 返回权限过滤条件
        return List.of(SearchFilter.eq("tenant_id", context.getTenantId()));
    }
}
```

### 3. 执行搜索

```java
@Resource
private UnifiedSearchService searchService;

public SearchResponse search(String keyword) {
    SearchRequest request = SearchRequest.builder()
        .keyword(keyword)
        .highlight(true)
        .build();
    return searchService.search(request);
}
```

## DDL

PG 索引表 DDL 见 `deploy/sql/modules/V1.4.0_search.sql`，需手动执行。

## 包结构

```
com.njydsz.common.search
├── api/                          # API 模型（SearchRequest/Response/Hit/...）
├── core/                         # 核心 SPI
│   ├── SearchStrategy            # 搜索策略接口
│   ├── IndexStrategy             # 索引策略接口
│   ├── SuggestStrategy           # 建议策略接口
│   ├── EngineCapability          # 引擎能力描述
│   ├── SearchEngineRegistry      # 引擎注册中心
│   ├── IndexDocument             # 索引文档（引擎无关）
│   └── SearchField               # 搜索字段定义
├── engine/                       # 引擎策略实现
│   ├── pg/PgSearchStrategy       # PostgreSQL tsvector
│   ├── memory/InMemorySearchStrategy  # 内存（降级）
│   ├── es/ElasticsearchSearchStrategy  # Elasticsearch
│   ├── redis/RediSearchStrategy       # RediSearch
│   ├── solr/SolrSearchStrategy        # Apache Solr
│   └── opensearch/OpenSearchStrategy  # OpenSearch
├── service/                      # 业务服务
│   ├── UnifiedSearchService      # 统一搜索入口
│   ├── IndexSyncService          # 索引同步
│   ├── IndexRebuildService       # 索引重建
│   ├── SuggestionService         # 搜索建议
│   ├── SearchCacheService        # 搜索缓存
│   └── SearchTextProcessor       # 文本处理（同义词/拼音/停用词）
├── provider/                     # 数据提供者 SPI
│   ├── SearchProvider            # Provider 接口
│   ├── SearchProviderRegistry    # Provider 注册中心
│   └── SearchProviderContext     # Provider 上下文
├── config/                       # 配置
│   ├── SearchProperties          # 配置属性
│   └── SearchAutoConfiguration   # 自动配置
├── health/                       # 健康检查
├── metrics/                      # 指标监控
├── indexer/                      # 内容索引
├── analytics/                    # 搜索分析
└── sync/                         # 索引同步事件
```
