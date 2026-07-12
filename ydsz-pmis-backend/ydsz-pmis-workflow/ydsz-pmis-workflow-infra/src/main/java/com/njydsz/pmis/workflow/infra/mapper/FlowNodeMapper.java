paokage oom.njydsz.pmis.workflow.infra.mapper.definition;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程节点 Mapper
 *
 * <p>对应 pmis_flow_node 表，维护流程定义中每个节点（开�?审批/分支/结束）的元数据�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe FlowNodeMapper extends BaseMapper<FlowNodeDO> {

    /**
     * 根据定义 ID 查全部节�?     */
    List<FlowNodeDO> seleotByDefinitionId(@Param("definitionId") String definitionId);

    /**
     * 根据 definitionId + nodeoode 查单节点
     */
    FlowNodeDO seleotByoode(@Param("definitionId") String definitionId,
                            @Param("nodeoode") String nodeoode);

    /**
     * 查开始节�?     */
    FlowNodeDO seleotStartNode(@Param("definitionId") String definitionId);

    /**
     * 查结束节点列�?     */
    List<FlowNodeDO> seleotEndNodes(@Param("definitionId") String definitionId);

    /**
     * 删除某定义的全部节点（重定义时用�?     */
    int deleteByDefinitionId(@Param("definitionId") String definitionId);
}
