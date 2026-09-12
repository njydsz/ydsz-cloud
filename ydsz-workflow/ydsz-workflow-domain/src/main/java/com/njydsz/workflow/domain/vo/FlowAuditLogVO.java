package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * FlowAuditLog 视图对象（工作流域内审计，节点流转追溯）。
 *
 * <p>与横切通用操作审计（{@code ydsz-common-audit}）为两套独立体系。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowAuditLogVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String instanceId;
  private String taskId;
  private String flowCode;
  private String businessType;
  private String businessId;
  private String nodeCode;
  private String nodeName;
  private String action;
  private String operatorId;
  private String operatorName;
  private String targetId;
  private String targetName;
  private String comment;
  private String commentType;
  private LocalDateTime operatedAt;
  private String providerTraceId;
  /** 租户标识（对齐实体继承链 MpBaseEntity.tenantId） */
  private String tenantId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
