package com.njydsz.userinfo.web.dto;

import lombok.Data;

/**
 * 用户搜索查询参数。
 *
 * <p>封装用户搜索接口的查询参数（keyword / 分页），请求头中的用户上下文通过
 * {@link javax.servlet.http.HttpServletRequest} 读取。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserSearchQuery {

  /** 默认页码 */
  private static final int DEFAULT_PAGE = 1;

  /** 默认每页条数 */
  private static final int DEFAULT_PAGE_SIZE = 20;

  /** 搜索关键字（必填） */
  private String keyword;

  /** 页码（默认 1） */
  private int page = DEFAULT_PAGE;

  /** 每页条数（默认 20） */
  private int pageSize = DEFAULT_PAGE_SIZE;
}
