package com.njydsz.message.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 消息批次分页查询 Query。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgBatchQuery extends PageQuery {

  /** 批次 ID */
  @Xss private String batchId;

  /** 通道 */
  @Xss private String channel;

  /** 业务类型 */
  @Xss private String bizType;

  /** 批次状态 */
  @Xss private String status;

  /** 发送人 ID */
  @Xss private String senderId;
}
