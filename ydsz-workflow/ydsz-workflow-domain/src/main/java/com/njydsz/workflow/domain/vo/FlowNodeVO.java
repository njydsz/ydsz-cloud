package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * FlowNode 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowNodeVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String definitionId;
  private String flowCode;
  private Integer nodeType;
  private String nodeCode;
  private String nodeName;
  private String permissionFlag;
  private String skipAnyNode;
  private String coordinate;
  private String skipList;
  private String ext;
  private String formFieldsConfig;
  private String slaConfig;
  private String providerTraceId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
