package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowTimer 视图对象。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowTimerVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String instanceId;
  private String definitionId;
  private String flowCode;
  private String nodeCode;
  private String nodeName;
  private String timerType;
  private String boundaryTaskId;
  private LocalDateTime fireAt;
  private String cycle;
  private String timerStatus;
  private LocalDateTime firedAt;
  private String cancelReason;
  private String providerTraceId;
  /** 租户标识（对齐实体继承链 MpBaseEntity.tenantId） */
  private String tenantId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
