package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowCc 视图对象。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowCcVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String instanceId;
  private String taskId;
  private String nodeCode;
  private String nodeName;
  private String flowCode;
  private String flowName;
  private String businessKey;
  private String ccUserId;
  private String ccUserName;
  private String ccType;
  private String triggerUserId;
  private String triggerUserName;
  private String title;
  private String content;
  private String readStatus;
  private LocalDateTime readAt;
  private String providerTraceId;
  /** 租户标识（对齐实体继承链 MpBaseEntity.tenantId） */
  private String tenantId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
