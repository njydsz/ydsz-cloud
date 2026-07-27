package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AgentDefinition 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AgentDefinitionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String agentCode;
    private String agentName;
    private String agentType;
    private String description;
    private String systemPrompt;
    private String modelConfig;
    private String toolNames;
    private Double temperature;
    private Integer maxTokens;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}