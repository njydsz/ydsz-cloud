package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowTemplate 视图对象。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowTemplateVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String templateCode;
  private String templateName;
  private String category;
  private String description;
  private String icon;
  private String bpmnXml;
  private String formPath;
  private Integer useCount;
  private Integer sortOrder;
  private String parentTemplateId;
  private Integer version;
  private String versionLabel;
  private String inheritType;
  private Integer isLatest;
  /** 状态（对齐实体 MpBaseEntity.status） */
  private Integer status;
  /** 租户标识（对齐实体 MpBaseEntity.tenantId） */
  private String tenantId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
