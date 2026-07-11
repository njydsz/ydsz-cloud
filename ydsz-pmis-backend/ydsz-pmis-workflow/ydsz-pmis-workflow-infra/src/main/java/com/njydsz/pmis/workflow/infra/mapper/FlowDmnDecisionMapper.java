package com.njydsz.pmis.workflow.infra.mapper.dmn;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.dmn.FlowDmnDecisionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * P0-1: DMN 决策表 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Mapper
public interface FlowDmnDecisionMapper extends BaseMapper<FlowDmnDecisionDO> {

    /**
     * 根据决策表编码查询已发布版本
     */
    FlowDmnDecisionDO selectPublishedByCode(@Param("decisionCode") String decisionCode,
                                             @Param("tenantId") String tenantId);

    /**
     * 根据流程编码 + 节点编码查询绑定的已发布决策表
     */
    FlowDmnDecisionDO selectByNode(@Param("flowCode") String flowCode,
                                    @Param("nodeCode") String nodeCode,
                                    @Param("tenantId") String tenantId);

    /**
     * 查询全部已发布决策表（分页用）
     */
    List<FlowDmnDecisionDO> selectPublishedList(@Param("tenantId") String tenantId,
                                                 @Param("decisionCode") String decisionCode);
}
