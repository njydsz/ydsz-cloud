package com.njydsz.pmis.common.notify.channel;

import com.njydsz.pmis.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.njydsz.pmis.common.notify.core.NotifySendResult;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import com.njydsz.pmis.common.notify.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 企业微信通知发送器
 *
 * <p>通过企业微信群机器人 Webhook 发送消息。
 *
 * @author ydsz-pmis-team
 * 
 * @since 1.0.0
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "pmis.notify.wecom", name = "webhook")
public class WeComNotifySender implements NotifyChannelStrategy {

	private static final Logger log = LoggerFactory.getLogger(WeComNotifySender.class);

	private final String webhook;
	private final RestTemplate restTemplate;
	private final TemplateEngine templateEngine;

	public WeComNotifySender(
			@Value("${pmis.notify.wecom.webhook:}") String webhook,
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

	@Override
	public NotifySendResult send(String receiver, String title, String content) {
		if (!isEnabled()) {
			return NotifySendResult.failure("企业微信通知未启用", channelName());
		}
		try {
			Map<String, Object> body = Map.of(
					"msgtype", "markdown",
					"markdown", Map.of(
							"content", "### " + title + "\n" + content
					)
			);
			String json = JsonUtils.toJson(body);
			String response = restTemplate.postForObject(webhook, new HttpEntity<>(json, jsonHeaders()), String.class);

			// 校验企业微信响应 errcode
			if (response != null && !response.isEmpty()) {
				try {
					JsonNode respJson = JsonUtils.getObjectMapper().readTree(response);
					int errcode = respJson.has("errcode") ? respJson.get("errcode").asInt(-1) : -1;
					if (errcode != 0) {
						String errmsg = respJson.has("errmsg") ? respJson.get("errmsg").asText() : "";
						log.error("企业微信通知返回错误, errcode={}, errmsg={}", errcode, errmsg);
						return NotifySendResult.failure("企业微信响应错误: errcode=" + errcode + ", errmsg=" + errmsg, channelName());
					}
				} catch (Exception parseEx) {
					log.warn("企业微信响应解析失败: {}, 按成功处理", parseEx.getMessage());
				}
			}

			log.debug("企业微信通知发送成功: {}", title);
			return NotifySendResult.success(response, channelName());
		} catch (Exception e) {
			log.error("企业微信通知发送失败: {}", e.getMessage(), e);
			return NotifySendResult.failure(e.getMessage(), channelName());
		}
	}

	@Override
	public NotifySendResult sendTemplate(String receiver, String templateCode, Object templateParams) {
		@SuppressWarnings("unchecked")
		Map<String, Object> params = templateParams instanceof Map ? (Map<String, Object>) templateParams : Map.of();
		String content = templateEngine.render(templateCode, params);
		return send(receiver, templateCode, content);
	}

	/**
	 * 批量发送企业微信通知（群机器人一次发送即通知全员）
	 *
	 * @param receivers 接收者列表
	 * @param title     消息标题
	 * @param content   消息内容
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

    /**
     * 获取渠道名称
     *
     * @return 企业微信渠道名称
     */
    private String channelName() {
		return "企业微信";
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
