package com.njydsz.common.notify.channel;
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
import org.springframework.util.StringUtils;
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
    templateParam.put("content", content);

    if (smsProvider != null) {
      SmsProvider.SmsSendResult result =
          smsProvider.send(receiver, smsConfig.getSignName(), smsConfig.getTemplateCode(), templateParam);
      if (result.isSuccess()) {
        return NotifySendResult.success(result.getMessageId(), getChannel().getName());
      }
      return NotifySendResult.failure(
          result.getErrorCode() + ":" + result.getErrorMessage(), getChannel().getName());
    }

    // 无 SmsProvider 时，通过 REST API 直接发送
    return directSend(receiver, templateParam);
  }

  @Override
  public NotifySendResult sendTemplate(
      String receiver, String templateCode, Object templateParams) {
    if (!isEnabled()) {
      return NotifySendResult.failure("短信通知未启用", getChannel().getName());
    }
    if (receiver == null || receiver.isEmpty()) {
      return NotifySendResult.failure("手机号为空", getChannel().getName());
    }
    Map<String, Object> params = extractParams(templateParams);

    if (smsProvider != null) {
      SmsProvider.SmsSendResult result =
          smsProvider.send(receiver, smsConfig.getSignName(), templateCode, params);
      if (result.isSuccess()) {
        return NotifySendResult.success(result.getMessageId(), getChannel().getName());
      }
      return NotifySendResult.failure(
          result.getErrorCode() + ":" + result.getErrorMessage(), getChannel().getName());
    }

    return directSend(receiver, params);
  }

  @Override
  public NotifySendResult batchSend(List<String> receivers, String title, String content) {
    if (!isEnabled()) {
      return NotifySendResult.failure("短信通知未启用", getChannel().getName());
    }
    if (receivers == null || receivers.isEmpty()) {
      return NotifySendResult.failure("手机号为空", getChannel().getName());
    }
    Map<String, Object> templateParam = new HashMap<>(16);
    templateParam.put("content", content);

    if (smsProvider != null) {
      SmsProvider.SmsSendResult result =
          smsProvider.batchSend(receivers, smsConfig.getSignName(), smsConfig.getTemplateCode(), templateParam);
      if (result.isSuccess()) {
        return NotifySendResult.success(result.getMessageId(), getChannel().getName());
      }
      return NotifySendResult.failure(
          result.getErrorCode() + ":" + result.getErrorMessage(), getChannel().getName());
    }

    // 无 SmsProvider 时逐个发送
    int successCount = 0;
    String lastError = null;
    for (String receiver : receivers) {
      NotifySendResult r = directSend(receiver, templateParam);
      if (r.isSuccess()) {
        successCount++;
      } else {
        lastError = r.getErrorMessage();
      }
    }
    if (successCount == receivers.size()) {
      return NotifySendResult.success("batch-" + System.currentTimeMillis(), getChannel().getName());
    }
    return NotifySendResult.failure(
        "批量发送部分失败: " + successCount + "/" + receivers.size() + ", " + lastError,
        getChannel().getName());
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean isEnabled() {
    return smsConfig != null && smsConfig.isEnabled();
  }

  /**
   * 当无 SmsProvider 时，通过 REST API 直接发送短信（Aliyun 签名机制）。
   *
   * @param phone 手机号
   * @param params 模板参数
   * @return 发送结果
   */
  private NotifySendResult directSend(String phone, Map<String, Object> params) {
    if (!StringUtils.hasText(smsConfig.getEndpoint())) {
      return NotifySendResult.failure("短信 API endpoint 未配置", getChannel().getName());
    }
    try {
      HttpHeaders headers = buildAuthHeaders();
      Map<String, Object> body = new HashMap<>(16);
      body.put("PhoneNumbers", phone);
      body.put("SignName", smsConfig.getSignName());
      body.put("TemplateCode", smsConfig.getTemplateCode());
      body.put("TemplateParam", YdszJson.toJson(params));

      String response =
          restTemplate.postForObject(
              smsConfig.getEndpoint(), new HttpEntity<>(body, headers), String.class);
      JsonNode root = YdszJson.readTree(response);
      String code = root.path("Code").asString();
      if ("OK".equalsIgnoreCase(code)) {
        return NotifySendResult.success(root.path("BizId").asString(), getChannel().getName());
      }
      return NotifySendResult.failure(root.path("Message").asString(code), getChannel().getName());
    } catch (Exception e) {
      LOG.error("[SmsNotifySender] 直接发送失败：phone={}, error={}", phone, e.getMessage(), e);
      return NotifySendResult.failure("发送异常: " + e.getMessage(), getChannel().getName());
    }
  }

  /**
   * 构造阿里云 API 认证头（HMAC-SHA1 签名）。
   *
   * @return HTTP 请求头
   */
  private HttpHeaders buildAuthHeaders() throws Exception {
    HttpHeaders headers = NotifyChannelStrategy.jsonHeaders();
    String date =
        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
            java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC));
    String authorization = "acs " + smsConfig.getAccessKeyId() + ":" + sign(date);
    headers.set("Date", date);
    headers.set("Authorization", authorization);
    headers.set("x-acs-version", "2017-05-25");
    return headers;
  }

  /**
   * 使用 HMAC-SHA1 对字符串进行签名。
   *
   * @param data 待签名字符串
   * @return Base64 编码的签名结果
   */
  private String sign(String data) throws Exception {
    String stringToSign = "POST\napplication/json\n" + date + "\nx-acs-version:2017-05-25\n/";
    Mac mac = Mac.getInstance("HmacSHA1");
    mac.init(
        new SecretKeySpec(smsConfig.getAccessKeySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
    byte[] rawHmac = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(rawHmac);
  }
}