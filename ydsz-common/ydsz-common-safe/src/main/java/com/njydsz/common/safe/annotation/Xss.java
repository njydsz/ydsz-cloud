package com.njydsz.common.safe.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import com.njydsz.common.safe.aspect.XssValidator;

/**
 * XSS 防护验证注解
 *
 * <p>用于验证字符串参数是否包含潜在的 XSS 攻击代码，如 HTML 标签、脚本标签等。 基于 Jakarta Validation 框架实现，可用于方法参数、字段、构造函数参数等场景。
 *
 * <p><b>威胁模型：</b>攻击者通过表单、URL 参数、JSON Body 注入 JavaScript / HTML 片段， 实现 cookie 窃取、钓鱼、UI 伪装等攻击。
 *
 * <p><b>实现原理：</b>
 *
 * <ul>
 *   <li>使用正则表达式匹配 HTML 标签模式 {@code <[^>]*>}
 *   <li>如果字符串包含任何 HTML 标签，则验证失败
 *   <li>与 XssFilter 的区别：此注解用于编程式校验，XssFilter 用于全局过滤
 * </ul>
 *
 * <p><b>误报控制：</b>严格匹配任意 HTML 标签，对于合法富文本场景（如 CMS）建议关闭或 使用白名单方案（OWASP Java HTML Sanitizer）。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 校验用户输入的用户名
 * @PostMapping("/update")
 * public Result updateUser(@Xss @RequestParam("username") String username) {
 *     return Result.ok(userService.updateUsername(username));
 * }
 *
 * // 校验实体对象中的字段
 * public class OrderDTO {
 *     @Xss(message = "订单备注包含非法内容")
 *     private String remark;
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see XssValidator
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(
    value = {ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
@Constraint(validatedBy = {XssValidator.class})
public @interface Xss {

  /**
   * 验证失败时的错误消息
   *
   * @return 错误消息，默认为 "不允许任何脚本运行"
   */
  String message() default "不允许任何脚本运行";

  /**
   * 验证分组
   *
   * <p>用于分组验证场景，可以在不同场景下启用不同的验证规则。
   *
   * @return 验证分组类数组
   */
  Class<?>[] groups() default {};

  /**
   * 有效载荷
   *
   * <p>用于携带额外的元数据信息给验证框架。
   *
   * @return 有效载荷类数组
   */
  Class<? extends Payload>[] payload() default {};
}
