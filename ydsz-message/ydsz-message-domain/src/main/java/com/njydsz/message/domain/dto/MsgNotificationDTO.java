package com.njydsz.message.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 站内通知 DTO（命令请求参数）。
 *
 * <p>用于 Repository 层 CUD 操作的统一入参，不区分 Create / Update：
 * <ul>
 *   <li>创建场景：{@code id} 字段不传</li>
 *   <li>更新场景：传入 {@code id}</li>
 * </ul>
 *
 * <p>遵循云顶编码规范第 34 节：domain 层 DTO 不区分 Create/Update。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgNotificationDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 通知唯一标识（主键，更新时传入） */
  private String id;

  /** 通知标题 */
  private String title;

  /** 通知内容 */
  private String content;

  /** 级别（INFO/WARN/ERROR/CRITICAL） */
  private String level;

  /** 分类 */
  private String category;

  /** 优先级（LOW/NORMAL/HIGH/URGENT） */
  private String priority;

  /** 发送人 ID */
  private String senderId;

  /** 接收人 ID */
  private String receiverId;

  /** 业务类型 */
  private String bizType;

  /** 业务 ID */
  private String bizId;

  /** 消息分组（同组消息在收件箱中折叠展示） */
  private String messageGroup;

  /** 批次 ID */
  private String batchId;

  /** 操作跳转 URL */
  private String actionUrl;

  /** 操作按钮文案 */
  private String actionText;

  /** 图标 */
  private String icon;

  /** 扩展信息（JSON） */
  private String extra;

  /** 来源模块 */
  private String sourceModule;

  /** 已读状态（0=未读，1=已读） */
  private Integer readStatus;

  /** 已读时间 */
  private LocalDateTime readTime;

  /** 撤回状态（null=未撤回，RECALLED=已撤回） */
  private String recallStatus;

  /** 撤回时间 */
  private LocalDateTime recallAt;

  /** 过期时间 */
  private LocalDateTime expiredAt;

  /** @提及用户 ID 列表，逗号分隔 */
  private String mentionUserIds;

  /** 状态（PENDING/SENT/FAILED） */
  private String status;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 租户 ID */
  private String tenantId;

  /** 删除标识 */
  private Boolean deleted;
}
