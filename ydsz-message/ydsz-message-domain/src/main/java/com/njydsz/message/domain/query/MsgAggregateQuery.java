package com.njydsz.message.domain.query;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 聚合批次分页查询 Query。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgAggregateQuery extends PageQuery {

  /** 聚合组 */
  @Xss private String aggregateGroup;

  /** 接收人 */
  @Xss private String receiver;

  /** 通道 */
  @Xss private String channel;

  /** 批次状态 */
  @Xss private String batchStatus;

  /** 计划发送时间（小于等于此时间的记录） */
  private LocalDateTime scheduledSendAtBefore;
}
