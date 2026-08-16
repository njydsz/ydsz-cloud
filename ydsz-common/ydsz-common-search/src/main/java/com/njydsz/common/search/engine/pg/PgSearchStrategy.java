package com.njydsz.common.search.engine.pg;

import com.njydsz.common.json.YdszJson;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 基于 PostgreSQL tsvector 的搜索策略实现。
 *
 * <p>利用 PostgreSQL 原生全文检索能力（tsvector / tsquery / GIN 索引）， 支持中文分词（zhparser/jieba）、高亮、相关性排序和时间衰减。
 *
 * <p><b>职责边界</b>：本类作为主搜索引擎，仅负责 PostgreSQL 层的索引与查询。 内存降级搜索由 {@code InMemorySearchStrategy}
 * 独立承担，不在本类中冗余实现。
 *
 * <h3>架构变更（refactor-v2）</h3>
 *
 * <ul>
 *   <li>移除内部 {@code memoryIndex} / {@code invertedIndex} 内存索引，统一由 InMemorySearchStrategy 承担
 *   <li>引擎不可用时 {@code search()} 返回空结果而非降级自搜，由 {@code SearchEngineRegistry} 统一降级
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class PgSearchStrategy implements SearchStrategy, IndexStrategy, SuggestStrategy {

  private static final String ENGINE_NAME = "pg";
  private static final String DEFAULT_SEARCH_CONFIG = "search_zh";
  private static final String FALLBACK_SEARCH_CONFIG = "simple";
  private static final Set<String> ALLOWED_COLUMNS =
      Set.of(
          "id",
          "doc_type",
          "title",
          "subtitle",
          "content",
          "snippet",
          "tags",
          "status",
          "path",
          "tenant_id",
          "created_by",
          "created_at",
          "updated_by",
          "updated_at",
          "searchable_text",
          "metadata",
          "created_at_ts",
          "updated_at_ts");

  private final JdbcTemplate jdbcTemplate;
  private final String searchConfig;
  private final SearchProperties.PgConfig pgConfig;
  private final String indexTable;
  private volatile boolean available;
  private final ThreadPoolTaskScheduler probeScheduler;

  public PgSearchStrategy(DataSource dataSource, SearchProperties.PgConfig pgConfig) {
    this(new JdbcTemplate(dataSource), pgConfig);
  }

  public PgSearchStrategy(JdbcTemplate jdbcTemplate, SearchProperties.PgConfig pgConfig) {
    this.jdbcTemplate = jdbcTemplate;
    this.pgConfig = pgConfig;
    this.indexTable = pgConfig.getIndexTable();
    this.searchConfig = detectSearchConfig();
    this.available = checkAvailability();
    this.probeScheduler = new ThreadPoolTaskScheduler();
    this.probeScheduler.setPoolSize(1);
    this.probeScheduler.setThreadNamePrefix("pg-search-probe-");
    this.probeScheduler.setDaemon(true);
    this.probeScheduler.setWaitForTasksToCompleteOnShutdown(false);
    this.probeScheduler.initialize();
    startRecoveryProbe();
  }

  @Override
  public SearchResponse search(SearchRequest request) {
    if (!available) {
      log.info("[PgSearchStrategy] 引擎不可用，跳过搜索，由降级链处理: keyword={}", request.getKeyword());
      return SearchResponse.empty(request.getPage(), request.getPageSize());
    }
    long start = System.currentTimeMillis();
    try {
      String keyword = sanitizeKeyword(request.getKeyword());
      if (keyword == null || keyword.isBlank()) {
        return SearchResponse.empty(request.getPage(), request.getPageSize());
      }

      List<Object> whereParams = new ArrayList<>();
      StringBuilder where = new StringBuilder(" WHERE 1=1");

      if (request.isFuzzy()) {
        where.append(" AND (to_tsvector(?, searchable_text) @@ plainto_tsquery(?, ?)");
        where.append(" OR searchable_text % ?)");
        whereParams.add(searchConfig);
        whereParams.add(searchConfig);
        whereParams.add(keyword);
        whereParams.add(keyword);
      } else {
        where.append(" AND to_tsvector(?, searchable_text) @@ plainto_tsquery(?, ?)");
        whereParams.add(searchConfig);
        whereParams.add(searchConfig);
        whereParams.add(keyword);
      }

      if (request.getFilters() != null) {
        for (SearchFilter filter : request.getFilters()) {
          appendFilter(where, whereParams, filter);
        }
      }
      if (request.getTenantId() != null && !request.getTenantId().isBlank()) {
        where.append(" AND tenant_id = ?");
        whereParams.add(request.getTenantId());
      }
      if (request.getTypes() != null && !request.getTypes().isEmpty()) {
        where
            .append(" AND doc_type IN (")
            .append(request.getTypes().stream().map(t -> "?").collect(Collectors.joining(",")))
            .append(")");
        whereParams.addAll(request.getTypes());
      }

      String countSql = "SELECT COUNT(1) FROM " + indexTable + where;
      Long total = jdbcTemplate.queryForObject(countSql, Long.class, whereParams.toArray());

      if (total == null || total == 0) {
        long took = System.currentTimeMillis() - start;
        return SearchResponse.builder()
            .hits(Collections.emptyList())
            .total(0L)
            .page(request.getPage())
            .pageSize(request.getPageSize())
            .tookMs(took)
            .engine(ENGINE_NAME)
            .build();
      }

      List<Object> queryParams = new ArrayList<>();
      StringBuilder selectSql =
          new StringBuilder("SELECT id, doc_type, title, subtitle, snippet, status, ");

      selectSql.append("ts_rank(");
      selectSql.append("setweight(to_tsvector(?, title), 'A') || ");
      selectSql.append("setweight(to_tsvector(?, subtitle), 'B') || ");
      selectSql.append("setweight(to_tsvector(?, content), 'C') || ");
      selectSql.append("setweight(to_tsvector(?, array_to_string(tags, ', ')), 'D'), ");
      selectSql.append("plainto_tsquery(?, ?)) AS rank");
      queryParams.add(searchConfig);
      queryParams.add(searchConfig);
      queryParams.add(searchConfig);
      queryParams.add(searchConfig);
      queryParams.add(searchConfig);
      queryParams.add(keyword);

      if (request.isHighlight()) {
        selectSql.append(", ts_headline(?, searchable_text, plainto_tsquery(?, ?), ");
        selectSql.append(
            "'MaxWords=60, MinWords=20, ShortWord=3, HighlightAll=FALSE, StartSel=' || ? || ', StopSel=' || ?) AS highlight");
        queryParams.add(searchConfig);
        queryParams.add(searchConfig);
        queryParams.add(keyword);
        queryParams.add(request.getHighlightPreTag());
        queryParams.add(request.getHighlightPostTag());
      }

      selectSql.append(" FROM ").append(indexTable).append(where);
      queryParams.addAll(whereParams);

      // Keyset 分页支持 — 游标存在时使用键集分页替代 OFFSET
      CursorParseResult cursorResult = null;
      boolean useKeysetPagination = false;
      if (request.getCursor() != null && !request.getCursor().isBlank()) {
        cursorResult = parseCursor(request.getCursor());
        if (cursorResult != null) {
          useKeysetPagination = true;
          appendKeysetFilter(selectSql, queryParams, cursorResult, request);
        }
      }

      if (request.getSortBy() != null && !request.getSortBy().isBlank()) {
        String direction = request.isAscending() ? "ASC" : "DESC";
        selectSql
            .append(" ORDER BY ")
            .append(sanitizeColumnName(request.getSortBy()))
            .append(" ")
            .append(direction);
      } else if (pgConfig.getTimeDecayDays() > 0) {
        selectSql.append(
            " ORDER BY (rank * EXP(-EXTRACT(EPOCH FROM (NOW() - updated_at_ts)) / 86400.0 / ? * LN(2))) DESC, updated_at DESC");
        queryParams.add(pgConfig.getTimeDecayDays());
      } else {
        selectSql.append(" ORDER BY rank DESC, updated_at DESC");
      }

      // 限制最大返回数，防止异常游标拉取过多数据
      int fetchSize = request.getPageSize();
      if (useKeysetPagination) {
        // keyset 分页时多取一条用于判断是否有下一页
        selectSql.append(" LIMIT ?");
        queryParams.add(fetchSize + 1);
      } else {
        selectSql.append(" LIMIT ? OFFSET ?");
        queryParams.add(fetchSize);
        queryParams.add(request.getOffset());
      }

      List<SearchHit> hits =
          jdbcTemplate.query(
              selectSql.toString(),
              new SearchHitRowMapper(request.isHighlight()),
              queryParams.toArray());

      long took = System.currentTimeMillis() - start;
      List<SearchAggregation> aggregations = Collections.emptyList();
      if (request.getAggregations() != null && !request.getAggregations().isEmpty()) {
        aggregations = executeAggregations(where, whereParams, request.getAggregations());
      }

      // 构建下一页游标
      String nextCursor = null;
      if (useKeysetPagination && hits.size() > fetchSize) {
        // 多取了一条，说明有下一页
        hits = hits.subList(0, fetchSize);
        SearchHit lastHit = hits.get(hits.size() - 1);
        nextCursor = buildCursor(lastHit, cursorResult.sortField);
      } else if (!useKeysetPagination
          && request.getCursor() == null
          && hits.size() == request.getPageSize()) {
        // 第一页且数据满页时，构建游标供下一页使用（深度分页优化）
        SearchHit lastHit = hits.get(hits.size() - 1);
        nextCursor = buildCursor(lastHit, null);
      }

      return SearchResponse.builder()
          .hits(hits)
          .total(total)
          .page(request.getPage())
          .pageSize(request.getPageSize())
          .tookMs(took)
          .aggregations(aggregations)
          .engine(ENGINE_NAME)
          .nextCursor(nextCursor)
          .build();

    } catch (Exception e) {
      log.error("[PgSearchStrategy] 搜索失败: keyword={}", request.getKeyword(), e);
      this.available = false;
      return SearchResponse.empty(request.getPage(), request.getPageSize());
    }
  }

  @Override
  public void index(IndexDocument document) {
    if (document == null || document.getId() == null) {
      return;
    }
    if (!available) {
      return;
    }
    try {
      String sql =
          """
                    INSERT INTO %s (id, doc_type, title, subtitle, content, snippet, tags, status, path,
                                    tenant_id, created_by, created_at, updated_by, updated_at,
                                    searchable_text, metadata, created_at_ts, updated_at_ts)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?,
                            to_tsvector(?, ?), ?::jsonb, NOW(), NOW())
                    ON CONFLICT (id) DO UPDATE SET
                        doc_type = EXCLUDED.doc_type, title = EXCLUDED.title,
                        subtitle = EXCLUDED.subtitle, content = EXCLUDED.content,
                        snippet = EXCLUDED.snippet, tags = EXCLUDED.tags,
                        status = EXCLUDED.status, path = EXCLUDED.path,
                        tenant_id = EXCLUDED.tenant_id, updated_by = EXCLUDED.updated_by,
                        updated_at = EXCLUDED.updated_at, searchable_text = EXCLUDED.searchable_text,
                        metadata = EXCLUDED.metadata, updated_at_ts = NOW()
                    """
              .formatted(indexTable);

      String searchableText = buildSearchableText(document);
      String tagsJson =
          YdszJson.toJson(
              document.getTags() != null ? document.getTags() : Collections.emptyList());
      String metadataJson =
          YdszJson.toJson(
              document.getMetadata() != null ? document.getMetadata() : Collections.emptyMap());

      jdbcTemplate.update(
          sql,
          document.getId(),
          document.getType(),
          document.getTitle(),
          document.getSubtitle(),
          document.getContent(),
          document.getSnippet(),
          tagsJson,
          document.getStatus(),
          document.getPath(),
          document.getTenantId(),
          document.getCreatedBy(),
          document.getCreatedAt(),
          document.getUpdatedBy(),
          document.getUpdatedAt(),
          searchConfig,
          searchableText,
          metadataJson);
    } catch (Exception e) {
      log.warn(
          "[PgSearchStrategy] 索引写入失败: id={}, type={}", document.getId(), document.getType(), e);
    }
  }

  @Override
  public void bulkIndex(List<IndexDocument> documents) {
    if (documents == null || documents.isEmpty()) {
      return;
    }
    if (!available) {
      return;
    }
    String sql =
        "INSERT INTO "
            + indexTable
            + " (id, doc_type, title, subtitle, content, snippet, tags, status, path, tenant_id,"
            + " created_by, created_at, updated_by, updated_at, searchable_text, metadata,"
            + " created_at_ts, updated_at_ts)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, to_tsvector(?, ?), ?::jsonb, NOW(), NOW())"
            + " ON CONFLICT (id) DO UPDATE SET doc_type = EXCLUDED.doc_type, title = EXCLUDED.title,"
            + " subtitle = EXCLUDED.subtitle, content = EXCLUDED.content, snippet = EXCLUDED.snippet,"
            + " tags = EXCLUDED.tags, status = EXCLUDED.status, path = EXCLUDED.path,"
            + " tenant_id = EXCLUDED.tenant_id, updated_by = EXCLUDED.updated_by,"
            + " updated_at = EXCLUDED.updated_at, searchable_text = EXCLUDED.searchable_text,"
            + " metadata = EXCLUDED.metadata, updated_at_ts = NOW()";

    int batchSize = 100;
    for (int i = 0; i < documents.size(); i += batchSize) {
      int end = Math.min(i + batchSize, documents.size());
      List<IndexDocument> batch = documents.subList(i, end);
      try {
        jdbcTemplate.batchUpdate(
            sql,
            batch,
            batch.size(),
            (ps, doc) -> {
              String searchableText = buildSearchableText(doc);
              String tagsJson =
                  YdszJson.toJson(doc.getTags() != null ? doc.getTags() : Collections.emptyList());
              String metadataJson =
                  YdszJson.toJson(
                      doc.getMetadata() != null ? doc.getMetadata() : Collections.emptyMap());
              ps.setString(1, doc.getId());
              ps.setString(2, doc.getType());
              ps.setString(3, doc.getTitle());
              ps.setString(4, doc.getSubtitle());
              ps.setString(5, doc.getContent());
              ps.setString(6, doc.getSnippet());
              ps.setString(7, tagsJson);
              ps.setString(8, doc.getStatus());
              ps.setString(9, doc.getPath());
              ps.setString(10, doc.getTenantId());
              ps.setString(11, doc.getCreatedBy());
              ps.setTimestamp(
                  12, doc.getCreatedAt() != null ? Timestamp.from(doc.getCreatedAt()) : null);
              ps.setString(13, doc.getUpdatedBy());
              ps.setTimestamp(
                  14, doc.getUpdatedAt() != null ? Timestamp.from(doc.getUpdatedAt()) : null);
              ps.setString(15, searchConfig);
              ps.setString(16, searchableText);
              ps.setString(17, metadataJson);
            });
      } catch (Exception e) {
        log.warn("[PgSearchStrategy] batch index failed, fallback to single: {}", e.getMessage());
        for (IndexDocument doc : batch) {
          index(doc);
        }
      }
    }
  }

  @Override
  public void deleteIndex(String type, String documentId) {
    if (!available) {
      return;
    }
    try {
      jdbcTemplate.update(
          "DELETE FROM " + indexTable + " WHERE id = ? AND doc_type = ?", documentId, type);
    } catch (Exception e) {
      log.warn("[PgSearchStrategy] 删除索引失败: id={}, type={}", documentId, type, e);
    }
  }

  @Override
  public void deleteAllIndices(String type) {
    if (!available) {
      return;
    }
    try {
      if (type == null) {
        jdbcTemplate.update("DELETE FROM " + indexTable);
      } else {
        jdbcTemplate.update("DELETE FROM " + indexTable + " WHERE doc_type = ?", type);
      }
    } catch (Exception e) {
      log.warn("[PgSearchStrategy] 清空索引失败: type={}", type, e);
    }
  }

  @Override
  public SearchSuggestion suggest(String prefix, int limit) {
    if (prefix == null || prefix.isBlank()) {
      return SearchSuggestion.builder()
          .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
          .suggestions(Collections.emptyList())
          .originalInput(prefix)
          .build();
    }
    if (!available) {
      return SearchSuggestion.builder()
          .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
          .suggestions(Collections.emptyList())
          .originalInput(prefix)
          .build();
    }
    try {
      String sql =
          "SELECT DISTINCT title FROM "
              + indexTable
              + " WHERE title ILIKE ? ESCAPE '\\' ORDER BY title LIMIT ?";
      String escapedPrefix =
          prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
      List<String> suggestions = jdbcTemplate.queryForList(sql, String.class, escapedPrefix, limit);
      return SearchSuggestion.builder()
          .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
          .suggestions(suggestions)
          .originalInput(prefix)
          .build();
    } catch (Exception e) {
      log.warn("[PgSearchStrategy] 搜索建议失败: prefix={}", prefix, e);
      return SearchSuggestion.builder()
          .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
          .suggestions(Collections.emptyList())
          .originalInput(prefix)
          .build();
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

  /**
   * 关闭可用性探测调度线程池，由容器在 Bean 销毁阶段调用。
   *
   * <p>探测任务每 30 秒执行一次，在引擎被标记为不可用时尝试 {@code SELECT 1 FROM <索引表> LIMIT 1} 以自动恢复降级状态。
   *
   * <p>关闭采用「优雅 + 强制」两段式：先 {@code shutdown()} 等待最多 3 秒， 超时或被中断则 {@code shutdownNow()} 强制终止；
   * 被中断时会补回线程中断标志，不吞掉中断信号。
   *
   * <p>关闭后引擎的 {@code available} 状态将不再自动恢复， 需重启应用重新建立探测。重复调用是安全的（幂等）。
   */
  public void shutdown() {
    probeScheduler.shutdown();
    try {
      if (!probeScheduler.getScheduledThreadPoolExecutor().awaitTermination(3, TimeUnit.SECONDS)) {
        probeScheduler.getScheduledThreadPoolExecutor().shutdownNow();
      }
    } catch (InterruptedException e) {
      probeScheduler.getScheduledThreadPoolExecutor().shutdownNow();
      Thread.currentThread().interrupt();
    }
    log.info("[PgSearchStrategy] 探测线程池已关闭");
  }

  // ==================== 私有方法 ====================

  private String buildSearchableText(IndexDocument doc) {
    StringBuilder sb = new StringBuilder();
    if (doc.getTitle() != null) {
      sb.append(doc.getTitle());
    }
    if (doc.getSubtitle() != null) {
      sb.append(' ').append(doc.getSubtitle());
    }
    if (doc.getContent() != null) {
      sb.append(' ').append(doc.getContent());
    }
    if (doc.getTags() != null) {
      for (String tag : doc.getTags()) {
        sb.append(' ').append(tag);
      }
    }
    return sb.toString();
  }

  // ==================== Keyset 分页辅助方法 ====================

  /**
   * 解析游标字符串。
   *
   * <p>游标格式：base64(score:id) 或 base64(sortValue:id:sortField)， 默认使用评分 + ID 作为键集分页锚点。
   *
   * @param cursor base64 编码的游标
   * @return 解析结果，解析失败返回 null
   */
  private CursorParseResult parseCursor(String cursor) {
    try {
      String decoded = new String(java.util.Base64.getUrlDecoder().decode(cursor));
      String[] parts = decoded.split(":", 3);
      if (parts.length >= 2) {
        double score = Double.parseDouble(parts[0]);
        String id = parts[1];
        String sortField = parts.length >= 3 ? parts[2] : null;
        return new CursorParseResult(score, id, sortField);
      }
    } catch (Exception e) {
      log.warn("[PgSearchStrategy] 游标解析失败，降级到 OFFSET: cursor={}", cursor, e);
    }
    return null;
  }

  /**
   * 构建下一页游标。
   *
   * <p>从最后一个 hit 中提取 score 和 id，编码为 base64 字符串。
   *
   * @param lastHit 当前页最后一个结果
   * @param sortField 排序字段（可选）
   * @return base64 编码的游标字符串，分数为 null 时返回 null
   */
  private String buildCursor(SearchHit lastHit, String sortField) {
    if (lastHit == null || lastHit.getScore() <= 0) {
      return null;
    }
    String cursorValue = lastHit.getScore() + ":" + lastHit.getId();
    if (sortField != null && !sortField.isBlank()) {
      cursorValue += ":" + sortField;
    }
    return java.util.Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(cursorValue.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /**
   * 追加 keyset 分页过滤条件（WHERE 子句）。
   *
   * <p>使用行值比较 (score, id) < (lastScore, lastId) 实现稳定的键集分页， 等价于 score < lastScore OR (score =
   * lastScore AND id < lastId)， 确保分页结果不重复、不遗漏。
   *
   * @param selectSql 正在构建的 SQL
   * @param queryParams 查询参数列表
   * @param cursor 解析后的游标
   * @param request 搜索请求
   */
  private void appendKeysetFilter(
      StringBuilder selectSql,
      List<Object> queryParams,
      CursorParseResult cursor,
      SearchRequest request) {
    if (cursor == null) {
      return;
    }
    if (request.getSortBy() != null && !request.getSortBy().isBlank()) {
      // 自定义排序字段时使用字段 + ID 作为锚点
      String direction = request.isAscending() ? ">" : "<";
      selectSql
          .append(" AND (")
          .append(sanitizeColumnName(request.getSortBy()))
          .append(", id) ")
          .append(direction)
          .append(" (?, ?)");
      queryParams.add(cursor.score);
      queryParams.add(cursor.id);
    } else {
      // 默认按评分排序时使用 (rank, id) 行值比较
      selectSql.append(" AND (rank, id) < (?, ?)");
      queryParams.add(cursor.score);
      queryParams.add(cursor.id);
    }
  }

  /**
   * 游标解析结果。
   *
   * @param score 上一页最后一行的分数
   * @param id 上一页最后一行的 ID
   * @param sortField 排序字段（可选）
   */
  private record CursorParseResult(double score, String id, String sortField) {}

  // ==================== 引擎可用性与配置 ====================

  private void startRecoveryProbe() {
    probeScheduler.scheduleWithFixedDelay(
        () -> {
          if (!available) {
            try {
              jdbcTemplate.queryForObject(
                  "SELECT 1 FROM " + indexTable + " LIMIT 1", Integer.class);
              available = true;
              log.info("[PgSearchStrategy] 降级恢复成功");
            } catch (Exception e) {
              log.debug("[PgSearchStrategy] 降级恢复探测失败: {}", e.getMessage());
            }
          }
        },
        Duration.ofSeconds(30));
  }

  private String detectSearchConfig() {
    try {
      Integer count =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(1) FROM pg_ts_config WHERE cfgname = 'search_zh'", Integer.class);
      if (count != null && count > 0) {
        log.info("[PgSearchStrategy] 检测到中文分词配置: search_zh");
        return DEFAULT_SEARCH_CONFIG;
      }
    } catch (Exception e) {
      log.debug("[PgSearchStrategy] zhparser 未安装");
    }
    log.info("[PgSearchStrategy] 使用 simple 搜索配置");
    return FALLBACK_SEARCH_CONFIG;
  }

  private boolean checkAvailability() {
    try {
      jdbcTemplate.queryForObject("SELECT 1 FROM " + indexTable + " LIMIT 1", Integer.class);
      log.info("[PgSearchStrategy] 索引表可用: table={}", indexTable);
      return true;
    } catch (Exception e) {
      log.warn("[PgSearchStrategy] 索引表不可用: {}", e.getMessage());
      return false;
    }
  }

  private List<SearchAggregation> executeAggregations(
      StringBuilder where, List<Object> params, List<String> aggFields) {
    List<SearchAggregation> aggregations = new ArrayList<>();
    for (String field : aggFields) {
      try {
        String safeField = sanitizeColumnName(field);
        String sql =
            "SELECT "
                + safeField
                + " AS key, COUNT(1) AS count FROM "
                + indexTable
                + where
                + " GROUP BY "
                + safeField
                + " ORDER BY count DESC";
        List<SearchAggregation.Bucket> buckets =
            jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                    SearchAggregation.Bucket.builder()
                        .key(rs.getString("key"))
                        .count(rs.getLong("count"))
                        .build(),
                params.toArray());
        aggregations.add(
            SearchAggregation.builder().field(field).label(field).buckets(buckets).build());
      } catch (Exception e) {
        log.warn("[PgSearchStrategy] 聚合查询失败: field={}", field, e);
      }
    }
    return aggregations;
  }

  private void appendFilter(StringBuilder where, List<Object> params, SearchFilter filter) {
    if (filter == null || filter.getField() == null) {
      return;
    }
    String field = sanitizeColumnName(filter.getField());
    switch (filter.getOperator()) {
      case EQ -> {
        where.append(" AND ").append(field).append(" = ?");
        if (filter.getValues() != null && !filter.getValues().isEmpty()) {
          params.add(filter.getValues().get(0));
        }
      }
      case NE -> {
        where.append(" AND ").append(field).append(" != ?");
        if (filter.getValues() != null && !filter.getValues().isEmpty()) {
          params.add(filter.getValues().get(0));
        }
      }
      case IN, NOT_IN -> {
        if (filter.getValues() != null && !filter.getValues().isEmpty()) {
          String op = filter.getOperator() == SearchFilter.Operator.IN ? "IN" : "NOT IN";
          where
              .append(" AND ")
              .append(field)
              .append(" ")
              .append(op)
              .append(" (")
              .append(filter.getValues().stream().map(v -> "?").collect(Collectors.joining(",")))
              .append(")");
          params.addAll(filter.getValues());
        }
      }
      case GT -> {
        where.append(" AND ").append(field).append(" > ?");
        if (filter.getValues() != null && !filter.getValues().isEmpty()) {
          params.add(filter.getValues().get(0));
        }
      }
      case LT -> {
        where.append(" AND ").append(field).append(" < ?");
        if (filter.getValues() != null && !filter.getValues().isEmpty()) {
          params.add(filter.getValues().get(0));
        }
      }
      case GTE -> {
        where.append(" AND ").append(field).append(" >= ?");
        if (filter.getValues() != null && !filter.getValues().isEmpty()) {
          params.add(filter.getValues().get(0));
        }
      }
      case LTE -> {
        where.append(" AND ").append(field).append(" <= ?");
        if (filter.getValues() != null && !filter.getValues().isEmpty()) {
          params.add(filter.getValues().get(0));
        }
      }
      case BETWEEN -> {
        if (filter.getValues() != null && filter.getValues().size() >= 2) {
          where.append(" AND ").append(field).append(" BETWEEN ? AND ?");
          params.add(filter.getValues().get(0));
          params.add(filter.getValues().get(1));
        }
      }
    }
  }

  private String sanitizeKeyword(String keyword) {
    if (keyword == null) {
      return null;
    }
    String trimmed = keyword.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > 200) {
      trimmed = trimmed.substring(0, 200);
    }
    return trimmed;
  }

  private String sanitizeColumnName(String column) {
    if (column == null) {
      return "id";
    }
    String lower = column.toLowerCase();
    if (ALLOWED_COLUMNS.contains(lower)) {
      return lower;
    }
    log.warn("[PgSearchStrategy] Column not in whitelist, fallback to id: {}", column);
    return "id";
  }

  /**
   * PG 搜索结果行映射器。
   *
   * <p>将查询结果集行映射为 {@link SearchHit}，{@code withHighlight} 控制 是否同时解析 ts_headline 高亮片段列。
   */
  private static class SearchHitRowMapper implements RowMapper<SearchHit> {
    private final boolean withHighlight;

    SearchHitRowMapper(boolean withHighlight) {
      this.withHighlight = withHighlight;
    }

    @Override
    public SearchHit mapRow(ResultSet rs, int rowNum) throws SQLException {
      SearchHit hit =
          SearchHit.builder()
              .id(rs.getString("id"))
              .type(rs.getString("doc_type"))
              .title(rs.getString("title"))
              .subtitle(rs.getString("subtitle"))
              .snippet(rs.getString("snippet"))
              .status(rs.getString("status"))
              .score(rs.getFloat("rank"))
              .build();
      try {
        hit.setPath(rs.getString("path"));
      } catch (SQLException ignored) {
        log.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
      try {
        String tagsJson = rs.getString("tags");
        if (tagsJson != null && !tagsJson.isBlank() && !tagsJson.equals("[]")) {
          List<String> tags = YdszJson.parseArray(tagsJson, String.class);
          if (tags != null && !tags.isEmpty()) {
            hit.setTags(tags);
          }
        }
      } catch (SQLException ignored) {
        log.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
      try {
        var createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
          hit.setCreatedAt(createdAt.toInstant().toString());
        }
      } catch (SQLException ignored) {
        log.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
      try {
        var updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
          hit.setUpdatedAt(updatedAt.toInstant().toString());
        }
      } catch (SQLException ignored) {
        log.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
      if (withHighlight) {
        hit.setHighlight(rs.getString("highlight"));
      }
      return hit;
    }
  }
}
