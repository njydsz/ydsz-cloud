package com.njydsz.common.util.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 API 处于试用（实验）阶段。
 *
 * <p>带有此注解的类、方法或字段表示其签名和行为可能在后续版本中发生不兼容变更，
 * 不建议在生产代码中依赖此类 API。试用期结束后将转为稳定版本或移除。</p>
 *
 * <p>本模块借用 Spring Boot / Guava 的 API 成熟度分级思路：</p>
 * <ul>
 *   <li>{@link Experimental} — 试用中，随时可能变更或移除</li>
 *   <li>稳定版本，承诺兼容性</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 4.2.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Experimental {

    /**
     * 试用 API 的说明，例如实验目的或预期稳定时间。
     *
     * @return 说明文本
     */
    String value() default "";
}
