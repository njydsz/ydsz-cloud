package com.njydsz.agent.infra.tool;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import com.njydsz.common.cache.api.Cache;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ToolDefinition;
import com.njydsz.agent.domain.tool.SemanticToolSearchService;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.common.cache.YdszCache;

/**
 * 基于 Embedding 向量相似度的语义 Tool 搜索服务实现
 *
 * <p>通过 LLM 提供的 embedding 接口将所有已注册工具的描述文本向量化并缓存，
 * 查询时计算查询向量与各工具描述向量的余弦相似度，按降序返回最相关的工具列表。
 *
 * <h3>安全策略</h3>
 *
 * <ul>
 *   <li>embedding 缓存上限 1000 条（LRU 淘汰）
 *   <li>搜索时若 LLM 不可用降级为字符串包含匹配
 *   <li>相似度阈值 0.5（低于此值不返回）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public class EmbeddingSemanticToolSearchService implements SemanticToolSearchService {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 缓存最大条目数 */
  private static final int MAX_CACHE_SIZE = 1000;

  /** 相似度阈值 */
  private static final BigDecimal SIMILARITY_THRESHOLD = new BigDecimal("0.5");

  /** 默认返回条数（topK 非法时使用） */
  private static final int DEFAULT_TOP_K = 5;

  /** 降级匹配时名称命中得分 */
  private static final int FALLBACK_NAME_MATCH_SCORE = 10;

  /** 降级匹配时描述命中得分 */
  private static final int FALLBACK_DESC_MATCH_SCORE = 5;

  /** MathContext 精度控制（10 位有效数字） */
  private static final MathContext MATH_CONTEXT = new MathContext(10, RoundingMode.HALF_UP);

  /** BigDecimal 标量单位（用于除以向量范数乘积） */
  private static final int SCALE = 8;

  /** LLM 客户端（用于获取 embedding 向量） */
  private final LlmClient llmClient;

  /** 工具注册中心 */
  private final ToolRegistry toolRegistry;

  /**
   * embedding 缓存（key=工具名, value=embedding 向量）。
   *
   * <p>使用 YdszCache 替代手写 LinkedHashMap + ReadWriteLock，
   * 获得 TinyLFU 自动驱逐（比 LRU 更优的命中率）、线程安全分片、命中率统计等能力。
   */
  private final Cache<String, List<Float>> embeddingCache =
      YdszCache.<String, List<Float>>newBuilder().maximumSize(MAX_CACHE_SIZE).build();

  public EmbeddingSemanticToolSearchService(LlmClient llmClient, ToolRegistry toolRegistry) {
    this.llmClient = llmClient;
    this.toolRegistry = toolRegistry;
  }

  /**
   * 预热：为所有已注册工具的描述构建 embedding 缓存。
   *
   * <p>建议在应用启动后或工具注册变更时调用。
   */
  public void warmup() {
    List<ToolDefinition> tools = toolRegistry.getToolDefinitions();
    log.info("[SemToolSearch] 开始预热，共 {} 个工具", tools.size());
    for (ToolDefinition tool : tools) {
      cacheToolEmbedding(tool);
    }
    log.info("[SemToolSearch] 预热完成，缓存 {} 个工具 embedding", embeddingCache.estimatedSize());
  }

  @Override
  public List<ToolDefinition> search(String query, int topK) {
    if (query == null || query.isBlank()) {
      log.warn("[SemToolSearch] 搜索查询为空，返回空列表");
      return List.of();
    }
    if (topK <= 0) {
      topK = DEFAULT_TOP_K;
    }

    // 1. 尝试获取查询向量
    List<Float> queryVector;
    try {
      queryVector = llmClient.embed(query);
    } catch (Exception e) {
      log.warn("[SemToolSearch] LLM embedding 不可用({})，降级为字符串包含匹配", e.getMessage());
      return fallbackStringMatch(query, topK);
    }
    if (queryVector == null || queryVector.isEmpty()) {
      log.warn("[SemToolSearch] LLM embedding 返回空向量，降级为字符串包含匹配");
      return fallbackStringMatch(query, topK);
    }

    // 2. 遍历缓存计算余弦相似度，按阈值过滤 + 降序排序
    List<ToolScore> scored = computeSimilarity(queryVector);

    // 3. 组装结果，取前 topK 个
    List<ToolDefinition> results = new ArrayList<>(Math.min(topK, scored.size()));
    for (int i = 0; i < Math.min(topK, scored.size()); i++) {
      results.add(scored.get(i).tool());
    }
    log.info("[SemToolSearch] 语义搜索完成: query='{}', 命中 {} 条", query, results.size());
    return results;
  }

  /**
   * 计算查询向量与缓存中所有工具向量的余弦相似度，过滤并排序。
   *
   * @param queryVector 查询向量
   * @return 按相似度降序排列的分数列表
   */
  private List<ToolScore> computeSimilarity(List<Float> queryVector) {
    List<ToolDefinition> allTools = toolRegistry.getToolDefinitions();
    List<ToolScore> scored = new ArrayList<>(allTools.size());

    for (ToolDefinition tool : allTools) {
      List<Float> toolVector = embeddingCache.getIfPresent(tool.getName());
      if (toolVector == null) {
        continue;
      }
      BigDecimal similarity = cosineSimilarity(queryVector, toolVector);
      if (similarity.compareTo(SIMILARITY_THRESHOLD) >= 0) {
        scored.add(new ToolScore(tool, similarity));
      }
    }

    scored.sort((a, b) -> b.score().compareTo(a.score()));
    return scored;
  }

  /**
   * 计算两个向量的余弦相似度。
   *
   * <p>使用 {@link BigDecimal} 避免浮点精度问题，公式：dotProduct(a,b) / (norm(a) * norm(b))。
   *
   * @param a 向量 A
   * @param b 向量 B
   * @return 余弦相似度值域 [-1, 1]
   */
  private BigDecimal cosineSimilarity(List<Float> a, List<Float> b) {
    if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
      return BigDecimal.ZERO;
    }
    int len = Math.min(a.size(), b.size());
    BigDecimal dotProduct = BigDecimal.ZERO;
    BigDecimal normA = BigDecimal.ZERO;
    BigDecimal normB = BigDecimal.ZERO;

    for (int i = 0; i < len; i++) {
      BigDecimal ai = BigDecimal.valueOf(a.get(i));
      BigDecimal bi = BigDecimal.valueOf(b.get(i));
      dotProduct = dotProduct.add(ai.multiply(bi, MATH_CONTEXT), MATH_CONTEXT);
      normA = normA.add(ai.multiply(ai, MATH_CONTEXT), MATH_CONTEXT);
      normB = normB.add(bi.multiply(bi, MATH_CONTEXT), MATH_CONTEXT);
    }

    BigDecimal normASqrt = BigDecimal.valueOf(Math.sqrt(normA.doubleValue()));
    BigDecimal normBSqrt = BigDecimal.valueOf(Math.sqrt(normB.doubleValue()));
    BigDecimal denominator = normASqrt.multiply(normBSqrt, MATH_CONTEXT);

    if (denominator.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return dotProduct.divide(denominator, SCALE, RoundingMode.HALF_UP);
  }

  /**
   * 为单个工具构建 embedding 并加入缓存。
   *
   * @param tool 工具定义
   */
  private void cacheToolEmbedding(ToolDefinition tool) {
    String description = tool.getDescription();
    if (description == null || description.isBlank()) {
      description = tool.getName();
    }
    try {
      List<Float> vector = llmClient.embed(description);
      if (vector != null && !vector.isEmpty()) {
        embeddingCache.put(tool.getName(), List.copyOf(vector));
      }
    } catch (Exception e) {
      log.warn("[SemToolSearch] 工具 '{}' embedding 失败: {}", tool.getName(), e.getMessage());
    }
  }

  /**
   * 降级搜索：字符串包含匹配。
   *
   * <p>当 LLM 不可用时作为兜底策略。
   *
   * @param query 查询字符串
   * @param topK 最多返回条数
   * @return 匹配的工具列表
   */
  private List<ToolDefinition> fallbackStringMatch(String query, int topK) {
    String lowerQuery = query.toLowerCase();
    List<ToolScore> scored = new ArrayList<>(COLLECTION_CAPACITY);
    List<ToolDefinition> allTools = toolRegistry.getToolDefinitions();
    for (ToolDefinition tool : allTools) {
      String name = tool.getName() != null ? tool.getName().toLowerCase() : "";
      String desc = tool.getDescription() != null ? tool.getDescription().toLowerCase() : "";
      int score = 0;
      if (name.contains(lowerQuery)) {
        score += FALLBACK_NAME_MATCH_SCORE;
      }
      if (desc.contains(lowerQuery)) {
        score += FALLBACK_DESC_MATCH_SCORE;
      }
      if (score > 0) {
        scored.add(new ToolScore(tool, BigDecimal.valueOf(score)));
      }
    }
    scored.sort((a, b) -> b.score().compareTo(a.score()));
    List<ToolDefinition> results = new ArrayList<>(Math.min(topK, scored.size()));
    for (int i = 0; i < Math.min(topK, scored.size()); i++) {
      results.add(scored.get(i).tool());
    }
    log.info("[SemToolSearch] 降级字符串匹配完成: query='{}', 命中 {} 条", query, results.size());
    return results;
  }

  /** 工具 + 相似度分数内部记录 */
  private record ToolScore(ToolDefinition tool, BigDecimal score) {}
}
