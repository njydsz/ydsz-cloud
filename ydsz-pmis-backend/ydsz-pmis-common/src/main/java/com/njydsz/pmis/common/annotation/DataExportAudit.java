package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据导出审计注解
 *
 * <p>标注在导出 Controller 方法上，由 DataExportAuditAspect 拦截并发布审计事件。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataExportAudit {

    /** 导出模块 */
    String module();

    /** 导出动作 */
    String action();

    /** 业务类型 */
    String bizType() default "";
}
