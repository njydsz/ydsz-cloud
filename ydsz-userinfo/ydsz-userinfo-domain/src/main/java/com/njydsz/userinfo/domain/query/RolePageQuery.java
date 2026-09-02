package com.njydsz.userinfo.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 角色分页查询参数，继承 {@link PageQuery} 提供分页基础字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RolePageQuery extends PageQuery {

  /** 角色编码，模糊查询 */
  private String roleCode;

  /** 角色名称，模糊查询 */
  private String roleName;

  /** 状态过滤：ENABLE/DISABLE */
  private String status;

  /** 租户 ID */
  private String tenantId;
}
