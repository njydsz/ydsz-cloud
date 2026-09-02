package com.njydsz.system.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 租户分页查询参数
 *
 * <p>对应 {@code ydsz_sys_tenant} 表的分页查询条件。继承自 {@link PageQuery}，自带 {@code pageNum} /
 * {@code pageSize} / {@code orderBy} / {@code sort} 等通用分页参数。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code tenantName} — 租户名称模糊匹配（可选）
 *   <li>{@code status} — 启用状态精确匹配（可选）
 * </ul>
 *
 * <p><b>多租户：</b>租户过滤由 MyBatis 拦截器（{@code ydsz-common-jdbc}）自动注入， 本类无需显式声明 {@code tenantId}。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.domain.query.PageQuery 父类（分页参数）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TenantPageQuery extends PageQuery {

  private static final long serialVersionUID = 1L;

  private String tenantName;

  private String tenantCode;

  private String status;
}
