package com.njydsz.agent.server.search;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.search.WebSearchService;

/**
 * 混合搜索服务（RAG + WebSearch）
 *
 * <p>融合知识库向量检索和 Web 搜索结果：
 *
 * <ol>
 *   <li>根据配置决定是否启用 web search（ydsz.agent.hybrid-search.web-enabled）</li>
 *   <li>并行执行 RAG 检索和 Web 搜索</li>
 *   <li>合并结果，标记来源（knowledge_base / web）</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public class HybridSearchService {

  /** 知识库结果默认占比 */
  private static final double DEFAULT_KB_RATIO = 0.7;

  /** 网络搜索结果默认占比 */
  private static final double DEFAULT_WEB_RATIO = 0.3;

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 非法 topK 时的默认返回条数 */
  private static final int DEFAULT_TOP_K_FALLBACK = 5;

  private final RagSearchDelegate ragSearchDelegate;
  private final WebSearchService webSearchService;
  private final AgentProperties properties;

  /**
   * 构造混合搜索服务
   *
   * @param ragSearchDelegate RAG 搜索委托（封装 RagService 的检索逻辑）
   * @param webSearchService Web 搜索服务
   * @param properties Agent 配置
   */
  public HybridSearchService(
      RagSearchDelegate ragSearchDelegate,
      WebSearchService webSearchService,
      AgentProperties properties) {
    this.ragSearchDelegate = ragSearchDelegate;
    this.webSearchService = webSearchService;
    this.properties = properties;
  }

  /**
   * 混合搜索
   *
   * <p>根据配置并行执行 RAG 检索和 Web 搜索，合并结果并标记来源。
   *
   * @param query 用户查询
   * @param datasetId 知识库 ID（可为 null 表示仅 web 搜索）
   * @param topK 总返回数
   * @return 混合结果列表
   */
  public List<HybridChunk> hybridSearch(String query, String datasetId, int topK) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    if (topK <= 0) {
      topK = DEFAULT_TOP_K_FALLBACK;
    }

    AgentProperties.HybridSearch hybridConfig = properties.getHybridSearch();
    boolean webEnabled = hybridConfig.isWebEnabled()
        && webSearchService != null
        && webSearchService.isAvailable();

    // 计算各来源分配数量
    int webCount = webEnabled ? (int) Math.ceil(topK * hybridConfig.getWebResultRatio()) : 0;
    int kbCount = topK - webCount;

    List<HybridChunk> results = new ArrayList<>(topK);

    // 1. 并行检索：RAG 向量检索
    if (kbCount > 0 && datasetId != null) {
      List<TextChunk> kbChunks = ragSearchDelegate.search(query, datasetId, kbCount);
      for (TextChunk chunk : kbChunks) {
        results.add(new HybridChunk(
            chunk.getContent(),
            "knowledge_base",
            chunk.getDocumentTitle() != null ? chunk.getDocumentTitle() : "知识库",
            chunk.getDocumentId(),
            null,
            null,
            BigDecimal.ONE));
      }
    }

    // 2. 并行检索：Web 搜索
    if (webCount > 0) {
      List<WebSearchService.SearchResult> webResults =
          webSearchService.search(query, webCount);
      for (WebSearchService.SearchResult result : webResults) {
        results.add(new HybridChunk(
            result.snippet(),
            "web",
            result.title(),
            null,
            result.url(),
            result.title(),
            BigDecimal.ONE));
      }
    }

    return results.isEmpty() ? List.of() : results;
  }

  /**
   * 混合搜索结果条目
   *
   * @param content 内容文本
   * @param sourceType 来源类型（knowledge_base / web）
   * @param sourceName 来源名称（文档标题或网页标题）
   * @param documentId 文档 ID（来源为知识库时）
   * @param url 网页链接（来源为 web 时）
   * @param title 标题
   * @param score 相关性得分（使用 BigDecimal 避免精度损失）
   */
  public record HybridChunk(
      String content,
      String sourceType,
      String sourceName,
      String documentId,
      String url,
      String title,
      BigDecimal score) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 判断是否来自知识库
     *
     * @return true 表示知识库来源
     */
    public boolean isKnowledgeBase() {
      return "knowledge_base".equals(sourceType);
    }

    /**
     * 判断是否来自 Web
     *
     * @return true 表示 Web 来源
     */
    public boolean isWeb() {
      return "web".equals(sourceType);
    }
  }

  /**
   * RAG 搜索委托接口
   *
   * <p>封装知识库向量检索逻辑，由 {@code RagSearchProvider} 实现并注入。
   * 解耦 HybridSearchService 与 RagService 的直接依赖，符合 DDD 分层规范。
   */
  public interface RagSearchDelegate {

    /**
     * 在指定知识库中执行向量检索
     *
     * @param query 查询文本
     * @param datasetId 数据集 ID
     * @param topK 返回条数
     * @return 文本块列表
     */
    List<TextChunk> search(String query, String datasetId, int topK);
  }
}
