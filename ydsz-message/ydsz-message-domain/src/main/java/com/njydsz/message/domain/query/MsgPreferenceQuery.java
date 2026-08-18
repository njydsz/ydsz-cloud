package com.njydsz.message.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 用户消息偏好分页查询 Query。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgPreferenceQuery extends PageQuery {

  /** 用户 ID */
  @Xss private String userId;

  /** 通道 */
  @Xss private String channel;

  /** 业务类型 */
  @Xss private String bizType;

  /** 状态（ACTIVE/INACTIVE） */
  @Xss private String status;
}
