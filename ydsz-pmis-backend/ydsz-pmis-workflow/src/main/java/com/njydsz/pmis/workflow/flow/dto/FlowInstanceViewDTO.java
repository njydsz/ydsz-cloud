package com.njydsz.pmis.workflow.flow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private static final long serialVersionUID = 1L;

    private Long id;
    private String flowCode;
    private String flowName;
    private String version;
    private String businessType;
    private String businessId;
    private String businessNo;
    private String title;
    private Long initiatorId;
    private String initiatorName;
    private String currentNodeCode;
    private String currentNodeName;
    private String flowStatus;
    private Integer activityStatus;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long durationMs;
    private String variable;
    private List<FlowTaskViewDTO> currentTasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowTaskViewDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long id;
        private String nodeCode;
        private String nodeName;
        private Integer nodeType;
        private String assigneeType;
        private String assigneeId;
        private String assigneeName;
        private String performType;
        private String taskStatus;
        private String comment;
        private LocalDateTime createAt;
        private LocalDateTime claimAt;
        private LocalDateTime finishAt;
        private Long durationMs;
        private LocalDateTime dueAt;
    }
}
