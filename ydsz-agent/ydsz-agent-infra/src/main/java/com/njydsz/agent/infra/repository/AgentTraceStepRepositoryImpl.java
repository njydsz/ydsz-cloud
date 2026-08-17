package com.njydsz.agent.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.repository.AgentTraceStepRepository;
import com.njydsz.agent.infra.entity.AgentTraceStepDO;
import com.njydsz.agent.infra.mapper.AgentTraceStepMapper;

/**
 * Agent 执行链路步骤 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link AgentTraceStepRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class AgentTraceStepRepositoryImpl implements AgentTraceStepRepository {

  private final AgentTraceStepMapper agentTraceStepMapper;

  @Override
  public void insert(AgentTraceStepDO entity) {
    agentTraceStepMapper.insert(entity);
  }

  @Override
  public List<AgentTraceStepDO> findByTraceId(String traceId) {
    return agentTraceStepMapper.selectList(
        new LambdaQueryWrapper<AgentTraceStepDO>()
            .eq(AgentTraceStepDO::getTraceId, traceId)
            .orderByAsc(AgentTraceStepDO::getStepIndex));
  }

  /**
   * 获取 AgentTraceStepMapper 实例（供 PgTraceRecorder 创建使用）
   *
   * @return AgentTraceStepMapper 实例
   */
  protected AgentTraceStepMapper getTraceStepMapper() {
    return agentTraceStepMapper;
  }
}
