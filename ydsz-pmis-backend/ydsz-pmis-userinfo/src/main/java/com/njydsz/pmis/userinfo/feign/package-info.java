/**
 * userinfo 模块 Feign 远程调用包。
 *
 * <p>封装 userinfo 微服务对外暴露的 Feign 客户端接口与降级（Fallback）实现，供 auth、workflow
 * 等上游服务按需调用。所有 Feign 路径统一以 {@code /feign/} 为前缀，并通过网关与 Feign 拦截器
 * 限制为内部调用，避免对外暴露。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>UserAuthClient - 供 auth 服务远程加载登录上下文的 Feign 客户端，提供按用户名/用户ID
 *       两种查询入口，依赖 {@code fallbackFactory} 实现服务降级。</li>
 *   <li>UserAuthClientFallback - UserAuthClient 的降级工厂，当 userinfo 不可用时统一返回
 *       {@code SERVICE_UNAVAILABLE} 业务错误码，避免上游雪崩。</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>粒度最小：Feign 接口方法应单一职责，按业务域拆分子接口，避免一个巨型客户端。</li>
 *   <li>降级必选：所有 Feign 客户端必须指定 {@code fallbackFactory}，不可省略，避免上游阻塞。</li>
 *   <li>路径语义清晰：Feign 端点路径以 {@code /feign/<domain>/<action>} 形式组织，区别于对外 REST 接口。</li>
 *   <li>DTO 隔离：Feign 客户端方法签名使用 {@code dto} 包内的轻量 DTO，不暴露内部实体。</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增 Feign 客户端需在本包定义接口 + Fallback 两个类，并在调用方模块的启动类上启用
 *       {@code @EnableFeignClients(basePackages = "com.njydsz.pmis.userinfo.feign")}。</li>
 *   <li>降级实现仅做兜底返回，不进行任何业务处理；调用方需结合降级响应做业务补偿。</li>
 *   <li>Feign 接口禁止做权限校验（依赖网关层统一鉴权），但应保证传输载荷最小化。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.userinfo.feign;
