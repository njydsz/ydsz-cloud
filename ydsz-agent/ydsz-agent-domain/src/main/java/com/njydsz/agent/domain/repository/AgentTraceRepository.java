package com.njydsz.agent.domain.repository;

import java.util.Optional;

import com.njydsz.agent.domain.dto.AgentTraceDTO;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.domain.vo.AgentTraceVO;

/**
 * Agent 执行链路 Repository
 *
 * <p>封装 {@code ydsz_agt_trace} 表的数据库访问，为 server 层提供链路元数据的持久化操作。
 *
 * <p><b>DDD 合规（P1 修复）</b>：本接口属于 domain 层，不得依赖 infra 层实现类。 原实现返回
 * {@code PgTraceRecorder}（infra 类），违反「domain ← infra 单向依赖」原则； 现改为返回领域接口
 * {@link TraceRecorder}，由 infra 层实现类负责实例化具体记录器。
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
   * 创建链路记录器实例（需要联合 AgentTraceStepRepository）。
   *
   * <p>DDD 合规：返回类型为领域接口 {@link TraceRecorder}，具体 infra 实现（如 PgTraceRecorder）由实现方决定。
   *
   * @param traceStepRepository 链路步骤 Repository
   * @return 链路记录器实例
   */
  TraceRecorder createTraceRecorder(AgentTraceStepRepository traceStepRepository);
}
