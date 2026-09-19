package com.njydsz.common.util.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 API 处于试用（实验）阶段。
 *
 * <p>带有此注解的类、方法或字段表示其签名和行为可能在后续版本中发生不兼容变更， 不建议在生产代码中依赖此类 API。试用期结束后将转为稳定版本或移除。
 *
 * <p>本模块借用 Spring Boot / Guava 的 API 成熟度分级思路：
 *
 * <ul>
 *   <li>{@link Experimental} — 试用中，随时可能变更或移除
 *   <li>稳定版本，承诺兼容性
 * </ul>
 *
 * <p><b>90 天试用期规则：</b>自 {@link #since()} 标注的版本日起，若 90 天内无消费方稳定调用， 该 API 将被移入 sandbox 模块或移除。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Experimental {

  /**
   * 试用 API 的说明，例如实验目的或预期稳定时间。
   *
   * @return 说明文本
   */
  String value() default "";

  /**
   * 起始版本号（格式 YY.MM.DD），用于 90 天试用期计算。
   *
   * <p>默认 {@code "0.0.0.0"} 表示未指定，不纳入试用期管理。
   *
   * @return 起始版本号字符串
   * @since 26.09.19
   */
  String since() default "0.0.0.0";
}
