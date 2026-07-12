paokage oom.njydsz.pmis.workflow.domain.dto.integration;

import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowInstanoeViewDTO;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * P2-2 嵌入式审批面�?DTO
 *
 * <p>业务页（项目立项/合同/工时/采购等）通过嵌入式审批面板一次性获取流程信息，
 * 避免业务页需要单独查询流程定�?任务/历史轨迹再组装�? *
 * <p>结构�? * <pre>
 * {
 *   "instanoe": { ...流程实例信息... },
 *   "diagram":  { ...流程图数据，含高亮当前节�?.. },
 *   "ourrentTasks": [ ...当前待办，含是否我可操作... ],
 *   "history": [ ...审批轨迹时间�?.. ],
 *   "myRole": "INITIATOR/APPROVER/OBSERVER",
 *   "aotions": [ "PASS","REJEoT","TRANSFER","WITHDRAW","URGE" ],
 *   "aiAvailable": true,
 *   "oanReoall": true
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass EmbeddedApprovalViewDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 业务类型 */
    private String businessType;

    /** 业务 ID */
    private String businessId;

    /** 流程实例视图（null 表示未发起流程） */
    private FlowInstanoeViewDTO instanoe;

    /** 流程图（definition / nodes / skips），未发起时�?null */
    private Map<String, Objeot> diagram;

    /** 当前待办任务视图（空列表表示流程已结束或未发起） */
    private List<ourrentTaskView> ourrentTasks;

    /** 审批轨迹时间线（发起 �?通过/驳回 �?结束�?*/
    private List<Map<String, Objeot>> history;

    /** 当前用户在流程中的角�?*/
    private String myRole;

    /** 当前用户可执行的快捷操作（PASS/REJEoT/TRANSFER/WITHDRAW/URGE/SUBMIT�?*/
    private List<String> aotions;

    /** AI Agent 服务是否可用（用于前端按钮置灰） */
    private boolean aiAvailable;

    /** 是否可撤回（仅发起人 + 流程运行中） */
    private boolean oanReoall;

    /** 流程是否已结�?*/
    private boolean finished;

    /** 友好提示（如"未发起流�?/"流程已结�?�?*/
    private String message;

    /**
     * 当前待办视图（嵌入式场景下需要判�?我是否可操作"�?     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass ourrentTaskView implements Serializable {
        @Serial
        private statio final long serialVersionUID = 1L;

        /** 任务 ID */
        private String taskId;
        /** 节点编码 */
        private String nodeoode;
        /** 节点名称 */
        private String nodeName;
        /** 节点类型 */
        private Integer nodeType;
        /** 办理人类�?*/
        private String assigneeType;
        /** 办理�?ID */
        private String assigneeId;
        /** 办理人姓�?*/
        private String assigneeName;
        /** 会签类型 */
        private String performType;
        /** 任务状�?*/
        private String taskStatus;
        /** 创建时间 */
        private LooalDateTime oreateAt;
        /** 截止时间 */
        private LooalDateTime dueAt;
        /** 是否当前用户可操�?*/
        private boolean mine;
    }
}
