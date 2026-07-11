package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等豁免注解
 *
 * <p>用于标注明确不需要幂等防护的写接口（如纯查询语义接口、认证/会话/2FA、审计清理、定时触发等）。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentExempt {

    /**
     * 豁免原因说明
     */
    String value() default "";
}
