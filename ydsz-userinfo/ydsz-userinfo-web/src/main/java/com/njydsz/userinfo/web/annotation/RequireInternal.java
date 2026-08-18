package com.njydsz.userinfo.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 内部接口调用标记注解（P0-6）。
 *
 * <p>标注在 {@code /api/internal/**} 的 Controller 类或方法上，表示该端点仅供其他后端服务
 * 通过 Feign 内部调用，不对前端/公网暴露。由 {@code RequireInternalAspect} 在方法执行前
 * 校验请求头 {@code X-Internal-Call: true}（服务端二次校验，作为网关白名单之外的最后一道防线）。
 *
 * <p><b>启用开关：</b>{@code ydsz.userinfo.internal-call.enabled=true} 时校验生效；
 * 默认关闭以兼容存量 Feign 客户端，各业务模块统一注入请求头后可开启。
 *
 * @author ydsz-team
 * @since 2.21.0
 * @see com.njydsz.userinfo.web.aspect.RequireInternalAspect 内部调用校验切面
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireInternal {}
