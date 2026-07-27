package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * FlowComment 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowCommentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String instanceId;
    private String taskId;
    private String nodeCode;
    private String userId;
    private String userName;
    private String content;
    private String type;
    private String parentCommentId;
    private String replyToUserId;
    private String replyToUserName;
    private String providerTraceId;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}