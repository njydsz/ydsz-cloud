package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowAdminRoleDO 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
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
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
