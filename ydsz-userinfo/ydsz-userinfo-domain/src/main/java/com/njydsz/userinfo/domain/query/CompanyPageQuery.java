package com.njydsz.userinfo.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 公司分页查询参数。
 *
 * <p>用于 {@code GET /api/v1/company/page} 接口，支持按公司编码、名称模糊查询和状态过滤。
 * 继承 {@link PageQuery} 获取分页参数（{@code pageNum} / {@code pageSize}）。
 *
 * <p>所有查询条件均为可选，未传则不作为筛选条件。树形结构请使用 {@code GET /api/v1/company/tree}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CompanyPageQuery extends PageQuery {

  /** 公司编码，模糊查询 */
  private String companyCode;

  /** 公司名称，模糊查询 */
  private String companyName;

  /** 状态过滤：ENABLE/DISABLE */
  private String status;
}
