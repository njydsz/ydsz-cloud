package com.njydsz.userinfo.domain.query;

import lombok.Data;

/**
 * API Key 分页查询。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Data
public class ApiKeyPageQuery {

  /** 默认每页大小 */
  private static final int DEFAULT_PAGE_SIZE = 20;

  /** 页码（从 1 开始） */
  private Integer pageNum = 1;

  /** 每页大小 */
  private Integer pageSize = DEFAULT_PAGE_SIZE;

  /** Key 名称模糊搜索 */
  private String keyName;

  /** 按用户 ID 筛选 */
  private String userId;

  /** 按启用状态筛选 */
  private Boolean isEnabled;
}
