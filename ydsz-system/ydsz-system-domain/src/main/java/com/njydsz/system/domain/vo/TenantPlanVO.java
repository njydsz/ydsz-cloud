package com.njydsz.system.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 租户套餐 VO（视图对象）
 *
 * <p>对应 {@code ydsz_tenant_plan} 表的展示视图，是「套餐管理」列表 / 详情接口的响应载体。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code planCode} — 套餐编码，全局唯一标识
 *   <li>{@code sortOrder} — 排序号（升序，影响前端套餐选择器顺序）
 * </ul>
 *
 * <p><b>注意：</b>本类为视图对象，不包含输入校验逻辑。输入校验由 {@link
 * com.njydsz.system.domain.dto.TenantPlanDTO} 负责。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.infra.entity.TenantPlan 套餐实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class TenantPlanVO {

  private String id;

  private String planCode;

  private String planName;

  private String description;

  private Integer sortOrder;

  private String quotaJson;

  private String featureJson;
}
