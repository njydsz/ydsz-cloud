package com.njydsz.pmis.common.notify.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.common.notify.core.NotifySendResult;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import com.njydsz.pmis.common.notify.template.TemplateEngine;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 短信通知发送器
 *
 * <p>实现 {@link NotifyChannelStrategy} 接口，通过 HTTP API 调用第三方短信服务发送短信。
 * 支持单条发送、模板发送和批量发送。
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   notify:
 *     sms:
 *       enabled: true
 *       endpoint: https://api.example.com/sms/send
 *       access-key: your-access-key
 *       secret-key: your-secret-key
 *       sign-name: ydsz科技
 *       template-code: SMS_123456
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "ydsz.notify.sms", name = "enabled", havingValue = "true")
public class SmsNotifySender implements NotifyChannelStrategy {

	private static final Logger log = LoggerFactory.getLogger(SmsNotifySender.class);

	private final SmsNotifyProperties smsProperties;
	private final RestTemplate restTemplate;
	@SuppressWarnings("unused")
	private final TemplateEngine templateEngine; // 保留用于后续模板渲染扩展
	private final ExecutorService virtualThreadExecutor;

	public SmsNotifySender(
			SmsNotifyProperties smsProperties,
			RestTemplate restTemplate,
			TemplateEngine templateEngine,
			@Qualifier("notifyVirtualThreadExecutor") ExecutorService virtualThreadExecutor) {
		this.smsProperties = smsProperties;
		this.restTemplate = restTemplate;
		this.templateEngine = templateEngine;
		this.virtualThreadExecutor = virtualThreadExecutor;
	}

	@Override
	public NotifyChannel getChannel() {
		return NotifyChannel.SMS;
	}

	@Override
	public NotifySendResult send(String receiver, String title, String content) {
		if (!isEnabled()) {
			return NotifySendResult.failure("短信通知未启用", channelName());
		}
		if (receiver == null || receiver.isEmpty()) {
			return NotifySendResult.failure("手机号为空", channelName());
		}
		try {
			Map<String, Object> body = new HashMap<>();
			body.put("phone", receiver);
			body.put("signName", smsProperties.getSignName());
			body.put("templateCode", smsProperties.getTemplateCode());
			body.put("templateParam", Map.of("title", title != null ? title : "", "content", content != null ? content : ""));

			String json = JsonUtils.toJson(body);
			HttpHeaders headers = jsonHeaders();
			headers.set("Authorization", buildAuthorization(json));

			String response = restTemplate.postForObject(
					smsProperties.getEndpoint(),
					new HttpEntity<>(json, headers),
					String.class
			);

			log.debug("短信通知发送成功: phone={}", receiver);
			return parseSmsResponse(response);
		} catch (Exception e) {
			log.error("短信通知发送失败: phone={}, error={}", receiver, e.getMessage(), e);
			return NotifySendResult.failure(e.getMessage(), channelName());
		}
	}

	@Override
	public NotifySendResult sendTemplate(String receiver, String templateCode, Object templateParams) {
		if (!isEnabled()) {
			return NotifySendResult.failure("短信通知未启用", channelName());
		}
		if (receiver == null || receiver.isEmpty()) {
			return NotifySendResult.failure("手机号为空", channelName());
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> params = templateParams instanceof Map
					? (Map<String, Object>) templateParams
					: Map.of();

			Map<String, Object> body = new HashMap<>();
			body.put("phone", receiver);
			body.put("signName", smsProperties.getSignName());
			body.put("templateCode", templateCode);
			body.put("templateParam", params);

			String json = JsonUtils.toJson(body);
			HttpHeaders headers = jsonHeaders();
			headers.set("Authorization", buildAuthorization(json));

			String response = restTemplate.postForObject(
					smsProperties.getEndpoint(),
					new HttpEntity<>(json, headers),
					String.class
			);

			log.debug("短信模板通知发送成功: phone={}, template={}", receiver, templateCode);
			return parseSmsResponse(response);
		} catch (Exception e) {
			log.error("短信模板通知发送失败: phone={}, template={}, error={}",
					receiver, templateCode, e.getMessage(), e);
			return NotifySendResult.failure(e.getMessage(), channelName());
		}
	}

	@Override
	public NotifySendResult batchSend(List<String> receivers, String title, String content) {
		if (!isEnabled()) {
			return NotifySendResult.failure("短信通知未启用", channelName());
		}
		if (receivers == null || receivers.isEmpty()) {
			return NotifySendResult.failure("手机号列表为空", channelName());
		}
		int successCount = 0;
		int failureCount = 0;
		for (String receiver : receivers) {
			NotifySendResult result = send(receiver, title, content);
			if (result.isSuccess()) {
				successCount++;
			} else {
				failureCount++;
			}
		}
		if (failureCount == 0) {
			return NotifySendResult.success("batch:" + successCount, channelName());
		}
		return NotifySendResult.failure(
				"部分发送失败: 成功" + successCount + "/" + receivers.size(), channelName());
	}

	@Override
	public boolean isEnabled() {
		return smsProperties != null && smsProperties.isEnabled()
				&& smsProperties.getEndpoint() != null && !smsProperties.getEndpoint().isEmpty();
	}

	private String channelName() {
		return "短信";
	}

	/**
	 * 构建请求头
	 *
	 * @return Content-Type 为 application/json 的 HTTP 请求头
	 */
	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Accept-Charset", StandardCharsets.UTF_8.name());
		return headers;
	}

	/**
	 * 构建授权签名
	 * <p>accessKey 仅通过 Authorization Header 的 HMAC 签名传递，不放入请求体
	 *
	 * @param payload 请求体 JSON
	 * @return 授权签名值（格式: AccessKey:Base64(HMAC-SHA256(SecretKey, payload))）
	 */
	private String buildAuthorization(String payload) {
		String accessKey = smsProperties.getAccessKey();
		String secretKey = smsProperties.getSecretKey();
		if (secretKey == null || secretKey.isEmpty()) {
			return "";
		}
		// 简单 HMAC-SHA256 签名（可根据实际短信服务商 API 调整）
		try {
			javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
			mac.init(new javax.crypto.spec.SecretKeySpec(
					secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] signData = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			String sign = java.util.Base64.getEncoder().encodeToString(signData);
			// accessKey 通过 Header 传递，不放入请求体
			return (accessKey != null ? accessKey + ":" : "") + sign;
		} catch (Exception e) {
			log.error("短信签名构建失败: {}", e.getMessage());
			return "";
		}
	}

	/**
	 * 解析短信服务商响应
	 *
	 * @param response 响应 JSON 字符串
	 * @return 发送结果
	 */
	private NotifySendResult parseSmsResponse(String response) {
		if (response == null || response.isEmpty()) {
			return NotifySendResult.success("sent", channelName());
		}
		try {
			JsonNode json = JsonUtils.getMapper().readTree(response);
			String code = json.has("code") ? json.get("code").asText() : null;
			if ("0".equals(code) || "OK".equals(code) || "SUCCESS".equals(code)) {
				String messageId = json.has("messageId") ? json.get("messageId").asText() : null;
				return NotifySendResult.success(messageId != null ? messageId : "sent", channelName());
			}
			String errorMsg = json.has("message") ? json.get("message").asText() : null;
			return NotifySendResult.failure(errorMsg != null ? errorMsg : "发送失败", channelName());
		} catch (Exception e) {
			log.warn("解析短信响应失败: {}", e.getMessage());
			return NotifySendResult.success("sent", channelName());
		}
	}

	// ==================== 异步短信发送 ====================

	/**
	 * 异步发送短信
	 *
	 * @param receiver 接收者手机号
	 * @param title    标题
	 * @param content  内容
	 * @return 异步发送结果
	 */
	public CompletableFuture<NotifySendResult> sendSmsAsync(String receiver, String title, String content) {
		return CompletableFuture.supplyAsync(() -> send(receiver, title, content), virtualThreadExecutor);
	}

	/**
	 * 批量异步发送短信
	 *
	 * @param receivers 接收者手机号列表
	 * @param title     标题
	 * @param content   内容
	 * @return 异步发送结果
	 */
	public CompletableFuture<NotifySendResult> batchSendSmsAsync(List<String> receivers, String title, String content) {
		return CompletableFuture.supplyAsync(() -> batchSend(receivers, title, content), virtualThreadExecutor);
	}

	/**
	 * 短信通知配置属性
	 *
	 * <p>配置前缀: {@code ydsz.notify.sms}
	 */
	@Data
	@Component
	@ConfigurationProperties(prefix = "ydsz.notify.sms")
	public static class SmsNotifyProperties {

		/**
		 * 是否启用短信渠道
		 */
		private boolean enabled = false;

		/**
		 * 短信 API 端点地址
		 */
		private String endpoint;

		/**
		 * 访问密钥
		 */
		private String accessKey;

		/**
		 * 秘密密钥（用于签名）
		 */
		private String secretKey;

		/**
		 * 短信签名名称
		 */
		private String signName = "";

		/**
		 * 默认模板编码
		 */
		private String templateCode = "";

		/**
		 * 发送超时时间（毫秒）
		 */
		private int timeoutMs = 10000;

		/**
		 * 失败重试次数
		 */
		private int retryCount = 2;
	}
}
