package com.njydsz.workflow.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * FlowUser 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String taskId;
    private String instanceId;
    private String nodeCode;
    private String userType;
    private String userId;
    private String userName;
    private Integer processed;
    private LocalDateTime processAt;
    private String comment;
    private Integer weight;
    private String signType;
    private String providerTraceId;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
