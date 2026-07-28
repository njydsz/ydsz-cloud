package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.TenantPlanMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户套餐菜单关联 Mapper 接口
 *
 * <p>提供对 {@code ydsz_tenant_plan_menu} 表的 CRUD 操作，
 * 继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD 能力。
 *
 * <p><b>租户隔离例外：</b>租户套餐菜单关联表本身<b>不参与</b>租户过滤（全局共享的套餐权限定义），
 * 由 MyBatis 拦截器通过白名单机制跳过。
 *
 * <p><b>索引利用：</b>{@code (plan_id, menu_id)} 命中 {@code uk_plan_menu} 唯一索引。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.TenantPlanMenu 套餐-菜单关联实体
 * @see com.njydsz.system.domain.entity.TenantPlan 租户套餐
 */
@Mapper
public interface TenantPlanMenuMapper extends BaseMapper<TenantPlanMenu> {
}
