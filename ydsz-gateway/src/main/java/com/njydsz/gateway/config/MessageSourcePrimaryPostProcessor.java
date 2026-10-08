package com.njydsz.gateway.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;

import com.njydsz.common.locales.config.LocalesAutoConfiguration;

/**
 * 将 LocalesAutoConfiguration 提供的 ydszMessageSource 标记为 @Primary。
 *
 * <p>WebFlux 网关同时存在两个 MessageSource Bean：
 * <ul>
 *   <li>{@code ydszMessageSource}（ydsz-common-locales 多模块聚合 i18n）</li>
 *   <li>{@code messageSource}（Spring Boot 自动配置默认 MessageSource）</li>
 * </ul>
 *
 * <p>二者类型相同，Spring 无法自动择一注入，导致"required a single bean, but 2 found"错误。
 * 将 {@code ydszMessageSource} 标记为 Primary，使注入点优先获取多模块聚合消息源。
 *
 * @since 26.10.08
 * @author ydsz-team
 */
@Component
public class MessageSourcePrimaryPostProcessor implements BeanDefinitionRegistryPostProcessor {

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
    String beanName = LocalesAutoConfiguration.MESSAGE_SOURCE_BEAN_NAME;
    if (registry.containsBeanDefinition(beanName)) {
      BeanDefinition bd = registry.getBeanDefinition(beanName);
      bd.setPrimary(true);
    }
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    // no-op
  }
}
