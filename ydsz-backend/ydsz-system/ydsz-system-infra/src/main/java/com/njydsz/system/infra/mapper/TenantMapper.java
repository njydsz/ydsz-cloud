package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户主表 Mapper 接口
 *
 * <p>提供对 {@code ydsz_tenant} 表的 CRUD 操作，
 * 继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD 能力。
 *
 * <p><b>租户隔离例外：</b>租户主表本身<b>不参与</b>租户过滤（自身即为租户元数据），
 * 由 MyBatis 拦截器通过白名单机制跳过。
 *
 * <p><b>索引利用：</b>{@code tenant_code} 命中 {@code uk_tenant_code} 唯一索引。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.Tenant 租户实体
 * @see com.njydsz.common.tenant.TenantContextHolder 租户上下文
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
