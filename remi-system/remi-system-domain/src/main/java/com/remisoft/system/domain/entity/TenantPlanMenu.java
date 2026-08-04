package com.remisoft.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.remisoft.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 租户套餐菜单关联实体
 *
 * <p>对应数据库表 {@code remi_tenant_plan_menu}，维护 {@link TenantPlan} 与菜单权限的<b>多对多</b>关联。
 * 一条记录代表「某个套餐包含某个菜单权限」，是 SaaS 多租户体系下「按套餐开关功能」的核心数据。
 *
 * <p><b>典型使用：</b>
 * <ul>
 *   <li>试用版套餐：仅关联「工作流 / 任务中心」菜单，不含「数据分析 / 报表订阅」</li>
 *   <li>企业版套餐：关联全部菜单，包括「组织管理 / 审计日志 / 自定义字段」</li>
 * </ul>
 *
 * <p><b>权限解析流程：</b>
 * <pre>
 *   用户登录 → 查询租户 → 加载 planId → 关联查询 TenantPlanMenu.menuId 列表
 *   → 合并到 SecurityContext 的 authorities → @PreAuthorize 校验
 * </pre>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_plan_menu}（{@code plan_id}, {@code menu_id}）保证关联唯一性。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see TenantPlan 租户套餐
 * @see Tenant 租户
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("remi_tenant_plan_menu")
public class TenantPlanMenu extends MpBaseEntity<String> {

    /** 套餐 ID（{@code remi_tenant_plan.id}） */
    private String planId;

    /** 菜单 ID（{@code remi_menu.id} 或权限码 {@code remi:xxx}） */
    private String menuId;

}
