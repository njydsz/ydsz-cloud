package com.njydsz.agent.domain.dto;

import java.time.LocalDateTime;

/**
 * Agent 执行轨迹列表 DTO
 *
 * <p>用于列表展示 Agent 执行历史记录，包含执行状态、耗时和步骤数等摘要信息。
 *
 * @param id 轨迹记录 ID
 * @param conversationId 会话 ID
 * @param agentType Agent 类型（CHAT/REACT/PLAN_EXECUTE/ROUTER）
 * @param status 执行状态（RUNNING/SUCCESS/FAILED/CANCELLED）
 * @param startedAt 开始时间
 * @param durationMs 执行耗时（毫秒）
 * @param stepCount 执行步骤数
 * @since 26.09.01
 * @author ydsz-team
 */
public record AgentTraceListDTO(
    String id,
    String conversationId,
    String agentType,
    String status,
    LocalDateTime startedAt,
    long durationMs,
    int stepCount) {}
