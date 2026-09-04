package com.njydsz.message.domain.dto;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 站内通知分页查询 DTO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationQueryDTO extends PageQuery {

  /** 通知分类 */
  private String category;

  /** 通知级别 */
  private String level;

  /** 已读状态: 0 未读 / 1 已读 */
  private Integer readStatus;

  /** 接收人 ID */
  private String receiverId;

  /** 通知 ID 列表（批量查询） */
  private List<String> ids;

  /** 租户 ID */
  private String tenantId;

  /** 消息分组 */
  private String messageGroup;

  /** 撤回状态 */
  private String recallStatus;

  /** 业务类型 */
  private String bizType;

  /** 业务 ID */
  private String bizId;
}
