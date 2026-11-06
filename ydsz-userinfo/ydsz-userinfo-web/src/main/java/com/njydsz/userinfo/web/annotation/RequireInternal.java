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
 * <p><b>已废弃（26.11.06）</b>：本注解已下沉至
 * {@link com.njydsz.common.safe.internal.RequireInternal}，由 common-safe 的
 * {@code RequireInternalAspect} 统一校验。新代码请直接使用公共注解；
 * 本注解保留仅为兼容存量引用，公共切面已同时识别两处注解。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @deprecated 使用 {@link com.njydsz.common.safe.internal.RequireInternal}
 * @see com.njydsz.common.safe.internal.RequireInternalAspect 内部调用校验切面（公共）
 */
@Deprecated(since = "26.11.06", forRemoval = true)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireInternal {}
