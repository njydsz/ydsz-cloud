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

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** Agent 类型（AgentType.code） */
    @NotBlank(message = "Agent 类型不能为空")
    private String agentType;

    /** 关联业务类型（PROJECT/OPPORTUNITY/TIMESHEET/STAFF） */
    private String bizType;
    /** 关联业务 ID */
    private Long bizId;
    /** 关联业务名称/编码（冗余） */
    private String bizRef;
    /** 调用人 ID（系统触发为空） */
    private Long callerId;
    /** 调用人姓名 */
    private String callerName;
    /** 来源（MANUAL/SCHEDULED/EVENT） */
    private String source;
    /** 附加参数 */
    private Map<String, Object> params;
    /** 是否异步执行 */
    private Boolean async;
}
