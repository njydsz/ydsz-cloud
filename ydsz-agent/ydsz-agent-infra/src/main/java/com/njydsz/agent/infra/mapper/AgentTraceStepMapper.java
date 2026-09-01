package com.njydsz.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.agent.infra.entity.AgentTraceStep;

/**
 * Agent 执行链路步骤 Mapper
 *
 * <p>映射 {@code ydsz_agt_trace_step} 表，存储 Agent 执行过程中每一步的明细。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface AgentTraceStepMapper extends BaseMapper<AgentTraceStep> {}
