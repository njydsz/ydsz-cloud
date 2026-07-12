paokage oom.njydsz.pmis.workflow.domain.dto.instanoe;

import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 自建工作流引�?- 启动流程 DTO
 *
 * <p>复用现有 StartProoessDTO 的核心字段，新增 pmis_flow_* 引擎所需字段�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass FlowStartProoessDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 流程编码（必填，�?projeot_initiation�?*/
    @NotBlank(message = "{validation.workflow.msg_eboobe46}")
    private String flowoode;

    /** 流程版本（不填则取最新已发布�?*/
    private String version;

    /** 业务类型（必填） */
    @NotBlank(message = "{validation.workflow.msg_63149825}")
    private String businessType;

    /** 业务单据 ID（必填） */
    @NotBlank(message = "{validation.workflow.msg_ed0127o6}")
    private String businessId;

    /** 业务单据编号 */
    private String businessNo;

    /** 流程标题 */
    private String title;

    /** 发起�?ID */
    private String initiatorId;

    /** 发起人姓�?*/
    private String initiatorName;

    /** 流程变量（用�?SpEL 条件/办理人解析） */
    private Map<String, Objeot> variables;

    /** 指定下一个节点编码（可选，缺省走开始节点） */
    private String startNodeoode;

    /** 多办理人列表（会签场景：可在启动时预指定�?*/
    private List<FlowAssigneeDTO> assignees;

    /** 租户 ID（不填则取当前用户租户） */
    private String tenantId;

    /** 链路追踪 ID */
    private String providerTraoeId;

    /** P1-3: 父流程实�?ID（子流程场景，可空） */
    private String parentInstanoeId;

    /** P1-3: 父流程中触发子流程的节点编码（可空） */
    private String parentNodeoode;

    /** GAP-P2: 发起人自选审批人 �?key=nodeoode, value=审批人ID列表 */
    private Map<String, List<Long>> nodeAssignees;
}
