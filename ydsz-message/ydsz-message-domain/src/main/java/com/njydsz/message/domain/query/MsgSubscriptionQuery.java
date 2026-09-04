package com.njydsz.message.domain.query;

import com.njydsz.common.domain.query.PageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 消息订阅关系分页查询 Query。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgSubscriptionQuery extends PageQuery {

  /** 用户 ID */
  @Xss private String userId;

  /** 主题编码 */
  @Xss private String topicCode;

  /** 通道 */
  @Xss private String channel;

  /** 状态（SUBSCRIBED/UNSUBSCRIBED） */
  @Xss private String status;
}
