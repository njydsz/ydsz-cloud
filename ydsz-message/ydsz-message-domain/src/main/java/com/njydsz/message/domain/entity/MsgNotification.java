package com.njydsz.message.domain.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.message.domain.enums.core.MessagePriorityEnum;
import com.njydsz.message.domain.enums.core.NotificationCategoryEnum;
import com.njydsz.message.domain.enums.core.NotificationLevelEnum;
import com.njydsz.message.domain.enums.receipt.ReadStatusEnum;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;

/**
 * 站内通知领域实体，系统消息/待办/预警/公告统一入口。
 *
 * <p>对应数据库表 {@code ydsz_msg_notification}。与普通消息的区别是面向站内用户，
 * 不经过第三方通道下发，直接写入数据库供用户登录后拉取展示。
 * 支持阅读状态（readStatus）、撤回状态（recallStatus）、过期时间、@提及等站内特有属性。
 *
 * @author ydsz
 * @since 26.09.24
 */
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 泛型擦除导致 unchecked 警告
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
public class MsgNotification implements Serializable {

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
  private String title;
  private String content;
  private NotificationLevelEnum level;
  private NotificationCategoryEnum category;
  private MessagePriorityEnum priority;
  private String senderId;
  private String receiverId;
  private String bizType;
  private String bizId;
  private String messageGroup;
  private String batchId;
  private String actionUrl;
  private String actionText;
  private String icon;
  private String extra;
  private String sourceModule;
  private ReadStatusEnum readStatus;
  private LocalDateTime readTime;
  private RecallStatusEnum recallStatus;
  private LocalDateTime recallAt;
  private LocalDateTime expiredAt;
  private String mentionUserIds;
}
