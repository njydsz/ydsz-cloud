package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 离线消息视图对象（VO）。
 *
 * <p>用于返回离线消息的完整信息，包含接收人、消息内容、推送状态及时间线。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgOfflineVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 离线消息唯一标识（主键） */
  private String id;

  /** 接收人用户 ID */
  private String userId;

  /** 消息类型标签 */
  private String msgType;

  /** 消息内容 JSON */
  private String payload;

  /** 消息时间戳（毫秒） */
  private Long msgTimestamp;

  /** 推送状态（PENDING/PUSHED/EXPIRED） */
  private String status;

  /** 推送时间 */
  private LocalDateTime pushedAt;

  /** 过期时间 */
  private LocalDateTime expiredAt;

  /** 租户 ID */
  private String tenantId;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
