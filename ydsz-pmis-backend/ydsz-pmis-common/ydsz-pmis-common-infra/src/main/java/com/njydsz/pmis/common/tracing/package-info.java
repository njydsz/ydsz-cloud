/**
 * 链路追踪层。
 *
 * <p>封装 Brave / Micrometer Tracing 的桥接逻辑，提供当前 traceId / spanId 的获取。
 * 业务侧无需直接依赖 Brave API，统一通过 {@link com.njydsz.pmis.common.util.TraceIdUtil} 访问。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.tracing.TracerHolder} - Brave {@code Tracer} 持有者（懒加载）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>业务侧禁止直接 {@code @Autowired} Brave {@code Tracer}，必须通过 {@code TracerHolder} /
 *       {@code TraceIdUtil} 访问，便于后续替换底层实现</li>
 *   <li>{@code @Async} 异步方法在子线程需重新通过 {@code TracerHolder.wrap()} 包裹，
 *       保证 traceId 跨线程传递</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.tracing;
