package com.njydsz.agent.domain.dto.put;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * AgentDefinitionDO 修改请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AgentDefinitionDOPutDTO implements Serializable {

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
}