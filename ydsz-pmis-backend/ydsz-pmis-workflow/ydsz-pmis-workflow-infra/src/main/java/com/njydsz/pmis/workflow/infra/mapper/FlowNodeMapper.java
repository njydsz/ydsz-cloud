package com.njydsz.pmis.workflow.infra.mapper.definition;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程节点 Mapper
 *
 * <p>对应 pmis_flow_node 表，维护流程定义中每个节点（开始/审批/分支/结束）的元数据。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowNodeMapper extends BaseMapper<FlowNodeDO> {

    /**
     * 根据定义 ID 查全部节点
     */
    List<FlowNodeDO> selectByDefinitionId(@Param("definitionId") String definitionId);

    /**
     * 根据 definitionId + nodeCode 查单节点
     */
    FlowNodeDO selectByCode(@Param("definitionId") String definitionId,
                            @Param("nodeCode") String nodeCode);

    /**
     * 查开始节点
     */
    FlowNodeDO selectStartNode(@Param("definitionId") String definitionId);

    /**
     * 查结束节点列表
     */
    List<FlowNodeDO> selectEndNodes(@Param("definitionId") String definitionId);

    /**
     * 删除某定义的全部节点（重定义时用）
     */
    int deleteByDefinitionId(@Param("definitionId") String definitionId);
}
