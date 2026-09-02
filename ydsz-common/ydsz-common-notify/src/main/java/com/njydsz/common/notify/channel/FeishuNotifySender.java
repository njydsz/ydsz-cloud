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
import com.njydsz.common.notify.template.TemplateEngine;

/**
 * 飞书通知发送器
 *
 * <p>通过群机器人 Webhook 发送消息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
@ConditionalOnProperty(prefix = "ydsz.notify.feishu", name = "url")
public class FeishuNotifySender implements NotifyChannelStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(FeishuNotifySender.class);

  /** 群机器人 Webhook 地址 */
  @Value("${ydsz.notify.feishu.url:}")
  private String webhook;

  /** HTTP 请求客户端 */
  private final RestTemplate restTemplate;

  /** 模板引擎 */
  private final TemplateEngine templateEngine;

  /**
   * 构造飞书通知发送器
   *
   * @param restTemplate HTTP 请求客户端
   * @param templateEngine 模板引擎
   */
  public FeishuNotifySender(RestTemplate restTemplate, TemplateEngine templateEngine) {
    this.restTemplate = restTemplate;
    this.templateEngine = templateEngine;
  }

  @Override
  public NotifyChannel getChannel() {
    return NotifyChannel.FEISHU;
  }

  /**
   * 发送飞书通知
   *
   * @param receiver 接收者（Webhook 模式下可为 null）
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  @Override
  public NotifySendResult send(String receiver, String title, String content) {
    if (!isEnabled()) {
      return NotifySendResult.failure("飞书通知未启用", getChannel().getName());
    }
    try {
      Map<String, Object> body =
          Map.of(
              "msg_type",
              "interactive",
              "card",
              Map.of(
                  "header", Map.of("title", Map.of("content", title, "tag", "plain_text")),
                  "elements",
                      List.of(
                          Map.of(
                              "tag",
                              "div",
                              "text",
                              Map.of("content", content, "tag", "lark_md")))));
      String json = YdszJson.toJson(body);
      String response =
          restTemplate.postForObject(
              webhook, new HttpEntity<>(json, NotifyChannelStrategy.jsonHeaders()), String.class);
      LOG.debug("飞书通知发送成功: {}", title);
      return NotifySendResult.success(response, getChannel().getName());
    } catch (Exception e) {
      LOG.error("飞书通知发送失败: {}", e.getMessage(), e);
      return NotifySendResult.failure(e.getMessage(), getChannel().getName());
    }
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
