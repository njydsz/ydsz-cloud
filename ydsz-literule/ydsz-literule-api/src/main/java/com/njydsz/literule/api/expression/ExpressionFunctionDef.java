package com.njydsz.literule.api.expression;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表达式函数定义（P1-7 函数市场）
 *
 * <p>用于向前端暴露注册函数列表，支持：
 *
 * <ul>
 *   <li>name — 函数名（用于补全匹配）
 *   <li>signature — 函数签名（用于显示和补全）
 *   <li>description — 函数说明（用于 hover tooltip）
 *   <li>sample — 示例代码（用于模板片段）
 *   <li>category — 函数分类（用于前端分组）
 *   <li>supportedEngines — 适用的表达式引擎（2.1.0 起仅 liteexpr/all）
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpressionFunctionDef implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 函数名 */
  private String name;

  /** 函数签名 */
  private String signature;

  /** 函数说明 */
  private String description;

  /** 示例代码 */
  private String sample;

  /** 函数分类 */
  private String category;

  /** 适用引擎：liteexpr / all（2.1.0 起仅保留 LiteExpr） */
  private String supportedEngines;

  /**
   * 返回内置函数市场默认函数清单。
   *
   * <p>汇集表达式引擎开箱即用的内置函数（字符串、类型转换、逻辑、日期、数学、类型判断等）， 供前端函数补全、签名展示与 hover 说明使用。2.1.0 起所有函数统一适用 LiteExpr
   * 引擎 （{@code supportedEngines="all"} 仅表示全量内置、不区分引擎）。
   *
   * @return 默认内置函数定义列表（不可变）
   */
  public static List<ExpressionFunctionDef> defaults() {
    return List.of(
        of(
            "concat",
            "concat(str, ...)",
            "字符串拼接，支持多个参数",
            "concat(\"hello\", \" \", \"world\")",
            "string",
            "all"),
        of("length", "length(str)", "字符串长度", "length(\"abc\") == 3", "string", "all"),
        of("upper", "upper(str)", "转大写", "upper(\"hello\")", "string", "all"),
        of("lower", "lower(str)", "转小写", "lower(\"WORLD\")", "string", "all"),
        of(
            "contains",
            "contains(str, sub)",
            "是否包含子串",
            "contains(\"hello\", \"ell\")",
            "string",
            "all"),
        of(
            "startsWith",
            "startsWith(str, prefix)",
            "是否以 prefix 开头",
            "startsWith(url, \"https\")",
            "string",
            "all"),
        of(
            "endsWith",
            "endsWith(str, suffix)",
            "是否以 suffix 结尾",
            "endsWith(file, \".pdf\")",
            "string",
            "all"),
        of("isNull", "isNull(v)", "判断值是否为 null", "isNull(amount)", "type", "all"),
        of("isNotNull", "isNotNull(v)", "判断值是否非 null", "isNotNull(amount)", "type", "all"),
        of("toNumber", "toNumber(s)", "字符串转数值", "toNumber(price)", "convert", "all"),
        of("toString", "toString(n)", "数值转字符串", "toString(amount)", "convert", "all"),
        of("if", "if(cond, a, b)", "三元表达式", "if(amount > 100, 1, 0)", "logic", "all"),
        of("now", "now()", "当前时间", "now()", "datetime", "all"),
        of(
            "dateFormat",
            "dateFormat(d, fmt)",
            "日期格式化",
            "dateFormat(now(), \"yyyy-MM-dd\")",
            "datetime",
            "all"),
        of("abs", "abs(n)", "绝对值", "abs(amount - 1000)", "math", "all"),
        of("max", "max(a, b, ...)", "最大值", "max(a, b, c)", "math", "all"),
        of("min", "min(a, b, ...)", "最小值", "min(a, b, c)", "math", "all"),
        of("round", "round(n, scale)", "四舍五入", "round(3.14159, 2)", "math", "all"));
  }

  private static ExpressionFunctionDef of(
      String name,
      String signature,
      String description,
      String sample,
      String category,
      String engines) {
    return new ExpressionFunctionDef(name, signature, description, sample, category, engines);
  }
}
