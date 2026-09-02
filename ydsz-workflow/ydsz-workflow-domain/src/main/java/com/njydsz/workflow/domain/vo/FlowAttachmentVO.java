package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowAttachment 视图对象。
 *
 * @author ydsz-team
 * @since 26.09.01
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
  /** 租户标识（对齐实体继承链 MpBaseEntity.tenantId） */
  private String tenantId;
  /** 逻辑删除标记（对齐实体继承链 MpBaseEntity.deleted） */
  private Integer deleted;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
