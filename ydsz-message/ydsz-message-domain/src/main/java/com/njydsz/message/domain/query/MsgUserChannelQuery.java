package com.njydsz.message.domain.query;

import com.njydsz.common.domain.query.PageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 用户通道绑定查询 Query。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgUserChannelQuery extends PageQuery {

  /** 用户 ID */
  @Xss private String userId;

  /** 通道类型 */
  @Xss private String channelType;

  /** 状态（ACTIVE/INACTIVE） */
  @Xss private String status;

  /** 是否优先查询主绑定（isPrimary=1） */
  private boolean primaryFirst;
}
