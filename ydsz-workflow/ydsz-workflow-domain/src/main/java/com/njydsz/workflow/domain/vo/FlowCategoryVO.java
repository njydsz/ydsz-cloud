package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowCategoryDO 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowCategoryVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String categoryCode;
  private String categoryName;
  private String parentId;
  private Integer sortNum;
  private String icon;
  private String remark;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
