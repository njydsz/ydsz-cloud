package com.njydsz.common.thread.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.metrics.MeteredRejectedHandler;
import com.njydsz.common.thread.metrics.ThreadPoolMetrics;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池指标装配后处理器。
 *
 * <p>在所有 Bean 初始化完成后，为每个平台线程池包装 {@link MeteredRejectedHandler}， 实现拒绝事件自动计入 Micrometer 指标。
 *
 * <p>虚拟线程池无法使用原生拒绝策略（虚拟线程池从不拒绝），因此无需包装。
 *
 * <p>冲突防护：仅处理名称以 "Executor" 结尾、存在配套 Metrics Bean 的平台线程池， 避免误处理业务自定义的 ThreadPoolTaskExecutor Bean。
 *
 * <p>26.09.19 重构：从 {@link ThreadPoolAutoConfiguration} 内部类提取为独立顶级类（P2-1）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ThreadPoolMetricsPostProcessor implements BeanPostProcessor, BeanFactoryAware {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolMetricsPostProcessor.class);

  private BeanFactory beanFactory;

  @Override
  public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
    this.beanFactory = beanFactory;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
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
        LOG.warn("[ydsz-thread] 线程池 [{}] 拒绝策略为 null，跳过指标包装", beanName);
        return bean;
      }

      // 避免重复包装
      if (currentHandler instanceof MeteredRejectedHandler) {
        return bean;
      }

      MeteredRejectedHandler meteredHandler = new MeteredRejectedHandler(currentHandler, metrics);
      threadPoolExecutor.setRejectedExecutionHandler(meteredHandler);
      LOG.info(
          "[ydsz-thread] 已为线程池 [{}] 装配指标感知拒绝策略 ([{}] → MeteredRejectedHandler)",
          beanName,
          currentHandler.getClass().getSimpleName());
    } catch (Exception e) {
      LOG.warn("[ydsz-thread] 为线程池 [{}] 装配指标感知拒绝策略失败: {}", beanName, e.getMessage());
    }

    return bean;
  }
}
