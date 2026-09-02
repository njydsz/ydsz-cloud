package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowComment 视图对象。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowCommentVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

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
  /** 租户标识（对齐实体继承链 MpBaseEntity.tenantId） */
  private String tenantId;
  /** 逻辑删除标记（对齐实体继承链 MpBaseEntity.deleted） */
  private Integer deleted;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
