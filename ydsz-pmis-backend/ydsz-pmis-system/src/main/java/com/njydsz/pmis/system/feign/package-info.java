/**
 * Feign 客户端层：声明对其他微服务（用户中心/消息服务）的 HTTP 调用接口与降级工厂。
 *
 * <p>本包使用 Spring Cloud OpenFeign 跨服务调用，结合 {@code fallbackFactory} 实现
 * 服务不可用时的本地降级，避免级联雪崩。所有 Feign 客户端均使用 {@code @FeignClient}
 * 显式声明服务名与降级策略，URL 路径与目标服务 Controller 保持一致。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code UserServiceClient} - 用户中心 Feign 客户端，调用 {@code /user/employee/{id}}
 *       获取员工基本信息（含 email/phone）</li>
 *   <li>{@code UserServiceClientFallback} - 用户服务降级实现，当用户中心不可用时返回 null
 *       并记录 WARN 日志，通知模块对此降级为"无邮箱即不发送邮件"</li>
 *   <li>{@code MessageServiceClient} - 消息服务 Feign 客户端，提供 {@code send} 与
 *       {@code channels} 两个端点；内置 {@code MessageFeignDTO} 简化调用方依赖</li>
 *   <li>{@code MessageServiceClientFallback} - 消息服务降级实现，发送失败返回包含错误信息的
 *       {@code Result}，便于调用方决定是否重试或降级</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>显式降级</b>：每个 FeignClient 必须配置 {@code fallbackFactory}，禁止使用 {@code fallback}
 *       （无法获取异常原因）</li>
 *   <li><b>Result 包装</b>：所有方法返回 {@code Result<T>}，由调用方根据 {@code code} 决定下一步动作</li>
 *   <li><b>超时与重试</b>：通过 {@code application.yml} 全局配置
 *       （{@code feign.client.config.default.connectTimeout/ readTimeout}），
 *       禁止在客户端硬编码</li>
 *   <li><b>日志可追踪</b>：Fallback 工厂内必须记录 WARN/ERROR 日志，便于排查下游故障</li>
 *   <li><b>DTO 最小化</b>：Feign DTO 仅暴露必要字段，避免目标服务表结构变更穿透</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 *   <ul>
 *     <li>新增 FeignClient 须显式声明 {@code name}（服务名）与 {@code fallbackFactory}（降级工厂）</li>
 *     <li>URL 路径前缀须与目标服务 {@code @RequestMapping} 完全一致</li>
 *     <li>降级实现须在 {@code package-info.java} 中登记，并保证线程安全（无状态）</li>
 *   </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.feign;
