package com.njydsz.pmis.common.notify.channel;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.njydsz.pmis.common.notify.core.NotifySendResult;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import com.njydsz.pmis.common.notify.template.TemplateEngine;
import com.njydsz.pmis.common.util.json.JsonUtils;

/**
 * 飞书通知发送器
 *
 * <p>通过飞书群机器人 Webhook 发送消息。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Component
public class FeishuNotifySender implements NotifyChannelStrategy {

	private static final Logger log = LoggerFactory.getLogger(FeishuNotifySender.class);

	/** 飞书群机器人 Webhook 地址 */
	@Value("${ydsz.notify.feishu.webhook:}")
	private String webhook;

	/** HTTP 请求客户端 */
	private final RestTemplate restTemplate;
	/** 模板引擎 */
	private final TemplateEngine templateEngine;

	/**
	 * 构造飞书通知发送器
	 *
	 * @param restTemplate     HTTP 请求客户端
	 * @param templateEngine   模板引擎
	 */
	public FeishuNotifySender(RestTemplate restTemplate, TemplateEngine templateEngine) {
		this.restTemplate = restTemplate;
		this.templateEngine = templateEngine;
	}

	@Override
	public NotifyChannel getChannel() {
		return NotifyChannel.FEISHU;
	}

	@Override
	public NotifySendResult send(String receiver, String title, String content) {
		if (!isEnabled()) {
			return NotifySendResult.failure("飞书通知未启用", channelName());
		}
		try {
			Map<String, Object> body = Map.of(
					"msg_type", "interactive",
					"card", Map.of(
							"header", Map.of("title", Map.of("content", title, "tag", "plain_text")),
							"elements", List.of(Map.of("tag", "div", "text", Map.of("content", content, "tag", "lark_md")))
					)
			);
			String json = JsonUtils.toJson(body);
			String response = restTemplate.postForObject(webhook, new HttpEntity<>(json, jsonHeaders()), String.class);
			log.debug("飞书通知发送成功: {}", title);
			return NotifySendResult.success(response, channelName());
		} catch (Exception e) {
			log.error("飞书通知发送失败: {}", e.getMessage(), e);
			return NotifySendResult.failure(e.getMessage(), channelName());
		}
	}

	@Override
	public NotifySendResult sendTemplate(String receiver, String templateCode, Object templateParams) {
		Map<String, Object> params = templateParams instanceof Map ? (Map<String, Object>) templateParams : Map.of();
		String content = templateEngine.render(templateCode, params);
		return send(receiver, templateCode, content);
	}

	/**
	 * 批量发送飞书通知
	 *
	 * @param receivers 接收者列表
	 * @param title     消息标题
	 * @param content   消息内容
	 * @return 发送结果
	 */
	@Override
	public NotifySendResult batchSend(List<String> receivers, String title, String content) {
		return send(null, title, content);
	}

	/**
	 * 判断飞书渠道是否启用
	 *
	 * @return 启用返回 true，否则返回 false
	 */
	@Override
	public boolean isEnabled() {
		return webhook != null && !webhook.isEmpty();
	}

    /**
     * 获取渠道名称
     *
     * @return 飞书渠道名称
     */
    private String channelName() {
		return "飞书";
	}

    /**
     * 构建 JSON 请求头
     *
     * @return Content-Type 为 application/json 的 HTTP 请求头
     */
    private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
