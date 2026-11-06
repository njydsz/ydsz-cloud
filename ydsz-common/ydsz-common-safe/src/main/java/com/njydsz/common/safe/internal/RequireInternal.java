package com.njydsz.common.safe.internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 内部接口调用标记注解（公共版，P0-2 下沉）。
 *
 * <p>标注在 {@code /internal/**} 的 Controller 类或方法上，表示该端点仅供其他后端服务
 * 通过 Feign 内部调用，不对前端/公网暴露。由 {@link RequireInternalAspect} 在方法执行前
 * 校验内部调用凭据，作为网关路由之外的服务端最后一道防线。
 *
 * <p><b>下沉说明（26.11.06）</b>：本注解原仅存在于 ydsz-userinfo-web，其余 6 个模块的
 * 内部 Controller 均无此保护。为收敛重复实现、统一安全语义，下沉至 common-safe，
 * 各业务模块直接复用；userinfo 的本地注解保留为 {@code @Deprecated} 别名以兼容存量代码。
 *
 * <p><b>启用开关</b>：{@code ydsz.internal-call.enabled=true} 时校验生效。
 * 默认关闭以兼容存量 Feign 客户端；各模块统一注入请求头/签名后再逐模块开启。
 *
 * <p><b>校验优先级</b>：签名校验（{@code ydsz.security.internal-sign.enabled=true}）
 * 通过后直接放行；未启用签名时回退到内部调用标记头校验。
 *
 * @author ydsz-team
 * @since 26.11.06
 * @see RequireInternalAspect 内部调用校验切面
 * @see InternalCallProperties 内部调用配置
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireInternal {}
