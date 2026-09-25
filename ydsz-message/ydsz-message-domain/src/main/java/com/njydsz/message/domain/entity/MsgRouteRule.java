package com.njydsz.message.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 消息路由规则实体，按 bizType + channel + SpEL 条件表达式将消息路由到目标通道并支持降级。
 *
 * <p>对应数据库表 {@code ydsz_msg_route_rule}。规则引擎按 priority 升序匹配规则，
 * conditionExpr（SpEL 表达式）命中后路由到 targetChannel；
 * 目标通道发送失败时自动降级到 fallbackChannel，保证消息可达性。
 *
 * @author ydsz
 * @since 26.09.24
 */// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合泛型父类继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_route_rule")
public class MsgRouteRule extends MpBaseEntity<String> {

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
  private Integer sort;
}
