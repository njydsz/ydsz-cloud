package com.njydsz.agent.server.search;

import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.rag.Retriever;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.VectorStore;

/**
 * RAG 搜索提供者
 *
 * <p>封装知识库向量检索逻辑，实现 {@link HybridSearchService.RagSearchDelegate} 接口，
 * 桥接 {@link RagService} 与混合搜索服务，解耦混合搜索对具体 RAG 实现的依赖。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Component
public class RagSearchProvider implements HybridSearchService.RagSearchDelegate {

  /** 默认返回条数 */
  private static final int DEFAULT_TOP_K = 5;

  /** 默认最小相似度阈值 */
  private static final double DEFAULT_MIN_SCORE = 0.5;

  private final VectorStore vectorStore;
  private final AgentProperties properties;

  /**
   * 构造 RAG 搜索提供者
   *
   * @param vectorStore 向量存储
   * @param properties Agent 配置
   */
  public RagSearchProvider(VectorStore vectorStore, AgentProperties properties) {
    this.vectorStore = vectorStore;
    this.properties = properties;
  }

  @Override
  public List<TextChunk> search(String query, String datasetId, int topK) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    if (topK <= 0) {
      topK = DEFAULT_TOP_K;
    }
    return vectorStore.search(query, topK, DEFAULT_MIN_SCORE);
  }
}
