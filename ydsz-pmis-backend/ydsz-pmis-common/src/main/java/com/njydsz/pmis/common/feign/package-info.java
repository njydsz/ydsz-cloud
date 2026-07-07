/**
 * 跨服务 Feign 客户端层。
 *
 * <p>所有业务模块对其他微服务的调用统一通过本包中的 Feign 客户端。
 * 为避免级联故障，每个 Feign 客户端都配套了 {@code FallbackFactory}（按业务模块粒度拆分），
 * 当被调方不可用时返回降级响应而非抛错。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>Feign 客户端命名：{@code <业务>FeignClient}（如 {@code InitiationFeignClient}）</li>
 *   <li>降级工厂命名：{@code <业务>FeignClientFallbackFactory}（必须用 {@code FallbackFactory}，
 *       保留异常堆栈便于排查）</li>
 *   <li>所有客户端统一通过 {@code PmisFeignInterceptor} 注入内部头签名（{@code X-Internal-Sig}），
 *       拦截绕过网关伪造内部头</li>
 *   <li>日志通过 {@code PmisFeignLogger} 统一输出请求 / 响应 / TraceId</li>
 *   <li>DTO 单独放在 {@code feign.dto} 子包，与 Feign 客户端解耦</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.feign;
