package com.njydsz.common.safe.aspect;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.njydsz.common.safe.annotation.Xss;
import com.njydsz.common.util.string.StringUtils;

/**
 * XSS 防护验证器
 *
 * <p>实现 Jakarta Validation 框架的 ConstraintValidator 接口， 用于验证字符串参数是否包含潜在的 XSS 攻击代码。
 *
 * <p><b>检测能力：</b>
 *
 * <ul>
 *   <li>HTML 标签检测：script、iframe、object、embed、applet 等
 *   <li>危险协议检测：javascript:, vbscript:, data:, mailto: 等
 *   <li>事件处理器检测：onload, onerror, onclick 等所有 on* 事件
 *   <li>危险函数检测：eval, alert, prompt, confirm, expression 等
 *   <li>DOM 对象检测：document, window, navigator, cookie 等
 *   <li>编码绕过检测：HTML 实体编码、Unicode 编码、URL 编码等
 * </ul>
 *
 * <p><b>与 XssFilter 的区别：</b>
 *
 * <ul>
 *   <li>XssValidator：用于编程式参数校验，基于 Jakarta Validation
 *   <li>XssFilter：用于全局请求过滤，基于 Servlet Filter
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see Xss
 */
public class XssValidator implements ConstraintValidator<Xss, String> {

  private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

  private static final Pattern SCRIPT_END_PATTERN =
      Pattern.compile("</script", Pattern.CASE_INSENSITIVE);
  private static final Pattern SCRIPT_START_PATTERN =
      Pattern.compile("<script", Pattern.CASE_INSENSITIVE);
  private static final Pattern IFRAME_PATTERN =
      Pattern.compile("<iframe|<frame|<object|<embed|<applet", Pattern.CASE_INSENSITIVE);
  private static final Pattern META_TAG_PATTERN =
      Pattern.compile("<meta|<link|<base|<area", Pattern.CASE_INSENSITIVE);
  private static final Pattern FORM_TAG_PATTERN =
      Pattern.compile("<form|<button|<textarea|<select|<option|<input", Pattern.CASE_INSENSITIVE);
  private static final Pattern STYLE_TAG_PATTERN =
      Pattern.compile("<style", Pattern.CASE_INSENSITIVE);

  private static final Pattern JAVASCRIPT_PROTOCOL_PATTERN =
      Pattern.compile(
          "javascript\\s*:|vbscript\\s*:|data\\s*:|livescript\\s*:|mocha\\s*:|feed\\s*:|webcast\\s*:",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern DANGEROUS_EVENT_PATTERN =
      Pattern.compile(
          "on(load|error|click|focus|blur|change|submit|reset|select|abort|drag|drop|"
              + "key|keydown|keyup|keypress|mouse|mousedown|mouseenter|mouseleave|"
              + "mousemove|mouseout|mouseover|mouseup|wheel|scroll|input|invalid|"
              + "propertychange|readystatechange|resize|beforeunload|unload)\\s*=",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern DANGEROUS_FUNCTION_PATTERN =
      Pattern.compile(
          "\\b(eval|alert|prompt|confirm|setTimeout|setInterval|setImmediate|execScript|exec|compile)\\s*\\(",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern EXPRESSION_PATTERN =
      Pattern.compile("expression\\s*\\(|url\\s*\\(|behavior\\s*:", Pattern.CASE_INSENSITIVE);
  private static final Pattern DOM_OBJECT_PATTERN =
      Pattern.compile(
          "\\b(document|window|navigator|history|location|localStorage|sessionStorage|"
              + "cookie|crypto|subtle|indexedDB)\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SVG_XML_PATTERN =
      Pattern.compile("<svg|<xml|<math|<xlink", Pattern.CASE_INSENSITIVE);
  private static final Pattern COMMENT_PATTERN =
      Pattern.compile("<!--|-->|<!\\[CDATA\\[|\\]\\]>", Pattern.CASE_INSENSITIVE);

  private static final Pattern HTML_ENTITY_PATTERN =
      Pattern.compile(
          "&#x?[0-9a-f]{1,8};?|&#\\d{1,8};?|&(?:lt|gt|amp|quot|apos|#\\d+|#x[0-9a-f]+);?",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern ENCODED_SCRIPT_PATTERN =
      Pattern.compile(
          "(?:%[0-9a-f]{2}|\\\\x[0-9a-f]{2}|\\\\u[0-9a-f]{4})+", Pattern.CASE_INSENSITIVE);

  /** 安全加固：检测 URL 编码形式的 XSS（如 %3Cscript%3E、%3cscript%3e） */
  private static final Pattern URL_ENCODED_XSS_PATTERN =
      Pattern.compile(
          "%3[cC](?:script|iframe|object|embed|form|input|img|svg|body|link|meta|base|area|applet|style|xml|math)",
          Pattern.CASE_INSENSITIVE);

  /** 安全加固：检测十六进制 HTML 实体（如 &#x3c;、&#x3C;） */
  private static final Pattern HEX_ENTITY_XSS_PATTERN =
      Pattern.compile("&#x0*3[cC];?", Pattern.CASE_INSENSITIVE);

  /** 安全加固：检测十进制 HTML 实体（如 &#60;、&#060;、&#0060;） */
  private static final Pattern DECIMAL_ENTITY_XSS_PATTERN =
      Pattern.compile("&#0*60;?", Pattern.CASE_INSENSITIVE);

  @Override
  public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
    if (StringUtils.isBlank(value)) {
      return true;
    }
    return !containsXss(value);
  }

  /**
   * 检查字符串是否包含 XSS 攻击代码
   *
   * <p>综合检测多种 XSS 攻击模式：
   *
   * <ul>
   *   <li>HTML 标签
   *   <li>危险协议
   *   <li>事件处理器
   *   <li>危险函数
   *   <li>DOM 对象
   *   <li>编码绕过
   * </ul>
   *
   * @param value 待检查的字符串
   * @return 是否包含 XSS 攻击代码
   */
  public static boolean containsXss(String value) {
    if (StringUtils.isBlank(value)) {
      return false;
    }

    String lowerValue = value.toLowerCase();

    if (containsHtmlTag(lowerValue)) {
      return true;
    }

    if (containsDangerousProtocol(lowerValue)) {
      return true;
    }

    if (containsDangerousEvent(lowerValue)) {
      return true;
    }

    if (containsDangerousFunction(lowerValue)) {
      return true;
    }

    if (containsExpression(lowerValue)) {
      return true;
    }

    if (containsDomObject(lowerValue)) {
      return true;
    }

    if (containsEncodedAttack(lowerValue)) {
      return true;
    }

    return false;
  }

  private static boolean containsHtmlTag(String value) {
    return SCRIPT_START_PATTERN.matcher(value).find()
        || SCRIPT_END_PATTERN.matcher(value).find()
        || IFRAME_PATTERN.matcher(value).find()
        || META_TAG_PATTERN.matcher(value).find()
        || FORM_TAG_PATTERN.matcher(value).find()
        || STYLE_TAG_PATTERN.matcher(value).find()
        || SVG_XML_PATTERN.matcher(value).find()
        || COMMENT_PATTERN.matcher(value).find();
  }

  private static boolean containsDangerousProtocol(String value) {
    return JAVASCRIPT_PROTOCOL_PATTERN.matcher(value).find();
  }

  private static boolean containsDangerousEvent(String value) {
    return DANGEROUS_EVENT_PATTERN.matcher(value).find();
  }

  private static boolean containsDangerousFunction(String value) {
    return DANGEROUS_FUNCTION_PATTERN.matcher(value).find();
  }

  private static boolean containsExpression(String value) {
    return EXPRESSION_PATTERN.matcher(value).find();
  }

  private static boolean containsDomObject(String value) {
    return DOM_OBJECT_PATTERN.matcher(value).find();
  }

  private static boolean containsEncodedAttack(String value) {
    return HTML_ENTITY_PATTERN.matcher(value).find()
        || ENCODED_SCRIPT_PATTERN.matcher(value).find()
        || URL_ENCODED_XSS_PATTERN.matcher(value).find()
        || HEX_ENTITY_XSS_PATTERN.matcher(value).find()
        || DECIMAL_ENTITY_XSS_PATTERN.matcher(value).find();
  }

  /**
   * 检查字符串是否包含 HTML 标签
   *
   * @param value 待检查的字符串
   * @return 是否包含 HTML 标签
   */
  public static boolean containsHtml(String value) {
    if (StringUtils.isBlank(value)) {
      return false;
    }
    return HTML_TAG_PATTERN.matcher(value).find();
  }
}
