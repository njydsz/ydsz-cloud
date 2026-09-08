package com.njydsz.common.sentry.telemetry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenTelemetry 集成配置属性。
 *
 * <p>配置前缀：{@code ydsz.sentry.otel}
 *
 * <p>通过 {@code ydsz.sentry.otel.enabled=true} 激活 OTel SDK 自动装配， 构建 SdkTracerProvider
 * 并导出到 OTel Collector。配合 {@link OtelAutoConfiguration} 使用。
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   sentry:
 *     otel:
 *       enabled: true
 *       service-name: ydsz-order
 *       endpoint: http://otel-collector:4317
 *       protocol: grpc
 *       sampler-ratio: 0.1
 *       propagation-format: w3c
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.sentry.otel")
public class OTelProperties {

  /** 是否启用 OTel SDK 自动装配 */
  private boolean isEnabled = false;

  /** 服务名（默认取 spring.application.name） */
  private String serviceName = "${spring.application.name}";

  /** OTLP 导出端点（gRPC 默认 4317，HTTP 默认 4318） */
  private String endpoint = "http://localhost:4317";

  /** 导出协议：grpc / http/protobuf */
  private String protocol = "grpc";

  /** 采样比例（0.0 ~ 1.0），基于 TraceId 哈希的固定比例采样 */
  private double samplerRatio = 0.1;

  /** 上下文传播格式：w3c / b3 */
  private String propagationFormat = "w3c";
}
