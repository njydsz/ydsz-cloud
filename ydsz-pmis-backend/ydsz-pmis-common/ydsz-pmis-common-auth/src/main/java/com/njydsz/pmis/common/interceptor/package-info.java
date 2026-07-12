/**
 * Web 拦截器层。
 *
 * <p>与 {@code com.njydsz.pmis.common.filter}（基于 Servlet 规范）不同，
 * 拦截器基于 Spring MVC {@code HandlerInterceptor}，能访问 {@code HandlerMethod}
 * 和 Spring 上下文，适用于需要"针对具体 Controller / 方法"的场景。
 *
 * <h3>拦截器清单</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.interceptor.AuthInterceptor} - 鉴权拦截器（校验网关透传的用户头 / 内部头签名）</li>
 * </ul>
 *
 * <h3>与 AOP 切面的边界</h3>
 * <ul>
 *   <li>拦截器：URL 维度的拦截（{@code /api/**}），无 Controller 方法注解感知</li>
 *   <li>AOP 切面：方法维度的拦截（{@code @PrePermission}），能感知方法参数 / 返回值</li>
 *   <li>两类能力按需选用，不重复实现</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.interceptor;
