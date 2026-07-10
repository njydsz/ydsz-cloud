package com.njydsz.pmis.workflow.mapper.instance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.instance.FlowSkipDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 节点跳转 Mapper
 *
 * <p>对应 pmis_flow_skip 表，记录节点之间的跳转关系（正向流转/退回），供引擎查找前驱/后继。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowSkipMapper extends BaseMapper<FlowSkipDO> {

    /**
     * 查某定义的全部跳转
     */
    List<FlowSkipDO> selectByDefinitionId(@Param("definitionId") String definitionId);

    /**
     * 查某节点的出发跳转
     */
    List<FlowSkipDO> selectByNodeCode(@Param("definitionId") String definitionId,
                                      @Param("nodeCode") String nodeCode,
                                      @Param("skipType") String skipType);

    /**
     * 查指向某节点的跳转（用于退回时找前驱）
     */
    List<FlowSkipDO> selectByNextNode(@Param("definitionId") String definitionId,
                                      @Param("nextNodeCode") String nextNodeCode);

    /**
     * 删除某定义的全部跳转
     */
    int deleteByDefinitionId(@Param("definitionId") String definitionId);
}
