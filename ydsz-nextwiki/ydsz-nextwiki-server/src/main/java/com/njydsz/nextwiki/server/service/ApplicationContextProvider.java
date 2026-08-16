package com.njydsz.nextwiki.server.service;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 应用上下文持有者。
 *
 * <p>提供静态方法获取 Spring Bean，用于 {@code @Async} 方法或无法注入的场景 （如静态工具类、领域对象内）。
 *
 * <p>由 Spring 框架在启动时通过 {@link ApplicationContextAware} 注入上下文。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {

  private static ApplicationContext context;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    context = applicationContext;
  }

  /**
   * 根据类型获取 Spring Bean。
   *
   * @param beanType Bean 类型
   * @param <T> Bean 泛型
   * @return Bean 实例
   * @throws IllegalStateException 上下文未初始化时抛出
   */
  public static <T> T getBean(Class<T> beanType) {
    if (context == null) {
      throw new IllegalStateException("Spring 上下文未初始化");
    }
    return context.getBean(beanType);
  }

  /**
   * 根据名称获取 Spring Bean。
   *
   * @param beanName Bean 名称
   * @return Bean 实例
   * @throws IllegalStateException 上下文未初始化时抛出
   */
  public static Object getBean(String beanName) {
    if (context == null) {
      throw new IllegalStateException("Spring 上下文未初始化");
    }
    return context.getBean(beanName);
  }
}
