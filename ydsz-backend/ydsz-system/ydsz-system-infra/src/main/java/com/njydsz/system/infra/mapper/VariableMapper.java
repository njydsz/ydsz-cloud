package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.VariableDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VariableMapper extends BaseMapper<VariableDO> {
}
