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
 * <p>对应数据库表 {@code ydsz_msg_notification}，支持优先级/聚合/撤回/业务跳转。
 * 与 {@code MsgNotificationDO} 的区别：
 *
 * <ul>
 *   <li>去除 MyBatis-Plus 持久化注解（{@code @TableName} 等）
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

  /** 主键 ID（雪花算法） */
  private String id;

  /** 租户 ID */
  private String tenantId;

  /** 创建人 ID */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 ID */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 删除标识: false 未删除 / true 已删除 */
  private Boolean deleted;

  // ===== 业务字段 =====

  /** 通知标题 */
  private String title;

  /** 通知内容(支持富文本/Markdown) */
  private String content;

  /** 通知级别: INFO/WARN/ERROR/URGENT */
  private NotificationLevelEnum level;

  /** 通知分类: SYSTEM/WORKFLOW/ALERT/TO_DO/ANNOUNCE */
  private NotificationCategoryEnum category;

  /** 发送优先级: LOW/NORMAL/HIGH/URGENT */
  private MessagePriorityEnum priority;

  /** 发送人 ID(系统通知为 SYSTEM) */
  private String senderId;

  /** 接收人 ID */
  private String receiverId;

  /** 关联业务类型(如 contract/invoice/risk) */
  private String bizType;

  /** 关联业务单据 ID */
  private String bizId;

  /** 聚合组(同组通知可合并为摘要) */
  private String messageGroup;

  /** 聚合批次 ID */
  private String batchId;

  /** 点击跳转 URL(前端路由或外链) */
  private String actionUrl;

  /** 跳转按钮文案(如"去处理") */
  private String actionText;

  /** 通知图标标识 */
  private String icon;

  /** 扩展字段 JSON(业务自定义透传) */
  private String extra;

  /** 来源模块(system/project/workflow/agent) */
  private String sourceModule;

  /** 已读状态: 0 未读 / 1 已读 */
  private Integer readStatus;

  /** 首次阅读时间 */
  private LocalDateTime readTime;

  /** 撤回状态: NONE 未撤回 / RECALLED 已撤回 */
  private RecallStatusEnum recallStatus;

  /** 撤回时间 */
  private LocalDateTime recallAt;

  /** 过期时间(过期后不再展示) */
  private LocalDateTime expiredAt;

  /** @提及用户 ID 列表(逗号分隔) */
  private String mentionUserIds;
}
