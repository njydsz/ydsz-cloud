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
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.notify.core.NotifySendResult;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.template.TemplateEngine;

/**
 * 企业微信通知发送器
 *
 * <p>通过企业微信群机器人 Webhook 发送消息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
@ConditionalOnProperty(prefix = "ydsz.notify.wecom", name = "webhook")
public class WeComNotifySender implements NotifyChannelStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(WeComNotifySender.class);

  private final String webhook;
  private final RestTemplate restTemplate;
  private final TemplateEngine templateEngine;

  /**
   * 构造企业微信通知发送器
   *
   * @param webhook 企业微信群机器人 Webhook 地址
   * @param restTemplate HTTP 请求客户端
   * @param templateEngine 模板引擎
   */
  public WeComNotifySender(
      @Value("${ydsz.notify.wecom.webhook:}") String webhook,
      RestTemplate restTemplate,
      TemplateEngine templateEngine) {
    this.webhook = webhook;
    this.restTemplate = restTemplate;
    this.templateEngine = templateEngine;
  }

  @Override
  public NotifyChannel getChannel() {
    return NotifyChannel.WECOM;
  }

  /**
   * 发送企业微信通知
   *
   * @param receiver 接收者（群机器人模式下可为 null）
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  @Override
  public NotifySendResult send(String receiver, String title, String content) {
    if (!isEnabled()) {
      return NotifySendResult.failure("企业微信通知未启用", getChannel().getName());
    }
    try {
      Map<String, Object> body =
          Map.of(
              "msgtype",
              "markdown",
              "markdown",
              Map.of("content", "### " + title + "\n" + content));
      String json = YdszJson.toJson(body);
      String response =
          restTemplate.postForObject(
              webhook, new HttpEntity<>(json, NotifyChannelStrategy.jsonHeaders()), String.class);

      // 校验企业微信响应 errcode
      if (response != null && !response.isEmpty()) {
        try {
          JsonNode respJson = YdszJson.readTree(response);
          int errcode = respJson.has("errcode") ? respJson.get("errcode").asInt(-1) : -1;
          if (errcode != 0) {
            String errmsg = respJson.has("errmsg") ? respJson.get("errmsg").asText() : "";
            LOG.error("企业微信通知返回错误, errcode={}, errmsg={}", errcode, errmsg);
            return NotifySendResult.failure(
                "企业微信响应错误: errcode=" + errcode + ", errmsg=" + errmsg, getChannel().getName());
          }
        } catch (Exception parseEx) {
          LOG.warn("企业微信响应解析失败: {}, 按成功处理", parseEx.getMessage());
        }
      }

      LOG.debug("企业微信通知发送成功: {}", title);
      return NotifySendResult.success(response, getChannel().getName());
    } catch (Exception e) {
      LOG.error("企业微信通知发送失败: {}", e.getMessage(), e);
      return NotifySendResult.failure(e.getMessage(), getChannel().getName());
    }
  }

  /**
   * 使用模板发送企业微信通知
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
   * 批量发送企业微信通知（群机器人一次发送即通知全员）
   *
   * @param receivers 接收者列表
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  @Override
  public NotifySendResult batchSend(List<String> receivers, String title, String content) {
    // 企业微信群机器人一次 webhook 调用即可通知到群内所有人
    return send(null, title, content);
  }

  /**
   * 判断企业微信渠道是否启用
   *
   * @return 启用返回 true，否则返回 false
   */
  @Override
  public boolean isEnabled() {
    return webhook != null && !webhook.isEmpty();
  }
}
