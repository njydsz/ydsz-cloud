package com.njydsz.pmis.common.auth.annotation;

import java.lang.annotation.*;

/**
 * 敏感操作二次认证注解（兼容旧 com.njydsz.pmis.common.annotation.RequireReAuth）。
 *
 * <p>标注在 Controller 方法上，表示该接口需要用户进行二次认证（如输入密码/验证码）
 * 后才能执行。拦截器会校验请求头中的 ReAuth-Token 是否有效。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @RequireReAuth(code = "USER_DELETE", name = "删除用户")
 * @PostMapping("/users/delete")
 * public Result delete(@RequestBody DeleteDTO dto) { ... }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireReAuth {

    /**
     * 二次认证类型，默认为 PASSWORD。
     * <p>可选值：PASSWORD / OTP / BIOMETRIC
     *
     * @return 认证类型
     */
    String type() default "PASSWORD";

    /**
     * 二次认证 Token 的最大有效时间（秒），默认 300 秒（5 分钟）。
     *
     * @return TTL 秒数
     */
    int ttlSeconds() default 300;

    /**
     * 操作编码，用于标识具体的敏感操作类型。
     * <p>例如：USER_DELETE / USER_RESET_PASSWORD / CONTRACT_DELETE 等。
     *
     * @return 操作编码
     */
    String code() default "";

    /**
     * 操作名称，用于前端展示和日志记录。
     *
     * @return 操作名称
     */
    String name() default "";
}
