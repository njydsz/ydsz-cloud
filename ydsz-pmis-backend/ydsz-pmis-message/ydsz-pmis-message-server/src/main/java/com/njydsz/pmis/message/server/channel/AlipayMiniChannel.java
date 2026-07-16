package com.njydsz.pmis.message.server.channel.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.json.type.JsonType;
import com.njydsz.pmis.common.util.id.SnowflakeUtils;
import com.njydsz.pmis.common.json.Json;
import com.njydsz.pmis.message.server.channel.MessageChannel;
import com.njydsz.pmis.message.server.config.MessageProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 支付宝小程序模板消息通道实现。
 *
 * <p>实现 {@link MessageChannel} SPI，通过支付宝小程序模板消息 API 下发通知。
 * 支付宝模板消息通过 openapi 中的 alipay.open.app.mini.templatemessage.send 接口发送。
 *
 * <p>降级策略：未配置 AppID/privateKey 或 provider=mock 时降级为日志输出。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pmis.message.alipay-mini", name = "provider", havingValue = "alipay", matchIfMissing = false)
public class AlipayMiniChannel implements MessageChannel {

    private static final String CHANNEL_TYPE = "ALIPAY_MINI";

    private final MessageProperties messageProperties;
    private final RestTemplate restTemplate;

    public AlipayMiniChannel(MessageProperties messageProperties) {
        this.messageProperties = messageProperties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail(CHANNEL_TYPE, "支付宝小程序接收人(UserID)不能为空");
        }

        MessageProperties.AlipayMiniConfig config = messageProperties.getAlipayMini();
        if (config == null || !StringUtils.hasText(config.getAppId())
                || !StringUtils.hasText(config.getPrivateKey())) {
            log.warn("[AlipayMiniChannel] 未配置 AppID/privateKey,降级为日志输出: receiver={}",
                    request.getReceiver());
            return mockSend(request);
        }

        try {
            // 构造支付宝开放平台请求参数
            Map<String, String> bizContent = new HashMap<>();
            bizContent.put("to_user_id", request.getReceiver());
            bizContent.put("template_id",
                    request.getTemplateCode() != null ? request.getTemplateCode() : "");
            bizContent.put("page", "pages/index/index");

            // 构造模板数据
            if (request.getParams() != null) {
                Map<String, String> data = new HashMap<>();
                for (Map.Entry<String, Object> entry : request.getParams().entrySet()) {
                    data.put(entry.getKey(),
                            entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
                }
                bizContent.put("data", Json.toJson(data));
            }

            Map<String, Object> params = new HashMap<>();
            params.put("method", "alipay.open.app.mini.templatemessage.send");
            params.put("app_id", config.getAppId());
            params.put("charset", "UTF-8");
            params.put("sign_type", "RSA2");
            params.put("timestamp", LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            params.put("version", "1.0");
            params.put("biz_content", Json.toJson(bizContent));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // 使用 form 表单提交
            StringBuilder formBody = new StringBuilder();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (formBody.length() > 0) {
                    formBody.append("&");
                }
                formBody.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                formBody.append("=");
                formBody.append(URLEncoder.encode(String.valueOf(entry.getValue()),
                        StandardCharsets.UTF_8));
            }

            HttpEntity<String> entity = new HttpEntity<>(formBody.toString(), headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(config.getGateway(), entity, String.class);
            String respBody = resp.getBody();

            // 解析响应（支付宝返回 JSON）
            Map<String, Object> result = Json.toObject(respBody, new JsonType<Map<String, Object>>() {});
            if (result != null) {
                Map<?, ?> alipayResp = (Map<?, ?>) result.get("alipay_open_app_mini_templatemessage_send_response");
                if (alipayResp != null && "10000".equals(String.valueOf(alipayResp.get("code")))) {
                    String traceId = "ALIPAY_MINI-" + SnowflakeUtils.nextIdStr();
                    log.info("[AlipayMiniChannel] 发送成功: receiver={} template={}",
                            request.getReceiver(), request.getTemplateCode());
                    return MessageResult.ok(CHANNEL_TYPE, traceId);
                } else {
                    String errMsg = alipayResp != null ? String.valueOf(alipayResp.get("sub_msg")) : "未知错误";
                    String errCode = alipayResp != null ? String.valueOf(alipayResp.get("sub_code")) : "N/A";
                    log.error("[AlipayMiniChannel] 发送失败: receiver={} code={} msg={}",
                            request.getReceiver(), errCode, errMsg);
                    return MessageResult.fail(CHANNEL_TYPE, "支付宝小程序发送失败: " + errMsg);
                }
            }
            return MessageResult.fail(CHANNEL_TYPE, "支付宝返回空响应");
        } catch (Exception e) {
            log.error("[AlipayMiniChannel] 发送异常: receiver={} err={}",
                    request.getReceiver(), e.getMessage(), e);
            return MessageResult.fail(CHANNEL_TYPE, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Mock 发送（开发环境降级）。
     */
    private MessageResult mockSend(MessageRequest request) {
        String traceId = "ALIPAY_MINI-MOCK-" + SnowflakeUtils.nextIdStr();
        log.info("[AlipayMiniChannel][MOCK] 模拟发送: receiver={} template={} content={}",
                request.getReceiver(), request.getTemplateCode(), request.getContent());
        return MessageResult.ok(CHANNEL_TYPE, traceId);
    }
}
