package com.njydsz.system.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.njydsz.system.domain.entity.TenantPlan;

/**
 * 租户套餐 Mapper
 *
 * <p>对应数据表 <code>ydsz_tenant_plan</code>。
 *
 * <p>套餐定义租户的功能/容量/价格（基础版/企业版/旗舰版），由 {@code TenantPlanMenu} 关联可访问菜单。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_plan_code — 套餐编码唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.TenantPlan 套餐实体
 * @see com.njydsz.system.server.service.TenantPlanService 套餐 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface TenantPlanMapper extends BaseMapper<TenantPlan> {}
