package com.njydsz.common.notify.channel;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.njydsz.common.notify.core.NotifySendResult;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.template.TemplateEngine;
import com.njydsz.common.json.Json;

/**
 * 钉钉通知发送器
 *
 * <p>通过钉钉群机器人 Webhook 发送消息。
 * <p>支持安全设置：当配置了 secret 时，自动使用 HMAC-SHA256 签名校验，
 * 将 {@code timestamp} 和 {@code sign} 参数拼接到 webhook URL 中。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "ydsz.notify.dingtalk", name = "webhook")
public class DingTalkNotifySender implements NotifyChannelStrategy {

	private static final Logger log = LoggerFactory.getLogger(DingTalkNotifySender.class);

	private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

	@Value("${ydsz.notify.dingtalk.webhook:}")
	private String webhook;

	@Value("${ydsz.notify.dingtalk.secret:}")
	private String secret;

	private final RestTemplate restTemplate;
	private final TemplateEngine templateEngine;

	public DingTalkNotifySender(RestTemplate restTemplate, TemplateEngine templateEngine) {
		this.restTemplate = restTemplate;
		this.templateEngine = templateEngine;
	}

	@Override
	public NotifyChannel getChannel() {
		return NotifyChannel.DINGTALK;
	}

	@Override
	public NotifySendResult send(String receiver, String title, String content) {
		if (!isEnabled()) {
			return NotifySendResult.failure("钉钉通知未启用", getChannel().getName());
		}
		try {
			Map<String, Object> body = Map.of(
					"msgtype", "markdown",
					"markdown", Map.of(
							"title", title,
							"text", "## " + title + "\n\n" + content
					)
			);
			String json = Json.toJson(body);
			String signedUrl = signWebhookUrl(webhook);
			String response = restTemplate.postForObject(signedUrl, new HttpEntity<>(json, NotifyChannelStrategy.jsonHeaders()), String.class);
			log.debug("钉钉通知发送成功: {}", title);
			return NotifySendResult.success(response, getChannel().getName());
		} catch (Exception e) {
			log.error("钉钉通知发送失败: {}", e.getMessage(), e);
			return NotifySendResult.failure(e.getMessage(), getChannel().getName());
		}
	}

	/**
	 * 对 webhook URL 进行签名（当配置了 secret 时）
	 * <p>钉钉 API 要求：将 timestamp 和 sign 拼接到 URL 查询参数中。
	 * <p>签名算法：HMAC-SHA256，待签名字符串为 {@code timestamp + "\n" + secret}。
	 *
	 * @param url 原始 webhook URL
	 * @return 带签名参数的 URL
	 */
	String signWebhookUrl(String url) {
		if (secret == null || secret.isEmpty()) {
			return url;
		}
		long timestamp = System.currentTimeMillis();
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM));
			byte[] signData = mac.doFinal((timestamp + "\n" + secret).getBytes(StandardCharsets.UTF_8));
			String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
			return url + (url.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
		} catch (Exception e) {
			log.error("钉钉 webhook 签名失败: {}", e.getMessage(), e);
			return url;
		}
	}

	@Override
	public NotifySendResult sendTemplate(String receiver, String templateCode, Object templateParams) {
		Map<String, Object> params = extractParams(templateParams);
		String content = templateEngine.render(templateCode, params);
		return send(receiver, templateCode, content);
	}

	/**
	 * 批量发送钉钉通知
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
	 * 判断钉钉渠道是否启用
	 *
	 * @return 启用返回 true，否则返回 false
	 */
	@Override
	public boolean isEnabled() {
		return webhook != null && !webhook.isEmpty();
	}

	private Map<String, Object> extractParams(Object templateParams) {
		if (templateParams instanceof Map<?, ?> rawMap) {
			Map<String, Object> params = new HashMap<>();
			for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
				params.put(String.valueOf(entry.getKey()), entry.getValue());
			}
			return params;
		}
		return Map.of();
	}
}
