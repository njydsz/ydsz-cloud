package com.njydsz.workflow.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowAutoTrigger;

/**
 * 流程自动触发规则 Mapper
 *
 * <p>对应 ydsz_flow_auto_trigger 表，提供按源流程编码查询启用规则。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface FlowAutoTriggerMapper extends BaseMapper<FlowAutoTrigger> {

    /**
     * 按源流程编码查询所有启用的触发规则
     *
     * @param sourceFlowCode 源流程编码
     * @return 启用的触发规则列表
     */
    List<FlowAutoTrigger> selectEnabledBySourceFlowCode(@Param("sourceFlowCode") String sourceFlowCode);
}