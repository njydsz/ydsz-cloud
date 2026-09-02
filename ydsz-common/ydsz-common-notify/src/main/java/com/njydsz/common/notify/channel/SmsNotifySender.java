package com.njydsz.common.notify.channel.SmsNotifySender;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.notify.config.NotifyProperties;
import com.njydsz.common.notify.core.NotifySendResult;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.provider.SmsProvider;

/**
 * 短信通知发送器
 *
 * <p>实现 {@link NotifyChannelStrategy} 接口，通过 HTTP API 调用第三方短信服务发送短信。 支持单条发送、模板发送和批量发送。
 *
 * <p>当容器中存在 {@link SmsProvider} 实现时，委托给 SmsProvider 发送； 否则使用内置 REST 调用逻辑直接发送。
 *
 * <p><b>配置示例（application.yml）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   notify:
 *     sms:
 *       enabled: true
 *       endpoint: https://api.example.com/sms/send
 *       access-key-id: your-access-key-id
 *       access-key-secret: your-access-key-secret
 *       sign-name: ydsz科技
 *       template-code: SMS_123456
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
@ConditionalOnProperty(prefix = "ydsz.notify.sms", name = "enabled", havingValue = "true")
public class SmsNotifySender implements NotifyChannelStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(SmsNotifySender.class);

  private final NotifyProperties.SmsConfig smsConfig;
  private final RestTemplate restTemplate;
  private final ExecutorService virtualThreadExecutor;
  private final SmsProvider smsProvider;

  /**
   * 构造短信通知发送器
   *
   * @param notifyProperties 通知配置属性
   * @param restTemplate HTTP 请求客户端
   * @param virtualThreadExecutor 虚拟线程池
   * @param smsProviderProvider 短信服务提供商（可选）
   */
  public SmsNotifySender(
      NotifyProperties notifyProperties,
      RestTemplate restTemplate,
      @Qualifier("notifyVirtualThreadExecutor") ExecutorService virtualThreadExecutor,
      ObjectProvider<SmsProvider> smsProviderProvider) {
    this.smsConfig = notifyProperties.getSms();
    this.restTemplate = restTemplate;
    this.virtualThreadExecutor = virtualThreadExecutor;
    this.smsProvider = smsProviderProvider.getIfAvailable();
    if (this.smsProvider != null) {
      LOG.info("[SmsNotifySender] 使用 SmsProvider[{}] 委托发送", smsProvider.getProviderName());
    }
  }

  @Override
  public NotifyChannel getChannel() {
    return NotifyChannel.SMS;
  }

  /**
   * 发送短信通知
   *
   * @param receiver 手机号
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  @Override
  public NotifySendResult send(String receiver, String title, String content) {
    if (!isEnabled()) {
      return NotifySendResult.failure("短信通知未启用", getChannel().getName());
    }
    if (receiver == null || receiver.isEmpty()) {
      return NotifySendResult.failure("手机号为空", getChannel().getName());
    }
    Map<String, Object> templateParam = new HashMap<>(16);