package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowHisVariableDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * P2-3 流程变量归档 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowHisVariableMapper extends BaseMapper<FlowHisVariableDO> {

    /**
     * 批量插入归档变量
     */
    int batchInsert(@Param("list") List<FlowHisVariableDO> variables);

    /**
     * 查询实例的归档变量
     */
    List<FlowHisVariableDO> selectByInstanceId(@Param("instanceId") String instanceId);
}
