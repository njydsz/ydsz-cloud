package com.njydsz.pmis.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程实例视图 DTO（Feign 友好，无内部敏感字段）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowInstanceViewDTO implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    /** 实例 ID */
    private String id;
    /** 流程编码 */
    private String flowCode;
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
    /** 发起人 ID */
    private String initiatorId;
    /** 发起人姓名 */
    private String initiatorName;
    /** 当前节点编码 */
    private String currentNodeCode;
    /** 当前节点名称 */
    private String currentNodeName;
    /** 实例状态（FlowInstanceStatus.name） */
    private String flowStatus;
    /** 激活状态：0 挂起 / 1 激活 */
    private Integer activityStatus;
    /** 启动时间 */
    private LocalDateTime startAt;
    /** 结束时间 */
    private LocalDateTime endAt;
    /** 耗时（毫秒） */
    private Long durationMs;
    /** 流程变量 JSON */
    private String variable;
    /** 当前待办任务列表 */
    private List<FlowTaskViewDTO> currentTasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowTaskViewDTO implements Serializable {
        @Serial
        private static final String serialVersionUID = "1";
        /** 任务 ID */
        private String id;
        /** 节点编码 */
        private String nodeCode;
        /** 节点名称 */
        private String nodeName;
        /** 节点类型（FlowNodeType.code） */
        private Integer nodeType;
        /** 办理人类型 */
        private String assigneeType;
        /** 办理人 ID */
        private String assigneeId;
        /** 办理人姓名 */
        private String assigneeName;
        /** 会签类型 */
        private String performType;
        /** 任务状态 */
        private String taskStatus;
        /** 审批意见 */
        private String comment;
        /** 创建时间 */
        private LocalDateTime createAt;
        /** 签收时间 */
        private LocalDateTime claimAt;
        /** 完成时间 */
        private LocalDateTime finishAt;
        /** 耗时（毫秒） */
        private Long durationMs;
        /** 截止时间 */
        private LocalDateTime dueAt;
        /** P1-1: 任务优先级（1-100，默认 50） */
        private Integer priority;
    }
}
