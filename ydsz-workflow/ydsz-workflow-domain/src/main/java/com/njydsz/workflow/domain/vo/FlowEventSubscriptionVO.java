package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowEventSubscription 视图对象。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowEventSubscriptionVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String instanceId;
  private String definitionId;
  private String flowCode;
  private String nodeCode;
  private String nodeName;
  private String eventType;
  private String eventRef;
  private String correlationKey;
  private String boundaryTaskId;
  private String subscriptionStatus;
  private String payload;
  private LocalDateTime triggeredAt;
  private String triggerSource;
  private String cancelReason;
  private String providerTraceId;
  /** 租户标识（对齐实体继承链 MpBaseEntity.tenantId） */
  private String tenantId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
