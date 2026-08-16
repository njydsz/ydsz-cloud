package com.njydsz.common.exception.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.config.YdszExceptionCoreAutoConfiguration;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

/**
 * 异常全局处理器自动配置
 *
 * <p>合并了原有的 4 个处理器配置类：
 *
 * <ul>
 *   <li>Spring MVC 全局异常处理器
 *   <li>Validation 参数校验异常处理器
 *   <li>WebFlux 响应式异常处理器
 *   <li>JDBC 数据访问异常处理器
 * </ul>
 *
 * <p>每个处理器按 Servlet / Reactive 类型自动适配， 在纯 WebMVC 项目或纯 WebFlux 项目中仅装配对应的一个。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration(after = YdszExceptionCoreAutoConfiguration.class)
@EnableConfigurationProperties(ExceptionProperties.class)
public class YdszExceptionHandlerAutoConfiguration {

  // ==================== Servlet MVC 处理器 ====================

  /**
   * 创建 Spring MVC 全局异常处理器 Bean
   *
   * @param environment Spring 环境对象
   * @param messageSource 国际化消息源
   * @param exceptionMetrics 异常指标统计器
   * @param properties 异常模块配置属性
   * @param eventPublisherProvider 事件发布器提供者
   * @return 处理结果
   */
  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnClass({HttpServletRequest.class, MvcExceptionHandler.class})
  @ConditionalOnProperty(
      prefix = "ydsz.exception",
      name = "global-handler-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public MvcExceptionHandler mvcExceptionHandler(
      Environment environment,
      MessageSource messageSource,
      ObjectProvider<ExceptionMetrics> exceptionMetrics,
      ObjectProvider<ExceptionProperties> properties,
      ObjectProvider<ApplicationEventPublisher> eventPublisherProvider) {
    return new MvcExceptionHandler(
        environment,
        messageSource,
        exceptionMetrics.getIfAvailable(),
        properties.getIfAvailable(),
        eventPublisherProvider);
  }

  /**
   * 创建 Validation 校验异常处理器 Bean
   *
   * <p>置于 MVC 全局处理器之后，优先级更高（{@code @Order(HIGHEST_PRECEDENCE + 10)}）， 专门拦截参数校验异常并返回结构化字段错误信息。
   *
   * @param environment Spring 环境对象
   * @param messageSource 国际化消息源
   * @param exceptionMetrics 异常指标统计器
   * @param properties 异常模块配置属性
   * @return 处理结果
   */
  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnClass({ConstraintViolationException.class, ValidationExceptionHandler.class})
  @ConditionalOnProperty(
      prefix = "ydsz.exception",
      name = "global-handler-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ValidationExceptionHandler validationExceptionHandler(
      Environment environment,
      MessageSource messageSource,
      ObjectProvider<ExceptionMetrics> exceptionMetrics,
      ExceptionProperties properties) {
    return new ValidationExceptionHandler(
        environment, messageSource, exceptionMetrics.getIfAvailable(), properties);
  }

  // ==================== Reactive WebFlux 处理器 ====================

  /**
   * 创建 WebFlux 全局异常处理器 Bean
   *
   * @param environment Spring 环境对象
   * @param messageSource 国际化消息源
   * @param exceptionMetrics 异常指标统计器
   * @param properties 异常模块配置属性
   * @return 处理结果
   */
  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
  @ConditionalOnClass({WebFluxConfigurer.class, WebFluxExceptionHandler.class})
  @ConditionalOnProperty(
      prefix = "ydsz.exception",
      name = "global-handler-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public WebFluxExceptionHandler webFluxExceptionHandler(
      Environment environment,
      MessageSource messageSource,
      ObjectProvider<ExceptionMetrics> exceptionMetrics,
      ObjectProvider<ExceptionProperties> properties) {
    return new WebFluxExceptionHandler(
        environment, messageSource, exceptionMetrics.getIfAvailable(), properties.getIfAvailable());
  }

  // ==================== JDBC 处理器 ====================

  /**
   * 创建 JDBC 异常处理器 Bean
   *
   * <p>捕获 JDBC 层的 {@code DataAccessException}，转换为标准错误响应。 识别唯一索引冲突、外键约束、连接超时、死锁等典型数据库异常。
   *
   * @param environment Spring 环境对象
   * @param messageSource 国际化消息源
   * @param exceptionMetrics 异常指标统计器
   * @param properties 异常模块配置属性
   * @return 处理结果
   */
  @Bean
  @ConditionalOnClass(name = "org.springframework.dao.DataAccessException")
  @ConditionalOnMissingBean(JdbcExceptionHandler.class)
  public JdbcExceptionHandler jdbcExceptionHandler(
      Environment environment,
      MessageSource messageSource,
      ObjectProvider<ExceptionMetrics> exceptionMetrics,
      ObjectProvider<ExceptionProperties> properties) {
    return new JdbcExceptionHandler(
        environment, messageSource, exceptionMetrics.getIfAvailable(), properties.getIfAvailable());
  }
}
