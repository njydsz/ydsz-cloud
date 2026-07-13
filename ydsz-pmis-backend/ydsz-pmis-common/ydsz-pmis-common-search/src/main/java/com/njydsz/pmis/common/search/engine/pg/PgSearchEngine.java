package com.njydsz.pmis.common.search.engine.pg;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.njydsz.pmis.common.search.api.SearchAggregation;
import com.njydsz.pmis.common.search.api.SearchFilter;
import com.njydsz.pmis.common.search.api.SearchHit;
import com.njydsz.pmis.common.search.api.SearchRequest;
import com.njydsz.pmis.common.search.api.SearchResponse;
import com.njydsz.pmis.common.search.api.SearchSuggestion;
import com.njydsz.pmis.common.search.core.IndexDocument;
import com.njydsz.pmis.common.search.core.SearchEngine;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 PostgreSQL tsvector 的搜索引擎实现
 * <p>
 * 利用 PG 原生全文检索能力，支持中文分词（zhparser/jieba）、高亮、相关性排序、模糊匹配。
 *
 * <p><b>索引策略：</b>
 * <ul>
 *   <li>使用统一索引表 {@code pmis_search_index} 存储所有可搜索文档</li>
 *   <li>通过 {@code to_tsvector('search_zh', searchable_text)} 构建中文全文索引</li>
 *   <li>支持 GIN 索引加速全文检索</li>
 *   <li>支持 pg_trgm 扩展实现模糊匹配</li>
 * </ul>
 *
 * <p><b>分词配置：</b>
 * <ul>
 *   <li>优先使用 {@code search_zh}（zhparser 扩展，中文分词）</li>
 *   <li>回退到 {@code simple}（不分词，按空格分割）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
public class PgSearchEngine implements SearchEngine {

    private static final String ENGINE_NAME = "pg-tsvector";

    /** 索引表名 */
    private static final String INDEX_TABLE = "pmis_search_index";

    /** 默认中文搜索配置 */
    private static final String DEFAULT_SEARCH_CONFIG = "search_zh";

    /** 回退搜索配置（无 zhparser 时使用） */
    private static final String FALLBACK_SEARCH_CONFIG = "simple";

    private final JdbcTemplate jdbcTemplate;
    private final String searchConfig;
    private volatile boolean available;

    /** 内存索引（当 PG 索引表不可用时的降级方案） */
    private final Map<String, IndexDocument> memoryIndex = new ConcurrentHashMap<>();

    public PgSearchEngine(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.searchConfig = detectSearchConfig();
        this.available = initIndexTable();
    }

    public PgSearchEngine(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.searchConfig = detectSearchConfig();
        this.available = initIndexTable();
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

            // 构建 SQL 参数
            List<Object> params = new ArrayList<>();
            StringBuilder where = new StringBuilder(" WHERE 1=1");

            // 全文匹配条件
            where.append(" AND to_tsvector(?, searchable_text) @@ plainto_tsquery(?, ?)");
            params.add(searchConfig);
            params.add(searchConfig);
            params.add(keyword);

            // 类型过滤
            if (request.getTypes() != null && !request.getTypes().isEmpty()) {
                where.append(" AND doc_type IN (")
                        .append(request.getTypes().stream()
                                .map(t -> "?")
                                .collect(Collectors.joining(",")))
                        .append(")");
                params.addAll(request.getTypes());
            }

            // 租户过滤
            if (request.getTenantId() != null && !request.getTenantId().isBlank()) {
                where.append(" AND tenant_id = ?");
                params.add(request.getTenantId());
            }

            // 自定义过滤条件
            if (request.getFilters() != null) {
                for (SearchFilter filter : request.getFilters()) {
                    appendFilter(where, params, filter);
                }
            }

            // 模糊匹配增强（pg_trgm）
            if (request.isFuzzy()) {
                where.append(" OR searchable_text % ?");
                params.add(keyword);
            }

            // 计算总数
            String countSql = "SELECT COUNT(1) FROM " + INDEX_TABLE + where;
            Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
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

            // 构建查询 SQL（含高亮和排序）
            List<Object> queryParams = new ArrayList<>(params);

            StringBuilder selectSql = new StringBuilder("SELECT id, doc_type, title, subtitle, snippet, status, ");
            selectSql.append("ts_rank(to_tsvector(?, searchable_text), plainto_tsquery(?, ?)) AS rank");

            // 高亮
            if (request.isHighlight()) {
                selectSql.append(", ts_headline(?, searchable_text, plainto_tsquery(?, ?), 'MaxWords=60, MinWords=20, ShortWord=3, HighlightAll=FALSE, StartSel=?, StopSel=?') AS highlight");
            }

            queryParams.add(0, searchConfig); // for ts_rank to_tsvector
            queryParams.add(1, searchConfig); // for ts_rank plainto_tsquery
            queryParams.add(2, keyword);       // for ts_rank plainto_tsquery

            if (request.isHighlight()) {
                queryParams.add(searchConfig); // for ts_headline to_tsvector
                queryParams.add(searchConfig); // for ts_headline plainto_tsquery
                queryParams.add(keyword);       // for ts_headline plainto_tsquery
                queryParams.add(request.getHighlightPreTag());
                queryParams.add(request.getHighlightPostTag());
            }

            selectSql.append(" FROM ").append(INDEX_TABLE).append(where);

            // 排序
            if (request.getSortBy() != null && !request.getSortBy().isBlank()) {
                String direction = request.isAscending() ? "ASC" : "DESC";
                selectSql.append(" ORDER BY ").append(sanitizeColumnName(request.getSortBy())).append(" ").append(direction);
            } else {
                // 默认按相关性排序
                selectSql.append(" ORDER BY rank DESC, updated_at DESC");
            }

            // 分页
            selectSql.append(" LIMIT ? OFFSET ?");
            queryParams.add(request.getPageSize());
            queryParams.add(request.getOffset());

            List<SearchHit> hits = jdbcTemplate.query(selectSql.toString(), new SearchHitRowMapper(request.isHighlight()), queryParams.toArray());

            long took = System.currentTimeMillis() - start;

            // 聚合
            List<SearchAggregation> aggregations = Collections.emptyList();
            if (request.getAggregations() != null && !request.getAggregations().isEmpty()) {
                aggregations = executeAggregations(where, params, request.getAggregations());
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

        // 同时写入内存索引（作为降级备份）
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
            String tagsJson = document.getTags() != null ? toJsonArray(document.getTags()) : "[]";
            String metadataJson = document.getMetadata() != null ? toJson(document.getMetadata()) : "{}";

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
        for (IndexDocument doc : documents) {
            index(doc);
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
            // 内存建议：从内存索引标题中匹配
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
            // 使用 PG ILIKE 前缀匹配
            String sql = "SELECT DISTINCT title FROM " + INDEX_TABLE +
                    " WHERE title ILIKE ? ORDER BY title LIMIT ?";
            List<String> suggestions = jdbcTemplate.queryForList(sql, String.class,
                    prefix + "%", limit);
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

    // ==================== 私有方法 ====================

    /**
     * 检测 PG 是否有中文分词配置
     */
    private String detectSearchConfig() {
        try {
            // 检查 zhparser 扩展和 search_zh 配置
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

    /**
     * 初始化索引表
     */
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

            // 创建 GIN 索引（全文检索加速）
            try {
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_" + INDEX_TABLE + "_fts " +
                                "ON " + INDEX_TABLE + " USING GIN (to_tsvector('" + searchConfig + "', searchable_text))");
            } catch (Exception e) {
                log.debug("[PgSearchEngine] GIN 索引创建跳过: {}", e.getMessage());
            }

            // 创建类型索引
            try {
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_" + INDEX_TABLE + "_type ON " + INDEX_TABLE + " (doc_type)");
            } catch (Exception e) {
                log.debug("[PgSearchEngine] 类型索引创建跳过: {}", e.getMessage());
            }

            // 创建租户索引
            try {
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_" + INDEX_TABLE + "_tenant ON " + INDEX_TABLE + " (tenant_id)");
            } catch (Exception e) {
                log.debug("[PgSearchEngine] 租户索引创建跳过: {}", e.getMessage());
            }

            // 创建 trigram 索引（模糊匹配加速）
            try {
                jdbcTemplate.execute(
                        "CREATE EXTENSION IF NOT EXISTS pg_trgm");
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

    /**
     * 内存搜索（降级方案）
     */
    private SearchResponse searchInMemory(SearchRequest request) {
        long start = System.currentTimeMillis();
        String keyword = request.getKeyword();
        if (keyword == null || keyword.isBlank()) {
            return SearchResponse.empty(request.getPage(), request.getPageSize());
        }

        String lowerKeyword = keyword.toLowerCase();

        List<SearchHit> allHits = memoryIndex.values().stream()
                .filter(doc -> {
                    // 类型过滤
                    if (request.getTypes() != null && !request.getTypes().isEmpty()
                            && !request.getTypes().contains(doc.getType())) {
                        return false;
                    }
                    // 租户过滤
                    if (request.getTenantId() != null && !request.getTenantId().isBlank()
                            && !request.getTenantId().equals(doc.getTenantId())) {
                        return false;
                    }
                    // 关键词匹配
                    String text = request.isTitleOnly() ? doc.getTitleSearchableText() : doc.getSearchableText();
                    return text != null && text.toLowerCase().contains(lowerKeyword);
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
                    // 简单高亮
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

    /**
     * 执行聚合查询
     */
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

    /**
     * 追加过滤条件
     */
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

    /**
     * 关键词安全处理
     */
    private String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // 限制长度
        if (trimmed.length() > 200) {
            trimmed = trimmed.substring(0, 200);
        }
        return trimmed;
    }

    /**
     * 列名安全处理（仅允许字母、数字、下划线）
     */
    private String sanitizeColumnName(String column) {
        if (column == null) {
            return "id";
        }
        String sanitized = column.replaceAll("[^a-zA-Z0-9_]", "");
        return sanitized.isEmpty() ? "id" : sanitized;
    }

    /**
     * 简单高亮（内存搜索降级用）
     */
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
     * 列表转 JSON 数组字符串
     */
    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Map 转 JSON 字符串
     */
    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (i++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof Number) {
                sb.append(val);
            } else {
                sb.append("\"").append(val.toString().replace("\"", "\\\"")).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 搜索结果行映射器
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
            if (withHighlight) {
                hit.setHighlight(rs.getString("highlight"));
            }
            return hit;
        }
    }
}
