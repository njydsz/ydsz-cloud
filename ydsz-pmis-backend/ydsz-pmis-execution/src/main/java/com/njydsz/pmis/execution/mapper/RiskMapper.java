package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.RiskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RiskMapper extends BaseMapper<RiskDO> {

    RiskDO selectByCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    List<RiskDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<Map<String, Object>> aggregateByLevel(@Param("initiationId") Long initiationId);

    /** 查询所有未结风险 */
    List<RiskDO> selectAll();
}
