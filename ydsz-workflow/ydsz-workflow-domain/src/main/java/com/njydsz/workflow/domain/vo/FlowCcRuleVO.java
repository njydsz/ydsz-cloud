package com.njydsz.workflow.domain.vo;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

/**
 * FlowCcRule 视图对象。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class FlowCcRuleVO {

  private String id;
  private String flowCode;
  private String nodeCode;
  private String ruleType;
  private String ruleTarget;
  @TableField("is_enabled")
  private Boolean isEnabled;
  private String providerTraceId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
