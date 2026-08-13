package com.njydsz.common.exception.registry;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.common.exception.enums.ExceptionCategory;

/**
 * 标注模块错误码枚举类。
 *
 * <p>P2-4: 错误码注册中心 — 启动时扫描所有标注此注解的枚举类，
 * 注册到统一错误码表 {@link com.njydsz.common.exception.code.ErrorCodeTable}，供前端和运维查询。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @YdszExceptionCode(module = "userinfo")
 * public enum UserInfoExceptionCode implements ExceptionCode {
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
public @interface YdszExceptionCode {

    /**
     * 模块名称（如 "userinfo"、"system"、"project"）
     * @return 处理结果
     */
    String module();

    /**
     * 模块描述（中文显示名）
     */
    String description() default "";

    /**
     * 标记该模块错误码枚举是否已废弃。
     *
     * <p>设为 true 后，启动扫描注册时会输出 WARN 日志引导调用方迁移，
     * 已废弃枚举中的错误码仍正常注册（保证存量调用方兼容）。
     *
     * <p>配合 {@link #replacement} 指定替代方案。
     *
     * @return true-已废弃
     */
    boolean deprecated() default false;

    /**
     * 替代方案说明（如新枚举类名或使用方式）。
     *
     * <p>当 {@link #deprecated()} 为 true 时，WARN 日志中将包含此提示，
     * 引导调用方迁移到新版错误码。
     *
     * @return 替代方案描述
     */
    String replacement() default "";

    /**
     * 模块级别默认异常分类。
     *
     * <p>默认值 {@link ExceptionCategory#BUSINESS}，扫描注册时会将此值
     * 记录为该模块所有错误码的默认分类（各枚举常量仍可通过覆盖
     * {@code ExceptionCode.getCategory()} 提供更细粒度的分类）。
     *
     * <p>仅当模块下所有错误码均为同一分类时设置此值；
     * 混合分类的模块请保持默认 {@link ExceptionCategory#BUSINESS}，
     * 由各枚举常量自行通过 {@code getCategory()} 声明。
     *
     * @return 模块默认异常分类
     */
    ExceptionCategory category() default ExceptionCategory.BUSINESS;
}
