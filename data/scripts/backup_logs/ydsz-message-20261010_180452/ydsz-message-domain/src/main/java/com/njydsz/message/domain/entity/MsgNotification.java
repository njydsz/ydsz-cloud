package com.njydsz.message.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.message.domain.enums.core.MessagePriorityEnum;
import com.njydsz.message.domain.enums.core.NotificationCategoryEnum;
import com.njydsz.message.domain.enums.core.NotificationLevelEnum;
import com.njydsz.message.domain.enums.receipt.ReadStatusEnum;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合 JPA 继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_notification")
public class MsgNotification extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

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
