package com.njydsz.system.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 租户套餐查询参数
 *
 * <p>对应 {@code ydsz_sys_tenant_plan} 表的查询条件，用于列表查询和统计场景。
 * 继承自 {@link PageQuery}，自带 {@code pageNum} / {@code pageSize} 等通用分页参数。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code planName} — 套餐名称模糊匹配（可选）
 *   <li>{@code status} — 启用状态精确匹配（可选）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.domain.query.PageQuery 父类（分页参数）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TenantPlanQuery extends PageQuery {

  private static final long serialVersionUID = 1L;

  private String planName;

  private String planCode;

  private String status;
}
