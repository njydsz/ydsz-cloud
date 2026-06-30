package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感操作二次认证注解
 *
 * <p>标注在敏感 Controller 方法上：
 * <ul>
 *   <li>需要请求头携带 {@code X-Re-Auth-Token}</li>
 *   <li>token 在指定时间窗口（默认 5 分钟）内有效</li>
 *   <li>由 SensitiveOperationAspect 拦截校验</li>
 * </ul>
 *
 * <p>典型场景：删除项目、调整合同金额、批量改密、薪酬变更等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireReAuth {

    /** 操作码 */
    String code();

    /** 操作名 */
    String name();

    /** token 有效时间（秒），默认 300 = 5 分钟 */
    int ttlSeconds() default 300;
}
