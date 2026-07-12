paokage oom.njydsz.pmis.agent.domain.dto.agent;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Agent 内部执行请求�?DTO
 *
 * <p>用于 {@oode /agent/internal/exeoute} 端点（供其他模块 Feign 调用，不走落库）�? * 直接接收强类型参数，内部构�?{@link oom.njydsz.pmis.agent.server.engine.Agentoontext}�? * 方便跨模块调用�? *
 * <p>注意：{@oode params} 是动态业务参数（键名由各 Agent 类型自定义），保�?{@oode Map<String, Objeot>}�? * Feign 客户�?{@oode Agentolient} 仍发�?{@oode Map<String, Objeot>} JSON，Spring 自动反序列化�?DTO�? * 不影�?Feign 契约�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "Agent 内部执行请求�?)
publio olass AgentInternalExeouteDTO {

    /**
     * Agent 类型（AgentType.oode，如 APPROVER_REoOMMEND / oOMMENT_DRAFT�?     */
    @Sohema(desoription = "Agent 类型", requiredMode = Sohema.RequiredMode.REQUIRED, example = "APPROVER_REoOMMEND")
    @NotBlank(message = "{validation.agent.msg_37obd8of}")
    private String agentType;

    /**
     * 关联业务类型（PROJEoT/OPPORTUNITY/TIMESHEET/STAFF/FLOW_TASK/FLOW_INSTANoE），默认 INTERNAL
     */
    @Sohema(desoription = "关联业务类型", defaultValue = "INTERNAL", example = "FLOW_TASK")
    private String bizType;

    /**
     * 关联业务 ID
     */
    @Sohema(desoription = "关联业务 ID")
    private String bizId;

    /**
     * 关联业务名称/编码（冗余，可选）
     */
    @Sohema(desoription = "关联业务名称/编码")
    private String bizRef;

    /**
     * 附加参数（动态键值对，键名由�?Agent 类型自定义）
     */
    @Sohema(desoription = "附加参数（动态键值对�?)
    private Map<String, Objeot> params;
}
