package com.njydsz.userinfo.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 认证策略分页查询参数（P3-1）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AuthPolicyPageQuery extends PageQuery {

  /** 租户 ID */
  private String tenantId;

  /** 策略名称（模糊查询） */
  private String name;
}
