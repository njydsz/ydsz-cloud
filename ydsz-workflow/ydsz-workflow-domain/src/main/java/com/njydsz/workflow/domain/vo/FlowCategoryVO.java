package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowCategory 视图对象。
 *
 * @author ydsz-team
 * @since 26.09.01
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
  /** 租户标识（对齐实体继承链 MpBaseEntity.tenantId） */
  private String tenantId;
  /** 逻辑删除标记（对齐实体继承链 MpBaseEntity.deleted） */
  private Integer deleted;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
