package com.njydsz.common.search.elasticsearch;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.njydsz.common.search.api.SearchAggregation;
import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.api.SearchHit;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.EngineCapability;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.IndexStrategy;
import com.njydsz.common.search.core.SearchStrategy;
import com.njydsz.common.search.core.SuggestStrategy;

/**
 * 基于 Elasticsearch Java Client 的搜索策略实现。
 *
 * <p>通过官方 {@code elasticsearch-java} 客户端封装 ES 全文检索、聚合与 completion suggester，
 * 输出标准化 {@link SearchResponse}，与 {@code PgSearchStrategy} / {@code InMemorySearchStrategy} 可互换。
 *
 * <p><b>职责边界</b>：本类仅负责 ES 层查询构造与结果解析；索引同步、缓存、限流等横切关注点由上层 {@code UnifiedSearchService} 承担。
 *
 * <p><b>条件激活</b>：需同时满足 classpath 中存在 {@link ElasticsearchClient} 且配置 {@code ydsz.search.engine = elasticsearch}。
 *
 * <p><b>安全说明</b>：所有过滤值通过 ES Java Client 类型安全 Builder 传递，不存在字符串拼接注入风险。
 *
 * @author ydsz-team
 * @since 26.10.14
 */
@Slf4j
@ConditionalOnClass(ElasticsearchClient.class)
@ConditionalOnProperty(prefix = "ydsz.search", name = "engine", havingValue = "elasticsearch")
public class ElasticsearchSearchStrategy implements SearchStrategy, IndexStrategy, SuggestStrategy {

  private static final String ENGINE_NAME = "elasticsearch";

  /** 默认搜索超时（秒）。 */
  private static final int DEFAULT_TIMEOUT_SECONDS = 5;

  /** 默认聚合桶上限。 */
  private static final int DEFAULT_AGG_SIZE = 50;

  /** 默认建议返回数。 */
  private static final int DEFAULT_SUGGEST_SIZE = 10;

  private final ElasticsearchClient client;

  /** 索引名（默认 "ydsz_search_index"）。 */
  private final String indexName;

  /** 当前可用标记。 */
  private volatile boolean available;

  /**
   * 构造 ES 搜索策略。
   *
   * @param client ES 客户端
   * @param properties 搜索配置
   */
  @Autowired
  public ElasticsearchSearchStrategy(ElasticsearchClient client, SearchProperties properties) {
    this.client = client;
    if (properties != null && properties.getElasticsearch() != null
        && properties.getElasticsearch().getIndexName() != null) {
      this.indexName = properties.getElasticsearch().getIndexName();
    } else {
      this.indexName = "ydsz_search_index";
    }
    this.available = checkAvailability();
  }

  /** 供测试使用的包级构造函数。 */
  ElasticsearchSearchStrategy(ElasticsearchClient client, String indexName) {
    this.client = client;
    this.indexName = indexName;
    this.available = true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public SearchResponse search(SearchRequest request) {
    if (!available) {
      log.info("[EsSearchStrategy] 引擎不可用: keyword={}", request.getKeyword());
      return SearchResponse.empty(request.getPage(), request.getPageSize());
    }
    long start = System.currentTimeMillis();
    try {
      String keyword = request.getKeyword();
      if (keyword == null || keyword.isBlank()) {
        return SearchResponse.empty(request.getPage(), request.getPageSize());
      }

      BoolQuery boolQuery = buildBoolQuery(request, keyword);
      int from = request.getOffset() > 0 ? request.getOffset() : (request.getPage() - 1) * request.getPageSize();
      int size = request.getPageSize();

      var esRequest = co.elastic.clients.elasticsearch.core.SearchRequest.of(sr -> {
        sr.index(indexName)
            .query(q -> q.bool(boolQuery))
            .from(from)
            .size(size)
            .timeout(DEFAULT_TIMEOUT_SECONDS + "s");

        if (request.isHighlight()) {
          String pre = request.getHighlightPreTag() != null ? request.getHighlightPreTag() : "<em>";
          String post = request.getHighlightPostTag() != null ? request.getHighlightPostTag() : "</em>";
          sr.highlight(h -> h
              .fields("title", f -> f)
              .fields("snippet", f -> f)
              .preTags(pre)
              .postTags(post));
        }

        if (request.getAggregations() != null && !request.getAggregations().isEmpty()) {
          for (SearchAggregation agg : request.getAggregations()) {
            if (agg.getField() != null && !agg.getField().isBlank()) {
              sr.aggregations(agg.getField(), a -> a.terms(t -> t.field(agg.getField()).size(DEFAULT_AGG_SIZE)));
            }
          }
        }

        if (request.getSortBy() != null && !request.getSortBy().isBlank()) {
          sr.sort(s -> s.field(f -> f.field(request.getSortBy())
              .order(request.isAscending() ? SortOrder.Asc : SortOrder.Desc)));
        } else {
          sr.sort(s -> s.field(f -> f.field("_score").order(SortOrder.Desc)));
        }

        return sr;
      });

      var esResponse = client.search(esRequest, Map.class);
      List<Hit<Map>> hits = esResponse.hits().hits();
      long total = esResponse.hits().total() != null ? esResponse.hits().total().value() : 0L;

      List<SearchHit> searchHits = new ArrayList<>(hits.size());
      for (Hit<Map> hit : hits) {
        searchHits.add(convertHit(hit));
      }

      List<SearchAggregation> aggregations = Collections.emptyList();
      if (request.getAggregations() != null && !request.getAggregations().isEmpty()) {
        aggregations = parseAggregations(esResponse, request.getAggregations());
      }

      long took = System.currentTimeMillis() - start;

      String nextCursor = null;
      if (request.getSortBy() == null && searchHits.size() == request.getPageSize()) {
        SearchHit last = searchHits.get(searchHits.size() - 1);
        if (last.getScore() > 0) {
          nextCursor = Base64.getUrlEncoder().withoutPadding()
              .encodeToString((last.getScore() + ":" + last.getId()).getBytes(StandardCharsets.UTF_8));
        }
      }

      return SearchResponse.builder()
          .hits(searchHits)
          .total(total)
          .page(request.getPage())
          .pageSize(request.getPageSize())
          .tookMs(took)
          .aggregations(aggregations)
          .engine(ENGINE_NAME)
          .nextCursor(nextCursor)
          .build();

    } catch (Exception e) {
      log.error("[EsSearchStrategy] 搜索失败: keyword={}", request.getKeyword(), e);
      this.available = false;
      return SearchResponse.empty(request.getPage(), request.getPageSize());
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public SearchSuggestion suggest(String prefix, int limit) {
    if (prefix == null || prefix.isBlank() || !available) {
      return SearchSuggestion.builder()
          .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
          .items(Collections.emptyList())
          .originalInput(prefix)
          .build();
    }
    try {
      int suggestSize = limit > 0 ? Math.min(limit, 50) : DEFAULT_SUGGEST_SIZE;
      var suggestRequest = co.elastic.clients.elasticsearch.core.SearchRequest.of(sr -> sr
          .index(indexName)
          .suggest(s -> s
              .suggesters("title-suggest", fs -> fs
                  .completion(c -> c
                      .field("title_suggest")
                      .size(suggestSize)
                      .skipDuplicates(true))
                  .prefix(prefix)))
          .size(0)
          .timeout("3s"));

      var response = client.search(suggestRequest, Map.class);
      var suggestMap = response.suggest();
      List<SearchSuggestion.SuggestionItem> items = new ArrayList<>(8);

      if (suggestMap != null && suggestMap.containsKey("title-suggest")) {
        var entries = suggestMap.get("title-suggest");
        for (var entry : entries) {
          var completion = entry.completion();
          if (completion != null) {
            for (var option : completion.options()) {
              items.add(new SearchSuggestion.SuggestionItem(
                  option.text(), SearchSuggestion.SuggestionSource.ENGINE));
            }
          }
        }
      }

      return SearchSuggestion.builder()
          .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
          .items(items)
          .originalInput(prefix)
          .build();
    } catch (Exception e) {
      log.warn("[EsSearchStrategy] 搜索建议失败: prefix={}", prefix, e);
      return SearchSuggestion.builder()
          .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
          .items(Collections.emptyList())
          .originalInput(prefix)
          .build();
    }
  }

  @Override
  public void index(IndexDocument document) {
    if (document == null || document.getId() == null || !available) {
      return;
    }
    try {
      client.index(i -> i.index(indexName).id(document.getId()).document(buildDocument(document)));
      log.debug("[EsSearchStrategy] 索引写入成功: id={}", document.getId());
    } catch (Exception e) {
      log.warn("[EsSearchStrategy] 索引写入失败: id={}", document.getId(), e);
    }
  }

  @Override
  public void bulkIndex(List<IndexDocument> documents) {
    if (documents == null || documents.isEmpty() || !available) {
      return;
    }
    try {
      var bulkRequest = co.elastic.clients.elasticsearch.core.BulkRequest.of(br -> {
        for (IndexDocument doc : documents) {
          if (doc.getId() != null) {
            Map<String, Object> docMap = buildDocument(doc);
            br.operations(op -> op.index(idx -> idx.index(indexName).id(doc.getId()).document(docMap)));
          }
        }
        return br;
      });
      var response = client.bulk(bulkRequest);
      if (response.errors()) {
        log.warn("[EsSearchStrategy] 批量索引部分失败");
      }
    } catch (Exception e) {
      log.warn("[EsSearchStrategy] 批量索引异常: count={}", documents.size(), e);
    }
  }

  @Override
  public void deleteIndex(String type, String documentId) {
    if (!available) {
      return;
    }
    try {
      if (type != null && !type.isBlank()) {
        client.deleteByQuery(d -> d.index(indexName)
            .query(q -> q.bool(b -> b
                .must(m -> m.term(t -> t.field("_id").value(documentId)))
                .must(m -> m.term(t -> t.field("doc_type").value(type))))));
      } else {
        client.delete(d -> d.index(indexName).id(documentId));
      }
    } catch (Exception e) {
      log.warn("[EsSearchStrategy] 删除索引失败: id={}, type={}", documentId, type, e);
    }
  }

  @Override
  public void deleteAllIndices(String type) {
    if (!available) {
      return;
    }
    try {
      if (type == null || type.isBlank()) {
        client.deleteByQuery(d -> d.index(indexName)
            .query(q -> q.matchAll(m -> m)));
      } else {
        client.deleteByQuery(d -> d.index(indexName)
            .query(q -> q.term(t -> t.field("doc_type").value(type))));
      }
    } catch (Exception e) {
      log.warn("[EsSearchStrategy] 清空索引失败: type={}", type, e);
    }
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
    return EngineCapability.full();
  }

  // ==================== 私有方法 ====================

  /**
   * 构建 ES 文档 Map。
   *
   * @param doc 索引文档
   * @return 文档字段映射
   */
  private Map<String, Object> buildDocument(IndexDocument doc) {
    Map<String, Object> map = new HashMap<>(16);
    map.put("doc_type", nullToEmpty(doc.getType()));
    map.put("title", nullToEmpty(doc.getTitle()));
    map.put("subtitle", nullToEmpty(doc.getSubtitle()));
    map.put("content", nullToEmpty(doc.getContent()));
    map.put("snippet", nullToEmpty(doc.getSnippet()));
    map.put("tags", doc.getTags() != null ? doc.getTags() : Collections.emptyList());
    map.put("status", nullToEmpty(doc.getStatus()));
    map.put("path", nullToEmpty(doc.getPath()));
    map.put("tenant_id", nullToEmpty(doc.getTenantId()));
    map.put("created_by", nullToEmpty(doc.getCreatedBy()));
    map.put("created_at", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : Instant.now().toString());
    map.put("updated_by", nullToEmpty(doc.getUpdatedBy()));
    map.put("updated_at", doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : Instant.now().toString());
    map.put("searchable_text", buildSearchableText(doc));
    map.put("metadata", doc.getMetadata() != null ? doc.getMetadata() : Collections.emptyMap());
    return map;
  }

  /**
   * 构建 ES BoolQuery。
   */
  private BoolQuery buildBoolQuery(SearchRequest request, String keyword) {
    return BoolQuery.of(b -> {
      if (request.isTitleOnly()) {
        b.must(m -> m.match(t -> t.field("title").query(keyword)));
      } else {
        b.must(m -> m.multiMatch(mm -> mm
            .query(keyword)
            .fields("title^1.0", "subtitle^0.7", "content^0.4", "tags^0.2")));
      }

      if (request.isFuzzy()) {
        b.should(s -> s.match(t -> t.field("searchable_text").query(keyword).fuzziness("AUTO")));
      }

      if (request.getFilters() != null) {
        for (SearchFilter filter : request.getFilters()) {
          applyFilter(b, filter);
        }
      }

      if (request.getTenantId() != null && !request.getTenantId().isBlank()) {
        b.filter(f -> f.term(t -> t.field("tenant_id").value(request.getTenantId())));
      }

      if (request.getTypes() != null && !request.getTypes().isEmpty()) {
        b.filter(f -> f.terms(t -> t.field("doc_type").terms(tv -> tv.value(
            request.getTypes().stream()
                .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                .toList()))));
      }

      return b;
    });
  }

  /**
   * 将 ES Hit 转换为统一 SearchHit。
   */
  @SuppressWarnings("unchecked")
  private SearchHit convertHit(Hit<Map> hit) {
    Map<String, Object> source = hit.source();
    if (source == null) {
      return SearchHit.builder()
          .id(hit.id())
          .score(hit.score() != null ? hit.score().floatValue() : 0.0f)
          .build();
    }

    SearchHit.SearchHitBuilder builder = SearchHit.builder()
        .id(hit.id())
        .score(hit.score() != null ? hit.score().floatValue() : 0.0f);

    if (source.get("title") instanceof String v) builder.title(v);
    if (source.get("subtitle") instanceof String v) builder.subtitle(v);
    if (source.get("snippet") instanceof String v) builder.snippet(v);
    if (source.get("doc_type") instanceof String v) builder.type(v);
    if (source.get("status") instanceof String v) builder.status(v);
    if (source.get("path") instanceof String v) builder.path(v);
    if (source.get("tags") instanceof List<?> rawTags) {
      List<String> tagList = new ArrayList<>(rawTags.size());
      for (Object tag : rawTags) {
        if (tag instanceof String s) tagList.add(s);
      }
      builder.tags(tagList);
    }

    if (hit.highlight() != null && !hit.highlight().isEmpty()) {
      List<String> snippetHighlights = hit.highlight().get("snippet");
      if (snippetHighlights != null && !snippetHighlights.isEmpty()) {
        builder.highlight(snippetHighlights.get(0));
      } else {
        List<String> titleHighlights = hit.highlight().get("title");
        if (titleHighlights != null && !titleHighlights.isEmpty()) {
          builder.highlight(titleHighlights.get(0));
        }
      }
    }

    return builder.build();
  }

  /**
   * 解析聚合结果。
   */
  private List<SearchAggregation> parseAggregations(
      co.elastic.clients.elasticsearch.core.SearchResponse<Map> response,
      List<SearchAggregation> aggConfigs) {
    List<SearchAggregation> result = new ArrayList<>(aggConfigs.size());
    for (SearchAggregation agg : aggConfigs) {
      String field = agg.getField();
      if (field == null || field.isBlank()) {
        continue;
      }
      try {
        var termsAgg = response.aggregations().get(field);
        if (termsAgg != null && termsAgg.isSterms()) {
          var buckets = termsAgg.sterms().buckets().array();
          List<SearchAggregation.Bucket> bucketList = new ArrayList<>(buckets.size());
          for (var bucket : buckets) {
            bucketList.add(SearchAggregation.Bucket.builder()
                .key(bucket.key().stringValue())
                .count(bucket.docCount())
                .build());
          }
          result.add(SearchAggregation.builder()
              .field(field).label(field).buckets(bucketList).build());
        }
      } catch (Exception e) {
        log.warn("[EsSearchStrategy] 聚合解析失败: field={}", field, e);
      }
    }
    return result;
  }

  /**
   * 应用过滤条件到 ES BoolQuery.Builder。
   */
  private void applyFilter(BoolQuery.Builder bool, SearchFilter filter) {
    if (filter == null || filter.getField() == null) {
      return;
    }
    String field = filter.getField();
    var op = filter.getOperator();
    try {
      switch (op) {
        case EQ -> {
          if (filter.getValues() != null && !filter.getValues().isEmpty()) {
            bool.filter(f -> f.term(t -> t.field(field).value(filter.getValues().get(0))));
          }
        }
        case NE -> {
          if (filter.getValues() != null && !filter.getValues().isEmpty()) {
            bool.mustNot(m -> m.term(t -> t.field(field).value(filter.getValues().get(0))));
          }
        }
        case IN -> {
          if (filter.getValues() != null && !filter.getValues().isEmpty()) {
            bool.filter(f -> f.terms(t -> t.field(field).terms(tv -> tv.value(
                filter.getValues().stream()
                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                    .toList()))));
          }
        }
        case GT -> {
          if (filter.getValues() != null && !filter.getValues().isEmpty()) {
            bool.filter(f -> f.range(r -> r.untyped(u -> u.field(field).gt(JsonData.of(filter.getValues().get(0))))));
          }
        }
        case LT -> {
          if (filter.getValues() != null && !filter.getValues().isEmpty()) {
            bool.filter(f -> f.range(r -> r.untyped(u -> u.field(field).lt(JsonData.of(filter.getValues().get(0))))));
          }
        }
        case GTE -> {
          if (filter.getValues() != null && !filter.getValues().isEmpty()) {
            bool.filter(f -> f.range(r -> r.untyped(u -> u.field(field).gte(JsonData.of(filter.getValues().get(0))))));
          }
        }
        case LTE -> {
          if (filter.getValues() != null && !filter.getValues().isEmpty()) {
            bool.filter(f -> f.range(r -> r.untyped(u -> u.field(field).lte(JsonData.of(filter.getValues().get(0))))));
          }
        }
        default -> {
          // 未知操作符：忽略
        }
      }
    } catch (Exception e) {
      log.warn("[EsSearchStrategy] 过滤条件应用失败: field={}, op={}", field, op, e);
    }
  }

  /** 构建可搜索文本。 */
  private String buildSearchableText(IndexDocument doc) {
    StringBuilder sb = new StringBuilder();
    if (doc.getTitle() != null) sb.append(doc.getTitle());
    if (doc.getSubtitle() != null) sb.append(' ').append(doc.getSubtitle());
    if (doc.getContent() != null) sb.append(' ').append(doc.getContent());
    if (doc.getTags() != null) {
      for (String tag : doc.getTags()) {
        sb.append(' ').append(tag);
      }
    }
    return sb.toString().trim();
  }

  /** 检查 ES 集群可用性。 */
  private boolean checkAvailability() {
    try {
      var health = client.cluster().health(h -> h.index(List.of(indexName)));
      boolean ok = health.status() != HealthStatus.Red;
      log.info("[EsSearchStrategy] ES 健康检查: status={}, index={}", health.status(), indexName);
      return ok;
    } catch (Exception e) {
      log.warn("[EsSearchStrategy] ES 健康检查失败: {}", e.getMessage());
      return false;
    }
  }

  private String nullToEmpty(String value) {
    return value != null ? value : "";
  }
}
