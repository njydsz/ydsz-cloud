package com.njydsz.pmis.common.sentry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要 Sentry 上报的异常切点
 *
 * 用法:
 *   <pre>
 *   {@code
 *   @SentryCapture(module = "execution", bizType = "invoice:create")
 *   public InvoiceVO create(InvoiceCreateDTO dto) { ... }
 *   }
 *   </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SentryCapture {
    /** 业务模块, 如 execution / project / finance */
    String module() default "";

    /** 业务类型, 如 invoice:create / contract:sign */
    String bizType() default "";

    /** 错误等级: error / warning / info / fatal */
    String level() default "error";
}
