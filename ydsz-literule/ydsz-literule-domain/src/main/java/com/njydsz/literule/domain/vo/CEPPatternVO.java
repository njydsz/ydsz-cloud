package com.njydsz.literule.domain.vo;

import java.math.BigDecimal;
import java.time.Duration;

import lombok.Data;

/**
 * CEP（复杂事件处理）模式视图对象（VO）。
 *
 * <p>用于前端配置与展示复杂事件模式，支持滚动窗口计数模式。与后端 {@code CEPPattern} 领域对象对应，仅承载展示所需字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class CEPPatternVO {

  /** 模式 ID（业务唯一标识） */
  private String id;

  /** 关联规则编码 */
  private String ruleCode;

  /** 模式名称（展示用） */
  private String name;

  /** 时间窗口长度（滚动窗口大小） */
  private Duration window;

  /** 触发阈值（窗口内事件次数达到该值时触发） */
  private BigDecimal threshold;

  /** 关注的事件类型（模式只匹配该类型事件） */
  private String eventType;

  /** 事件过滤表达式（对事件附加条件过滤） */
  private String filter;

  /** 模式描述 */
  private String description;
}
