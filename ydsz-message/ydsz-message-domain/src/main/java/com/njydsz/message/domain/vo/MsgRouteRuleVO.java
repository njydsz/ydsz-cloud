package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息路由规则视图对象（VO）。
 *
 * <p>用于 Controller 层返回消息路由规则的配置信息，路由规则决定消息 从哪个通道发出、按什么条件筛选等，支撑消息智能路由。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgRouteRuleVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 路由规则唯一标识（主键） */
  private String id;

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

  /** 状态（ENABLED/DISABLED） */
  private String status;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
