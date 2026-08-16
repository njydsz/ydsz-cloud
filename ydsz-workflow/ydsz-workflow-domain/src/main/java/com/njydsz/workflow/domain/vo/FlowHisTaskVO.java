package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * FlowHisTask 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowHisTaskVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String instanceId;
    private String taskId;
    private String flowCode;
    private String definitionId;
    private String nodeCode;
    private String nodeName;
    private Integer nodeType;
    private String businessType;
    private String businessId;
    private String businessNo;
    private String flowName;
    private String title;
    private String assigneeType;
    private String assigneeId;
    private String assigneeName;
    private String performType;
    private Integer approveCount;
    private Integer approveFinished;
    private BigDecimal votePassRate;
    private String taskStatus;
    private String comment;
    private LocalDateTime claimAt;
    private LocalDateTime finishAt;
    private Long durationMs;
    private String providerTraceId;
    private String iterVar;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
