package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * <p>用于 Controller 方法上，由 OperationLogAspect 拦截，自动记录操作日志。
 *
 * <p>用法：
 * <pre>
 *   {@code @OperationLog(module = "用户管理", action = "创建用户", bizType = "USER")}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 模块名 */
    String module() default "";

    /** 操作 */
    String action() default "";

    /** 业务类型 */
    String bizType() default "";

    /** 是否保存请求参数 */
    boolean saveParams() default true;

    /** 是否保存响应结果 */
    boolean saveResult() default false;

    /** 排除保存的字段（脱敏） */
    String[] excludeFields() default {"password", "oldPassword", "newPassword"};
}
