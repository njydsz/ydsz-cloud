/**
 * YDSZ Common Core — 平台基石模块（L1 基础设施层）。
 *
 * <p>本模块提供统一 API 响应封装、业务结果码定义、请求级上下文传播、多协议链路追踪、
 * 分页响应封装、全局常量、国际化消息资源、Spring Boot 自动配置等基础能力。</p>
 *
 * <h3>设计定位</h3>
 * <ul>
 *   <li><b>零业务语义</b>：仅承载跨切面技术能力，不泄漏认证、租户、审计等具体业务域代码</li>
 *   <li><b>类型安全</b>：上下文读写以 {@code ContextKey<T>} 为编译期担保入口</li>
 *   <li><b>可观测优先</b>：响应体默认埋入 traceId/spanId/requestId，链路可查</li>
 * </ul>
 *
 * <h3>模块依赖约束</h3>
 * <p>本模块依赖以下外部组件，接入方需确保 classpath 存在：</p>
 * <ul>
 *   <li><b>ydsz-common-json</b>（核心硬依赖）：{@code BaseResponse} / {@code IResponse} /
 *       {@code PageResult} 上使用的 {@code @JsonInclude}、{@code @JsonPropertyOrder}、
 *       {@code @JsonClass} 注解由 ydsz-common-json 引擎解析。若未引入该模块，
 *       序列化输出将不带字段白名单与空值控制。</li>
 *   <li><b>transmittable-thread-local</b>（TTL）：线程池场景的上下文传递</li>
 *   <li><b>Lombok</b>（provided 范围）：编译期代码生成</li>
 *   <li><b>slf4j-api</b>：MDC 操作</li>
 * </ul>
 *
 * <h3>编码规范</h3>
 * <ul>
 *   <li>上下文写入推荐使用 {@code ContextKey<T>} 替代字符串键
 *       （{@code RequestContext.put(String, Object)} 已标记 {@code @Deprecated}）</li>
 *   <li>结果码扩展：业务模块自定义错误码应实现 {@code ResultCode} 接口，不在 core 模块中直接修改</li>
 *   <li>响应构建：通用场景使用 {@code BaseResponse.success(data)}；分页场景使用 {@code PageResult}；
 *       带可观测字段使用 {@code Results.okWithObservability(data, requestId, spanId)}</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.njydsz.common.core;
