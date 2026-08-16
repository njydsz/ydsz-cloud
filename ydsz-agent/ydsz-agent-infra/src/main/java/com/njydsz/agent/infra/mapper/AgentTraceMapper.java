package com.njydsz.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.agent.domain.entity.AgentTraceDO;

/**
 * Agent 执行链路 Mapper
 *
 * <p>映射 {@code ydsz_agent_trace} 表，存储 Agent 执行的元数据。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface AgentTraceMapper extends BaseMapper<AgentTraceDO> {}
