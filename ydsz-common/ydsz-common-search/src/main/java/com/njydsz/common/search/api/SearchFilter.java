package com.njydsz.common.search.api;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索过滤条件
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "搜索过滤条件")
public class SearchFilter implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 字段名 */
  @Schema(description = "过滤字段名")
  private String field;

  /** 过滤值列表（OR 关系） */
  @Schema(description = "过滤值列表")
  private List<String> values;

  /** 过滤操作符 */
  @Schema(description = "过滤操作符")
  @Builder.Default
  private Operator operator = Operator.EQ;

  /** 过滤操作符 */
  public enum Operator {
    /** 等于 */
    EQ,
    /** 不等于 */
    NE,
    /** 在...范围内 */
    IN,
    /** 不在...范围内 */
    NOT_IN,
    /** 大于 */
    GT,
    /** 小于 */
    LT,
    /** 大于等于 */
    GTE,
    /** 小于等于 */
    LTE,
    /** 介于两者之间 */
    BETWEEN
  }
}
