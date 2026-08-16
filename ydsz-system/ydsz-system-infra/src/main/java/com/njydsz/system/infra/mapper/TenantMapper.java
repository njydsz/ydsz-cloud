package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.system.domain.entity.Tenant;

/**
 * 租户 Mapper
 *
 * <p>对应数据表 <code>ydsz_tenant</code>。
 *
 * <p>租户是系统多租户隔离的最高层（每条业务数据都通过 {@code tenant_id} 关联），租户状态/计划/到期时间集中管理。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_tenant_code — 租户编码唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.Tenant 租户实体
 * @see com.njydsz.system.server.service.TenantService 租户 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {}
