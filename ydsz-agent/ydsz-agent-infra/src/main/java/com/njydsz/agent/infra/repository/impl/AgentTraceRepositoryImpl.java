package com.njydsz.agent.infra.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.entity.AgentTraceDO;
import com.njydsz.agent.infra.mapper.AgentTraceMapper;
import com.njydsz.agent.infra.mapper.AgentTraceStepMapper;
import com.njydsz.agent.infra.repository.AgentTraceRepository;
import com.njydsz.agent.infra.repository.AgentTraceStepRepository;
import com.njydsz.agent.infra.trace.PgTraceRecorder;

/**
 * Agent 执行链路 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link AgentTraceRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class AgentTraceRepositoryImpl implements AgentTraceRepository {

  private final AgentTraceMapper agentTraceMapper;

  @Override
  public void insert(AgentTraceDO entity) {
    agentTraceMapper.insert(entity);
  }

  @Override
  public AgentTraceDO findById(String traceId) {
    return agentTraceMapper.selectById(traceId);
  }

  @Override
  public void updateById(AgentTraceDO entity) {
    agentTraceMapper.updateById(entity);
  }

  @Override
  public PgTraceRecorder createTraceRecorder(AgentTraceStepRepository traceStepRepository) {
    return new PgTraceRecorder(agentTraceMapper, extractStepMapper(traceStepRepository));
  }

  /**
   * 从 AgentTraceStepRepository 实现中提取 Mapper
   *
   * @param traceStepRepository 链路步骤 Repository
   * @return AgentTraceStepMapper 实例
   */
  private AgentTraceStepMapper extractStepMapper(AgentTraceStepRepository traceStepRepository) {
    if (traceStepRepository instanceof AgentTraceStepRepositoryImpl impl) {
      return impl.getTraceStepMapper();
    }
    throw new IllegalArgumentException(
        "不支持的 AgentTraceStepRepository 实现类型: " + traceStepRepository.getClass().getName());
  }
}
