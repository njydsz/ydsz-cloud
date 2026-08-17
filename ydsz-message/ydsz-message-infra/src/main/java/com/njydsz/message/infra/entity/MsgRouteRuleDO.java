package com.njydsz.message.infra.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 消息路由规则表: 按 biz_type/channel/条件表达式路由到目标通道,支持降级
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_route_rule")
public class MsgRouteRuleDO extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 规则编码（唯一） */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 业务类型 */
  private String bizType;

  /** 通道 */
  private String channel;

  /** 优先级（数值越小越优先） */
  private Integer priority;

  /** 路由条件（SpEL 表达式） */
  private String conditionExpr;

  /** 命中后目标通道 */
  private String targetChannel;

  /** 目标通道发送失败时降级通道 */
  private String fallbackChannel;

  /** 描述说明 */
  private String description;

  /** 排序序号 */
  private Integer sortOrder;
}
