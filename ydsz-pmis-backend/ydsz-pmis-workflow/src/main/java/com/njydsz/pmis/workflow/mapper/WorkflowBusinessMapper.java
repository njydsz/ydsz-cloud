package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.WorkflowBusinessDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 业务流程实例关联 Mapper
 */
@Mapper
public interface WorkflowBusinessMapper extends BaseMapper<WorkflowBusinessDO> {

    /**
     * 根据流程实例 ID 查询
     */
    WorkflowBusinessDO selectByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

    /**
     * 根据业务类型与业务 ID 查询
     */
    WorkflowBusinessDO selectByBusiness(@Param("businessType") String businessType,
                                        @Param("businessId") String businessId);

    /**
     * 根据发起人查询列表
     */
    List<WorkflowBusinessDO> selectByInitiator(@Param("initiatorId") Long initiatorId,
                                               @Param("status") String status);

    /**
     * 更新流程实例状态
     */
    int updateStatusByInstanceId(@Param("processInstanceId") String processInstanceId,
                                 @Param("status") String status,
                                 @Param("currentNode") String currentNode,
                                 @Param("endTime") java.time.LocalDateTime endTime,
                                 @Param("durationMs") Long durationMs);
}
