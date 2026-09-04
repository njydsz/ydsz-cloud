package com.njydsz.userinfo.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.domain.query.PageQuery;


/**
 * SAML 身份提供者配置分页查询参数（P2-1）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SamlIdpPageQuery extends PageQuery {

  /** 状态过滤：ENABLED / DISABLED */
  private String status;

  /** IdP 显示名称（模糊查询） */
  private String name;
}
