package com.njydsz.common.search.core;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 索引操作
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexOperation implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 操作类型 */
  private OperationType operation;

  /** 索引文档（UPSERT 时必填） */
  private IndexDocument document;

  /** 索引文档列表（BULK 时必填） */
  private List<IndexDocument> documents;

  /** 文档 ID（DELETE 时必填） */
  private String documentId;

  /** 实体类型（DELETE 时必填） */
  private String type;

  /** 操作类型 */
  public enum OperationType {
    /** 新增/更新索引 */
    UPSERT,
    /** 删除索引 */
    DELETE,
    /** 批量操作 */
    BULK
  }

  /**
   * 创建 UPSERT 操作。
   *
   * @param document 待索引文档
   * @return UPSERT 操作实例
   */
  public static IndexOperation upsert(IndexDocument document) {
    return IndexOperation.builder().operation(OperationType.UPSERT).document(document).build();
  }

  /**
   * 创建 DELETE 操作。
   *
   * @param type 文档类型
   * @param documentId 文档 ID
   * @return DELETE 操作实例
   */
  public static IndexOperation delete(String type, String documentId) {
    return IndexOperation.builder()
        .operation(OperationType.DELETE)
        .type(type)
        .documentId(documentId)
        .build();
  }

  /**
   * 创建批量 UPSERT 操作。
   *
   * @param documents 待索引文档列表
   * @return 批量 UPSERT 操作实例
   */
  public static IndexOperation bulkUpsert(List<IndexDocument> documents) {
    return IndexOperation.builder().operation(OperationType.BULK).documents(documents).build();
  }
}
