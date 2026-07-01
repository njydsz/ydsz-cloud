package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowSkipDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 节点跳转 Mapper
 */
@Mapper
public interface FlowSkipMapper extends BaseMapper<FlowSkipDO> {

    /**
     * 查某定义的全部跳转
     */
    List<FlowSkipDO> selectByDefinitionId(@Param("definitionId") Long definitionId);

    /**
     * 查某节点的出发跳转
     */
    List<FlowSkipDO> selectByNodeCode(@Param("definitionId") Long definitionId,
                                      @Param("nodeCode") String nodeCode,
                                      @Param("skipType") String skipType);

    /**
     * 查指向某节点的跳转（用于退回时找前驱）
     */
    List<FlowSkipDO> selectByNextNode(@Param("definitionId") Long definitionId,
                                      @Param("nextNodeCode") String nextNodeCode);

    /**
     * 删除某定义的全部跳转
     */
    int deleteByDefinitionId(@Param("definitionId") Long definitionId);
}
