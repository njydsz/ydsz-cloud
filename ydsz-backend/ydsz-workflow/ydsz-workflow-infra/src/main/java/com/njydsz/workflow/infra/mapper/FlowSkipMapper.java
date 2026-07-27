package com.njydsz.workflow.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowSkip;

/**
 * 节点跳转 Mapper
 *
 * <p>对应 ydsz_flow_skip 表，记录节点之间的跳转关系（正向流转/退回），供引擎查找前驱/后继。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface FlowSkipMapper extends BaseMapper<FlowSkip> {

    /**
     * 查某定义的全部跳转
     */
    List<FlowSkip> selectByDefinitionId(@Param("definitionId") String definitionId);

    /**
     * 查某节点的出发跳转
     */
    List<FlowSkip> selectByNodeCode(@Param("definitionId") String definitionId,
                                      @Param("nodeCode") String nodeCode,
                                      @Param("skipType") String skipType);

    /**
     * 查指向某节点的跳转（用于退回时找前驱）
     */
    List<FlowSkip> selectByNextNode(@Param("definitionId") String definitionId,
                                      @Param("nextNodeCode") String nextNodeCode);

    /**
     * 删除某定义的全部跳转
     */
    int deleteByDefinitionId(@Param("definitionId") String definitionId);
}
