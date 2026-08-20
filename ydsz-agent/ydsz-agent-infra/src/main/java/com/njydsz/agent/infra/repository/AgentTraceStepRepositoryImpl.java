package com.njydsz.agent.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.dto.AgentTraceStepDTO;
import com.njydsz.agent.domain.repository.AgentTraceStepRepository;
import com.njydsz.agent.domain.vo.AgentTraceStepVO;
import com.njydsz.agent.infra.converter.AgentConverter;
import com.njydsz.agent.infra.entity.AgentTraceStepDO;
import com.njydsz.agent.infra.mapper.AgentTraceStepMapper;

/**
 * Agent 执行链路步骤 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link AgentTraceStepRepository} 接口。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>通过 {@link AgentConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link AgentConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class AgentTraceStepRepositoryImpl implements AgentTraceStepRepository {

  private final AgentTraceStepMapper agentTraceStepMapper;

  private final AgentConverter converter;

  @Override
  public boolean insert(AgentTraceStepDTO dto) {
    AgentTraceStepDO entity = converter.dtoToEntity(dto);
    return agentTraceStepMapper.insert(entity) > 0;
  }

  @Override
  public List<AgentTraceStepVO> findByTraceId(String traceId) {
    return converter.agentTraceStepListToVO(
        agentTraceStepMapper.selectList(
            new LambdaQueryWrapper<AgentTraceStepDO>()
                .eq(AgentTraceStepDO::getTraceId, traceId)
                .orderByAsc(AgentTraceStepDO::getStepIndex)));
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
