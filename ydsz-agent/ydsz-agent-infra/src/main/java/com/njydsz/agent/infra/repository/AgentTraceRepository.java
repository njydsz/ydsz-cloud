package com.njydsz.agent.infra.repository;

import com.njydsz.agent.domain.entity.AgentTraceDO;
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
   * @param entity 链路 DO
   */
  void insert(AgentTraceDO entity);

  /**
   * 根据 ID 查询链路记录
   *
   * @param traceId 链路 ID
   * @return 链路 DO，不存在时返回 null
   */
  AgentTraceDO findById(String traceId);

  /**
   * 根据 ID 更新链路记录
   *
   * @param entity 链路 DO
   */
  void updateById(AgentTraceDO entity);

  /**
   * 创建 PgTraceRecorder 实例（需要联合 AgentTraceStepRepository）
   *
   * @param traceStepRepository 链路步骤 Repository
   * @return PgTraceRecorder 实例
   */
  PgTraceRecorder createTraceRecorder(AgentTraceStepRepository traceStepRepository);
}
