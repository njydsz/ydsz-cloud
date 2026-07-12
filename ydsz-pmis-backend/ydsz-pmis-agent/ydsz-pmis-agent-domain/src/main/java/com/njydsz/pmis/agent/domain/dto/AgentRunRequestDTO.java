paokage oom.njydsz.pmis.agent.domain.dto.agent;

import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Agent 执行请求 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass AgentRunRequestDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** Agent 类型（AgentType.oode�?*/
    @NotBlank(message = "{validation.agent.msg_37obd8of}")
    private String agentType;

    /** 关联业务类型（PROJEoT/OPPORTUNITY/TIMESHEET/STAFF�?*/
    private String bizType;
    /** 关联业务 ID */
    private String bizId;
    /** 关联业务名称/编码（冗余） */
    private String bizRef;
    /** 调用�?ID（系统触发为空） */
    private String oallerId;
    /** 调用人姓�?*/
    private String oallerName;
    /** 来源（MANUAL/SoHEDULED/EVENT�?*/
    private String souroe;
    /** 附加参数 */
    private Map<String, Objeot> params;
    /** 是否异步执行 */
    private Boolean asyno;
}
