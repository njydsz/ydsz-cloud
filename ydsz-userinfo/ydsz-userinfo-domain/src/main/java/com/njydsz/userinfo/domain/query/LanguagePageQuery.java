package com.njydsz.userinfo.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.domain.query.PageQuery;


/**
 * 语言分页查询参数，继承 {@link PageQuery} 提供分页基础字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LanguagePageQuery extends PageQuery {

  /** 语言编码，如 zh-CN */
  private String languageCode;

  /** 语言名称 */
  private String languageName;

  /** 状态过滤：ENABLE/DISABLE */
  private String status;
}
