package com.njydsz.common.notify.health;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.common.notify.config.NotifyProperties;
import com.njydsz.common.notify.core.NotifyCircuitBreakerRegistry;
import com.njydsz.common.notify.core.NotifyRetryQueue;

/**
 * 通知模块健康检查指示器
 *
 * <p>检查通知渠道（邮件、短信、企业微信等）配置状态， 暴露 /actuator/health/notify 端点。
 *
 * <p><b>检测逻辑：</b>
 *
 * <ul>
 *   <li>检查各通知渠道是否已启用并完成必要配置
 *   <li>返回各渠道配置就绪状态
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(
    prefix = "ydsz.notify",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NotifyHealthIndicator implements HealthIndicator {

  private final NotifyProperties notifyProperties;
  private final ObjectProvider<List<NotifyChannelStrategy>> strategiesProvider;
  private final ObjectProvider<NotifyRetryQueue> retryQueueProvider;
  private final ObjectProvider<NotifyCircuitBreakerRegistry> circuitBreakerProvider;

  public NotifyHealthIndicator(
      NotifyProperties notifyProperties,
      ObjectProvider<List<NotifyChannelStrategy>> strategiesProvider,
      ObjectProvider<NotifyRetryQueue> retryQueueProvider,
      ObjectProvider<NotifyCircuitBreakerRegistry> circuitBreakerProvider) {
    this.notifyProperties = notifyProperties;
    this.strategiesProvider = strategiesProvider;
    this.retryQueueProvider = retryQueueProvider;
    this.circuitBreakerProvider = circuitBreakerProvider;
  }

  @Override
  public Health health() {
    try {
      Map<String, Object> channels = new LinkedHashMap<>();
      int configuredCount = 0;

      // 邮件渠道
      NotifyProperties.EmailConfig email = notifyProperties.getEmail();
      if (email != null && email.isEnabled()) {
        boolean ready =
            email.getSmtpHost() != null
                && !email.getSmtpHost().isEmpty()
                && email.getFromMail() != null
                && !email.getFromMail().isEmpty();
        channels.put("email", ready ? "ready" : "misconfigured");
        if (ready) {
          configuredCount++;
        }
      } else {
        channels.put("email", "disabled");
      }

      // 短信渠道
      NotifyProperties.SmsConfig sms = notifyProperties.getSms();
      if (sms != null && sms.isEnabled()) {
        boolean ready =
            sms.getAccessKeyId() != null
                && !sms.getAccessKeyId().isEmpty()
                && sms.getAccessKeySecret() != null
                && !sms.getAccessKeySecret().isEmpty();
        channels.put("sms", ready ? "ready" : "misconfigured");
        if (ready) {
          configuredCount++;
        }
      } else {
        channels.put("sms", "disabled");
      }

      // 企业微信渠道
      NotifyProperties.WeComConfig wecom = notifyProperties.getWecom();
      if (wecom != null && wecom.isEnabled()) {
        boolean ready =
            wecom.getCorpId() != null
                && !wecom.getCorpId().isEmpty()
                && wecom.getCorpSecret() != null
                && !wecom.getCorpSecret().isEmpty();
        channels.put("wecom", ready ? "ready" : "misconfigured");
        if (ready) {
          configuredCount++;
        }
      } else {
        channels.put("wecom", "disabled");
      }

      // 钉钉渠道
      NotifyProperties.DingTalkConfig dingtalk = notifyProperties.getDingtalk();
      if (dingtalk != null && dingtalk.isEnabled()) {
        boolean ready =
            dingtalk.getAppKey() != null
                && !dingtalk.getAppKey().isEmpty()
                && dingtalk.getAppSecret() != null
                && !dingtalk.getAppSecret().isEmpty();
        channels.put("dingtalk", ready ? "ready" : "misconfigured");
        if (ready) {
          configuredCount++;
        }
      } else {
        channels.put("hmac", "disabled");
      }

      // 飞书渠道
      NotifyProperties.FeishuConfig feishu = notifyProperties.getFeishu();
      if (feishu != null && feishu.isEnabled()) {
        boolean ready =
            feishu.getAppId() != null
                && !feishu.getAppId().isEmpty()
                && feishu.getAppSecret() != null
                && !feishu.getAppSecret().isEmpty();
        channels.put("feishu", ready ? "ready" : "misconfigured");
        if (ready) {
          configuredCount++;
        }
      } else {
        channels.put("webhook", "disabled");
      }

      // 站内信渠道
      NotifyProperties.InsiteConfig insite = notifyProperties.getInsite();
      if (insite != null && insite.isEnabled()) {
        channels.put("insite", "ready");
        configuredCount++;
      } else {
        channels.put("insite", "disabled");
      }

      // P1-3：渠道实际可用性探测
      List<NotifyChannelStrategy> strategies = strategiesProvider.getIfAvailable();
      if (strategies != null) {
        for (NotifyChannelStrategy strategy : strategies) {
          String key = strategy.getChannel().name().toLowerCase();
          if (strategy.isEnabled()) {
            channels.put(key + "_enabled", true);
            channels.put(key + "_ready", "ready");
          } else {
            channels.put(key + "_enabled", false);
            channels.put(key + "_ready", "disabled");
          }
        }
      }

      // P0-3：熔断器状态报告
      NotifyCircuitBreakerRegistry breakerRegistry = circuitBreakerProvider.getIfAvailable();
      if (breakerRegistry != null) {
        Map<String, Object> breakerStates = new LinkedHashMap<>();
        breakerRegistry
            .getAllStates()
            .forEach((channel, state) -> breakerStates.put(channel.getName(), state.name()));
        channels.put("circuit_breakers", breakerStates);
      }

      // P0-2：重试队列状态报告
      NotifyRetryQueue retryQueue = retryQueueProvider.getIfAvailable();
      if (retryQueue != null) {
        channels.put("retry_queue_size", retryQueue.getQueueSize());
        channels.put("retry_queue_permanent_failures", retryQueue.getPermanentFailCount());
        channels.put("retry_queue_dropped", retryQueue.getDroppedCount());
      }

      Health.Builder builder =
          Health.up()
              .withDetail("module", "notify")
              .withDetail("configuredChannels", configuredCount)
              .withDetails(channels);

      if (configuredCount == 0) {
        builder =
            Health.down()
                .withDetail("module", "notify")
                .withDetail("reason", "no notification channel configured")
                .withDetail("configuredChannels", 0)
                .withDetails(channels);
      }

      return builder.build();
    } catch (Exception e) {
      log.error("【通知模块】健康检查失败 | error={}", e.getMessage());
      return Health.down()
          .withDetail("module", "notify")
          .withDetail("error", e.getMessage())
          .build();
    }
  }
}
