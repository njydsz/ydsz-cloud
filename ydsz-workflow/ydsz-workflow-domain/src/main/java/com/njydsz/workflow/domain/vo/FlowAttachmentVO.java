package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowAttachmentDO 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowAttachmentVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

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
