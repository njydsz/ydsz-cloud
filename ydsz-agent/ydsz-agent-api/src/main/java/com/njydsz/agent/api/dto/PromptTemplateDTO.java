package com.njydsz.agent.api.dto;

import java.time.LocalDateTime;

/**
 * Prompt 模板 DTO
 *
 * <p>用于 API 响应返回 Prompt 模板信息，包含模板内容、版本和描述。
 *
 * @param id 模板 ID
 * @param name 模板名称
 * @param content 模板内容（支持 #{variable} 占位符）
 * @param version 模板版本
 * @param description 模板描述
 * @param createdAt 创建时间
 * @since 1.0.0
 * @author ydsz-team
 */
public record PromptTemplateDTO(
    Long id,
    String name,
    String content,
    String version,
    String description,
    LocalDateTime createdAt) {}
