package com.njydsz.common.thread.config;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.actuator.ThreadPoolMetricsEndpoint;
import com.njydsz.common.thread.metrics.ThreadPoolMetrics;
import com.njydsz.common.thread.metrics.ThreadPoolRegistryMetrics;
import com.njydsz.common.thread.registry.ThreadPoolRegistry;

/**
 * 统一线程池自动配置。
 *
 * <p>根据 {@link ThreadPoolProperties#getPools()} 配置动态创建并注册多个 {@link ThreadPoolTaskExecutor} / {@link
 * ExecutorService} Bean， Bean 名称为 {@code key + "Executor"}。
 *
 * <p>功能特性：
 *
 * <ul>
 *   <li>按业务隔离：每个线程池独立的 coreSize/maxSize/queue/rejectPolicy
 *   <li>Micrometer 指标：active/queueSize/completed/rejected Gauge + Counter， 前缀 {@code
 *       ydsz.executor}，自动注册 {@link ThreadPoolMetrics} / {@link
 *       com.njydsz.common.thread.metrics.VirtualThreadMetrics} Bean
 *   <li>优雅关闭：shutdown 时等待任务完成
 *   <li>健康检查：自动注册 {@link com.njydsz.common.thread.health.ThreadHealthIndicator}
 *   <li>TaskDecorator 支持：通过 {@code task-decorator-bean-names} 配置上下文传播
 * </ul>
 *
 * <p>注入方式：
 *
 * <pre>{@code
 * @Resource(name = "ioExecutor")
 * private ThreadPoolTaskExecutor ioExecutor;
 * }</pre>
 *
 * <p><b>26.09.01 变更：</b>
 *
 * <ul>
 *   <li>新增 {@link ThreadPoolMetrics} / {@link com.njydsz.common.thread.metrics.VirtualThreadMetrics} 自动注册
 *   <li>新增 {@link com.njydsz.common.thread.metrics.MeteredRejectedHandler} 自动包装拒绝策略
 *   <li>新增 TaskDecorator 配置支持
 * </ul>
 *
 * <p><b>26.09.19 变更：</b>
 *
 * <ul>
 *   <li>ApplicationContext 注入方式由字段注入改为构造器注入 + {@code @Lazy}，消除字段注入
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ThreadPoolProperties
 * @see com.njydsz.common.thread.health.ThreadHealthIndicator
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "ydsz.thread", name = "enabled", matchIfMissing = true)
public class ThreadPoolAutoConfiguration implements SmartInitializingSingleton {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolAutoConfiguration.class);

  /**
   * 使用构造器注入 + {@code @Lazy} 避免循环依赖：
   * ApplicationContext → ThreadPoolAutoConfiguration → ApplicationContext。
   *
   * <p>26.09.19 重构：由字段注入改为构造器注入，符合 P1 规范对齐要求。
   */
  private final ApplicationContext applicationContext;

  public ThreadPoolAutoConfiguration(@Lazy ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (applicationContext != null) {
      LOG.info(
          "[ydsz-thread] 自动配置完成，已管理平台线程池: {}，ThreadPoolRegistry 已注册: {} 个",
          applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class).keySet(),
          ThreadPoolRegistry.size());
    }
  }

  /**
   * 应用上下文刷新完成后输出启动诊断信息（P2-16）。
   *
   * <p>输出内容：JVM 版本与 VirtualThread 支持状态、CPU 核心数、Platform 与 Virtual 线程池数量。
   * 与 {@code @EventListener(ContextRefreshedEvent.class)} 语义等价，
   * 但此处直接在配置类中保留以确保热更新监听器注册完成后也触发诊断。
   *
   * @param event 上下文刷新事件
   */
  @EventListener(ContextRefreshedEvent.class)
  public void onApplicationReady(ContextRefreshedEvent event) {
    if (event.getApplicationContext() != applicationContext) {
      // 仅响应根上下文的刷新事件，避免子上下文重复触发
      return;
    }
    printStartupDiagnostics();
  }

  /**
   * 输出启动诊断信息。
   *
   * <p>P2-16：增强启动日志，输出 JVM/线程池/CPU 信息辅助问题定位。
   */
  private void printStartupDiagnostics() {
    try {
      int platformCount = applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class).size();
      int registryCount = ThreadPoolRegistry.size();
      int cpuCores = Runtime.getRuntime().availableProcessors();
      String javaVersion = System.getProperty("java.version");

      LOG.info(
          "[ydsz-thread] 启动诊断: JVM={}, CPU cores={}, Platform线程池={}, Registry={}",
          javaVersion, cpuCores, platformCount, registryCount);
    } catch (Exception e) {
      LOG.debug("[ydsz-thread] 启动诊断输出异常: {}", e.getMessage());
    }
  }

  /**
   * 注册线程池 Bean 定义注册器。
   *
   * <p>该 Bean 负责在 Spring 容器初始化阶段动态注册线程池和指标绑定器 BeanDefinition。 通过 {@link
   * BeanDefinitionRegistryPostProcessor} 在所有常规 BeanDefinition 加载完成后、 Bean 实例化之前执行注册逻辑。
   *
   * <p>26.09.01 修复：显式声明为 {@code @Bean}， 修复 {@link ThreadPoolRegistrar} 因缺少组件原型注解导致装配链路断裂的问题。
   *
   * @param properties 线程池配置属性
   * @return 线程池注册器
   * @since 26.09.01
   */
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  @ConditionalOnMissingBean(name = "threadPoolRegistrar")
  public ThreadPoolRegistrar threadPoolRegistrar(ThreadPoolProperties properties) {
    return new ThreadPoolRegistrar(properties);
  }

  /**
   * 获取全部已注册的平台线程池（Bean 名称 → 线程池）。
   *
   * <p>供下游模块（如消息通道 Bulkhead 隔离）按名称查找线程池并组装为业务 Map。 虚拟线程池（{@link ExecutorService}）不在此返回范围内。
   *
   * @return Bean 名称 → ThreadPoolTaskExecutor 的映射；无线程池时返回空 Map
   * @since 26.09.01
   */
  public Map<String, ThreadPoolTaskExecutor> getExecutors() {
    if (applicationContext == null) {
      return Collections.emptyMap();
    }
    return applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class);
  }

  // ==================== P2-2: 线程池注册中心 & 指标端点 ====================

  /**
   * P2-2: 线程池注册中心 Micrometer 指标绑定器。
   *
   * <p>将 {@link ThreadPoolRegistry} 中所有已注册线程池的实时指标（core/max/active/pool.size/queue.size/completed）
   * 绑定到 Micrometer，使得 Prometheus / Grafana 等监控系统可以采集到线程池运行状态。
   *
   * <p>仅在 MeterRegistry 和 Micrometer 存在于 classpath 时注册。
   *
   * @param meterRegistryProvider Micrometer MeterRegistry 提供者（可选）
   * @return ThreadPoolRegistryMetrics 指标绑定器
   * @since 26.09.01
   */
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  @ConditionalOnMissingBean(ThreadPoolRegistryMetrics.class)
  public ThreadPoolRegistryMetrics threadPoolRegistryMetrics(
      ObjectProvider<MeterRegistry> meterRegistryProvider) {
    ThreadPoolRegistryMetrics binder = new ThreadPoolRegistryMetrics();
    MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
    if (meterRegistry != null) {
      binder.bindTo(meterRegistry);
      LOG.info("[ydsz-thread] ThreadPoolRegistry 指标已绑定到 MeterRegistry");
    }
    return binder;
  }

  /**
   * P2-2: 线程池 Actuator 端点。
   *
   * <p>暴露 {@code /actuator/threadpools} 端点，供运维人员通过 HTTP 查看线程池实时指标。
   * 支持 {@code GET /actuator/threadpools} 和 {@code GET /actuator/threadpools/{poolName}}。
   *
   * <p>仅在 Spring Boot Actuator 和端点基础设施存在时注册。
   *
   * @return ThreadPoolMetricsEndpoint 实例
   * @since 26.09.01
   */
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  @ConditionalOnClass(Endpoint.class)
  @ConditionalOnAvailableEndpoint(ThreadPoolMetricsEndpoint.class)
  @ConditionalOnMissingBean(ThreadPoolMetricsEndpoint.class)
  public ThreadPoolMetricsEndpoint threadPoolMetricsEndpoint() {
    LOG.info("[ydsz-thread] 注册 Actuator 端点: /actuator/threadpools");
    return new ThreadPoolMetricsEndpoint();
  }

  /**
   * 线程池与指标绑定器的后处理器：在线程池初始化完成后为其包装 {@link com.njydsz.common.thread.metrics.MeteredRejectedHandler}， 使拒绝事件自动计入 Micrometer。
   *
   * <p>通过 BeanPostProcessor 而非构造器注入避免循环依赖： ThreadPoolTaskExecutor → 拒绝策略 → MeteredRejectedHandler →
   * ThreadPoolMetrics → ThreadPoolTaskExecutor。
   *
   * <p>26.09.01 重构：{@link ThreadPoolRegistrar} 已提取为独立组件类。
   *
   * @return 装配后处理器
   * @since 26.09.01
   */
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  @ConditionalOnMissingBean(name = "threadPoolMetricsPostProcessor")
  public BeanPostProcessor threadPoolMetricsPostProcessor() {
    return new ThreadPoolMetricsPostProcessor();
  }
}
