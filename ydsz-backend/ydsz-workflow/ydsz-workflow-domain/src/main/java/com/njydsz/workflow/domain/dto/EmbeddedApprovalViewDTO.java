package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.njydsz.common.safe.sensitive.SensitiveData;
import com.njydsz.common.safe.sensitive.SensitiveDataSerializer;
import com.njydsz.common.safe.sensitive.SensitiveType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * P2-2 嵌入式审批面板 DTO
 *
 * <p>业务页（项目立项/合同/工时/采购等）通过嵌入式审批面板一次性获取流程信息，
 * 避免业务页需要单独查询流程定义/任务/历史轨迹再组装。
 *
 * <p>结构：
 * <pre>
 * {
 *   "instance": { ...流程实例信息... },
 *   "diagram":  { ...流程图数据，含高亮当前节点... },
 *   "currentTasks": [ ...当前待办，含是否我可操作... ],
 *   "history": [ ...审批轨迹时间线... ],
 *   "myRole": "INITIATOR/APPROVER/OBSERVER",
 *   "actions": [ "PASS","REJECT","TRANSFER","WITHDRAW","URGE" ],
 *   "canRecall": true
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddedApprovalViewDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务类型 */
    private String businessType;

    /** 业务 ID */
    private String businessId;

    /** 流程实例视图（null 表示未发起流程） */
    private FlowInstanceViewDTO instance;

    /** 流程图（definition / nodes / skips），未发起时为 null */
    private Map<String, Object> diagram;

    /** 当前待办任务视图（空列表表示流程已结束或未发起） */
    private List<CurrentTaskView> currentTasks;

    /** 审批轨迹时间线（发起 → 通过/驳回 → 结束） */
    private List<Map<String, Object>> history;

    /** 当前用户在流程中的角色 */
    private String myRole;

    /** 当前用户可执行的快捷操作（PASS/REJECT/TRANSFER/WITHDRAW/URGE/SUBMIT） */
    private List<String> actions;

    /** 是否可撤回（仅发起人 + 流程运行中） */
    private boolean canRecall;

    /** 流程是否已结束 */
    private boolean finished;

    /** 友好提示（如"未发起流程"/"流程已结束"） */
    private String message;

    /**
     * 当前待办视图（嵌入式场景下需要判定"我是否可操作"）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentTaskView implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 任务 ID */
        private String taskId;
        /** 节点编码 */
        private String nodeCode;
        /** 节点名称 */
        private String nodeName;
        /** 节点类型 */
        private Integer nodeType;
        /** 办理人类型 */
        private String assigneeType;
        /** 办理人 ID */
        private String assigneeId;
        /** 办理人姓名 */
        @JsonSerialize(using = SensitiveDataSerializer.class)
        @SensitiveData(SensitiveType.CHINESE_NAME)
        private String assigneeName;
        /** 会签类型 */
        private String performType;
        /** 任务状态 */
        private String taskStatus;
        /** 创建时间 */
        private LocalDateTime createAt;
        /** 截止时间 */
        private LocalDateTime dueAt;
        /** 是否当前用户可操作 */
        private boolean mine;
    }
}
