package com.njydsz.message.infra.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.njydsz.message.domain.enums.core.MessagePriorityEnum;
import com.njydsz.message.domain.enums.core.NotificationCategoryEnum;
import com.njydsz.message.domain.enums.core.NotificationLevelEnum;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 站内通知领域实体 — 系统消息/待办/预警/公告统一入口。
 *
 * <p>对应数据库表 {@code ydsz_msg_notification}。
 * 与 {@code MsgNotificationDO} 的区别：
 * <ul>
 *   <li>去除 MyBatis-Plus 持久化注解
 *   <li>级别/分类/优先级/撤回状态字段使用枚举类型替代 String
 *   <li>不继承 {@code MpBaseEntity}，审计字段平铺定义
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
  private Boolean deleted;

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
  private Integer readStatus;
  private LocalDateTime readTime;
  private RecallStatusEnum recallStatus;
  private LocalDateTime recallAt;
  private LocalDateTime expiredAt;
  private String mentionUserIds;
}
