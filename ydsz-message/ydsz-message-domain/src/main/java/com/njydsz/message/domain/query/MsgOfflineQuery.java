package com.njydsz.message.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 离线消息分页查询 Query。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgOfflineQuery extends PageQuery {

  /** 用户 ID */
  @Xss private String userId;

  /** 消息类型 */
  @Xss private String msgType;

  /** 推送状态（PENDING/PUSHED/EXPIRED） */
  @Xss private String status;
}
