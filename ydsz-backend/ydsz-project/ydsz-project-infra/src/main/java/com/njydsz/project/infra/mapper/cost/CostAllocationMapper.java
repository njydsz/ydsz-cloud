package com.njydsz.project.infra.mapper.cost;

import com.njydsz.project.domain.entity.cost.CostAllocationDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * CostAllocation Mapper。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Mapper
public interface CostAllocationMapper extends BaseMapper<CostAllocationDO> {
}
