package com.njydsz.common.util.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.njydsz.common.util.http.ServletRequestUtils;
import com.njydsz.common.util.http.TrustedProxyConfiguration;
import com.njydsz.common.util.http.TrustedProxyProperties;
import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.common.util.id.SnowflakeHealthIndicator;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.util.id.SnowflakeProperties;
import com.njydsz.common.util.id.WorkerIdAllocator;
import com.njydsz.common.util.id.WorkerIdAllocatorChain;
import com.njydsz.common.util.internal.proxy.RequestContextProxy;
import com.njydsz.common.util.internal.proxy.TraceIdGeneratorProxy;
import com.njydsz.common.util.io.TempFileManager;
import com.njydsz.common.util.io.TempFileProperties;

/**
 * 通用工具类自动配置。
 *
 * <p>统一通过 {@code AutoConfiguration.imports} 注册（不依赖业务侧组件扫描）， 确保引入依赖即可装配 Snowflake ID
 * 生成器等基础能力，避免因业务主类未扫描 {@code com.njydsz.common} 包而导致的静默降级。
 *
 * <p>注册的 Bean：
 *
 * <ul>
 *   <li>{@link SnowflakeIdGenerator} — 分布式 ID 生成器（原 SnowflakeIdBean 并入）
 *   <li>{@link WorkerIdAllocatorChain} — WorkerId 分配策略链（PodOrdinal → IpHash）
 *   <li>{@link SnowflakeHealthIndicator} — Snowflake 健康检查（仅 actuator 在 classpath 时注册）
 *   <li>{@link TrustedProxyConfiguration} — 可信代理配置（{@code ydsz.util.trusted-proxies} 属性直挂）
 *   <li>{@link TempFileManager} — 临时文件统一管理（TTL 兜底清理）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties({
  SnowflakeProperties.class,
  TrustedProxyProperties.class,
  TempFileProperties.class
})
public class UtilAutoConfiguration {

  private final ObjectProvider<SnowflakeIdGenerator> idGeneratorProvider;
  private final ObjectProvider<TrustedProxyConfiguration> trustedProxyConfigProvider;

  /**
   * 构造方法注入（优于字段注入，遵循云顶编码规范 5.3 节）。
   *
   * @param idGeneratorProvider Snowflake ID 生成器提供者
   * @param trustedProxyConfigProvider 可信代理配置提供者
   */
  public UtilAutoConfiguration(
      ObjectProvider<SnowflakeIdGenerator> idGeneratorProvider,
      ObjectProvider<TrustedProxyConfiguration> trustedProxyConfigProvider) {
    this.idGeneratorProvider = idGeneratorProvider;
    this.trustedProxyConfigProvider = trustedProxyConfigProvider;
  }

  /** 注册静态工具类的 Supplier，替代 SpringContextHolder 查找；并对反射桥接做启动期自检。 */
  @PostConstruct
  public void registerStaticToolSuppliers() {
    IdGenerator.setGeneratorSupplier(idGeneratorProvider::getIfAvailable);
    ServletRequestUtils.setTrustedProxyConfigSupplier(trustedProxyConfigProvider::getIfAvailable);
    // 反射桥接启动自检：core 在 classpath 但方法绑定失败时立即显式告警，
    // 避免运行期静默降级（详见 docs/ADR-0002-trace-contract-sinking.md）
    TraceIdGeneratorProxy.verifyBinding();
    RequestContextProxy.verifyBinding();
  }

  /**
   * Snowflake ID 生成器 Bean（原 SnowflakeIdBean 并入，保证无需组件扫描即可装配）。
   *
   * @param properties Snowflake 配置属性
   * @param allocator WorkerId 分配策略链
   * @return SnowflakeIdGenerator 实例
   */
  @Bean
  @Primary
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "ydsz.util.snowflake", name = "enabled", matchIfMissing = true)
  public SnowflakeIdGenerator snowflakeIdGenerator(
      SnowflakeProperties properties, WorkerIdAllocator allocator) {
    int sequenceBits =
        properties.getSequenceBits() != null
            ? properties.getSequenceBits()
            : SnowflakeIdGenerator.DEFAULT_SEQUENCE_BITS;
    return new SnowflakeIdGenerator(properties, allocator, sequenceBits);
  }

  /**
   * WorkerId 分配策略链 —— PodOrdinal → IpHash。
   *
   * <p>业务方可声明自定义 {@link WorkerIdAllocator} Bean，通过 {@link WorkerIdAllocatorChain#prepend}
   * 插入更高优先级策略。
   *
   * @return WorkerIdAllocatorChain 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public WorkerIdAllocatorChain workerIdAllocatorChain() {
    return WorkerIdAllocatorChain.defaults();
  }

  /**
   * 可信代理配置 Bean —— 将 {@code ydsz.util.trusted-proxies} 属性直接装配为 Bean。
   *
   * <p>未配置时注册空集合实例（仅内网/回环可信）。业务方可声明自定义 {@link TrustedProxyConfiguration} Bean 覆盖。
   *
   * <p><b>K8s 风险提示：</b>默认策略将所有内网/回环地址视为可信代理。 Kubernetes 集群内所有 Pod IP
   * 均为内网地址，即任意 Pod 伪造的 X-Forwarded-For 都会被采信。 集群内部署时建议将可信代理收敛为明确的入口网关 IP 集合。
   *
   * @param properties 可信代理配置属性
   * @return TrustedProxyConfiguration 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public TrustedProxyConfiguration trustedProxyConfiguration(TrustedProxyProperties properties) {
    return new TrustedProxyConfiguration(properties.getTrustedProxies());
  }

  /**
   * 临时文件统一管理器 Bean（TTL 兜底清理 + JVM ShutdownHook + 优雅停机清理）。
   *
   * @param properties 临时文件配置属性
   * @return TempFileManager 实例
   */
  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  public TempFileManager tempFileManager(TempFileProperties properties) {
    return new TempFileManager(properties.getRetention(), properties.getCleanupInterval());
  }

  /**
   * Snowflake ID 生成器健康检查 Bean
   *
   * <p>仅当 classpath 上存在 {@link org.springframework.boot.health.contributor.HealthIndicator} （即引入
   * spring-boot-actuator）时才加载，避免缺少 actuator 依赖时 因 {@link SnowflakeHealthIndicator} 实现的接口类不存在而触发
   * {@code NoClassDefFoundError}。
   *
   * <p>使用内部静态 @Configuration 类 + {@code @ConditionalOnClass} 是 Spring Boot 标准做法：通过 ASM
   * 字节码分析评估条件，避免在条件不满足时触发相关类的加载。
   */
  @Configuration(proxyBeanMethods = false)
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  static class HealthIndicatorConfiguration {

    /**
     * 注册 SnowflakeHealthIndicator Bean
     *
     * <p>检查 Snowflake ID 生成器的健康状态（时钟回拨、workerId 有效性、ID 生成能力）。 仅在 {@code
     * ydsz.util.snowflake.enabled=true}（或缺省，matchIfMissing=true）时注册， 避免在 Snowflake 被显式禁用时仍强制初始化该组件。
     *
     * @param idGeneratorProvider Snowflake ID 生成器提供者
     * @return SnowflakeHealthIndicator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ydsz.util.snowflake", name = "enabled", matchIfMissing = true)
    public SnowflakeHealthIndicator snowflakeHealthIndicator(
        ObjectProvider<SnowflakeIdGenerator> idGeneratorProvider) {
      return new SnowflakeHealthIndicator(idGeneratorProvider);
    }
  }

  /**
   * Micrometer 指标注册（仅 micrometer 在 classpath 时生效）。
   *
   * <p>注册指标：
   *
   * <ul>
   *   <li>{@code ydsz.util.id.degraded} — IdGenerator 降级累计次数（非 0 表示有调用生成过随机数降级 ID，
   *       可能与 Snowflake 主键空间冲突，需要告警关注）
   *   <li>{@code ydsz.util.tempfile.tracked} — 当前受跟踪临时文件数（持续增长说明存在泄漏）
   * </ul>
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry.class)
  static class MetricsConfiguration {

    /**
     * 注册工具层核心运行指标（构造即注册，Gauge 绑定单例/静态状态，无需持有实例）。
     *
     * @param meterRegistry Micrometer 注册表（可选）
     * @param tempFileManager 临时文件管理器（可选）
     * @return 指标注册器占位 Bean（保留 Bean 以明确生命周期归属，便于测试断言）
     */
    @Bean
    public UtilMetrics utilMetrics(
        ObjectProvider<MeterRegistry> meterRegistry,
        ObjectProvider<TempFileManager> tempFileManager) {
      return new UtilMetrics(meterRegistry.getIfAvailable(), tempFileManager.getIfAvailable());
    }
  }

  /** 工具层核心运行指标注册器（见 {@link MetricsConfiguration}）。 */
  static class UtilMetrics {

    /**
     * 向注册表绑定 Gauge 指标。
     *
     * @param registry Micrometer 注册表；为 null 时不注册任何指标
     * @param tempFileManager 临时文件管理器；为 null 时跳过临时文件指标
     */
    UtilMetrics(MeterRegistry registry, TempFileManager tempFileManager) {
      if (registry == null) {
        return;
      }
      registry.gauge("ydsz.util.id.degraded", IdGenerator.getDegradedCount());
      if (tempFileManager != null) {
        registry.gauge("ydsz.util.tempfile.tracked", tempFileManager, TempFileManager::getTrackedCount);
      }
    }
  }
}
