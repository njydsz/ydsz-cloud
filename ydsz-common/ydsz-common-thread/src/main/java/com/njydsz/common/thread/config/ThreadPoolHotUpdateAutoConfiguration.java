package com.njydsz.common.thread.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

/**
 * 线程池热更新自动配置。
 *
 * <p>提供 {@link ThreadPoolHotUpdateListener} 的自动装配，无需业务模块手动创建 Bean。
 *
 * <p>启用方式：在 application.yml 中配置：
 *
 * <pre>{@code
 * ydsz:
 *   thread:
 *     hot-update:
 *       enabled: true
 * }</pre>
 *
 * <p>启用后，应用启动时会自动打印线程池注册摘要，并可通过 {@link ThreadPoolHotUpdateListener#resizePool} 等方法运行时调整线程池参数。
 *
 * <p>v1.4.0 新增：从半自动（需业务模块手动创建 Bean）改为全自动配置， 符合 Spring Boot 自动配置惯例。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ThreadPoolHotUpdateListener
 */
@AutoConfiguration(after = ThreadPoolAutoConfiguration.class)
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "ydsz.thread.hot-update", name = "enabled", havingValue = "true")
public class ThreadPoolHotUpdateAutoConfiguration {

  private static final Logger LOG =
      LoggerFactory.getLogger(ThreadPoolHotUpdateAutoConfiguration.class);

  /**
   * 注册线程池热更新监听器。
   *
   * <p>该 Bean 在应用启动完成后自动打印线程池注册摘要， 并提供运行时调整线程池参数的能力。
   *
   * @return 热更新监听器
   */
  @Bean(name = ThreadPoolHotUpdateListener.BEAN_NAME)
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  @ConditionalOnMissingBean(name = ThreadPoolHotUpdateListener.BEAN_NAME)
  public ThreadPoolHotUpdateListener threadPoolHotUpdateListener() {
    LOG.info("[ydsz-thread] 热更新监听器已启用，可通过 ydsz.thread.hot-update.enabled=false 禁用");
    return new ThreadPoolHotUpdateListener();
  }
}
