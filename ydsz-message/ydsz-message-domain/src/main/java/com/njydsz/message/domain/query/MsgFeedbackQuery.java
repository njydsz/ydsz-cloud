package com.njydsz.message.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 消息用户反馈分页查询 Query。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgFeedbackQuery extends PageQuery {

  /** 消息 ID */
  @Xss private String msgId;

  /** 用户 ID */
  @Xss private String userId;

  /** 通道 */
  @Xss private String channel;

  /** 反馈类型 */
  @Xss private String feedbackType;

  /** 状态（PENDING/PROCESSED） */
  @Xss private String status;
}
