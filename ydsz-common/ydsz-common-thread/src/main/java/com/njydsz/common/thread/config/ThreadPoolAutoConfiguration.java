package com.njydsz.common.thread.config;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.metrics.MeteredRejectedHandler;
import com.njydsz.common.thread.metrics.ThreadPoolMetrics;

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
 *       ydsz.executor}，自动注册 {@link ThreadPoolMetrics} / {@link VirtualThreadMetrics} Bean
 *   <li>优雅关闭：shutdown 时等待任务完成
 *   <li>健康检查：自动注册 {@link ThreadHealthIndicator}
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
 *   <li>新增 {@link ThreadPoolMetrics} / {@link VirtualThreadMetrics} 自动注册
 *   <li>新增 {@link MeteredRejectedHandler} 自动包装拒绝策略
 *   <li>新增 TaskDecorator 配置支持
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ThreadPoolProperties
 * @see ThreadHealthIndicator
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "ydsz.thread", name = "enabled", matchIfMissing = true)
public class ThreadPoolAutoConfiguration implements SmartInitializingSingleton {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolAutoConfiguration.class);

  /**
   * 延迟注入 ApplicationContext，支持 {@code ApplicationContextRunner} 测试场景。
   *
   * <p>使用字段注入而非构造器注入，避免测试时缺少默认构造器的问题。
   */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @Autowired private ApplicationContext applicationContext;
  // CHECKSTYLE.ON: RegexpSinglelineJava

  @Override
  public void afterSingletonsInstantiated() {
    if (applicationContext != null) {
      LOG.info(
          "[ydsz-thread] 自动配置完成，已管理平台线程池: {}",
          applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class).keySet());
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

  /**
   * 线程池与指标绑定器的后处理器：在线程池初始化完成后为其包装 {@link MeteredRejectedHandler}， 使拒绝事件自动计入 Micrometer。
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

  /**
   * 线程池指标装配后处理器。
   *
   * <p>在所有 Bean 初始化完成后，为每个平台线程池包装 {@link MeteredRejectedHandler}， 实现拒绝事件自动计入 Micrometer 指标。
   *
   * <p>虚拟线程池无法使用原生拒绝策略（虚拟线程池从不拒绝），因此无需包装。
   *
   * <p>冲突防护：仅处理名称以 "Executor" 结尾、存在配套 Metrics Bean 的平台线程池， 避免误处理业务自定义的 ThreadPoolTaskExecutor Bean。
   *
   * @since 26.09.01
   */
  public static class ThreadPoolMetricsPostProcessor
      implements BeanPostProcessor, BeanFactoryAware {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolMetricsPostProcessor.class);

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
      this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
        throws BeansException {
      // 仅处理平台线程池（虚拟线程池没有原生拒绝策略）
      if (!(bean instanceof ThreadPoolTaskExecutor)) {
        return bean;
      }

      // 仅处理 ydsz-common-thread 管理的 Bean：
      //   1. 名称以 "Executor" 结尾
      //   2. 存在配套的 "<beanName>Metrics" Bean
      if (!beanName.endsWith("Executor") || beanFactory == null) {
        return bean;
      }

      // 排除工厂本身
      if ("threadPoolExecutorFactory".equals(beanName)) {
        return bean;
      }

      String metricsBeanName = beanName + "Metrics";
      if (!beanFactory.containsBean(metricsBeanName)) {
        // 不存在配套 Metrics Bean，说明不是 ydsz-common-thread 管理的线程池
        return bean;
      }

      try {
        Object metricsBean = beanFactory.getBean(metricsBeanName);
        if (!(metricsBean instanceof ThreadPoolMetrics)) {
          return bean;
        }

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) bean;
        ThreadPoolMetrics metrics = (ThreadPoolMetrics) metricsBean;

        // 通过底层 ThreadPoolExecutor 获取拒绝策略（ThreadPoolTaskExecutor 本身不提供 getter）
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        RejectedExecutionHandler currentHandler = threadPoolExecutor.getRejectedExecutionHandler();
        if (currentHandler == null) {
          LOG.warn("ydsz-thread: 线程池 [{}] 拒绝策略为 null，跳过指标包装", beanName);
          return bean;
        }

        // 避免重复包装
        if (currentHandler instanceof MeteredRejectedHandler) {
          return bean;
        }

        MeteredRejectedHandler meteredHandler = new MeteredRejectedHandler(currentHandler, metrics);
        threadPoolExecutor.setRejectedExecutionHandler(meteredHandler);
        LOG.info(
            "ydsz-thread: 已为线程池 [{}] 装配指标感知拒绝策略 ([{}] → MeteredRejectedHandler)",
            beanName,
            currentHandler.getClass().getSimpleName());
      } catch (Exception e) {
        LOG.warn("ydsz-thread: 为线程池 [{}] 装配指标感知拒绝策略失败: {}", beanName, e.getMessage());
      }

      return bean;
    }
  }
}
