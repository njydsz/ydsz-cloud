package com.njydsz.agent.infra.trace;

import io.opentelemetry.api.OpenTelemetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.agent.domain.trace.AgentSpanExporter;

/**
 * OpenTelemetry 自动配置。
 *
 * <p>仅当以下三个条件全部满足时激活：
 * <ol>
 *   <li>类路径存在 {@code io.opentelemetry.api.OpenTelemetry}（OTel SDK 已引入）</li>
 *   <li>类路径存在 {@code io.opentelemetry.api.trace.Tracer}（OTel API 可用）</li>
 *   <li>配置项 {@code ydsz.agent.otel.enabled=true}</li>
 * </ol>
 *
 * <p>当未引入 OTel SDK 或显式关闭 OTel 时，{@code NoopAgentSpanExporter} 作为默认 Bean 生效，
 * 链路记录器仍可写入 PG 表，只是不向外部 OTel Collector 推送。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(name = "io.opentelemetry.api.OpenTelemetry")
public class AgentOtelAutoConfiguration {

  /**
   * 注册 OTel Span 导出器。
   *
   * <p>要求 Spring 容器中存在 {@link OpenTelemetry} Bean（通常由 Micrometer/Actuator 自动装配提供）。
   * 自动配置类的 OTel SDK 依赖标记为 optional，类路径上无 OTel 时本配置不激活。
   *
   * @param openTelemetry OpenTelemetry 实例
   * @return OTel Span 导出器
   */
  @Bean
  @ConditionalOnMissingBean(AgentSpanExporter.class)
  @ConditionalOnProperty(
      prefix = "ydsz.agent.otel",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public AgentSpanExporter otelAgentSpanExporter(OpenTelemetry openTelemetry) {
    log.info("[AgentOtel] 已注册 OpenTelemetry Span 导出器");
    return new OtelAgentSpanExporter(openTelemetry);
  }

  /**
   * 无 OTel SDK 时的兜底空操作导出器。
   *
   * <p>当 OTel SDK 不在类路径上、或 OTel 禁用时注册此 Bean。
   *
   * @return 空操作导出器
   */
  @Bean
  @ConditionalOnMissingBean(AgentSpanExporter.class)
  public AgentSpanExporter noopAgentSpanExporter() {
    log.info("[AgentOtel] OpenTelemetry 未启用，使用空操作 Span 导出器");
    return NoopAgentSpanExporter.getInstance();
  }
}
