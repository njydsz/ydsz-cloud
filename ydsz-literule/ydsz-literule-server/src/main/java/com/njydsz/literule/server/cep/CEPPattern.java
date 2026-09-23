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

  /**
   * 窗口类型（P0-F1 26.09.23）：TUMBLING = 滚动窗口（默认，不重叠），SLIDING = 滑动窗口（重叠，精度更高）
   */
  @Builder.Default
  private CEPWindowType windowType = CEPWindowType.TUMBLING;

  /**
   * 聚合类型（P0-F1 26.09.23）：COUNT = 计数（默认），SUM = 求和，AVG = 平均值
   */
  @Builder.Default
  private CEPAggregationType aggregationType = CEPAggregationType.COUNT;

  /**
   * 聚合字段（仅 SUM/AVG 有效），从事件 attributes 中取指名字段做数值聚合。COUNT 模式下忽略此字段。
   */
  private String aggregationField;

  /**
   * 窗口类型枚举（P0-F1 26.09.23）
   */
  public enum CEPWindowType {
    /** 滚动窗口：固定大小不重叠，到期后清空 */
    TUMBLING,
    /** 滑动窗口：重叠窗口，每次事件到达都重新计算 */
    SLIDING
  }

  /**
   * 聚合类型枚举（P0-F1 26.09.23）
   */
  public enum CEPAggregationType {
    /** 计数 */
    COUNT,
    /** 求和 */
    SUM,
    /** 平均值 */
    AVG
  }
}
