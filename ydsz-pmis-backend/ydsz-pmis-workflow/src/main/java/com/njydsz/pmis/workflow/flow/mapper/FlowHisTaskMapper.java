package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 历史任务 Mapper
 */
@Mapper
public interface FlowHisTaskMapper extends BaseMapper<FlowHisTaskDO> {

    /**
     * 查用户已办（历史）
     */
    List<FlowHisTaskDO> selectDoneByAssignee(@Param("assigneeId") String assigneeId,
                                             @Param("tenantId") Long tenantId);

    /**
     * 查某实例的所有历史
     */
    List<FlowHisTaskDO> selectByInstanceId(@Param("instanceId") Long instanceId);
}
