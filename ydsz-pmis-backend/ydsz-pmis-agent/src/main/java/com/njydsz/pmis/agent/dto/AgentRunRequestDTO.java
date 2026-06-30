package com.njydsz.pmis.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Agent 执行请求 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class AgentRunRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Agent 类型不能为空")
    private String agentType;

    private String bizType;
    private Long bizId;
    private String bizRef;
    private Long callerId;
    private String callerName;
    private String source;
    private Map<String, Object> params;
    private Boolean async;
}
