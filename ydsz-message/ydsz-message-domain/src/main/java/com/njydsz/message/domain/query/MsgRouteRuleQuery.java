package com.njydsz.message.domain.query;

import com.njydsz.common.domain.query.PageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 消息路由规则分页查询 Query。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgRouteRuleQuery extends PageQuery {

  /** 规则编码 */
  @Xss private String ruleCode;

  /** 规则名称 */
  @Xss private String ruleName;

  /** 业务类型 */
  @Xss private String bizType;

  /** 通道 */
  @Xss private String channel;

  /** 状态（ENABLED/DISABLED） */
  @Xss private String status;
}
