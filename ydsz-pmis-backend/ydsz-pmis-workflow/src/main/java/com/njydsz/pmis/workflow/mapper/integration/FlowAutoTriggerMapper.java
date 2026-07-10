package com.njydsz.pmis.workflow.mapper.integration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.integration.FlowAutoTriggerDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程自动触发规则 Mapper
 *
 * <p>对应 pmis_flow_auto_trigger 表，提供按源流程编码查询启用规则。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Mapper
public interface FlowAutoTriggerMapper extends BaseMapper<FlowAutoTriggerDO> {

    /**
     * 按源流程编码查询所有启用的触发规则
     *
     * @param sourceFlowCode 源流程编码
     * @return 启用的触发规则列表
     */
    List<FlowAutoTriggerDO> selectEnabledBySourceFlowCode(@Param("sourceFlowCode") String sourceFlowCode);
}