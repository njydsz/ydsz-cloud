package com.njydsz.agent.domain.text2sql;

import java.util.List;

/**
 * Schema 召回服务接口（领域层）。
 *
 * <p>根据用户问题智能分析与可用表集合的匹配度，返回最相关的 Top-N 表 Schema 子集，
 * 避免将全量 Schema 注入 Prompt，降低 Token 消耗并提升 SQL 生成质量。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface SchemaRecallService {

  /**
   * 根据用户问题召回相关表 Schema。
   *
   * @param query 用户自然语言查询
   * @param availableTables 当前租户可见的全部可用表 Schema
   * @param maxRecall 最大召回表数量
   * @return 按相关性排序的表 Schema 列表，不会为 null（可能为空列表表示无匹配）
   */
  List<TableSchema> recallRelevantSchemas(
      String query, List<TableSchema> availableTables, int maxRecall);
}
