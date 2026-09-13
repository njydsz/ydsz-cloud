package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

/**
 * FlowAutoTrigger 视图对象。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowAutoTriggerVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String sourceFlowCode;
  private String targetFlowCode;
  private String conditionExpression;
  private String description;
  @TableField("is_enabled")
  private Boolean isEnabled;
  private Integer sort;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
