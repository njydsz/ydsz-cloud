package com.njydsz.agent.domain.repository;

import java.util.Optional;

import com.njydsz.agent.domain.dto.AgentTraceDTO;
import com.njydsz.agent.domain.vo.AgentTraceVO;
import com.njydsz.agent.infra.trace.PgTraceRecorder;

/**
 * Agent 执行链路 Repository
 *
 * <p>封装 {@code ydsz_agent_trace} 表的数据库访问，为 server 层提供链路元数据的持久化操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AgentTraceRepository {

  /**
   * 插入链路记录
   *
   * @param dto 链路 DTO
   * @return 插入成功返回 {@code true}
   */
  boolean insert(AgentTraceDTO dto);

  /**
   * 根据 ID 查询链路记录
   *
   * @param traceId 链路 ID
   * @return 链路 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<AgentTraceVO> findById(String traceId);

  /**
   * 根据 ID 更新链路记录
   *
   * @param dto 链路 DTO（含 traceId）
   * @return 更新成功返回 {@code true}
   */
  boolean updateById(AgentTraceDTO dto);

  /**
   * 创建 PgTraceRecorder 实例（需要联合 AgentTraceStepRepository）
   *
   * @param traceStepRepository 链路步骤 Repository
   * @return PgTraceRecorder 实例
   */
  PgTraceRecorder createTraceRecorder(AgentTraceStepRepository traceStepRepository);
}
