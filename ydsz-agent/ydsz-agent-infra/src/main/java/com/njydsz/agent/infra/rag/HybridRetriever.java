package com.njydsz.agent.infra.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.agent.domain.rag.Reranker;
import com.njydsz.agent.domain.rag.Retriever;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.VectorStore;
import com.njydsz.common.tenant.TenantContextHolder;

/**
 * 混合检索器（Hybrid Retrieval）
 *
 * <p>结合向量相似度检索和全文检索（BM25/ILIKE），通过 RRF（Reciprocal Rank Fusion） 融合两路检索结果，提升召回率和精确度。
 *
 * <p>实现 domain 层 {@link Retriever} 接口，符合 DDD 分层规范。
 *
 * <h3>RRF 算法</h3>
 *
 * <pre>
 * score(d) = Σ 1 / (k + rank_i(d))
 * </pre>
 *
 * <p>其中 k 为平滑常数（默认 60），rank_i(d) 为文档 d 在第 i 路检索中的排名（从 1 开始）。
 *
 * <p><b>多租户隔离（P0 修复）</b>：全文检索走 {@link JdbcTemplate} 原生 SQL，不经过 MyBatis 租户拦截器，SQL 中需显式追加 {@code
 * tenant_id} 过滤，防止跨租户文档被召回。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class HybridRetriever implements Retriever {

  /** RRF 平滑常数 */
  private static final int RRF_K = 60;

  /** 候选召回分数下限缩放系数（召回时放宽为最低分数的一半） */
  private static final double RECALL_MIN_SCORE_FACTOR = 0.5;

  /** 日志中查询文本的截断长度 */
  private static final int LOG_QUERY_TRUNCATE_LENGTH = 50;

  /** 文本块表名 */
  private static final String TABLE_NAME = "ydsz_agt_document_chunk";

  /** 向量存储 */
  private final VectorStore vectorStore;

  /** JDBC 模板 */
  private final JdbcTemplate jdbcTemplate;

  /** 重排序器（可选，未配置时使用 IdentityReranker 兜底） */
  private final Reranker reranker;

  /** 全文检索是否可用（运行期可重新探测，补建表后自动恢复） */
  private volatile boolean fullTextAvailable;

  /** 上次全文可用性探测时间（毫秒） */
  private volatile long lastProbeAt = 0L;

  /** 全文可用性重新探测冷却间隔（毫秒）：探测失败后最多 1 分钟重试一次 */
  private static final long PROBE_RETRY_INTERVAL_MS = 60_000L;

  /** 是否启用租户隔离 */
  private final boolean tenantIsolationEnabled;

  public HybridRetriever(
      VectorStore vectorStore, JdbcTemplate jdbcTemplate, boolean tenantIsolationEnabled) {
    this(vectorStore, jdbcTemplate, new IdentityReranker(), tenantIsolationEnabled);
  }

  public HybridRetriever(
      VectorStore vectorStore,
      JdbcTemplate jdbcTemplate,
      Reranker reranker,
      boolean tenantIsolationEnabled) {
    this.vectorStore = vectorStore;
    this.jdbcTemplate = jdbcTemplate;
    this.reranker = reranker != null ? reranker : new IdentityReranker();
    this.tenantIsolationEnabled = tenantIsolationEnabled;
    this.fullTextAvailable = checkFullTextAvailability();
    this.lastProbeAt = System.currentTimeMillis();
  }

  /**
   * 执行混合检索：并行召回向量与全文两路结果，再用 RRF 融合排序。
   *
   * <p>召回阶段刻意放宽条件——两路各取 {@code topK * 2} 条、向量路阈值降为 {@code minScore * 0.5}，目的是给 RRF 留出足够的重排空间；
   * 单路排名靠后但两路都命中的文档，融合后可能反超单路头部结果。
   *
   * <p><b>降级策略</b>：建表检查在构造期完成，若 {@code ydsz_agt_document_chunk} 不存在则全程跳过全文检索；全文 SQL 运行期异常也只记 warn
   * 并返回空列表， 退化为纯向量检索，不会让整个检索链路失败。
   *
   * @param query 用户查询语句；为 {@code null} 或空白时直接返回空列表，不产生任何 IO
   * @param topK 融合后返回的文档条数上限
   * @param minScore 向量检索相似度下限，内部按 50% 放宽后再交由 RRF 精排
   * @return 按 RRF 分值降序排列的文本块；无命中时返回空列表而非 {@code null}
   */
  @Override
  public List<TextChunk> retrieve(String query, int topK, double minScore) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    List<TextChunk> vectorResults = vectorStore.search(query, topK * 2, minScore * RECALL_MIN_SCORE_FACTOR);
    log.debug("[Hybrid-Retrieval] 向量检索: {} 条", vectorResults.size());

    List<TextChunk> fullTextResults = List.of();
    if (fullTextAvailable) {
      fullTextResults = fullTextSearch(query, topK * 2);
      log.debug("[Hybrid-Retrieval] 全文检索: {} 条", fullTextResults.size());
    }

    List<TextChunk> merged = rrfFuse(vectorResults, fullTextResults, topK);
    // 精排阶段：通过 Reranker 对融合结果做重排序，提升 Top-K 精确度
    List<TextChunk> reranked = reranker.rerank(query, merged, topK);
    log.info(
        "[Hybrid-Retrieval] 混合检索完成: query='{}', vector={}, fulltext={}, merged={}, reranked={}",
        truncate(query, LOG_QUERY_TRUNCATE_LENGTH),
        vectorResults.size(),
        fullTextResults.size(),
        merged.size(),
        reranked.size());
    return reranked;
  }

  private List<TextChunk> rrfFuse(
      List<TextChunk> vectorResults, List<TextChunk> fullTextResults, int topK) {
    Map<String, RrfEntry> entryMap = new HashMap<>();

    for (int i = 0; i < vectorResults.size(); i++) {
      TextChunk chunk = vectorResults.get(i);
      String key = chunk.getId() != null ? chunk.getId() : chunk.getContent();
      entryMap.computeIfAbsent(key, k -> new RrfEntry(chunk)).addScore(1.0 / (RRF_K + i + 1));
    }

    for (int i = 0; i < fullTextResults.size(); i++) {
      TextChunk chunk = fullTextResults.get(i);
      String key = chunk.getId() != null ? chunk.getId() : chunk.getContent();
      entryMap.computeIfAbsent(key, k -> new RrfEntry(chunk)).addScore(1.0 / (RRF_K + i + 1));
    }

    return entryMap.values().stream()
        .sorted((a, b) -> Double.compare(b.rrfScore, a.rrfScore))
        .limit(topK)
        .map(e -> e.chunk)
        .toList();
  }

  private List<TextChunk> fullTextSearch(String query, int topK) {
    // P1 优化：构造期探测失败后运行期带冷却重试，启动后补建表可自动恢复全文检索
    if (!fullTextAvailable) {
      long now = System.currentTimeMillis();
      if (now - lastProbeAt < PROBE_RETRY_INTERVAL_MS) {
        return List.of();
      }
      lastProbeAt = now;
      fullTextAvailable = checkFullTextAvailability();
      if (!fullTextAvailable) {
        return List.of();
      }
      log.info("[Hybrid-Retrieval] 全文检索可用性恢复，已重新启用全文检索");
    }
    try {
      StringBuilder sql =
          new StringBuilder(
              "SELECT id, content, document_id, document_title, source, "
                  + "chunk_index, token_count FROM "
                  + TABLE_NAME
                  + " "
                  + "WHERE deleted = false AND content ILIKE ? ");
      List<Object> params = new ArrayList<>();
      String pattern = "%" + query.replace("%", "\\%").replace("_", "\\_") + "%";
      params.add(pattern);
      // 多租户：全文检索走 JdbcTemplate，需显式追加租户过滤，避免跨租户召回
      String tenantId = resolveTenantId();
      if (tenantId != null) {
        sql.append("AND tenant_id = ? ");
        params.add(tenantId);
      }
      sql.append(
          "ORDER BY ts_rank(to_tsvector('simple', content), "
              + "plainto_tsquery('simple', ?)) DESC "
              + "LIMIT ?");
      params.add(query);
      params.add(topK);
      return jdbcTemplate.query(
          sql.toString(),
          (rs, rowNum) ->
              new TextChunk(
                  rs.getString("id"),
                  rs.getString("content"),
                  rs.getString("document_id"),
                  rs.getString("document_title"),
                  rs.getString("source"),
                  rs.getInt("chunk_index"),
                  rs.getInt("token_count"),
                  Map.of(),
                  null),
          params.toArray());
    } catch (Exception e) {
      log.warn("[Hybrid-Retrieval] 全文检索失败，降级到纯向量检索: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * 解析当前请求租户 ID；无需隔离时返回 null。
   *
   * @return 租户 ID；未启用隔离 / 无租户上下文 / 超管 / 跳过隔离时返回 null
   */
  private String resolveTenantId() {
    if (!tenantIsolationEnabled
        || !TenantContextHolder.isPresent()
        || TenantContextHolder.isSuperAdmin()
        || TenantContextHolder.isSkipIsolation()) {
      return null;
    }
    return TenantContextHolder.getTenantId();
  }

  private boolean checkFullTextAvailability() {
    try {
      Integer count =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '"
                  + TABLE_NAME
                  + "'",
              Integer.class);
      return count != null && count > 0;
    } catch (Exception e) {
      log.warn("[Hybrid-Retrieval] 全文检索可用性检查失败, DB可能不可用, err={}", e.getMessage());
      return false;
    }
  }

  private String truncate(String text, int maxLen) {
    if (text == null) {
      return "";
    }
    return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
  }

  /**
   * RRF（Reciprocal Rank Fusion）融合条目。
   *
   * <p>记录某文本块在多个检索通道（向量/关键词）中的累计融合得分， 用于混合检索结果的最终排序。
   */
  private static class RrfEntry {
    /** 文本块 */
    final TextChunk chunk;

    /** 累计 RRF 得分（各通道 1/(k+rank) 之和） */
    double rrfScore = 0.0;

    RrfEntry(TextChunk chunk) {
      this.chunk = chunk;
    }

    /**
     * 累加一个通道的得分。
     *
     * @param score 该通道的 1/(k+rank) 分值
     */
    void addScore(double score) {
      this.rrfScore += score;
    }
  }
}
