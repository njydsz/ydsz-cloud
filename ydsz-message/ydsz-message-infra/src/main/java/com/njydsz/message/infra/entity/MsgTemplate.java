package com.njydsz.message.infra.entity;

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
 * 消息模板领域实体 — 支持 {@code ${var}} 嵌套占位符 / 多语言 i18n / 版本 / 审核 / 分类 / 场景。
 *
 * <p>对应数据库表 {@code ydsz_msg_template}。
 * 与 {@code MsgTemplate} 的区别：
 * <ul>
 *   <li>去除 MyBatis-Plus 持久化注解
 *   <li>通道/状态/审核状态字段使用枚举类型替代 String
 *   <li>不继承 {@code MpBaseEntity}，审计字段平铺定义
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SuppressWarnings("unchecked") // @SuperBuilder 生成的代码会触发 unchecked 警告，无法在源码层面修复
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
  private Boolean deleted;

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
