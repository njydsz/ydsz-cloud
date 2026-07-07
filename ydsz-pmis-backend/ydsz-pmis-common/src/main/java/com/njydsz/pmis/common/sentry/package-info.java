/**
 * Sentry 异常监控层。
 *
 * <p>集成 Sentry SDK，实现全平台异常自动捕获、面包屑追踪、性能监控。
 * 通过 {@code @SentryCapture} 注解 + AOP 切面，简化业务侧接入。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.sentry.SentryProperties}    - Sentry 配置（DSN / 环境 / 采样率）</li>
 *   <li>{@link com.njydsz.pmis.common.sentry.SentryConfig}         - Sentry 客户端配置（启动时按环境初始化）</li>
 *   <li>{@link com.njydsz.pmis.common.sentry.SentryCapture}        - 注解（业务方法级异常捕获）</li>
 *   <li>{@link com.njydsz.pmis.common.sentry.SentryCaptureAspect}  - AOP 切面（拦截注解方法，捕获异常上报）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>开发 / SIT / UAT 环境开启，生产环境可按需开启（避免敏感数据上报）</li>
 *   <li>业务异常（{@code BizException}）不上报 Sentry（仅 WARN 日志）</li>
 *   <li>采样率按环境隔离：开发 100% / 生产 10%</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.sentry;
