package com.njydsz.agent.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * AgentDefinitionDO 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AgentDefinitionDOPostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String agentCode;
    private String agentName;
    private String agentType;
    private String description;
    private String systemPrompt;
    private String modelConfig;
    private String toolNames;
    private Double temperature;
    private Integer maxTokens;
}