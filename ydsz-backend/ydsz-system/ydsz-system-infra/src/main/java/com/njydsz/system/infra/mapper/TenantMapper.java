package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户主表 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
