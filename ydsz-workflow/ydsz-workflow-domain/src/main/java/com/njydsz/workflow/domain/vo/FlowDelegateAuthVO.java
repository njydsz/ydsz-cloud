package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * FlowDelegateAuth 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowDelegateAuthVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String ownerUserId;
  private String ownerUserName;
  private String delegateUserId;
  private String delegateUserName;
  private String scopeType;
  private String flowCode;
  private String nodeCode;
  private String roleCode;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String authStatus;
  private String reason;
  private String providerTraceId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
