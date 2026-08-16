package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 租户套餐实体
 *
 * <p>对应数据库表 {@code ydsz_tenant_plan}，定义 SaaS 多租户体系下的<b>套餐/订阅计划</b>。 一个套餐对应一组「功能菜单 + 资源配额 + 计费规则」，供
 * {@link Tenant#getPlanId()} 引用。
 *
 * <p><b>典型使用：</b>
 *
 * <ul>
 *   <li>试用版（{@code TRIAL}）— 7 天有效，限制 5 用户、3 项目
 *   <li>标准版（{@code STANDARD}）— ¥99/月，50 用户、不限项目
 *   <li>企业版（{@code ENTERPRISE}）— 联系销售，定制配额 + 独立 DB
 * </ul>
 *
 * <p><b>关联关系：</b>
 *
 * <ul>
 *   <li>{@link Tenant} → {@link TenantPlan}：多对一，一个租户订阅一个套餐
 *   <li>{@link TenantPlan} → {@link TenantPlanMenu}：一对多，套餐与菜单权限多对多关联
 * </ul>
 *
 * <p><b>配额实现：</b>功能 / 配额规则存储在 {@link TenantPlanMenu} 关联表，运行时由 {@code TenantQuotaAspect} AOP
 * 拦截校验；超限抛 {@code TenantQuotaExceededException}。
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_plan_code}（{@code plan_code}）保证套餐编码全局唯一。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see Tenant 租户实体
 * @see TenantPlanMenu 套餐-菜单关联
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_tenant_plan")
public class TenantPlan extends MpBaseEntity<String> {

  /** 套餐编码（唯一标识，如 {@code TRIAL} / {@code STANDARD} / {@code ENTERPRISE}） */
  private String planCode;

  /** 套餐名称（展示用，如「试用版」「企业版」） */
  private String planName;

  /** 套餐描述（包含价格、功能清单、配额上限） */
  private String description;

  /** 排序号（升序，影响前端套餐选择器顺序） */
  private Integer sortOrder;
}
