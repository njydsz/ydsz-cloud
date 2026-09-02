package com.njydsz.literule.domain.vo;

import lombok.Data;

/**
 * 表达式函数定义视图对象（VO）。
 *
 * <p>用于前端展示规则表达式中可调用内置函数的元信息（名称、签名、示例等）， 辅助业务人员编写表达式并做语法提示。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class ExpressionFunctionDefVO {

  /** 函数名称（表达式中实际调用的名字，如 add） */
  private String name;

  /** 函数签名（形式参数声明，如 add(a, b)） */
  private String signature;

  /** 函数功能说明（中文描述） */
  private String description;

  /** 调用示例（如 add(1, 2)） */
  private String sample;

  /** 函数分类（如 MATH/STRING/DATE，用于分组展示） */
  private String category;

  /** 支持的表达式引擎（逗号分隔，如 LiteExpr/MVEL） */
  private String supportedEngines;
}
