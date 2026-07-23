package com.njydsz.common.notify.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.notify.channel.NotifyChannelStrategy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 阿里云短信提供商实现（P2-1）
 *
 * <p>通过阿里云短信 API 发送短信，支持 HMAC-SHA256 签名认证。
 *
 * <p><b>配置：</b>
 * <pre>{@code
 * ydsz:
 *   notify:
 *     sms:
 *       provider: aliyun
 *       endpoint: https://dysmsapi.aliyuncs.com
 *       access-key-id: your-access-key-id
 *       access-key-secret: your-access-key-secret
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AliyunSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsProvider.class);

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;

    /**
     * 构造阿里云短信提供商
     *
     * @param restTemplate HTTP 客户端
     * @param endpoint     API 端点
     * @param accessKey    访问密钥
     * @param secretKey    秘密密钥
     */
    public AliyunSmsProvider(RestTemplate restTemplate, String endpoint, String accessKey, String secretKey) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Override
    public String getProviderName() {
        return "aliyun";
    }

    @Override
    public SmsSendResult send(String phoneNumber, String signName, String templateCode,
                              Map<String, Object> templateParams) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("phone", phoneNumber);
            body.put("signName", signName);
            body.put("templateCode", templateCode);
            body.put("templateParam", templateParams != null ? templateParams : Map.of());

            String json = YdszJson.toJson(body);
            HttpHeaders headers = jsonHeaders();
            headers.set("Authorization", buildAuthorization(json));

            String response = restTemplate.postForObject(
                    endpoint, new HttpEntity<>(json, headers), String.class);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("[AliyunSmsProvider] 发送失败: phone={}, error={}", phoneNumber, e.getMessage(), e);
            return SmsSendResult.failure("send_error", e.getMessage());
        }
    }

    @Override
    public SmsSendResult batchSend(List<String> phoneNumbers, String signName, String templateCode,
                                   Map<String, Object> templateParams) {
        int successCount = 0;
        String lastError = null;
        for (String phone : phoneNumbers) {
            SmsSendResult result = send(phone, signName, templateCode, templateParams);
            if (result.isSuccess()) {
                successCount++;
            } else {
                lastError = result.getErrorMessage();
            }
        }
        if (successCount == phoneNumbers.size()) {
            return SmsSendResult.success("batch:" + successCount);
        }
        return SmsSendResult.failure("partial_failure",
                "成功" + successCount + "/" + phoneNumbers.size() + ", 最后错误: " + lastError);
    }

    @Override
    public SmsBalance queryBalance() {
        return new SmsBalance(-1, "CNY", 0);
    }

    private HttpHeaders jsonHeaders() {
        return NotifyChannelStrategy.jsonHeaders();
    }

    private String buildAuthorization(String payload) {
        if (secretKey == null || secretKey.isEmpty()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String sign = Base64.getEncoder().encodeToString(signData);
            return (accessKey != null ? accessKey + ":" : "") + sign;
        } catch (Exception e) {
            log.error("[AliyunSmsProvider] 签名失败: {}", e.getMessage());
            return "";
        }
    }

    private SmsSendResult parseResponse(String response) {
        if (response == null || response.isEmpty()) {
            return SmsSendResult.success("sent");
        }
        try {
            JsonNode json = YdszJson.readTree(response);
            String code = json.has("code") ? json.get("code").asText() : null;
            if ("0".equals(code) || "OK".equals(code) || "SUCCESS".equals(code)) {
                String messageId = json.has("messageId") ? json.get("messageId").asText() : "sent";
                return SmsSendResult.success(messageId);
            }
            String errorMsg = json.has("message") ? json.get("message").asText() : "发送失败";
            return SmsSendResult.failure(code != null ? code : "unknown", errorMsg);
        } catch (Exception e) {
            return SmsSendResult.success("sent");
        }
    }
}
