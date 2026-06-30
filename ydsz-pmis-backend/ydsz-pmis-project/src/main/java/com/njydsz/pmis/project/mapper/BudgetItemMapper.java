package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.BudgetItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BudgetItemMapper extends BaseMapper<BudgetItemDO> {

    List<BudgetItemDO> selectByInitiationId(@Param("initiationId") Long initiationId);

    List<Map<String, Object>> sumByCategory(@Param("initiationId") Long initiationId);

    int deleteByInitiationId(@Param("initiationId") Long initiationId);
}
