package com.njydsz.system.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 接口权限分页查询参数
 *
 * <p>对应 {@code ydsz_sys_api_permission} 表的分页查询条件，由 Controller 接收并透传给 {@code ApiPermissionService.page()}。
 * 继承自 {@link PageQuery}，自带 {@code pageNum} / {@code pageSize} / {@code orderBy} / {@code sort} 等通用分页参数。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code apiCode} — 权限码模糊匹配（{@code LIKE %xxx%}）
 *   <li>{@code apiName} — 接口名称模糊匹配
 *   <li>{@code controllerClass} — Controller 类名模糊匹配
 *   <li>{@code status} — 启用状态精确匹配（{@code =}），可空
 * </ul>
 *
 * <p><b>多租户：</b>租户过滤由 MyBatis 拦截器自动注入。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.domain.query.PageQuery 父类（分页参数）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ApiPermissionQuery extends PageQuery {

  private static final long serialVersionUID = 1L;

  /** 权限码模糊匹配 */
  private String apiCode;

  /** 接口名称模糊匹配 */
  private String apiName;

  /** Controller 类名模糊匹配 */
  private String controllerClass;

  /** 状态精确匹配 */
  private String status;
}
