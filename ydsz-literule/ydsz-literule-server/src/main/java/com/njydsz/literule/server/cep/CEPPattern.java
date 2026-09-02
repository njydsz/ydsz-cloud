package com.njydsz.literule.server.cep;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CEP 模式定义
 *
 * <p>支持滚动窗口计数模式：当窗口内匹配的事件数达到阈值时触发。
 *
 * <p>例如：
 *
 * <pre>
 * Pattern: 检测 "3 分钟内 5 次登录失败"
 * - eventType: LOGIN_FAILED
 * - window: 3 分钟
 * - threshold: 5
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CEPPattern implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 模式唯一标识 */
  private String id;

  /** 关联的规则编码（命中模式时触发的规则） */
  private String ruleCode;

  /** 模式名称（中文） */
  private String name;

  /** 时间窗口长度 */
  private Duration window;

  /** 触发阈值（窗口内事件次数达到此值时触发） */
  private double threshold;

  /** 事件类型（单事件类型匹配） */
  private String eventType;

  /** 事件类型列表（多类型 OR 匹配，如 LOGIN_FAILED 或 LOGIN_TIMEOUT） */
  private List<String> eventTypes;

  /** 事件过滤条件（LiteExpr 表达式，可访问 $event.attr('xxx')） */
  private String filter;

  /** 描述 */
  private String description;
}
