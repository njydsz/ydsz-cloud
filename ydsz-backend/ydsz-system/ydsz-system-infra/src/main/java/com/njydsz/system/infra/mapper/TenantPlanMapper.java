package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.TenantPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户套餐 Mapper 接口
 *
 * <p>提供对 {@code ydsz_tenant_plan} 表的 CRUD 操作，
 * 继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD 能力。
 *
 * <p><b>租户隔离例外：</b>租户套餐表本身<b>不参与</b>租户过滤（全局共享的套餐定义），
 * 由 MyBatis 拦截器通过白名单机制跳过。
 *
 * <p><b>索引利用：</b>{@code plan_code} 命中 {@code uk_plan_code} 唯一索引。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.TenantPlan 租户套餐实体
 * @see com.njydsz.system.domain.entity.Tenant 租户实体（通过 {@code planId} 关联）
 */
@Mapper
public interface TenantPlanMapper extends BaseMapper<TenantPlan> {
}
