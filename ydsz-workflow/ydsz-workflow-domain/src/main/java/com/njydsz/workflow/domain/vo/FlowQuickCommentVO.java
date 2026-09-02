package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowQuickComment 视图对象。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowQuickCommentVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String userId;
  private String content;
  private String commentType;
  private Integer sortNum;
  private Integer useCount;
  private Integer isSystem;
  /** 租户标识（对齐实体继承链 MpBaseEntity.tenantId） */
  private String tenantId;
  /** 逻辑删除标记（对齐实体继承链 MpBaseEntity.deleted） */
  private Integer deleted;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
