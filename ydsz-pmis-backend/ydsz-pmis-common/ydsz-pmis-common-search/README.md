# ydsz-pmis-common-search

> YDSZ PMIS 统一搜索服务框架 — PG tsvector + zhparser 中文分词 + SPI 多引擎抽象 + 索引同步 + 搜索建议 + 聚合分面

## 核心特性

- **中文分词搜索**：基于 PostgreSQL zhparser 扩展，支持中文关键词分词检索
- **SPI 多引擎架构**：SearchEngine SPI 支持 PG tsvector / Elasticsearch / 内存引擎自由切换
- **统一搜索 API**：SearchRequest/SearchResponse 统一模型，跨实体搜索一行调用
- **搜索提供者 SPI**：各业务模块实现 SearchProvider 即可接入统一搜索
- **高亮支持**：`ts_headline()` 生成 `<em>` 高亮片段
- **模糊匹配**：pg_trgm 扩展实现 typo tolerance（容错搜索）
- **聚合分面**：按类型/标签/状态等维度聚合统计
- **搜索建议**：自动补全 + "您是不是要找"纠错建议
- **索引同步**：Spring Event 异步索引同步 + 全量重建
- **文档内容索引**：可选集成 common-docs 解析 PDF/Word/Excel 内容索引
- **搜索分析**：热门搜索词、零结果率、每日搜索量统计
- **指标监控**：Micrometer QPS/P99延迟/命中率/零结果率
- **健康检查**：Spring Boot Actuator HealthIndicator
- **降级容错**：PG 不可用时自动降级到内存索引；搜索服务异常时降级到原 SQL

## 快速接入

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-search</artifactId>
</dependency>
```

### 2. 配置

```yaml
ydsz:
  search:
    engine: pg          # pg / memory / es
    highlight: true
    fuzzy: true
    page-size: 20
    suggest-limit: 10
    cache:
      enabled: true
      ttl: 60
    index:
      sync-mode: event
      batch-size: 100
```

### 3. 初始化数据库

执行 `deploy/sql/modules/search_index_init.sql`

### 4. 实现 SearchProvider

```java
@Component
public class ProjectSearchProvider implements SearchProvider<InitiationDO> {
    @Override
    public String getType() { return "project"; }

    @Override
    public IndexDocument toIndexDocument(InitiationDO entity) {
        return IndexDocument.builder()
            .id(entity.getId())
            .type("project")
            .title(entity.getProjectName())
            .subtitle(entity.getCustomerName())
            .build();
    }
}
```

### 5. 使用搜索

```java
// 注入统一搜索服务
@Autowired
private UnifiedSearchService searchService;

// 搜索
SearchResponse response = searchService.search(
    SearchRequest.of("项目管理", 1, 20)
);

// 搜索建议
List<String> suggestions = searchService.suggest("项目").getSuggestions();
```

### 6. 索引同步

```java
// 发布索引同步事件
eventPublisher.publishEvent(
    IndexSyncListener.IndexOperationEvent.upsert(document)
);

// 删除索引
eventPublisher.publishEvent(
    IndexSyncListener.IndexOperationEvent.delete("project", "123")
);
```

## 架构设计

```
┌─────────────────────────────────────────────────────┐
│              ydsz-pmis-common-search                 │
│                                                      │
│  SearchEngine (SPI)  ←  SearchProvider (SPI)         │
│       ↑                          ↑                   │
│       │                          │                   │
│  PgSearchEngine          ProjectSearchProvider       │
│  InMemorySearchEngine    WikiSearchProvider          │
│                                                      │
│  UnifiedSearchService    IndexSyncService            │
│  SuggestionService       IndexRebuildService         │
│  SearchAnalyticsService  SearchMetrics               │
│                                                      │
│  ← common-cache (结果缓存)                           │
│  ← common-docs  (内容索引)                           │
│  ← common-redis (热词)                               │
└─────────────────────────────────────────────────────┘
```

## 模块清单

| 包 | 说明 |
|---|---|
| `api` | 搜索 API 契约（SearchRequest/Response/Hit/Filter/Aggregation/Suggestion） |
| `core` | 搜索引擎 SPI（SearchEngine）+ 索引模型（IndexDocument/IndexOperation/SearchField） |
| `provider` | 搜索提供者 SPI（SearchProvider）+ 注册中心（SearchProviderRegistry） |
| `engine.pg` | PostgreSQL tsvector + zhparser 搜索引擎实现 |
| `engine.memory` | 内存搜索引擎（测试/降级用） |
| `service` | 统一搜索服务 + 索引同步 + 索引重建 + 搜索建议 |
| `sync` | 索引同步事件监听器 |
| `indexer` | 文档内容索引器（可选集成 common-docs） |
| `analytics` | 搜索分析服务（热词/零结果率/每日统计） |
| `metrics` | Micrometer 搜索指标 |
| `health` | 搜索引擎健康检查 |
| `config` | 自动配置 + 属性 |

## 技术选型

| 决策点 | 方案 | 理由 |
|---|---|---|
| 搜索引擎 | PG tsvector + zhparser | 复用现有 PG，避免引入 ES 运维成本 |
| 中文分词 | zhparser（基于 SCWS） | PG 原生集成，无需额外服务 |
| 模糊匹配 | pg_trgm + similarity() | PG 原生扩展，支持编辑距离匹配 |
| 搜索建议 | PG ILIKE + Redis ZSET | 复用现有 Redis，热词实时更新 |
| 索引同步 | Spring Event + 线程池 | 小规模用 Event，大规模切换到 MQ |
| 降级策略 | 内存索引 + SQL LIKE | 多级降级保证可用性 |
