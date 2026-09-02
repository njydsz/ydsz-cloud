package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowCcRule 视图对象。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowCcRuleVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String flowCode;
  private String nodeCode;
  private String ruleType;
  private String ruleTarget;
  private Integer enabled;
  private String providerTraceId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
