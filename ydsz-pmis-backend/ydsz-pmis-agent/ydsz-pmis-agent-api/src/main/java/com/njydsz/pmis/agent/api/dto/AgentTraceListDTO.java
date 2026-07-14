package com.njydsz.pmis.agent.api.dto;

import java.time.LocalDateTime;

public record AgentTraceListDTO(
    String id,
    String conversationId,
    String agentType,
    String status,
    LocalDateTime startedAt,
    long durationMs,
    int stepCount
) {}
