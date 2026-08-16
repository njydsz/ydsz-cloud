package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowSkip 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowSkipVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String definitionId;
  private String flowCode;
  private String skipName;
  private String skipType;
  private String coordinate;
  private String skipCondition;
  private String nextNodeCode;
  private Integer nextNodeType;
  private String coordinateNext;
  private String skipList;
  private String ext;
  private String providerTraceId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
