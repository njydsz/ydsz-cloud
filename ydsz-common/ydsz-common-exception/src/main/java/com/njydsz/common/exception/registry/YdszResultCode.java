package com.njydsz.common.exception.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注模块错误码枚举类。
 *
 * <p>P2-4: 错误码注册中心 — 启动时扫描所有标注此注解的枚举类，
 * 注册到统一错误码表 {@link com.njydsz.common.exception.code.ErrorCodeTable}，供前端和运维查询。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @YdszResultCode(module = "userinfo")
 * public enum UserInfoResultCode implements IResultCode {
 *     USER_NOT_FOUND(2001, "用户不存在"),
 *     // ...
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface YdszResultCode {

    /**
     * 模块名称（如 "userinfo"、"system"、"project"）
     */
    String module();

    /**
     * 模块描述（中文显示名）
     */
    String description() default "";
}
