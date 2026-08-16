package com.njydsz.common.sentry.config;

import com.njydsz.common.notify.core.NotifyService;
import com.njydsz.common.sentry.alerting.AlertConverger;
import com.njydsz.common.sentry.alerting.DefaultAlertPublisher;
import com.njydsz.common.sentry.alerting.NotifyAlertHandler;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.common.sentry.spi.AlertPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 告警自动配置。
 *
 * <p>装配告警发布链路：{@link DefaultAlertPublisher} 外层包 {@link AlertConverger} 收敛。
 *
 * <p><b>降级策略</b>：{@link NotifyService} 存在时才注册 {@link NotifyAlertHandler}， 把 P0/P1/P2 级告警桥接到钉钉 /
 * 邮件；common-notify 未引入时告警仅落日志， 不抛异常也不阻断启动。P3 及以下级别不外发通知。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@EnableConfigurationProperties(SentryProperties.class)
public class AlertingAutoConfiguration {

  /**
   * 装配告警发布链路：收敛器包裹原始发布器。
   *
   * @param properties 监控配置
   * @param notifyServiceProvider 通知服务提供者，可能为空（触发仅日志降级）
   * @return 带收敛能力的告警发布器
   */
  @Bean
  @ConditionalOnMissingBean(AlertPublisher.class)
  @ConditionalOnProperty(
      prefix = "ydsz.sentry.alerting",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public AlertPublisher alertPublisher(
      SentryProperties properties, ObjectProvider<NotifyService> notifyServiceProvider) {
    DefaultAlertPublisher publisher =
        new DefaultAlertPublisher(properties.getAlerting().isLogAlerts());

    // 当 NotifyService 可用时注册通知处理器
    NotifyService notifyService = notifyServiceProvider.getIfAvailable();
    if (notifyService != null) {
      NotifyAlertHandler handler =
          new NotifyAlertHandler(
              notifyService,
              properties.getAlerting().getDingtalkReceiver(),
              properties.getAlerting().getEmailReceiver());
      publisher.registerHandler(AlertSeverity.P0, handler);
      publisher.registerHandler(AlertSeverity.P1, handler);
      publisher.registerHandler(AlertSeverity.P2, handler);
      log.info("[Sentry] NotifyAlertHandler 已注册, 告警将通过 common-notify 发送");
    } else {
      log.info("[Sentry] NotifyService 不可用, 告警仅记录日志");
    }

    return new AlertConverger(publisher, properties.getAlerting().getSilencePeriodMillis());
  }
}
