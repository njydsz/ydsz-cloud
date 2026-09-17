package com.njydsz.agent.domain.tool;

import java.util.List;

import com.njydsz.agent.domain.model.ToolDefinition;

/**
 * 语义 Tool 搜索服务接口
 *
 * <p>通过 embedding 向量相似度匹配，根据自然语言查询返回最相关的工具列表。
 * 相比全量列出所有 Tool，语义搜索可精准匹配用户意图。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface SemanticToolSearchService {

  /**
   * 语义搜索工具
   *
   * @param query 自然语言查询（非空）
   * @param topK 返回数量上限（不超过 20）
   * @return 按相关度降序排列的工具列表，无结果时返回空列表
   */
  List<ToolDefinition> search(String query, int topK);
}
