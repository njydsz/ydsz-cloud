package com.njydsz.system.domain.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 租户套餐分页查询参数
 *
 * <p>对应 {@code ydsz_tenant_plan} 表的分页查询条件。继承自 {@link PageQuery}，自带 {@code pageNum} /
 * {@code pageSize} / {@code orderBy} / {@code sort} 等通用分页参数。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code planName} — 套餐名称模糊匹配（可选）
 *   <li>{@code status} — 启用状态精确匹配（可选）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.common.domain.query.PageQuery 父类（分页参数）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "租户套餐分页查询参数")
public class TenantPlanPageQuery extends PageQuery {

  private static final long serialVersionUID = 1L;

  @Schema(description = "套餐名称（模糊匹配）")
  private String planName;

  @Schema(description = "启用状态：ENABLED/DISABLED")
  private String status;
}
