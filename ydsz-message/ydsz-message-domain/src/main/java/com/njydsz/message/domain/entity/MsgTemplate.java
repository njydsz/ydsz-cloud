package com.njydsz.message.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.message.domain.enums.core.MessageChannelEnum;
import com.njydsz.message.domain.enums.template.TemplateAuditStatusEnum;
import com.njydsz.message.domain.enums.template.TemplateStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 消息模板领域实体，支持 var 嵌套占位符、多语言 i18n、版本、审核、分类与场景。
 *
 * <p>对应数据库表 {@code ydsz_msg_template}。templateCode 全局唯一标识模板，
 * channel 关联发送通道，status 标识启用/禁用，auditStatus 跟踪审核流程。
 * content 字段存放含 var 占位符的模板正文，variableDefs 定义变量元数据。
 *
 * <p><b>status 字段说明：</b>DB 存储为 VARCHAR（枚举 name 字符串，如 ENABLED/DISABLED），
 * 领域方法通过 {@link TemplateStatusEnum#fromString(String)} 做枚举转换，保证类型安全与兼容基类 {@code MpBaseEntity}。
 *
 * @author ydsz
 * @since 26.09.24
 */
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合 JPA 继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MsgTemplate extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

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
  private TemplateAuditStatusEnum auditStatus;
  private String auditBy;
  private LocalDateTime auditAt;
  private String auditRemark;
  private String description;
  private String variableDefs;

  // ===== 领域访问器 =====

  /**
   * 获取模板启用状态枚举视图。
   *
   * @return 状态枚举，无法解析时返回 {@link TemplateStatusEnum#DISABLED}
   */
  public TemplateStatusEnum getStatusEnum() {
    return TemplateStatusEnum.fromString(getStatus());
  }

  /**
   * 设置模板启用状态（通过枚举）。
   *
   * @param statusEnum 状态枚举
   */
  public void setStatusEnum(TemplateStatusEnum statusEnum) {
    if (statusEnum == null) {
      setStatus(TemplateStatusEnum.DISABLED.name());
    } else {
      setStatus(statusEnum.name());
    }
  }
}
