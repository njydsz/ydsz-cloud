package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程实例 Mapper
 */
@Mapper
public interface FlowInstanceMapper extends BaseMapper<FlowInstanceDO> {

    /**
     * 根据业务关联查实例
     */
    FlowInstanceDO selectByBusiness(@Param("businessType") String businessType,
                                    @Param("businessId") String businessId);

    /**
     * 状态更新
     */
    int updateStatus(@Param("id") Long id,
                     @Param("flowStatus") String flowStatus,
                     @Param("currentNodeCode") String currentNodeCode,
                     @Param("currentNodeName") String currentNodeName,
                     @Param("endAt") java.time.LocalDateTime endAt,
                     @Param("durationMs") Long durationMs);

    /**
     * 发起人维度查询
     */
    List<FlowInstanceDO> selectByInitiator(@Param("initiatorId") Long initiatorId,
                                           @Param("flowStatus") String flowStatus);
}
