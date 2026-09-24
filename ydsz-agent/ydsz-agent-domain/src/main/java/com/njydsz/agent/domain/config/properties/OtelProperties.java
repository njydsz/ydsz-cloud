package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenTelemetry 链路追踪配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.otel}，控制是否启用 OTel 分布式链路追踪以及
 * 服务名称标识。默认不开启（isEnabled=false），服务名默认 "ydsz-agent"，
 * 启用后由 AgentOtelAutoConfiguration 装配导出器 Bean。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtelProperties {
  private boolean isEnabled = false;
  private String serviceName = "ydsz-agent";
}
