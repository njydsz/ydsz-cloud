package com.njydsz.common.sentry;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Sentry 模块的 Spring 上下文工具类。
 *
 * <p>为 SentryMetricsAdapter 等静态工具类提供获取 Spring Bean 的能力， 解耦底层 MetricsCollector 和上层 Adapter。
 *
 * <p>本类由 Spring 容器自动装配（{@link ApplicationContextAware}），无需显式配置。
 *
 * @author ydsz-team
 * @since 2.1.0
 */
@Component
public class SentryApplicationContextUtils implements ApplicationContextAware {

  private static volatile ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(ApplicationContext context) throws BeansException {
    applicationContext = context;
  }

  /**
   * 获取 Spring 上下文。
   *
   * @return ApplicationContext 实例
   */
  public static ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  /**
   * 获取指定类型的 Bean。
   *
   * @param beanClass Bean 类型
   * @param <T> Bean 类型泛型
   * @return Bean 实例，未找到时返回 null
   */
  @SuppressWarnings("unchecked")
  public static <T> T getBean(Class<T> beanClass) {
    if (applicationContext == null) {
      return null;
    }
    try {
      return applicationContext.getBean(beanClass);
    } catch (BeansException e) {
      return null;
    }
  }

  /**
   * 判断 Sentry 模块是否已装配。
   *
   * @return {@code true} 表示 Spring 上下文已装配
   */
  public static boolean isAvailable() {
    return applicationContext != null;
  }
}
