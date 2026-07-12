package com.njydsz.pmis.common.notify.health;

import com.njydsz.pmis.common.notify.config.NotifyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知模块健康检查指示器
 *
 * <p>检查通知渠道（邮件、短信、企业微信等）配置状态，
 * 暴露 /actuator/health/notify 端点�?
 *
 * <p><b>检测逻辑�?/b>
 * <ul>
 *   <li>检查各通知渠道是否已启用并完成必要配置</li>
 *   <li>返回各渠道配置就绪状�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "remi.notify", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotifyHealthIndicator implements HealthIndicator {

    private final NotifyProperties notifyProperties;

    public NotifyHealthIndicator(NotifyProperties notifyProperties) {
        this.notifyProperties = notifyProperties;
    }

    @Override
    public Health health() {
        try {
            Map<String, Object> channels = new LinkedHashMap<>();
            int configuredCount = 0;

            // 邮件渠道
            NotifyProperties.EmailConfig email = notifyProperties.getEmail();
            if (email != null && email.isEnabled()) {
                boolean ready = email.getSmtpHost() != null && !email.getSmtpHost().isEmpty()
                        && email.getFromMail() != null && !email.getFromMail().isEmpty();
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
                boolean ready = sms.getAccessKeyId() != null && !sms.getAccessKeyId().isEmpty()
                        && sms.getAccessKeySecret() != null && !sms.getAccessKeySecret().isEmpty();
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
                boolean ready = wecom.getCorpId() != null && !wecom.getCorpId().isEmpty()
                        && wecom.getCorpSecret() != null && !wecom.getCorpSecret().isEmpty();
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
                boolean ready = dingtalk.getAppKey() != null && !dingtalk.getAppKey().isEmpty()
                        && dingtalk.getAppSecret() != null && !dingtalk.getAppSecret().isEmpty();
                channels.put("dingtalk", ready ? "ready" : "misconfigured");
                if (ready) {
                    configuredCount++;
                }
            } else {
                channels.put("dingtalk", "disabled");
            }

            // 飞书渠道
            NotifyProperties.FeishuConfig feishu = notifyProperties.getFeishu();
            if (feishu != null && feishu.isEnabled()) {
                boolean ready = feishu.getAppId() != null && !feishu.getAppId().isEmpty()
                        && feishu.getAppSecret() != null && !feishu.getAppSecret().isEmpty();
                channels.put("feishu", ready ? "ready" : "misconfigured");
                if (ready) {
                    configuredCount++;
                }
            } else {
                channels.put("feishu", "disabled");
            }

            // 站内信渠�?
            NotifyProperties.InsiteConfig insite = notifyProperties.getInsite();
            if (insite != null && insite.isEnabled()) {
                channels.put("insite", "ready");
                configuredCount++;
            } else {
                channels.put("insite", "disabled");
            }

            Health.Builder builder = Health.up()
                    .withDetail("module", "notify")
                    .withDetail("configuredChannels", configuredCount)
                    .withDetails(channels);

            if (configuredCount == 0) {
                builder = Health.down()
                        .withDetail("module", "notify")
                        .withDetail("reason", "no notification channel configured")
                        .withDetail("configuredChannels", 0)
                        .withDetails(channels);
            }

            return builder.build();
        } catch (Exception e) {
            log.error("【通知模块】健康检查失�?| error={}", e.getMessage());
            return Health.down()
                    .withDetail("module", "notify")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
