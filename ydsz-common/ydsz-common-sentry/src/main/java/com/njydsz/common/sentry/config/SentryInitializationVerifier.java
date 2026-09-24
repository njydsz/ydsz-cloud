package com.njydsz.common.sentry.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.sentry.SentryObservation;
import com.njydsz.common.sentry.SentryService;

/**
 * Sentry 模块初始化验证器。
 *
 * <p>在容器启动后（{@link ApplicationReadyEvent}）检测 {@link SentryService} Bean 是否成功创建并注入
 * 到 {@link SentryObservation} 静态门面。若 {@code ydsz.sentry.fail-fast=true}（默认）且初始化失败，
 * 将抛出异常阻止应用上线，避免可观测能力静默丢失。
 *
 * <p>26.09.20 新增：配合 {@link SentryProperties#isFailFast} 实现 fail-fast 检测机制。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class SentryInitializationVerifier implements ApplicationListener<ApplicationReadyEvent> {

  private final SentryProperties sentryProperties;
  private final ObjectProvider<SentryService> sentryServiceProvider;

  /**
   * 构造验证器。
   *
   * @param sentryProperties 监控配置
   * @param sentryServiceProvider SentryService 提供者
   */
  public SentryInitializationVerifier(
      SentryProperties sentryProperties,
      ObjectProvider<SentryService> sentryServiceProvider) {
    this.sentryProperties = sentryProperties;
    this.sentryServiceProvider = sentryServiceProvider;
  }

  /**
   * 容器就绪后执行 Fail-Fast 检测。
   *
   * <p>若 {@link SentryService} Bean 不可用且 fail-fast=true，抛出 {@link org.springframework.beans.BeanCreationException} 阻止应用启动；
   * 若 fail-fast=false，仅输出 ERROR 级别日志提醒。
   *
   * @param event 应用就绪事件
   */
  @Override
  /**
   * on application event。
   * @param event 参数
   */
  public void onApplicationEvent(ApplicationReadyEvent event) {
    SentryService service = sentryServiceProvider.getIfAvailable();
    if (service != null) {
      log.info("[Sentry] 初始化验证通过：SentryService Bean 已就绪");
      return;
    }

    String msg =
        "[Sentry] Fail-Fast 检测失败：ydsz.sentry.enabled=true 但 SentryService Bean 未创建。"
            + "请检查 ydzs-common-sentry 模块依赖是否完整（SPI 实现是否正确声明）。"
            + "若需降级为 no-op，请设置 ydsz.sentry.fail-fast=false 或 ydsz.sentry.enabled=false";

    if (sentryProperties.isFailFast()) {
      log.error(msg);
      throw new org.springframework.beans.factory.BeanCreationException(msg);
    } else {
      log.error(msg);
    }
  }
}
