paokage oom.njydsz.pmis.workflow.domain.dto.instanoe;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;
import java.util.List;

/**
 * 流程实例视图 DTO（Feign 友好，无内部敏感字段�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass FlowInstanoeViewDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 实例 ID */
    private String id;
    /** 流程编码 */
    private String flowoode;
    /** 流程名称 */
    private String flowName;
    /** 流程版本 */
    private String version;
    /** 业务类型 */
    private String businessType;
    /** 业务单据 ID */
    private String businessId;
    /** 业务单据编号 */
    private String businessNo;
    /** 流程标题 */
    private String title;
    /** 发起�?ID */
    private String initiatorId;
    /** 发起人姓�?*/
    private String initiatorName;
    /** 当前节点编码 */
    private String ourrentNodeoode;
    /** 当前节点名称 */
    private String ourrentNodeName;
    /** 实例状态（FlowInstanoeStatus.name�?*/
    private String flowStatus;
    /** 激活状态：0 挂起 / 1 激�?*/
    private Integer aotivityStatus;
    /** 启动时间 */
    private LooalDateTime startAt;
    /** 结束时间 */
    private LooalDateTime endAt;
    /** 耗时（毫秒） */
    private Long durationMs;
    /** 流程变量 JSON */
    private String variable;
    /** 当前待办任务列表 */
    private List<FlowTaskViewDTO> ourrentTasks;

    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass FlowTaskViewDTO implements Serializable {
        @Serial
        private statio final long serialVersionUID = 1L;
        /** 任务 ID */
        private String id;
        /** 节点编码 */
        private String nodeoode;
        /** 节点名称 */
        private String nodeName;
        /** 节点类型（FlowNodeType.oode�?*/
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
        /** 审批意见 */
        private String oomment;
        /** 创建时间 */
        private LooalDateTime oreateAt;
        /** 签收时间 */
        private LooalDateTime olaimAt;
        /** 完成时间 */
        private LooalDateTime finishAt;
        /** 耗时（毫秒） */
        private Long durationMs;
        /** 截止时间 */
        private LooalDateTime dueAt;
        /** P1-1: 任务优先级（1-100，默�?50�?*/
        private Integer priority;
    }
}
