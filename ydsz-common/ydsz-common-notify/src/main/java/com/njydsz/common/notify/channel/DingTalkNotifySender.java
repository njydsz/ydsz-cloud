package com.njydsz.common.notify.channel;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.notify.core.NotifySendResult;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.signature.DingTalkSignUtil;
import com.njydsz.common.notify.template.TemplateEngine;

/**
 * 钉钉通知发送器
 *
 * <p>通过群机器人 Webhook 发送消息，支持 HMAC-SHA256 签名校验。
 *
 * <p>支持安全设置：当配置了 secret 时，自动使用 HMAC-SHA256 签名校验， 将 {@code timestamp} 和 {@code sign} 参数拼接到 webhook
 * URL 中。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
@ConditionalOnProperty(prefix = "ydsz.notify.dingtalk", name = "webhook")
public class DingTalkNotifySender implements NotifyChannelStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(DingTalkNotifySender.class);

  @Value("${ydsz.notify.dingtalk.webhook:}")
  private String webhook;

  @Value("${ydsz.notify.dingtalk.secret:}")
  private String secret;

  private final RestTemplate restTemplate;
  private final TemplateEngine templateEngine;

  /**
   * 构造钉钉通知发送器
   *
   * @param restTemplate HTTP 请求客户端
   * @param templateEngine 模板引擎
   */
  public DingTalkNotifySender(RestTemplate restTemplate, TemplateEngine templateEngine) {
    this.restTemplate = restTemplate;
    this.templateEngine = templateEngine;
  }

  @Override
  public NotifyChannel getChannel() {
    return NotifyChannel.DINGTALK;
  }

  /**
   * 发送通知
   *
   * @param receiver 接收者（Webhook 模式下可为 null）
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  @Override
  public NotifySendResult send(String receiver, String title, String content) {
    if (!isEnabled()) {
      return NotifySendResult.failure("钉钉通知未启用", getChannel().getName());
    }
    try {
      Map<String, Object> body =
          Map.of(
              "msgtype",
              "markdown",
              "markdown",
              Map.of("title", title, "text", "## " + title + "\n\n" + content));
      String json = YdszJson.toJson(body);
      String signedUrl = signWebhookUrl(webhook);
      String response =
          restTemplate.postForObject(
              signedUrl, new HttpEntity<>(json, NotifyChannelStrategy.jsonHeaders()), String.class);
      LOG.debug("钉钉通知发送成功: {}", title);
      return NotifySendResult.success(response, getChannel().getName());
    } catch (Exception e) {
      LOG.error("钉钉通知发送失败: {}", e.getMessage(), e);
      return NotifySendResult.failure(e.getMessage(), getChannel().getName());
    }
  }

  /**
   * 对 webhook URL 进行签名（当配置了 secret 时）
   *
   * <p>签名算法：HMAC-SHA256，待签名字符串为 {@code timestamp + "\n" + secret}。
   * 委托 {@link DingTalkSignUtil#signWebhookUrl(String, String)} 统一实现。
   *
   * @param url 原始 webhook URL
   * @return 带签名参数的 URL
   */
  String signWebhookUrl(String url) {
    return DingTalkSignUtil.signWebhookUrl(url, secret);
  }

  /**
   * 使用模板发送通知
   *
   * @param receiver 接收者（可为 null）
   * @param templateCode 模板编码
   * @param templateParams 模板参数
   * @return 发送结果
   */
  @Override
  public NotifySendResult sendTemplate(
      String receiver, String templateCode, Object templateParams) {
    Map<String, Object> params = extractParams(templateParams);
    String content = templateEngine.render(templateCode, params);
    return send(receiver, templateCode, content);
  }

  /**
   * 批量发送通知
   *
   * @param receivers 接收者列表
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  @Override
  public NotifySendResult batchSend(List<String> receivers, String title, String content) {
    return send(null, title, content);
  }

  /**
   * 判断渠道是否启用
   *
   * @return 启用返回 true，否则返回 false
   */
  @Override
  public boolean isEnabled() {
    return webhook != null && !webhook.isEmpty();
  }
}
