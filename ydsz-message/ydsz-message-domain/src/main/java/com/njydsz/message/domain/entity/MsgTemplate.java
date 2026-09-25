package com.njydsz.message.domain.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.message.domain.enums.core.MessageChannelEnum;
import com.njydsz.message.domain.enums.template.TemplateAuditStatusEnum;
import com.njydsz.message.domain.enums.template.TemplateStatusEnum;

/**
 * 消息模板领域实体，支持 var 嵌套占位符、多语言 i18n、版本、审核、分类与场景。
 *
 * <p>对应数据库表 {@code ydsz_msg_template}。templateCode 全局唯一标识模板，
 * channel 关联发送通道，status 标识启用/禁用，auditStatus 跟踪审核流程。
 * content 字段存放含 var 占位符的模板正文，variableDefs 定义变量元数据。
 *
 * @author ydsz
 * @since 26.09.24
 */
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合 JPA 继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
public class MsgTemplate implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ===== 审计字段 =====
  private String id;
  private String tenantId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
  private Boolean isDeleted;

  // ===== 业务字段 =====
  private String templateCode;
  private MessageChannelEnum channel;
  private String locale;
  private String version;
  private String category;
  private String sceneCode;
  private String subject;
  private String content;
  private String provider;
  private String providerKey;
  private String signName;
  private TemplateStatusEnum status;
  private TemplateAuditStatusEnum auditStatus;
  private String auditBy;
  private LocalDateTime auditAt;
  private String auditRemark;
  private String description;
  private String variableDefs;
}
