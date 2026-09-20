package com.njydsz.common.search.analytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.search.api.SearchHit;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.api.VectorSearchRequest;

/**
 * 搜索结果融合服务（RRF + WEIGHTED 双策略）。
 *
 * <p>解决多引擎 / 多检索模式协同查询时的结果合并问题：
 *
 * <ul>
 *   <li><b>RRF</b>（Reciprocal Rank Fusion）— 基于排名的倒数融合，对关键词和向量两路结果统一打分；
 *       公式：{@code RRF(d) = Σ 1/(k + rank_i(d))}，k=60 抑制单一列表的高位次绝对优势。
 *       不依赖绝对分数，对 BM25 vs cosine 这种量纲不同的分数天然归一化。</li>
 *   <li><b>WEIGHTED</b> — 将两路分数分别归一化到 [0,1] 后按 {@code keywordWeight} / {@code vectorWeight} 线性加权，
 *       适用于调用方明确知道相对重要性（如关键词优先 70%、语义补充 30%）的场景。</li>
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * SearchResponse keywordResp = engine.search(keywordReq);
 * SearchResponse vectorResp = engine.vectorSearch(vectorReq);
 * SearchResponse merged = fusionService.fusion(keywordResp, vectorResp, FusionOptions.rrf());
 * }</pre>
 *
 * <p>当任意一路返回空结果时直接退化为另一路（不降级为空列表），保证召回率不因单路故障受损。
 *
 * @author ydsz-team
 * @since 26.09.21
 */
@Slf4j
public class SearchResultFusionService {

  /** RFF 阻尼常数：值越大高排名文档的优势越被抑制；业界常用 60（Microsoft之举） */
  private static final int RRF_K = 60;

  /** 最小命中分数阈值（低于此值的结果不参与融合） */
  private static final float MIN_SCORE_THRESHOLD = 1e-6f;

  /**
   * 将关键词检索结果与向量语义检索结果融合为单一结果集。
   *
   * <p>融合后结果总数不超过 {@code limit}，如两路合并且无重复则最多 2*limit 候选中取前 limit 条。
   *
   * @param keywordResponse 关键词检索结果（BM25 / tsvector）
   * @param vectorResponse 向量语义检索结果（cosine similarity）
   * @param options 融合策略参数
   * @param limit 返回条数上限
   * @return 融合后的搜索结果，总条数不超过 {@code limit}
   */
  public SearchResponse fusion(
      SearchResponse keywordResponse,
      SearchResponse vectorResponse,
      FusionOptions options,
      int limit) {

    // 快速路径：一路为空则直接返回另一路
    if (isEmptyResponse(keywordResponse)) {
      return clamp(vectorResponse, limit);
    }
    if (isEmptyResponse(vectorResponse)) {
      return clamp(keywordResponse, limit);
    }

    List<SearchHit> keywordHits = keywordResponse.getHits() != null ? keywordResponse.getHits() : Collections.emptyList();
    List<SearchHit> vectorHits = vectorResponse.getHits() != null ? vectorResponse.getHits() : Collections.emptyList();

    List<SearchHit> merged;
    if (options.getStrategy() == VectorSearchRequest.CombinationStrategy.WEIGHTED) {
      merged = fusionWeighted(keywordHits, vectorHits, options);
    } else {
      // 默认 RRF
      merged = fusionRrf(keywordHits, vectorHits);
    }

    // 按融合后分数降序排列，取前 limit
    merged.sort(Comparator.comparingDouble((SearchHit h) -> h.getScore()).reversed());
    List<SearchHit> result = merged.size() > limit ? merged.subList(0, limit) : merged;

    return SearchResponse.builder()
        .hits(result)
        .total((long) result.size())
        .page(keywordResponse.getPage())
        .pageSize(keywordResponse.getPageSize())
        .tookMs(Math.max(keywordResponse.getTookMs(), vectorResponse.getTookMs()))
        .isDegraded(keywordResponse.isDegraded() && vectorResponse.isDegraded())
        .build();
  }

  // ==================== RRF 实现 ====================

  /**
   * 倒数排名融合（RRF）。
   *
   * <p>对每路结果按排名位置（1-based）计算 {@code 1/(k + rank)} 贡献值后累加。 同一文档在两路中都出现时分数叠加，自然获得更高排序（符合直觉：多路命中的文档更相关）。
   *
   * @param listA 关键词命中文档列表
   * @param listB 向量命中文档列表
   * @return 按融合分数降序排列的结果列表
   */
  private List<SearchHit> fusionRrf(List<SearchHit> listA, List<SearchHit> listB) {
    // docId → 融合分数
    Map<String, Double> rrfScores = new HashMap<>(listA.size() + listB.size());
    // docId → 命中条目（取 snippet / highlight 最丰富的一条）
    Map<String, SearchHit> hitIndex = new HashMap<>(listA.size() + listB.size());

    for (int i = 0; i < listA.size(); i++) {
      SearchHit hit = listA.get(i);
      if (hit == null || hit.getId() == null) {
        continue;
      }
      double score = 1.0 / (RRF_K + (i + 1));
      rrfScores.merge(hit.getId(), score, Double::sum);
      hitIndex.merge(hit.getId(), hit, this::pickBetterHit);
    }

    for (int i = 0; i < listB.size(); i++) {
      SearchHit hit = listB.get(i);
      if (hit == null || hit.getId() == null) {
        continue;
      }
      double score = 1.0 / (RRF_K + (i + 1));
      rrfScores.merge(hit.getId(), score, Double::sum);
      hitIndex.merge(hit.getId(), hit, this::pickBetterHit);
    }

    List<SearchHit> merged = new ArrayList<>(rrfScores.size());
    for (Map.Entry<String, Double> entry : rrfScores.entrySet()) {
      SearchHit base = hitIndex.get(entry.getKey());
      if (base != null) {
        // 用融合分数覆盖原始分数，保留其他字段
        base.setScore(entry.getValue().floatValue());
        merged.add(base);
      }
    }
    return merged;
  }

  // ==================== WEIGHTED 实现 ====================

  /**
   * 加权归一化融合。
   *
   * <p>对两路分数分别做 min-max 归一化后按权重线性组合： {@code final = w_a * norm(score_a) + w_b * norm(score_b)}。
   * 当某路中某文档不存在时该路分数视为 0。
   */
  private List<SearchHit> fusionWeighted(
      List<SearchHit> listA, List<SearchHit> listB, FusionOptions options) {
    float wA = options.getKeywordWeight();
    float wB = options.getVectorWeight();
    float totalW = wA + wB;
    if (totalW < MIN_SCORE_THRESHOLD) {
      // 权重都为 0 → 退化为 RRF
      return fusionRrf(listA, listB);
    }
    wA = wA / totalW;
    wB = wB / totalW;

    // 收集两路最大 / 最小分数用于归一化
    float minA = Float.MAX_VALUE, maxA = Float.MIN_VALUE;
    for (SearchHit h : listA) {
      if (h != null) {
        minA = Math.min(minA, h.getScore());
        maxA = Math.max(maxA, h.getScore());
      }
    }
    float minB = Float.MAX_VALUE, maxB = Float.MIN_VALUE;
    for (SearchHit h : listB) {
      if (h != null) {
        minB = Math.min(minB, h.getScore());
        maxB = Math.max(maxB, h.getScore());
      }
    }

    Map<String, Double> combinedScores = new HashMap<>(listA.size() + listB.size());
    Map<String, SearchHit> hitIndex = new HashMap<>(listA.size() + listB.size());

    for (SearchHit hit : listA) {
      if (hit == null || hit.getId() == null) {
        continue;
      }
      double normalized = normalize(hit.getScore(), minA, maxA);
      combinedScores.merge(hit.getId(), wA * normalized, Double::sum);
      hitIndex.merge(hit.getId(), hit, this::pickBetterHit);
    }

    for (SearchHit hit : listB) {
      if (hit == null || hit.getId() == null) {
        continue;
      }
      double normalized = normalize(hit.getScore(), minB, maxB);
      combinedScores.merge(hit.getId(), wB * normalized, Double::sum);
      hitIndex.merge(hit.getId(), hit, this::pickBetterHit);
    }

    List<SearchHit> merged = new ArrayList<>(combinedScores.size());
    for (Map.Entry<String, Double> entry : combinedScores.entrySet()) {
      SearchHit base = hitIndex.get(entry.getKey());
      if (base != null) {
        base.setScore(entry.getValue().floatValue());
        merged.add(base);
      }
    }
    return merged;
  }

  // ==================== 内部工具 ====================

  /**
   * min-max 归一化；最大 == 最小（等分）时返回 0.5。
   */
  private double normalize(float value, float min, float max) {
    if (max - min < MIN_SCORE_THRESHOLD) {
      return 0.5;
    }
    return (value - min) / (max - min);
  }

  /**
   * 当同一 docId 在两路中均命中时，保留 snippet / highlight 更丰富的一条。
   */
  private SearchHit pickBetterHit(SearchHit a, SearchHit b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    boolean aHasSnippet = a.getSnippet() != null && !a.getSnippet().isBlank();
    boolean bHasSnippet = b.getSnippet() != null && !b.getSnippet().isBlank();
    if (aHasSnippet && !bHasSnippet) {
      return a;
    }
    if (bHasSnippet && !aHasSnippet) {
      return b;
    }
    // 都有 snippet → 保留分数更高者
    return a.getScore() >= b.getScore() ? a : b;
  }

  private boolean isEmptyResponse(SearchResponse response) {
    return response == null
        || response.getHits() == null
        || response.getHits().isEmpty();
  }

  private SearchResponse clamp(SearchResponse response, int limit) {
    if (isEmptyResponse(response)) {
      return response;
    }
    if (response.getHits().size() <= limit) {
      return response;
    }
    List<SearchHit> hits = new ArrayList<>(response.getHits().subList(0, limit));
    return SearchResponse.builder()
        .hits(hits)
        .total(response.getTotal())
        .page(response.getPage())
        .pageSize(response.getPageSize())
        .tookMs(response.getTookMs())
        .isDegraded(response.isDegraded())
        .build();
  }

  /**
   * 融合策略配置。
   */
  public static class FusionOptions {

    private final VectorSearchRequest.CombinationStrategy strategy;
    private final float keywordWeight;
    private final float vectorWeight;

    private FusionOptions(VectorSearchRequest.CombinationStrategy strategy, float keywordWeight, float vectorWeight) {
      this.strategy = strategy;
      this.keywordWeight = keywordWeight;
      this.vectorWeight = vectorWeight;
    }

    /** RRF 融合策略。 */
    public static FusionOptions rrf() {
      return new FusionOptions(VectorSearchRequest.CombinationStrategy.RRF, 0.5f, 0.5f);
    }

    /** 加权融合策略。 */
    public static FusionOptions weighted(float keywordWeight, float vectorWeight) {
      return new FusionOptions(VectorSearchRequest.CombinationStrategy.WEIGHTED, keywordWeight, vectorWeight);
    }

    public VectorSearchRequest.CombinationStrategy getStrategy() {
      return strategy;
    }

    public float getKeywordWeight() {
      return keywordWeight;
    }

    public float getVectorWeight() {
      return vectorWeight;
    }
  }
}
