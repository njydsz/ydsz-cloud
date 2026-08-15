package com.njydsz.common.util.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 API 处于稳定阶段，承诺在后续版本中保持兼容性。
 *
 * <p>带有此注解的类、方法或字段已完成试用（{@link Experimental}），
 * 其签名和行为将被视为公共 API，遵循语义化版本规范进行演进。</p>
 *
 * <p>{@code since} 字段指示该 API 被标记为稳定的版本号，便于调用方判断最低兼容版本。</p>
 *
 * @author ydsz-team
 * @since 4.2.0
 * @see Experimental
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Stable {

    /**
     * API 被标记为稳定的版本号（如 {@code "4.2.0"}）。
     *
     * @return 版本号
     */
    String since();
}
