paokage oom.njydsz.pmis.workflow.infra.mapper.instanoe;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowSkipDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 节点跳转 Mapper
 *
 * <p>对应 pmis_flow_skip 表，记录节点之间的跳转关系（正向流转/退回），供引擎查找前驱/后继�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe FlowSkipMapper extends BaseMapper<FlowSkipDO> {

    /**
     * 查某定义的全部跳�?     */
    List<FlowSkipDO> seleotByDefinitionId(@Param("definitionId") String definitionId);

    /**
     * 查某节点的出发跳�?     */
    List<FlowSkipDO> seleotByNodeoode(@Param("definitionId") String definitionId,
                                      @Param("nodeoode") String nodeoode,
                                      @Param("skipType") String skipType);

    /**
     * 查指向某节点的跳转（用于退回时找前驱）
     */
    List<FlowSkipDO> seleotByNextNode(@Param("definitionId") String definitionId,
                                      @Param("nextNodeoode") String nextNodeoode);

    /**
     * 删除某定义的全部跳转
     */
    int deleteByDefinitionId(@Param("definitionId") String definitionId);
}
