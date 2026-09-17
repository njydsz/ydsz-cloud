package com.njydsz.agent.domain.trace;

import java.time.LocalDateTime;

/**
 * 链路元数据。
 *
 * <p>记录执行链路的基本信息，用于调试面板展示和链路列表查询。
 *
 * <p>业务关联维度（botId / turnId / conversationId / accountId）支持在可观测性后台中
 * 按 Agent 定义、对话轮次、会话、账号等维度聚合和过滤链路数据。
 *
 * @param traceId 链路 ID
 * @param conversationId 对话 ID
 * @param agentId Agent ID
 * @param botId 关联的 Agent 定义 ID
 * @param turnId 对话轮次 ID
 * @param accountId 账号/用户 ID
 * @param startedAt 开始时间
 * @param status 执行状态
 * @param totalDurationMs 总耗时（毫秒）
 * @param stepCount 步骤数（链路列表展示用；由实现类在查询时填充，避免调用方 N+1 查询）
 * @author ydsz-team
 * @since 26.09.17
 */
public record TraceMeta(
    String traceId,
    String conversationId,
    String agentId,
    String botId,
    String turnId,
    String accountId,
    LocalDateTime startedAt,
    String status,
    long totalDurationMs,
    int stepCount) {

  /**
   * 兼容构造器（不指定步骤数，按 0 处理）。
   *
   * @param traceId 链路 ID
   * @param conversationId 对话 ID
   * @param agentId Agent ID
   * @param botId 关联的 Agent 定义 ID
   * @param turnId 对话轮次 ID
   * @param accountId 账号/用户 ID
   * @param startedAt 开始时间
   * @param status 执行状态
   * @param totalDurationMs 总耗时（毫秒）
   */
  public TraceMeta(
      String traceId,
      String conversationId,
      String agentId,
      String botId,
      String turnId,
      String accountId,
      LocalDateTime startedAt,
      String status,
      long totalDurationMs) {
    this(traceId, conversationId, agentId, botId, turnId, accountId, startedAt, status, totalDurationMs, 0);
  }

  /**
   * 兼容构造器（不含业务关联维度，逐步迁移过渡时使用）。
   *
   * @param traceId 链路 ID
   * @param conversationId 对话 ID
   * @param agentId Agent ID
   * @param startedAt 开始时间
   * @param status 执行状态
   * @param totalDurationMs 总耗时（毫秒）
   */
  public TraceMeta(
      String traceId,
      String conversationId,
      String agentId,
      LocalDateTime startedAt,
      String status,
      long totalDurationMs,
      int stepCount) {
    this(traceId, conversationId, agentId, null, null, null, startedAt, status, totalDurationMs, stepCount);
  }

  /**
   * 兼容构造器（不指定步骤数且不含业务关联维度）。
   *
   * @param traceId 链路 ID
   * @param conversationId 对话 ID
   * @param agentId Agent ID
   * @param startedAt 开始时间
   * @param status 执行状态
   * @param totalDurationMs 总耗时（毫秒）
   */
  public TraceMeta(
      String traceId,
      String conversationId,
      String agentId,
      LocalDateTime startedAt,
      String status,
      long totalDurationMs) {
    this(traceId, conversationId, agentId, null, null, null, startedAt, status, totalDurationMs, 0);
  }
}
