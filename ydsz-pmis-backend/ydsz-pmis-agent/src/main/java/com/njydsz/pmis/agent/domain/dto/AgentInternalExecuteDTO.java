package com.njydsz.pmis.agent.domain.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Agent 内部执行请求体 DTO
 *
 * <p>用于 {@code /agent/internal/execute} 端点（供其他模块 Feign 调用，不走落库）。
 * 直接接收强类型参数，内部构造 {@link com.njydsz.pmis.agent.server.engine.AgentContext}，
 * 方便跨模块调用。
 *
 * <p>注意：{@code params} 是动态业务参数（键名由各 Agent 类型自定义），保留 {@code Map<String, Object>}。
 * Feign 客户端 {@code AgentClient} 仍发送 {@code Map<String, Object>} JSON，Spring 自动反序列化为 DTO，
 * 不影响 Feign 契约。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "Agent 内部执行请求体")
public class AgentInternalExecuteDTO {

    /**
     * Agent 类型（AgentType.code，如 APPROVER_RECOMMEND / COMMENT_DRAFT）
     */
    @Schema(description = "Agent 类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "APPROVER_RECOMMEND")
    @NotBlank(message = "{validation.agent.msg_37cbd8cf}")
    private String agentType;

    /**
     * 关联业务类型（PROJECT/OPPORTUNITY/TIMESHEET/STAFF/FLOW_TASK/FLOW_INSTANCE），默认 INTERNAL
     */
    @Schema(description = "关联业务类型", defaultValue = "INTERNAL", example = "FLOW_TASK")
    private String bizType;

    /**
     * 关联业务 ID
     */
    @Schema(description = "关联业务 ID")
    private String bizId;

    /**
     * 关联业务名称/编码（冗余，可选）
     */
    @Schema(description = "关联业务名称/编码")
    private String bizRef;

    /**
     * 附加参数（动态键值对，键名由各 Agent 类型自定义）
     */
    @Schema(description = "附加参数（动态键值对）")
    private Map<String, Object> params;
}
