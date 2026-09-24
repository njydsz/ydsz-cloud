package com.njydsz.agent.infra.repository;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.dto.AgentTraceStepDTO;
import com.njydsz.agent.domain.entity.AgentTraceStep;
import com.njydsz.agent.domain.repository.AgentTraceStepRepository;
import com.njydsz.agent.domain.vo.AgentTraceStepVO;
import com.njydsz.agent.infra.mapper.AgentTraceStepMapper;

/**
 * Agent 执行链路步骤 Repository 实现
 *
 * <p>基于自定义 MyBatis Mapper 实现 {@link AgentTraceStepRepository} 接口（不使用 BaseMapper，
 * 因该表使用复合业务键）。
 * 写入时通过 {@link AgentPoConverter} 将 domain 实体转为 PO，
 * 读取时通过 {@link AgentPoConverter} 将 PO 转为 domain 后再经 {@link AgentConverter} 转为 VO。
 *
 * <p><b>DDD 分层：</b> domain 层 AgentTraceStep 为纯净 POJO；PO 层承载 MyBatis 映射注解。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class AgentTraceStepRepositoryImpl implements AgentTraceStepRepository {

  private final AgentTraceStepMapper agentTraceStepMapper;

  private final AgentConverter converter;

  @Override
  public boolean insert(AgentTraceStepDTO dto) {
    AgentTraceStep entity = converter.dtoToEntity(dto);
    return agentTraceStepMapper.batchInsert(
        java.util.List.of(entity)) > 0;
  }

  @Override
  public List<AgentTraceStepVO> findByTraceId(String traceId) {
    List<AgentTraceStep> domainList = agentTraceStepMapper.selectByTraceId(traceId);
    return converter.agentTraceStepListToVO(domainList);
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
