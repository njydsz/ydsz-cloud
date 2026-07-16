package com.njydsz.agent.api.dto;

import java.time.LocalDateTime;

public record PromptTemplateDTO(
    Long id,
    String name,
    String content,
    String version,
    String description,
    LocalDateTime createdAt
) {}
