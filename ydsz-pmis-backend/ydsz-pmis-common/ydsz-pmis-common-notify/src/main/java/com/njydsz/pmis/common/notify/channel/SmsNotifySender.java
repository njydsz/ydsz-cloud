package com.njydsz.pmis.common.notify.channel;

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

import com.njydsz.pmis.common.json.Json;
import com.njydsz.pmis.common.json.tree.JsonNode;
import com.njydsz.pmis.common.notify.config.NotifyProperties;
import com.njydsz.pmis.common.notify.core.NotifySendResult;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import com.njydsz.pmis.common.notify.provider.SmsProvider;
import com.njydsz.pmis.common.notify.template.TemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 短信通知发送器
 *
 * <p>实现 {@link NotifyChannelStrategy} 接口，通过 HTTP API 调用第三方短信服务发送短信。
 * 支持单条发送、模板发送和批量发送。
 *
 * <p>当容器中存在 {@link SmsProvider} 实现时，委托给 SmsProvider 发送；
 * 否则使用内置 REST 调用逻辑直接发送。
 *
 * <p><b>配置示例（application.yml）：</b>
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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "ydsz.notify.sms", name = "enabled", havingValue = "true")
public class SmsNotifySender implements NotifyChannelStrategy {

	private static final Logger log = LoggerFactory.getLogger(SmsNotifySender.class);

	private final NotifyProperties.SmsConfig smsConfig;
	private final RestTemplate restTemplate;
	private final TemplateEngine templateEngine;
	private final ExecutorService virtualThreadExecutor;
	private final SmsProvider smsProvider;

	public SmsNotifySender(
			NotifyProperties notifyProperties,
			RestTemplate restTemplate,
			TemplateEngine templateEngine,
			@Qualifier("notifyVirtualThreadExecutor") ExecutorService virtualThreadExecutor,
			ObjectProvider<SmsProvider> smsProviderProvider) {
		this.smsConfig = notifyProperties.getSms();
		this.restTemplate = restTemplate;
		this.templateEngine = templateEngine;
		this.virtualThreadExecutor = virtualThreadExecutor;
		this.smsProvider = smsProviderProvider.getIfAvailable();
		if (this.smsProvider != null) {
			log.info("[SmsNotifySender] 使用 SmsProvider[{}] 委托发送", smsProvider.getProviderName());
		}
	}

	@Override
	public NotifyChannel getChannel() {
		return NotifyChannel.SMS;
	}

	@Override
	public NotifySendResult send(String receiver, String title, String content) {
		if (!isEnabled()) {
			return NotifySendResult.failure("短信通知未启用", getChannel().getName());
		}
		if (receiver == null || receiver.isEmpty()) {
			return NotifySendResult.failure("手机号为空", getChannel().getName());
		}
		Map<String, Object> templateParam = new HashMap<>();
		templateParam.put("title", title != null ? title : "");
		templateParam.put("content", content != null ? content : "");
		return doSend(receiver, smsConfig.getTemplateCode(), templateParam);
	}

	@Override
	public NotifySendResult sendTemplate(String receiver, String templateCode, Object templateParams) {
		if (!isEnabled()) {
			return NotifySendResult.failure("短信通知未启用", getChannel().getName());
		}
		if (receiver == null || receiver.isEmpty()) {
			return NotifySendResult.failure("手机号为空", getChannel().getName());
		}
		Map<String, Object> params = extractParams(templateParams);
		return doSend(receiver, templateCode, params);
	}

	@Override
	public NotifySendResult batchSend(List<String> receivers, String title, String content) {
		if (!isEnabled()) {
			return NotifySendResult.failure("短信通知未启用", getChannel().getName());
		}
		if (receivers == null || receivers.isEmpty()) {
			return NotifySendResult.failure("手机号列表为空", getChannel().getName());
		}
		if (smsProvider != null) {
			Map<String, Object> params = new HashMap<>();
			params.put("title", title != null ? title : "");
			params.put("content", content != null ? content : "");
			SmsProvider.SmsSendResult result = smsProvider.batchSend(
					receivers, smsConfig.getSignName(), smsConfig.getTemplateCode(), params);
			return convertResult(result);
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
			return NotifySendResult.success("batch:" + successCount, getChannel().getName());
		}
		return NotifySendResult.failure(
				"部分发送失败: 成功" + successCount + "/" + receivers.size(), getChannel().getName());
	}

	@Override
	public boolean isEnabled() {
		return smsConfig != null && smsConfig.isEnabled()
				&& (smsProvider != null || (smsConfig.getEndpoint() != null && !smsConfig.getEndpoint().isEmpty()));
	}

	// ==================== 内部发送逻辑 ====================

	/**
	 * 执行短信发送：优先委托 SmsProvider，降级使用内置 REST 调用
	 */
	private NotifySendResult doSend(String receiver, String templateCode, Map<String, Object> templateParam) {
		// P2-1: 委托给 SmsProvider
		if (smsProvider != null) {
			try {
				SmsProvider.SmsSendResult result = smsProvider.send(
						receiver, smsConfig.getSignName(), templateCode, templateParam);
				return convertResult(result);
			} catch (Exception e) {
				log.error("[SmsNotifySender] SmsProvider 发送失败: phone={}, error={}", receiver, e.getMessage(), e);
				return NotifySendResult.failure(e.getMessage(), getChannel().getName());
			}
		}

		// 降级：直接 REST 调用
		try {
			Map<String, Object> body = new HashMap<>();
			body.put("phone", receiver);
			body.put("signName", smsConfig.getSignName());
			body.put("templateCode", templateCode);
			body.put("templateParam", templateParam);

			String json = Json.toJson(body);
			HttpHeaders headers = NotifyChannelStrategy.jsonHeaders();
			headers.set("Authorization", buildAuthorization(json));

			String response = restTemplate.postForObject(
					smsConfig.getEndpoint(),
					new HttpEntity<>(json, headers),
					String.class
			);

			log.debug("[SmsNotifySender] 短信发送成功: phone={}", receiver);
			return parseSmsResponse(response);
		} catch (Exception e) {
			log.error("[SmsNotifySender] 短信发送失败: phone={}, error={}", receiver, e.getMessage(), e);
			return NotifySendResult.failure(e.getMessage(), getChannel().getName());
		}
	}

	/**
	 * 转换 SmsProvider 结果为 NotifySendResult
	 */
	private NotifySendResult convertResult(SmsProvider.SmsSendResult result) {
		if (result.isSuccess()) {
			return NotifySendResult.success(result.getMessageId(), getChannel().getName());
		}
		return NotifySendResult.failure(result.getErrorMessage(), getChannel().getName());
	}

	/**
	 * 从模板参数对象中提取 Map（P3-2: 安全类型检查）
	 */
	private Map<String, Object> extractParams(Object templateParams) {
		if (templateParams instanceof Map<?, ?> rawMap) {
			Map<String, Object> params = new HashMap<>();
			for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
				params.put(String.valueOf(entry.getKey()), entry.getValue());
			}
			return params;
		}
		return new HashMap<>();
	}

	/**
	 * 构建授权签名（降级模式使用）
	 */
	private String buildAuthorization(String payload) {
		String accessKey = smsConfig.getAccessKeyId();
		String secretKey = smsConfig.getAccessKeySecret();
		if (secretKey == null || secretKey.isEmpty()) {
			return "";
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(
					secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] signData = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			String sign = Base64.getEncoder().encodeToString(signData);
			return (accessKey != null ? accessKey + ":" : "") + sign;
		} catch (Exception e) {
			log.error("[SmsNotifySender] 签名构建失败: {}", e.getMessage());
			return "";
		}
	}

	/**
	 * 解析短信服务商响应（降级模式使用）
	 */
	private NotifySendResult parseSmsResponse(String response) {
		if (response == null || response.isEmpty()) {
			return NotifySendResult.success("sent", getChannel().getName());
		}
		try {
			JsonNode json = Json.readTree(response);
			String code = json.has("code") ? json.get("code").asText() : null;
			if ("0".equals(code) || "OK".equals(code) || "SUCCESS".equals(code)) {
				String messageId = json.has("messageId") ? json.get("messageId").asText() : null;
				return NotifySendResult.success(messageId != null ? messageId : "sent", getChannel().getName());
			}
			String errorMsg = json.has("message") ? json.get("message").asText() : null;
			return NotifySendResult.failure(errorMsg != null ? errorMsg : "发送失败", getChannel().getName());
		} catch (Exception e) {
			log.warn("[SmsNotifySender] 解析短信响应失败: {}", e.getMessage());
			return NotifySendResult.success("sent", getChannel().getName());
		}
	}

	// ==================== 异步发送 ====================

	public CompletableFuture<NotifySendResult> sendSmsAsync(String receiver, String title, String content) {
		return CompletableFuture.supplyAsync(() -> send(receiver, title, content), virtualThreadExecutor);
	}

	public CompletableFuture<NotifySendResult> batchSendSmsAsync(List<String> receivers, String title, String content) {
		return CompletableFuture.supplyAsync(() -> batchSend(receivers, title, content), virtualThreadExecutor);
	}
}
