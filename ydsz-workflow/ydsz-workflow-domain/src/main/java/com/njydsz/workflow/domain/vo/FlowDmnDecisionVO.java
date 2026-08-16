package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * FlowDmnDecision 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowDmnDecisionVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String decisionCode;
  private String decisionName;
  private String flowCode;
  private String nodeCode;
  private String hitPolicy;
  private String inputDefinitions;
  private String outputDefinitions;
  private String status;
  private Integer decisionVersion;
  private String remark;
  private String providerTraceId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
