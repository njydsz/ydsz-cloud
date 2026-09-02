package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowAdminRole 视图对象。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowAdminRoleVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String userId;
  private String roleCode;
  private Boolean enabled;
  private String grantedBy;
  private LocalDateTime grantedAt;
  private LocalDateTime expireAt;
  /** 租户标识（对齐实体继承链 MpBaseEntity.tenantId） */
  private String tenantId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
