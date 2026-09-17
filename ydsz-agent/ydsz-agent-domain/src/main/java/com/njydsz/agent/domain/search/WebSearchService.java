package com.njydsz.agent.domain.search;

import java.io.Serializable;
import java.util.List;

/**
 * Web 搜索服务接口
 *
 * <p>提供实时互联网搜索结果，与 RAG 知识库检索互为补充。
 * 返回的搜索结果片段可直接作为上下文注入 LLM prompt。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface WebSearchService {

  /**
   * 执行 Web 搜索
   *
   * @param query 搜索关键词
   * @param topK 返回结果数
   * @return 搜索结果列表
   */
  List<SearchResult> search(String query, int topK);

  /**
   * 判断当前 Web 搜索服务是否可用
   *
   * @return true 表示服务可用
   */
  boolean isAvailable();

  /**
   * 搜索结果条目
   *
   * @param title 网页标题
   * @param snippet 内容摘要片段
   * @param url 网页链接
   */
  record SearchResult(String title, String snippet, String url) implements Serializable {
    private static final long serialVersionUID = 1L;
  }
}
