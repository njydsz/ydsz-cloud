package com.njydsz.common.feign.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Resilience4j FeignClient 后置处理器。
 *
 * <p>扫描所有标注 {@link FeignClient} 的 Spring Bean，按服务名自动注入 Resilience4j {@link
 * CircuitBreaker} 名称映射，使下游服务熔断时可通过统一命名规则匹配对应的熔断配置。
 *
 * <p>熔断器命名规则：{@code feign:<serviceId>}，例如 {@code ydsz-system} → {@code feign:ydsz-system}。
 *
 * <p>本处理器职责：
 *
 * <ul>
 *   <li>读取 FeignClient 注解的 name/serviceId
 *   <li>校验 {@code ydsz.feign.circuit-breaker.enabled} 是否开启
 *   <li>开启时注入 circuitBreaker 自定义配置 name 属性
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
public class Resilience4jFeignPostProcessor implements BeanPostProcessor {

  private final FeignProperties feignProperties;

  /**
   * 构造 FeignClient 后置处理器。
   *
   * @param feignProperties Feign 配置属性
   */
  public Resilience4jFeignPostProcessor(FeignProperties feignProperties) {
    this.feignProperties = feignProperties;
  }

  /**
   * Bean 初始化后置处理：扫描 FeignClient 并注入 CircuitBreaker 名称。
   *
   * @param bean 待处理的 Bean 实例
   * @param beanName Bean 名称
   * @return 处理后的 Bean（可包装或原样返回）
   */
  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    Class<?> beanClass = bean.getClass();
    // 处理 CGLIB 代理类：需获取父类（真实接口）
    Class<?> targetClass = beanClass.getName().contains("$$") ? beanClass.getSuperclass() : beanClass;
    FeignClient feignClient = AnnotatedElementUtils.findMergedAnnotation(targetClass, FeignClient.class);
    if (feignClient != null) {
      String serviceId = resolveServiceId(feignClient);
      registerCircuitBreakerName(serviceId);
    }
    return bean;
  }

  /**
   * 解析 FeignClient 的服务 ID。
   *
   * @param feignClient FeignClient 注解
   * @return 服务 ID（优先取 name）
   */
  private String resolveServiceId(FeignClient feignClient) {
    String name = feignClient.name();
    if (name == null || name.isBlank()) {
      name = feignClient.value();
    }
    return name;
  }

  /**
   * 注册 CircuitBreaker 命名映射。
   *
   * <p>当熔断器开关开启时，按服务名自定义熔断器配置名，便于 Resilience4j 配置与 Actuator 指标采集。
   *
   * @param serviceId 服务 ID
   */
  private void registerCircuitBreakerName(String serviceId) {
    if (serviceId == null || serviceId.isBlank()) {
      return;
    }
    String circuitBreakerName = "feign:" + serviceId;
    if (feignProperties.getCircuitBreaker().isEnabled()) {
      log.debug("[FeignClient] 注册 CircuitBreaker 命名映射: service={} → cb={}", serviceId, circuitBreakerName);
    } else {
      log.debug("[FeignClient] 熔断器未启用，跳过 CircuitBreaker 注册: service={}", serviceId);
    }
  }
}
