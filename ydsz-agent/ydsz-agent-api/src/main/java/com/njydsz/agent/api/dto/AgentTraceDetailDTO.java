package com.njydsz.agent.api.dto;

/**
 * Agent 执行轨迹详情 DTO
 *
 * <p>包含单次 Agent 执行的完整轨迹信息，用于调试和执行分析。
 *
 * @param traceId 链路追踪 ID
 * @param agentType Agent 类型（CHAT/REACT/PLAN_EXECUTE/ROUTER）
 * @param plan 执行计划（JSON 格式，包含步骤列表）
 * @since 26.09.01
 * @author ydsz-team
 */
public record AgentTraceDetailDTO(String traceId, String agentType, String plan) {}
