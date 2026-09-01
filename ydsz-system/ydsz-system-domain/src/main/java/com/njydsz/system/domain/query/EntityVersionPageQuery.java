package com.njydsz.system.domain.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实体版本分页查询条件（P2-3 分页优化）。
 *
 * <p>用于 EntityVersion 的分页查询，统一管理 Config/Dict/Variable 版本的翻页参数。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>继承 {@link BaseQuery} 获取标准分页参数（pageNum / pageSize）
 *   <li>资源类型 + 资源键为必填条件（版本按资源隔离）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see BaseQuery 分页基类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityVersionPageQuery {

  /** 资源类型（CONFIG/DICT/VARIABLE） */
  private String resourceType;

  /** 资源唯一标识（如配置键、字典类型编码、变量键） */
  private String resourceKey;

  /** 页码（1-based，默认 1） */
  @Builder.Default
  private Integer pageNum = 1;

  /** 默认每页条数 */
  private static final int DEFAULT_PAGE_SIZE = 20;

  /** 每页条数（默认 20，最大 500） */
  @Builder.Default
  private Integer pageSize = DEFAULT_PAGE_SIZE;
}
