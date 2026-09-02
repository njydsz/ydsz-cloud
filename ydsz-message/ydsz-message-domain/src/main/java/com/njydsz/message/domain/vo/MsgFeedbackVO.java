package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息用户反馈视图对象（VO）。
 *
 * <p>用于 Controller 层返回用户对消息的反馈信息，包含评分、反馈类型和内容， 支撑消息质量评估和用户满意度分析。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgFeedbackVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 反馈记录唯一标识（主键） */
  private String id;

  /** 关联消息 ID */
  private String msgId;

  /** 关联通知 ID */
  private String notificationId;

  /** 用户 ID */
  private String userId;

  /** 通道 */
  private String channel;

  /** 业务类型 */
  private String bizType;

  /** 评分（1~5） */
  private Integer rating;

  /** 反馈类型（USEFUL/USELESS/SPAM/OTHER） */
  private String feedbackType;

  /** 反馈内容 */
  private String content;

  /** 状态（PENDING/PROCESSED） */
  private String status;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
