package com.njydsz.agent.infra.repository;

import java.util.List;

import com.njydsz.agent.domain.entity.AgentTraceStepDO;

/**
 * Agent 执行链路步骤 Repository
 *
 * <p>封装 {@code ydsz_agent_trace_step} 表的数据库访问，为 server 层提供链路步骤的持久化操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AgentTraceStepRepository {

  /**
   * 插入链路步骤记录
   *
   * @param entity 链路步骤 DO
   */
  void insert(AgentTraceStepDO entity);

  /**
   * 根据链路 ID 查询步骤列表（按步骤序号升序）
   *
   * @param traceId 链路 ID
   * @return 链路步骤 DO 列表
   */
  List<AgentTraceStepDO> findByTraceId(String traceId);
}
