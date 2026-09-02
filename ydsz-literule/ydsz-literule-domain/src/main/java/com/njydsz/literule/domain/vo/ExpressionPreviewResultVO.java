package com.njydsz.literule.domain.vo;

import lombok.Data;

/**
 * 表达式预览结果视图对象（VO）。
 *
 * <p>用于前端实时预览某条表达式在给定事实下的求值结果， 包含求值结果值、类型、布尔判定及耗时，便于调试表达式。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class ExpressionPreviewResultVO {

  /** 被预览的表达式原文 */
  private String expression;

  /** 求值结果值（以字符串形式呈现，便于展示） */
  private String value;

  /** 结果 Java 类型（如 Boolean/BigDecimal/String，用于前端格式化） */
  private String javaType;

  /** 布尔型求值结果（条件表达式的真假判定） */
  private Boolean booleanValue;

  /** 求值耗时（毫秒，用于性能评估） */
  private long elapsedMs;

  /** 求值错误信息（无错误时为空） */
  private String error;
}
