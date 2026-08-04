package com.remisoft.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * FlowAttachment 视图对象。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class FlowAttachmentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String instanceId;
    private String taskId;
    private String nodeCode;
    private String bizType;
    private String fileName;
    private String fileExt;
    private Long fileSize;
    private String contentType;
    private String storageKey;
    private String storageType;
    private String uploaderId;
    private String uploaderName;
    private String downloadUrl;
    private String md5;
    private String providerTraceId;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}