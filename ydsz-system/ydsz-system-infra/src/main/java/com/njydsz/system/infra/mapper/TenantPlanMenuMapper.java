package com.njydsz.system.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.njydsz.system.domain.entity.TenantPlanMenu;

/**
 * 租户套餐-菜单关联 Mapper
 *
 * <p>对应数据表 <code>ydsz_tenant_plan_menu</code>。
 *
 * <p>租户购买套餐后自动获得关联菜单的访问权限，是 RBAC 的「套餐级」权限分配。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_plan_menu — (套餐+菜单) 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.TenantPlanMenu 套餐-菜单关联实体
 * @see com.njydsz.system.server.service.TenantPlanService 套餐 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface TenantPlanMenuMapper extends BaseMapper<TenantPlanMenu> {}
