package com.njydsz.message.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 消息轨迹分页查询 Query。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgTraceQuery extends PageQuery {

  /** 消息 ID */
  @Xss private String msgId;

  /** 链路追踪 ID */
  @Xss private String traceId;

  /** 轨迹节点类型 */
  @Xss private String node;

  /** 节点状态 */
  @Xss private String status;

  /** 业务类型 */
  @Xss private String bizType;

  /** 业务单据 ID */
  @Xss private String bizId;
}
