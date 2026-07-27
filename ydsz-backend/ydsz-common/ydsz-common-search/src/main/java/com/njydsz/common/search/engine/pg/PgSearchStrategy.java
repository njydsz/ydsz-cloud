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
import com.njydsz.common.search.core.EngineCapability;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.IndexStrategy;
import com.njydsz.common.search.core.SearchStrategy;
import com.njydsz.common.search.core.SuggestStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 PostgreSQL tsvector 的搜索策略实现。
 *
 * <p>利用 PostgreSQL 原生全文检索能力（tsvector / tsquery / GIN 索引），
 * 支持中文分词（zhparser/jieba）、高亮、相关性排序和时间衰减。
 *
 * <h3>架构变更（1.3.0）</h3>
 * <ul>
 *   <li>实现 {@link SearchStrategy} + {@link IndexStrategy} + {@link SuggestStrategy} 三个策略接口</li>
 *   <li>构造器不再自动建表，DDL 由独立 SQL 脚本执行</li>
 *   <li>{@code getSearchableText()} 逻辑从 IndexDocument 内化到此类</li>
 *   <li>字段权重从 {@link SearchProperties.PgConfig} 读取</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
public class PgSearchStrategy implements SearchStrategy, IndexStrategy, SuggestStrategy {

    private static final String ENGINE_NAME = "pg";
    private static final String DEFAULT_SEARCH_CONFIG = "search_zh";
    private static final String FALLBACK_SEARCH_CONFIG = "simple";
    private static final int MAX_MEMORY_INDEX_SIZE = 10000;
    private static final Set<String> ALLOWED_COLUMNS = Set.of(
            "id", "doc_type", "title", "subtitle", "content", "snippet",
            "tags", "status", "path", "tenant_id", "created_by", "created_at",
            "updated_by", "updated_at", "searchable_text", "metadata",
            "created_at_ts", "updated_at_ts"
    );

    private final JdbcTemplate jdbcTemplate;
    private final String searchConfig;
    private final SearchProperties.PgConfig pgConfig;
    private final String indexTable;
    private volatile boolean available;
    private final Map<String, IndexDocument> memoryIndex;
    private final ScheduledExecutorService probeExecutor;

    public PgSearchStrategy(DataSource dataSource, SearchProperties.PgConfig pgConfig) {
        this(new JdbcTemplate(dataSource), pgConfig);
    }

    public PgSearchStrategy(JdbcTemplate jdbcTemplate, SearchProperties.PgConfig pgConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.pgConfig = pgConfig;
        this.indexTable = pgConfig.getIndexTable();
        this.searchConfig = detectSearchConfig();
        this.available = checkAvailability();
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
                where.append(" AND doc_type IN (")
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
            StringBuilder selectSql = new StringBuilder("SELECT id, doc_type, title, subtitle, snippet, status, ");

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
                selectSql.append("'MaxWords=60, MinWords=20, ShortWord=3, HighlightAll=FALSE, StartSel=' || ? || ', StopSel=' || ?) AS highlight");
                queryParams.add(searchConfig);
                queryParams.add(searchConfig);
                queryParams.add(keyword);
                queryParams.add(request.getHighlightPreTag());
                queryParams.add(request.getHighlightPostTag());
            }

            selectSql.append(" FROM ").append(indexTable).append(where);
            queryParams.addAll(whereParams);

            if (request.getSortBy() != null && !request.getSortBy().isBlank()) {
                String direction = request.isAscending() ? "ASC" : "DESC";
                selectSql.append(" ORDER BY ").append(sanitizeColumnName(request.getSortBy())).append(" ").append(direction);
            } else if (pgConfig.getTimeDecayDays() > 0) {
                selectSql.append(" ORDER BY (rank * EXP(-EXTRACT(EPOCH FROM (NOW() - updated_at_ts)) / 86400.0 / ? * LN(2))) DESC, updated_at DESC");
                queryParams.add(pgConfig.getTimeDecayDays());
            } else {
                selectSql.append(" ORDER BY rank DESC, updated_at DESC");
            }

            selectSql.append(" LIMIT ? OFFSET ?");
            queryParams.add(request.getPageSize());
            queryParams.add(request.getOffset());
            List<SearchHit> hits = jdbcTemplate.query(selectSql.toString(),
                    new SearchHitRowMapper(request.isHighlight()), queryParams.toArray());

            long took = System.currentTimeMillis() - start;
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
            log.error("[PgSearchStrategy] 搜索失败，降级到内存搜索: keyword={}", request.getKeyword(), e);
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
                        doc_type = EXCLUDED.doc_type, title = EXCLUDED.title,
                        subtitle = EXCLUDED.subtitle, content = EXCLUDED.content,
                        snippet = EXCLUDED.snippet, tags = EXCLUDED.tags,
                        status = EXCLUDED.status, path = EXCLUDED.path,
                        tenant_id = EXCLUDED.tenant_id, updated_by = EXCLUDED.updated_by,
                        updated_at = EXCLUDED.updated_at, searchable_text = EXCLUDED.searchable_text,
                        metadata = EXCLUDED.metadata, updated_at_ts = NOW()
                    """.formatted(indexTable);

            String searchableText = buildSearchableText(document);
            String tagsJson = YdszJson.toJson(document.getTags() != null ? document.getTags() : Collections.emptyList());
            String metadataJson = YdszJson.toJson(document.getMetadata() != null ? document.getMetadata() : Collections.emptyMap());

            jdbcTemplate.update(sql,
                    document.getId(), document.getType(), document.getTitle(), document.getSubtitle(),
                    document.getContent(), document.getSnippet(), tagsJson, document.getStatus(),
                    document.getPath(), document.getTenantId(), document.getCreatedBy(),
                    document.getCreatedAt(), document.getUpdatedBy(), document.getUpdatedAt(),
                    searchConfig, searchableText, metadataJson);
        } catch (Exception e) {
            log.warn("[PgSearchStrategy] 索引写入失败: id={}, type={}", document.getId(), document.getType(), e);
        }
    }

    @Override
    public void bulkIndex(List<IndexDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (IndexDocument doc : documents) {
            if (doc != null && doc.getId() != null) {
                memoryIndex.put(doc.getType() + ":" + doc.getId(), doc);
            }
        }
        if (!available) {
            return;
        }
        String sql = "INSERT INTO " + indexTable + " (id, doc_type, title, subtitle, content, snippet, tags, status, path, tenant_id, created_by, created_at, updated_by, updated_at, searchable_text, metadata, created_at_ts, updated_at_ts) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, to_tsvector(?, ?), ?::jsonb, NOW(), NOW()) ON CONFLICT (id) DO UPDATE SET doc_type = EXCLUDED.doc_type, title = EXCLUDED.title, subtitle = EXCLUDED.subtitle, content = EXCLUDED.content, snippet = EXCLUDED.snippet, tags = EXCLUDED.tags, status = EXCLUDED.status, path = EXCLUDED.path, tenant_id = EXCLUDED.tenant_id, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at, searchable_text = EXCLUDED.searchable_text, metadata = EXCLUDED.metadata, updated_at_ts = NOW()";

        int batchSize = 100;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<IndexDocument> batch = documents.subList(i, end);
            try {
                jdbcTemplate.batchUpdate(sql, batch, batch.size(), (ps, doc) -> {
                    String searchableText = buildSearchableText(doc);
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
        memoryIndex.remove(type + ":" + documentId);
        if (!available) {
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM " + indexTable + " WHERE id = ? AND doc_type = ?", documentId, type);
        } catch (Exception e) {
            log.warn("[PgSearchStrategy] 删除索引失败: id={}, type={}", documentId, type, e);
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
            List<String> suggestions = memoryIndex.values().stream()
                    .map(IndexDocument::getTitle)
                    .filter(t -> t != null && t.toLowerCase().contains(prefix.toLowerCase()))
                    .distinct().limit(limit).toList();
            return SearchSuggestion.builder()
                    .type(SearchSuggestion.SuggestionType.AUTOCOMPLETE)
                    .suggestions(suggestions)
                    .originalInput(prefix)
                    .build();
        }
        try {
            String sql = "SELECT DISTINCT title FROM " + indexTable +
                    " WHERE title ILIKE ? ESCAPE '\\' ORDER BY title LIMIT ?";
            List<String> suggestions = jdbcTemplate.queryForList(sql, String.class,
                    prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%", limit);
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
        log.info("[PgSearchStrategy] 探测线程池已关闭");
    }

    // ==================== 私有方法 ====================

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
        return sb.toString();
    }

    private void startRecoveryProbe() {
        probeExecutor.scheduleAtFixedRate(() -> {
            if (!available) {
                try {
                    jdbcTemplate.queryForObject("SELECT 1 FROM " + indexTable + " LIMIT 1", Integer.class);
                    available = true;
                    log.info("[PgSearchStrategy] 降级恢复成功");
                } catch (Exception e) {
                    log.debug("[PgSearchStrategy] 降级恢复探测失败: {}", e.getMessage());
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private String detectSearchConfig() {
        try {
            Integer count = jdbcTemplate.queryForObject(
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
            log.warn("[PgSearchStrategy] 索引表不可用，降级到内存索引: {}", e.getMessage());
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
                    String text = buildSearchableText(doc);
                    return text != null && text.toLowerCase().contains(lowerKeyword);
                })
                .map(doc -> {
                    SearchHit hit = SearchHit.builder()
                            .id(doc.getId()).type(doc.getType()).title(doc.getTitle())
                            .subtitle(doc.getSubtitle()).snippet(doc.getSnippet())
                            .path(doc.getPath()).status(doc.getStatus()).tags(doc.getTags())
                            .score(1.0f)
                            .createdAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
                            .updatedAt(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null)
                            .build();
                    if (request.isHighlight() && doc.getTitle() != null) {
                        hit.setHighlight(simpleHighlight(doc.getTitle(), keyword,
                                request.getHighlightPreTag(), request.getHighlightPostTag()));
                    }
                    return hit;
                }).toList();

        long total = allHits.size();
        int fromIndex = Math.min(request.getOffset(), allHits.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), allHits.size());

        return SearchResponse.builder()
                .hits(allHits.subList(fromIndex, toIndex))
                .total(total)
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .tookMs(System.currentTimeMillis() - start)
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
                String sql = "SELECT " + safeField + " AS key, COUNT(1) AS count FROM " + indexTable +
                        where + " GROUP BY " + safeField + " ORDER BY count DESC";
                List<SearchAggregation.Bucket> buckets = jdbcTemplate.query(sql,
                        (rs, rowNum) -> SearchAggregation.Bucket.builder()
                                .key(rs.getString("key")).count(rs.getLong("count")).build(),
                        params.toArray());
                aggregations.add(SearchAggregation.builder().field(field).label(field).buckets(buckets).build());
            } catch (Exception e) {
                log.warn("[PgSearchStrategy] 聚合查询失败: field={}", field, e);
            }
        }
        return aggregations;
    }

    private void appendFilter(StringBuilder where, List<Object> params, SearchFilter filter) {
        if (filter == null || filter.getField() == null) return;
        String field = sanitizeColumnName(filter.getField());
        switch (filter.getOperator()) {
            case EQ -> { where.append(" AND ").append(field).append(" = ?");
                if (filter.getValues() != null && !filter.getValues().isEmpty()) params.add(filter.getValues().get(0)); }
            case NE -> { where.append(" AND ").append(field).append(" != ?");
                if (filter.getValues() != null && !filter.getValues().isEmpty()) params.add(filter.getValues().get(0)); }
            case IN, NOT_IN -> {
                if (filter.getValues() != null && !filter.getValues().isEmpty()) {
                    String op = filter.getOperator() == SearchFilter.Operator.IN ? "IN" : "NOT IN";
                    where.append(" AND ").append(field).append(" ").append(op).append(" (")
                            .append(filter.getValues().stream().map(v -> "?").collect(Collectors.joining(",")))
                            .append(")");
                    params.addAll(filter.getValues());
                }
            }
            case GT -> { where.append(" AND ").append(field).append(" > ?");
                if (filter.getValues() != null && !filter.getValues().isEmpty()) params.add(filter.getValues().get(0)); }
            case LT -> { where.append(" AND ").append(field).append(" < ?");
                if (filter.getValues() != null && !filter.getValues().isEmpty()) params.add(filter.getValues().get(0)); }
            case GTE -> { where.append(" AND ").append(field).append(" >= ?");
                if (filter.getValues() != null && !filter.getValues().isEmpty()) params.add(filter.getValues().get(0)); }
            case LTE -> { where.append(" AND ").append(field).append(" <= ?");
                if (filter.getValues() != null && !filter.getValues().isEmpty()) params.add(filter.getValues().get(0)); }
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
        if (keyword == null) return null;
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > 200) trimmed = trimmed.substring(0, 200);
        return trimmed;
    }

    private String sanitizeColumnName(String column) {
        if (column == null) return "id";
        String lower = column.toLowerCase();
        if (ALLOWED_COLUMNS.contains(lower)) return lower;
        log.warn("[PgSearchStrategy] Column not in whitelist, fallback to id: {}", column);
        return "id";
    }

    private String simpleHighlight(String text, String keyword, String preTag, String postTag) {
        if (text == null || keyword == null) return text;
        int idx = text.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) return text;
        return text.substring(0, idx) + preTag + text.substring(idx, idx + keyword.length()) + postTag
                + text.substring(idx + keyword.length());
    }

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
            try { hit.setPath(rs.getString("path")); } catch (SQLException ignored) { }
            try {
                String tagsJson = rs.getString("tags");
                if (tagsJson != null && !tagsJson.isBlank() && !tagsJson.equals("[]")) {
                    List<String> tags = YdszJson.parseArray(tagsJson, String.class);
                    if (tags != null && !tags.isEmpty()) hit.setTags(tags);
                }
            } catch (SQLException ignored) { }
            try {
                var createdAt = rs.getTimestamp("created_at");
                if (createdAt != null) hit.setCreatedAt(createdAt.toInstant().toString());
            } catch (SQLException ignored) { }
            try {
                var updatedAt = rs.getTimestamp("updated_at");
                if (updatedAt != null) hit.setUpdatedAt(updatedAt.toInstant().toString());
            } catch (SQLException ignored) { }
            if (withHighlight) hit.setHighlight(rs.getString("highlight"));
            return hit;
        }
    }
}
