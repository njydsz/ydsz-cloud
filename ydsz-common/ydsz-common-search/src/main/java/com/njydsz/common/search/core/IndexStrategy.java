package com.njydsz.common.search.core;

import java.util.List;

/**
 * 索引策略 SPI
 *
 * <p>需要显式索引维护的引擎（PG/ES/Solr/OpenSearch）实现此接口。 RediSearch 等直接索引数据源的引擎可不实现此接口。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see SearchStrategy
 */
public interface IndexStrategy {

  /**
   * 索引单文档（新增/更新）
   *
   * @param document 索引文档
   */
  void index(IndexDocument document);

  /**
   * 批量索引
   *
   * @param documents 索引文档列表
   */
  void bulkIndex(List<IndexDocument> documents);

  /**
   * 删除索引
   *
   * @param type 实体类型
   * @param documentId 文档 ID
   */
  void deleteIndex(String type, String documentId);

  /**
   * 删除指定类型的全部索引
   *
   * @param type 实体类型，null 表示全部
   */
  void deleteAllIndices(String type);

  /**
   * 获取指定类型的索引文档数
   *
   * @param type 实体类型，null 表示全部
   * @return 文档数，不支持时返回 -1
   */
  default long count(String type) {
    return -1;
  }

  /**
   * 获取指定类型的全部文档 ID
   *
   * @param type 实体类型
   * @return 文档 ID 列表
   */
  default List<String> getAllDocumentIds(String type) {
    return List.of();
  }
}
