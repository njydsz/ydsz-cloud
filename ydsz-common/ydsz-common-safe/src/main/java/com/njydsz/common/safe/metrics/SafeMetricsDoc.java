package com.njydsz.common.safe.metrics;

/**
 * 安全模块度量标准（Metrics Standards）
 *
 * <p>定义安全模块的 SLI（Service Level Indicator）、SLO（Service Level Objective）和错误预算， 用于量化安全防护能力的有效性和可靠性。
 *
 * <h2>指标分类</h2>
 *
 * <h3>1. 有效性指标（Effectiveness）</h3>
 *
 * <table>
 *   <tr><th>指标</th><th>PromQL</th><th>SLO 目标</th><th>说明</th></tr>
 *   <tr><td>XSS 拦截率</td><td>sum(safe_xss_attacks_total) / sum(http_requests_total)</td><td>&gt; 99%</td><td>攻击检测有效性</td></tr>
 *   <tr><td>CSRF 拦截率</td><td>sum(safe_csrf_failures_total) / sum(http_requests_total)</td><td>&gt; 99%</td><td>跨站请求伪造检测有效性</td></tr>
 *   <tr><td>限流准确率</td><td>sum(safe_rate_limit_triggered_total) / sum(safe_rate_limit_checked_total)</td><td>&gt; 95%</td><td>限流触发准确性</td></tr>
 * </table>
 *
 * <h3>2. 性能指标（Performance）</h3>
 *
 * <table>
 *   <tr><th>指标</th><th>PromQL</th><th>SLO 目标</th><th>说明</th></tr>
 *   <tr><td>过滤器 P99 延迟</td><td>histogram_quantile(0.99, rate(safe_filter_duration_seconds_bucket[5m]))</td><td>&lt; 5ms</td><td>安全过滤器处理延迟</td></tr>
 *   <tr><td>过滤器 P50 延迟</td><td>histogram_quantile(0.50, rate(safe_filter_duration_seconds_bucket[5m]))</td><td>&lt; 1ms</td><td>安全过滤器处理延迟（中位数）</td></tr>
 * </table>
 *
 * <h3>3. 可靠性指标（Reliability）</h3>
 *
 * <table>
 *   <tr><th>指标</th><th>PromQL</th><th>SLO 目标</th><th>说明</th></tr>
 *   <tr><td>安全模块可用率</td><td>1 - sum(safe_filter_errors_total) / sum(safe_filter_invocations_total)</td><td>&gt; 99.9%</td><td>模块整体可用性</td></tr>
 *   <tr><td>降级触发次数</td><td>sum(safe_fallback_triggered_total)</td><td>&lt; 10/min</td><td>降级为本地限流的频率</td></tr>
 * </table>
 *
 * <h2>告警规则（Alerting Rules）</h2>
 *
 * <pre>{@code
 * # XSS 攻击突增告警
 * - alert: XSSAttackSpike
 *   expr: rate(safe_xss_attacks_total[5m]) > 100
 *   for: 2m
 *   labels:
 *     severity: warning
 *   annotations:
 *     summary: "XSS 攻击频率突增"
 *
 * # 安全过滤器延迟过高
 * - alert: SafeFilterHighLatency
 *   expr: histogram_quantile(0.99, rate(safe_filter_duration_seconds_bucket[5m])) > 0.01
 *   for: 5m
 *   labels:
 *     severity: warning
 *   annotations:
 *     summary: "安全过滤器 P99 延迟超过 10ms"
 *
 * # 限流误触发告警
 * - alert: RateLimitFalsePositive
 *   expr: rate(safe_rate_limit_triggered_total[5m]) > 1000
 *   for: 3m
 *   labels:
 *     severity: critical
 *   annotations:
 *     summary: "限流触发频率异常，可能存在误触发"
 * }</pre>
 *
 * <h2>配置热更新支持</h2>
 *
 * <p>安全模块支持通过 Spring Cloud Context 的 {@code /actuator/refresh} 端点进行配置热更新：
 *
 * <ul>
 *   <li>{@code ydsz.safe.xss.enabled} - XSS 开关（热更新）
 *   <li>{@code ydsz.safe.csrf.enabled} - CSRF 开关（热更新）
 *   <li>{@code ydsz.safe.ratelimit.enabled} - 限流开关（热更新）
 *   <li>{@code ydsz.safe.ip-access.enabled} - IP 访问控制开关（热更新）
 * </ul>
 *
 * <p><b>注意：</b>热更新需要 {@code spring-cloud-context} 依赖和 {@code @RefreshScope} 注解。
 * 安全过滤器链的顺序调整需要重启应用生效。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SafeMetrics
 */
public final class SafeMetricsDoc {

  private SafeMetricsDoc() {
    // 纯文档类，不可实例化
  }
}
