package com.njydsz.common.search.engine.pg;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.search.api.SearchAggregation;
import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.api.SearchHit;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchEngine;

import com.njydsz.common.json.YdszJson;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 PostgreSQL tsvector 的搜索引擎实现
 * <p>
 * 利用 PG 原生全文检索能力，支持中文分词（zhparser/jieba）、高亮、相关性排序、模糊匹配。
 * 支持降级自动恢复探测、内存索引有界化。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
public class PgSearchEngine implements SearchEngine {

    private static final String ENGINE_NAME = "pg-tsvector";
    private static final String INDEX_TABLE = "ydsz_search_index";
    private static final String DEFAULT_SEARCH_CONFIG = "search_zh";
    private static final String FALLBACK_SEARCH_CONFIG = "simple";

    /** P1-11: 内存索引最大容量 */
    private static final int MAX_MEMORY_INDEX_SIZE = 10000;
    /** P2-14: Allowed sort columns whitelist */
    private static final Set<String> ALLOWED_COLUMNS = Set.of(
            "id", "doc_type", "title", "subtitle", "content", "snippet",
            "tags", "status", "path", "tenant_id", "created_by", "created_at",
            "updated_by", "updated_at", "searchable_text", "metadata",
            "created_at_ts", "updated_at_ts"
    );

    private final JdbcTemplate jdbcTemplate;
    private final String searchConfig;
    private final SearchProperties properties;
    private volatile boolean available;

    /** P1-11: 有界内存索引（LRU 淘汰策略） */
    private final Map<String, IndexDocument> memoryIndex;

    /** P1-8: 降级恢复探测调度器 */
    private final ScheduledExecutorService probeExecutor;

    public PgSearchEngine(DataSource dataSource, SearchProperties properties) {
        this(new JdbcTemplate(dataSource), properties);
    }

    public PgSearchEngine(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new SearchProperties());
    }

    private PgSearchEngine(JdbcTemplate jdbcTemplate, SearchProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.searchConfig = detectSearchConfig();
        this.available = initIndexTable();
        this.memoryIndex = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, IndexDocument> eldest) {
                return size() > MAX_MEMORY_INDEX_SIZE;
            }
        });
        this.probeExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pg-search-probe");
            t.setDaemon(true);
            return t;
        });
        startRecoveryProbe();
    }
    @Override
    public SearchResponse search(SearchRequest request) {
        if (!available) {
            return searchInMemory(request);
        }

        long start = System.currentTimeMillis();
        try {
            String keyword = sanitizeKeyword(request.getKeyword());
            if (keyword == null || keyword.isBlank()) {
                return SearchResponse.empty(request.getPage(), request.getPageSize());
            }

            List<Object> whereParams = new ArrayList<>();
            StringBuilder where = new StringBuilder(" WHERE 1=1");

            // 全文检索 + 模糊匹配
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

            // P0-7: 应用过滤条件（含权限过滤）
            if (request.getFilters() != null) {
                for (SearchFilter filter : request.getFilters()) {
                    appendFilter(where, whereParams, filter);
                }
            }

            // 租户隔离
            if (request.getTenantId() != null && !request.getTenantId().isBlank()) {
                where.append(" AND tenant_id = ?");
                whereParams.add(request.getTenantId());
            }

            // 类型过滤
            if (request.getTypes() != null && !request.getTypes().isEmpty()) {
                where.append(" AND doc_type IN (")
                        .append(request.getTypes().stream().map(t -> "?").collect(Collectors.joining(",")))
                        .append(")");
                whereParams.addAll(request.getTypes());
            }

            // P0-1: 执行 COUNT 查询获取总匹配数
            String countSql = "SELECT COUNT(1) FROM " + INDEX_TABLE + where;
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

            // SELECT 查询参数：先 SELECT 部分，再 WHERE 部分
            List<Object> queryParams = new ArrayList<>();

            StringBuilder selectSql = new StringBuilder("SELECT id, doc_type, title, subtitle, snippet, status, ");

            // ts_rank with setweight (6 params)
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

            // highlight
            if (request.isHighlight()) {
                selectSql.append(", ts_headline(?, searchable_text, plainto_tsquery(?, ?), ");
                selectSql.append("'MaxWords=60, MinWords=20, ShortWord=3, HighlightAll=FALSE, StartSel=' || ? || ', StopSel=' || ?) AS highlight");
                queryParams.add(searchConfig);
                queryParams.add(searchConfig);
                queryParams.add(keyword);
                queryParams.add(request.getHighlightPreTag());
                queryParams.add(request.getHighlightPostTag());
            }

            selectSql.append(" FROM ").append(INDEX_TABLE).append(where);

            // WHERE params after SELECT params
            queryParams.addAll(whereParams);

            if (request.getSortBy() != null && !request.getSortBy().isBlank()) {
                String direction = request.isAscending() ? "ASC" : "DESC";
                selectSql.append(" ORDER BY ").append(sanitizeColumnName(request.getSortBy())).append(" ").append(direction);
            } else if (properties.getRank().getTimeDecayDays() > 0) {
                // P1-11: 时间衰减排序 — rank * EXP(-age_days / half_life * ln2)
                selectSql.append(" ORDER BY (rank * EXP(-EXTRACT(EPOCH FROM (NOW() - updated_at_ts)) / 86400.0 / ? * LN(2))) DESC, updated_at DESC");
                queryParams.add(properties.getRank().getTimeDecayDays());
            } else {
                selectSql.append(" ORDER BY rank DESC, updated_at DESC");
            }

            selectSql.append(" LIMIT ? OFFSET ?");
            queryParams.add(request.getPageSize());
            queryParams.add(request.getOffset());
            List<SearchHit> hits = jdbcTemplate.query(selectSql.toString(),
                    new SearchHitRowMapper(request.isHighlight()), queryParams.toArray());

            long took = System.currentTimeMillis() - start;

            // 聚合
            List<SearchAggregation> aggregations = Collections.emptyList();
            if (request.getAggregations() != null && !request.getAggregations().isEmpty()) {
                aggregations = executeAggregations(where, whereParams, request.getAggregations());
            }

            return SearchResponse.builder()
                    .hits(hits)
                    .total(total)
                    .page(request.getPage())
                    .pageSize(request.getPageSize())
                    .tookMs(took)
                    .aggregations(aggregations)
                    .engine(ENGINE_NAME)
                    .build();

        } catch (Exception e) {
            log.error("[PgSearchEngine] 搜索失败，降级到内存搜索: keyword={}", request.getKeyword(), e);
            this.available = false;
            return searchInMemory(request);
        }
    }

    @Override
    public void index(IndexDocument document) {
        if (document == null || document.getId() == null) {
            return;
        }

        String key = document.getType() + ":" + document.getId();
        memoryIndex.put(key, document);

        if (!available) {
            return;
        }

        try {
            String sql = """
                    INSERT INTO %s (id, doc_type, title, subtitle, content, snippet, tags, status, path,
                                    tenant_id, created_by, created_at, updated_by, updated_at,
                                    searchable_text, metadata, created_at_ts, updated_at_ts)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?,
                            to_tsvector(?, ?), ?::jsonb, NOW(), NOW())
                    ON CONFLICT (id) DO UPDATE SET
                        doc_type = EXCLUDED.doc_type,
                        title = EXCLUDED.title,
                        subtitle = EXCLUDED.subtitle,
                        content = EXCLUDED.content,
                        snippet = EXCLUDED.snippet,
                        tags = EXCLUDED.tags,
                        status = EXCLUDED.status,
                        path = EXCLUDED.path,
                        tenant_id = EXCLUDED.tenant_id,
                        updated_by = EXCLUDED.updated_by,
                        updated_at = EXCLUDED.updated_at,
                        searchable_text = EXCLUDED.searchable_text,
                        metadata = EXCLUDED.metadata,
                        updated_at_ts = NOW()
                    """.formatted(INDEX_TABLE);

            String searchableText = document.getSearchableText();
            String tagsJson = YdszJson.toJson(document.getTags() != null ? document.getTags() : Collections.emptyList());
            String metadataJson = YdszJson.toJson(document.getMetadata() != null ? document.getMetadata() : Collections.emptyMap());

            jdbcTemplate.update(sql,
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
                    metadataJson
            );
        } catch (Exception e) {
            log.warn("[PgSearchEngine] 索引写入失败（降级到内存）: id={}, type={}", document.getId(), document.getType(), e);
        }
    }

    @Override
    public void bulkIndex(List<IndexDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        // P0-5: write to memory index first
        for (IndexDocument doc : documents) {
            if (doc != null && doc.getId() != null) {
                memoryIndex.put(doc.getType() + ":" + doc.getId(), doc);
            }
        }

        if (!available) {
            return;
        }

        String sql = "INSERT INTO " + INDEX_TABLE + " (id, doc_type, title, subtitle, content, snippet, tags, status, path, tenant_id, created_by, created_at, updated_by, updated_at, searchable_text, metadata, created_at_ts, updated_at_ts) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, to_tsvector(?, ?), ?::jsonb, NOW(), NOW()) ON CONFLICT (id) DO UPDATE SET doc_type = EXCLUDED.doc_type, title = EXCLUDED.title, subtitle = EXCLUDED.subtitle, content = EXCLUDED.content, snippet = EXCLUDED.snippet, tags = EXCLUDED.tags, status = EXCLUDED.status, path = EXCLUDED.path, tenant_id = EXCLUDED.tenant_id, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at, searchable_text = EXCLUDED.searchable_text, metadata = EXCLUDED.metadata, updated_at_ts = NOW()";

        int batchSize = properties.getIndex().getBatchSize();
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<IndexDocument> batch = documents.subList(i, end);
            try {
                jdbcTemplate.batchUpdate(sql, batch, batch.size(), (ps, doc) -> {
                    String tagsJson = YdszJson.toJson(doc.getTags() != null ? doc.getTags() : Collections.emptyList());
                    String metadataJson = YdszJson.toJson(doc.getMetadata() != null ? doc.getMetadata() : Collections.emptyMap());
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
                    ps.setTimestamp(12, doc.getCreatedAt() != null ? Timestamp.from(doc.getCreatedAt()) : null);
                    ps.setString(13, doc.getUpdatedBy());
                    ps.setTimestamp(14, doc.getUpdatedAt() != null ? Timestamp.from(doc.getUpdatedAt()) : null);
                    ps.setString(15, searchConfig);
                    ps.setString(16, doc.getSearchableText());
                    ps.setString(17, metadataJson);
                });
            } catch (Exception e) {
                log.warn("[PgSearchEngine] batch index failed, fallback to single: {}", e.getMessage());
                for (IndexDocument doc : batch) {
                    index(doc);
                }
            }
        }
    }

    @Override
    public void deleteIndex(String type, String documentId) {
        String key = type + ":" + documentId;
        memoryIndex.remove(key);

        if (!available) {
            return;
        }

        try {
            jdbcTemplate.update("DELETE FROM " + INDEX_TABLE + " WHERE id = ? AND doc_type = ?",
                    documentId, type);
        } catch (Exception e) {
            log.warn("[PgSearchEngine] 删除索引失败: id={}, type={}", documentId, type, e);
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
            List<String> suggestions = memoryIndex.values().stream()
                    .map(IndexDocument::getTitle)
                    .filter(t -> t != null && t.toLowerCase().contains(prefix.toLowerCase()))
                    .distinct()
                    .limit(limit)
                    .toList();
            return SearchSuggestion.builder()
                    .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
                    .suggestions(suggestions)
                    .originalInput(prefix)
                    .build();
        }

        try {
            String sql = "SELECT DISTINCT title FROM " + INDEX_TABLE +
                    " WHERE title ILIKE ? ESCAPE '\\' ORDER BY title LIMIT ?";
            List<String> suggestions = jdbcTemplate.queryForList(sql, String.class,
                    prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%", limit);
            return SearchSuggestion.builder()
                    .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
                    .suggestions(suggestions)
                    .originalInput(prefix)
                    .build();
        } catch (Exception e) {
            log.warn("[PgSearchEngine] 搜索建议失败: prefix={}", prefix, e);
            return SearchSuggestion.builder()
                    .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
                    .suggestions(Collections.emptyList())
                    .originalInput(prefix)
                    .build();
        }
    }

    @Override
    public void deleteAllIndices(String type) {
        if (type == null) {
            memoryIndex.clear();
        } else {
            memoryIndex.entrySet().removeIf(e -> e.getKey().startsWith(type + ":"));
        }

        if (!available) {
            return;
        }

        try {
            if (type == null) {
                jdbcTemplate.update("DELETE FROM " + INDEX_TABLE);
            } else {
                jdbcTemplate.update("DELETE FROM " + INDEX_TABLE + " WHERE doc_type = ?", type);
            }
        } catch (Exception e) {
            log.warn("[PgSearchEngine] 清空索引失败: type={}", type, e);
        }
    }

    @Override
    public String getName() {
        return ENGINE_NAME;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * 关闭资源（Spring 生命周期回调）
     */
    public void shutdown() {
        probeExecutor.shutdown();
        try {
            if (!probeExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                probeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            probeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[PgSearchEngine] 探测线程池已关闭");
    }

    // ==================== 私有方法 ====================

    /**
     * P1-8: 启动降级自动恢复探测
     */
    private void startRecoveryProbe() {
        int interval = properties.getDegrade().getProbeInterval();
        probeExecutor.scheduleAtFixedRate(() -> {
            if (!available) {
                log.debug("[PgSearchEngine] 执行降级恢复探测...");
                try {
                    jdbcTemplate.queryForObject("SELECT 1 FROM " + INDEX_TABLE + " LIMIT 1", Integer.class);
                    available = true;
                    log.info("[PgSearchEngine] 降级恢复探测成功，PG 索引已恢复可用");
                } catch (Exception e) {
                    log.debug("[PgSearchEngine] 降级恢复探测失败: {}", e.getMessage());
                }
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    private String detectSearchConfig() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM pg_ts_config WHERE cfgname = 'search_zh'",
                    Integer.class);
            if (count != null && count > 0) {
                log.info("[PgSearchEngine] 检测到中文分词配置: search_zh (zhparser)");
                return DEFAULT_SEARCH_CONFIG;
            }
        } catch (Exception e) {
            log.debug("[PgSearchEngine] zhparser 未安装，回退到 simple 配置");
        }
        log.info("[PgSearchEngine] 使用 simple 搜索配置（无中文分词）");
        return FALLBACK_SEARCH_CONFIG;
    }

    private boolean initIndexTable() {
        try {
            String ddl = """
                    CREATE TABLE IF NOT EXISTS %s (
                        id          VARCHAR(128) NOT NULL,
                        doc_type    VARCHAR(64)  NOT NULL,
                        title       TEXT,
                        subtitle    TEXT,
                        content     TEXT,
                        snippet     TEXT,
                        tags        JSONB        DEFAULT '[]'::jsonb,
                        status      VARCHAR(32),
                        path        TEXT,
                        tenant_id   VARCHAR(64),
                        created_by  VARCHAR(64),
                        created_at  TIMESTAMPTZ,
                        updated_by  VARCHAR(64),
                        updated_at  TIMESTAMPTZ,
                        searchable_text TEXT,
                        metadata    JSONB        DEFAULT '{}'::jsonb,
                        created_at_ts   TIMESTAMPTZ DEFAULT NOW(),
                        updated_at_ts   TIMESTAMPTZ DEFAULT NOW(),
                        PRIMARY KEY (id)
                    )
                    """.formatted(INDEX_TABLE);

            jdbcTemplate.execute(ddl);

            try {
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_" + INDEX_TABLE + "_fts " +
                                "ON " + INDEX_TABLE + " USING GIN (to_tsvector('" + searchConfig + "', searchable_text))");
            } catch (Exception e) {
                log.debug("[PgSearchEngine] GIN 索引创建跳过: {}", e.getMessage());
            }

            try {
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_" + INDEX_TABLE + "_type ON " + INDEX_TABLE + " (doc_type)");
            } catch (Exception e) {
                log.debug("[PgSearchEngine] 类型索引创建跳过: {}", e.getMessage());
            }

            try {
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_" + INDEX_TABLE + "_tenant ON " + INDEX_TABLE + " (tenant_id)");
            } catch (Exception e) {
                log.debug("[PgSearchEngine] 租户索引创建跳过: {}", e.getMessage());
            }

            try {
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_" + INDEX_TABLE + "_trgm " +
                                "ON " + INDEX_TABLE + " USING GIN (searchable_text gin_trgm_ops)");
            } catch (Exception e) {
                log.debug("[PgSearchEngine] pg_trgm 扩展不可用: {}", e.getMessage());
            }

            log.info("[PgSearchEngine] 索引表初始化成功: table={}, config={}", INDEX_TABLE, searchConfig);
            return true;

        } catch (Exception e) {
            log.warn("[PgSearchEngine] 索引表初始化失败，降级到内存索引: {}", e.getMessage());
            return false;
        }
    }

    private SearchResponse searchInMemory(SearchRequest request) {
        long start = System.currentTimeMillis();
        String keyword = request.getKeyword();
        if (keyword == null || keyword.isBlank()) {
            return SearchResponse.empty(request.getPage(), request.getPageSize());
        }

        String lowerKeyword = keyword.toLowerCase();

        List<SearchHit> allHits = memoryIndex.values().stream()
                .filter(doc -> {
                    if (request.getTypes() != null && !request.getTypes().isEmpty()
                            && !request.getTypes().contains(doc.getType())) {
                        return false;
                    }
                    if (request.getTenantId() != null && !request.getTenantId().isBlank()
                            && !request.getTenantId().equals(doc.getTenantId())) {
                        return false;
                    }
                    String text = request.isTitleOnly() ? doc.getTitleSearchableText() : doc.getSearchableText();
                    return text != null && text.toLowerCase().contains(lowerKeyword);
                })
                // P1-5: apply SearchFilter permission filters in degraded mode
                .filter(doc -> {
                    if (request.getFilters() == null || request.getFilters().isEmpty()) {
                        return true;
                    }
                    for (SearchFilter filter : request.getFilters()) {
                        if (!matchesFilter(doc, filter)) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(doc -> {
                    SearchHit hit = SearchHit.builder()
                            .id(doc.getId())
                            .type(doc.getType())
                            .title(doc.getTitle())
                            .subtitle(doc.getSubtitle())
                            .snippet(doc.getSnippet())
                            .path(doc.getPath())
                            .status(doc.getStatus())
                            .tags(doc.getTags())
                            .score(1.0f)
                            .createdAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
                            .updatedAt(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null)
                            .build();
                    if (request.isHighlight() && doc.getTitle() != null) {
                        hit.setHighlight(simpleHighlight(doc.getTitle(), keyword,
                                request.getHighlightPreTag(), request.getHighlightPostTag()));
                    }
                    return hit;
                })
                .toList();

        long total = allHits.size();
        int fromIndex = Math.min(request.getOffset(), allHits.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), allHits.size());
        List<SearchHit> pageHits = allHits.subList(fromIndex, toIndex);

        long took = System.currentTimeMillis() - start;
        return SearchResponse.builder()
                .hits(pageHits)
                .total(total)
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .tookMs(took)
                .engine(ENGINE_NAME + "-memory")
                .degraded(true)
                .build();
    }

    private List<SearchAggregation> executeAggregations(StringBuilder where, List<Object> params,
                                                         List<String> aggFields) {
        List<SearchAggregation> aggregations = new ArrayList<>();
        for (String field : aggFields) {
            try {
                String safeField = sanitizeColumnName(field);
                String sql = "SELECT " + safeField + " AS key, COUNT(1) AS count FROM " + INDEX_TABLE +
                        where + " GROUP BY " + safeField + " ORDER BY count DESC";
                List<SearchAggregation.Bucket> buckets = jdbcTemplate.query(sql,
                        (rs, rowNum) -> SearchAggregation.Bucket.builder()
                                .key(rs.getString("key"))
                                .count(rs.getLong("count"))
                                .build(),
                        params.toArray());
                aggregations.add(SearchAggregation.builder()
                        .field(field)
                        .label(field)
                        .buckets(buckets)
                        .build());
            } catch (Exception e) {
                log.warn("[PgSearchEngine] 聚合查询失败: field={}", field, e);
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
                    where.append(" AND ").append(field).append(" ").append(op).append(" (")
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
        log.warn("[PgSearchEngine] Column not in whitelist, fallback to id: {}", column);
        return "id";
    }


    // P1-5: check if document matches a SearchFilter (degraded mode)
    private boolean matchesFilter(IndexDocument doc, SearchFilter filter) {
        if (filter.getField() == null || filter.getValues() == null || filter.getValues().isEmpty()) {
            return true;
        }
        String field = filter.getField();
        String firstValue = filter.getValues().get(0);
        switch (filter.getOperator()) {
            case EQ -> { return matchesField(doc, field, firstValue); }
            case NE -> { return !matchesField(doc, field, firstValue); }
            case IN -> { return filter.getValues().stream().anyMatch(v -> matchesField(doc, field, v)); }
            case NOT_IN -> { return filter.getValues().stream().noneMatch(v -> matchesField(doc, field, v)); }
            default -> { return true; }
        }
    }

    private boolean matchesField(IndexDocument doc, String field, String value) {
        return switch (field) {
            case "doc_type", "type" -> value.equals(doc.getType());
            case "status" -> value.equals(doc.getStatus());
            case "tenant_id" -> value.equals(doc.getTenantId());
            case "created_by" -> value.equals(doc.getCreatedBy());
            case "updated_by" -> value.equals(doc.getUpdatedBy());
            default -> true;
        };
    }
    private String simpleHighlight(String text, String keyword, String preTag, String postTag) {
        if (text == null || keyword == null) {
            return text;
        }
        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int idx = lowerText.indexOf(lowerKeyword);
        if (idx < 0) {
            return text;
        }
        return text.substring(0, idx) + preTag + text.substring(idx, idx + keyword.length()) + postTag
                + text.substring(idx + keyword.length());
    }

    /**
     * P2: 搜索结果行映射器 — 补全 tags/path/metadata/时间戳字段映射
     */
    private static class SearchHitRowMapper implements RowMapper<SearchHit> {
        private final boolean withHighlight;

        SearchHitRowMapper(boolean withHighlight) {
            this.withHighlight = withHighlight;
        }

        @Override
        public SearchHit mapRow(ResultSet rs, int rowNum) throws SQLException {
            SearchHit hit = SearchHit.builder()
                    .id(rs.getString("id"))
                    .type(rs.getString("doc_type"))
                    .title(rs.getString("title"))
                    .subtitle(rs.getString("subtitle"))
                    .snippet(rs.getString("snippet"))
                    .status(rs.getString("status"))
                    .score(rs.getFloat("rank"))
                    .build();

            // P2: 补全字段映射
            try {
                String path = rs.getString("path");
                if (path != null) {
                    hit.setPath(path);
                }
            } catch (SQLException ignored) {
                // path 列可能不存在
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
                // tags 列可能不存在
            }

            try {
                var createdAt = rs.getTimestamp("created_at");
                if (createdAt != null) {
                    hit.setCreatedAt(createdAt.toInstant().toString());
                }
            } catch (SQLException ignored) {
                // created_at 列可能不存在
            }

            try {
                var updatedAt = rs.getTimestamp("updated_at");
                if (updatedAt != null) {
                    hit.setUpdatedAt(updatedAt.toInstant().toString());
                }
            } catch (SQLException ignored) {
                // updated_at 列可能不存在
            }

            if (withHighlight) {
                hit.setHighlight(rs.getString("highlight"));
            }
            return hit;
        }
    }
}
