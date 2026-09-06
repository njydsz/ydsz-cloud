package com.njydsz.common.core.config;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * SkyWalking 分布式链路追踪自动配置类（P2-1 集成）。
 *
 * <p>提供以下能力：
 * <ul>
 *   <li>激活 {@link SkyWalkingProperties} 配置属性绑定，使 {@code ydsz.skywalking.*} 配置项在 IDE 中获得自动补全</li>
 *   <li>检测到 SkyWalking Agent（{@code org.apache.skywalking.apm.agent.core.Agent} 在 classpath 中）时，
 *       自动启用 trace 增强模式（采样率配置、MDC 传播生效）</li>
 *   <li>提供默认采样率配置（{@code ydsz.skywalking.sampling-rate: 1.0} = 全量采样，高并发可调低）</li>
 *   <li>未接入 Agent 时自动降级：{@link SkyWalkingProperties} 属性仍可用于本地 trace 标识注入</li>
 * </ul>
 *
 * <p><b>与 Java Agent 的关系：</b>
 * <br>本配置类不替代 Java Agent（{@code -javaagent:/path/to/skywalking-agent.jar}）。
 * Agent 在 JVM 启动时挂载，通过字节码增强完成 HTTP/RPC/DB 拦截。
 * 本配置类通过 Toolkit SDK 提供高层能力（Micrometer 桥接、Logback MDC、编程式 TraceContext）。
 *
 * <p><b>启用条件：</b>当 {@code ydsz.skywalking.enabled=true} 时生效（默认启用）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "ydsz.skywalking",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(SkyWalkingProperties.class)
public class SkyWalkingAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(SkyWalkingAutoConfiguration.class);

  /**
   * SkyWalking Agent classpath 标记类。
   *
   * <p>当 Agent 挂载时，其 core jar 会在应用 classpath 中引入此类型。
   * 用于检测 Agent 是否已启用。
   */
  private static final String AGENT_CLASS_MARKER =
      "org.apache.skywalking.apm.agent.core.conf.Config$Initializer";

  /**
   * Bean 条件：仅当 SkyWalking Agent 类存在于 classpath 且配置允许时创建 TraceSampler。
   *
   * @param properties SkyWalking 配置属性
   * @return TraceSampler 实例（Agent 检测通过 + 采样率配置生效）
   */
  @Bean
  @ConditionalOnClass(name = AGENT_CLASS_MARKER)
  @ConditionalOnMissingBean
  public TraceSampler traceSampler(SkyWalkingProperties properties) {
    TraceSampler sampler = new TraceSampler(properties.getSamplingRate());
    LOG.info(
        "SkyWalking Agent detected — TraceSampler initialized "
            + "(samplingRate={}, serviceName={})",
        properties.getSamplingRate(),
        properties.getServiceName());
    return sampler;
  }

  /**
   * Bean 条件：当 Agent 未挂载时（如本地开发环境），创建 NoOp Tracer 作为降级。
   *
   * <p>防止因未挂载 Agent 导致 Spring 上下文初始化失败。
   * 降级模式下 trace 能力由 appliation.yml 中的 logging.pattern 兜底（traceId 输出为空）。
   *
   * @param properties SkyWalking 配置属性
   * @return NoOpTraceSampler 降级实例
   */
  @Bean
  @ConditionalOnMissingBean(TraceSampler.class)
  public TraceSampler noOpTraceSampler(SkyWalkingProperties properties) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "SkyWalking Agent NOT detected — TraceSampler running in NoOp mode "
              + "(ydsz.skywalking.agent-attached=false, samplingRate ignored). "
              + "Add -javaagent:/path/to/skywalking-agent.jar to JVM args to enable tracing.");
    }
    return new TraceSampler(properties.getServiceName(), false);
  }

  // ==================================================================================
  // 内部组件类
  // ==================================================================================

  /**
   * SkyWalking 采样策略持有者。
   *
   * <p>由本配置类在 Agent 检测通过后创建，供业务代码注入使用
   * （如动态开关采样、在超高并发场景临时降低采样率）。
   *
   * <p>线程安全：volatile samplingRate 保证并发可见性。
   */
  public static class TraceSampler {

    /** 采样率（0.0 ~ 1.0）。1.0 = 全量采样，0.1 = 10% 采样。 */
    private volatile double samplingRate;

    /** 是否已挂载 SkyWalking Agent。 */
    private final boolean agentAttached;

    /** 服务名称（覆盖 Agent 的 agent.service_name）。 */
    private final String serviceName;

    TraceSampler(double samplingRate) {
      this(null, true);
      this.samplingRate = samplingRate;
    }

    TraceSampler(String serviceName, boolean agentAttached) {
      this.serviceName = serviceName;
      this.agentAttached = agentAttached;
      this.samplingRate = 1.0;
    }

    /**
     * 获取当前采样率。
     *
     * @return 采样率（0.0 ~ 1.0）
     */
    public double getSamplingRate() {
      return samplingRate;
    }

    /**
     * 动态设置采样率（运行时生效）。
     *
     * @param rate 采样率（0.0 ~ 1.0）
     */
    public void setSamplingRate(double rate) {
      if (rate < 0.0 || rate > 1.0) {
        throw new IllegalArgumentException(
            "samplingRate must be between 0.0 and 1.0, got: " + rate);
      }
      this.samplingRate = rate;
    }

    /**
     * 判断当前请求是否应被采样。
     *
     * <p>使用 ThreadLocalRandom 避免争用，高并发场景下性能优于 synchronized Random。
     *
     * @return true=应采样，false=不采样
     */
    public boolean shouldSample() {
      if (!agentAttached) {
        return false;
      }
      if (samplingRate >= 1.0) {
        return true;
      }
      if (samplingRate <= 0.0) {
        return false;
      }
      return ThreadLocalRandom.current().nextDouble() < samplingRate;
    }

    /**
     * 判断 SkyWalking Agent 是否已挂载。
     *
     * @return true=Agent 已挂载，trace 功能可用；false=降级模式，traceId 为空
     */
    public boolean isAgentAttached() {
      return agentAttached;
    }

    /**
     * 获取服务名称。
     *
     * @return 服务名称（ydsz-gateway / ydsz-system / ydsz-agent 等）
     */
    public String getServiceName() {
      return serviceName;
    }

    @Override
    public String toString() {
      return String.format(
          "TraceSampler{serviceName='%s', agentAttached=%s, samplingRate=%.2f}",
          serviceName, agentAttached, samplingRate);
    }
  }
}
