package com.njydsz.agent.infra.repository;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.dto.AgentTraceDTO;
import com.njydsz.agent.domain.entity.AgentTrace;
import com.njydsz.agent.domain.repository.AgentTraceRepository;
import com.njydsz.agent.domain.repository.AgentTraceStepRepository;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.domain.vo.AgentTraceVO;
import com.njydsz.agent.infra.mapper.AgentTraceMapper;
import com.njydsz.agent.infra.mapper.AgentTraceStepMapper;
import com.njydsz.agent.infra.trace.PgTraceRecorder;

/**
 * Agent 执行链路 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link AgentTraceRepository} 接口。
 * 读取时通过 {@link AgentConverter} 将 domain 实体转为 VO。
 *
 * <p><b>DDD 分层：</b> domain 层 AgentTrace 直接承载 MP 注解用于持久化。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class AgentTraceRepositoryImpl implements AgentTraceRepository {

  private final AgentTraceMapper agentTraceMapper;

  private final AgentConverter converter;

  @Override
  public boolean insert(AgentTraceDTO dto) {
    AgentTrace entity = converter.dtoToEntity(dto);
    return agentTraceMapper.insert(entity) > 0;
  }

  @Override
  public Optional<AgentTraceVO> findById(String traceId) {
    AgentTrace entity = agentTraceMapper.selectById(traceId);
    return Optional.ofNullable(entity)
        .map(converter::entityToVO);
  }

  @Override
  public boolean updateById(AgentTraceDTO dto) {
    AgentTrace entity = converter.dtoToEntityWithId(dto);
    return agentTraceMapper.updateById(entity) > 0;
  }

  @Override
  public TraceRecorder createTraceRecorder(AgentTraceStepRepository traceStepRepository) {
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
