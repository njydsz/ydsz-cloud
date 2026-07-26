package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.TenantPlanMenuDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户套餐菜单关联 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface TenantPlanMenuMapper extends BaseMapper<TenantPlanMenuDO> {
}
