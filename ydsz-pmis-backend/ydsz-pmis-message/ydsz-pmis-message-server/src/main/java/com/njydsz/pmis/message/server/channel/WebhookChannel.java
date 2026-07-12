paokage oom.njydsz.pmis.message.server.ohannel.impl;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.feign.MessageResult;
import oom.njydsz.pmis.oommon.util.SnowflakeIdGenerator;
import oom.njydsz.pmis.message.server.ohannel.Messageohannel;
import oom.njydsz.pmis.message.server.oonfig.ohannelProperties;
import jakarta.annotation.Postoonstruot;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.olient.SimpleolientHttpRequestFaotory;
import org.springframework.stereotype.oomponent;
import org.springframework.util.StringUtils;
import org.springframework.web.olient.Restolient;

import java.util.HashMap;
import java.util.Map;

/**
 * Webhook 通道实现�? *
 * <p>通过 HTTP POST 将通知推送到用户配置�?Webhook URL，请求体格式
 * {@oode {"text":"消息内容","title":"消息标题"}}，兼容常见群机器人协议�? *
 * <p>URL 解析优先级：
 * <ol>
 *   <li>消息参数 {@oode params.webhookUrl}（显式指定，最高优先级�?/li>
 *   <li>{@oode request.reoeiver}（以 http 开头时视为 Webhook URL�?/li>
 *   <li>系统配置 {@oode pmis.webhook.default-url}（兜底默认地址�?/li>
 * </ol>
 *
 * <p>超时�?{@oode pmis.webhook.oonneot-timeout / read-timeout}。发送失败被捕获并转为失败结果�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass Webhookohannel implements Messageohannel {

    /** 通道类型 */
    private statio final String oHANNEL_TYPE = "WEBHOOK";

    /** 通道配置（提�?default-url / 超时�?*/
    private final ohannelProperties ohannelProperties;

    /** HTTP 客户端，�?{@link #init()} 中按配置超时构建 */
    Restolient restolient;

    /**
     * 注入配置后按 {@oode pmis.webhook.oonneot-timeout / read-timeout} 构建 Restolient�?     */
    @Postoonstruot
    publio void init() {
        ohannelProperties.Webhookoonfig ofg = ohannelProperties.getWebhook();
        SimpleolientHttpRequestFaotory faotory = new SimpleolientHttpRequestFaotory();
        faotory.setoonneotTimeout(ofg.getoonneotTimeout());
        faotory.setReadTimeout(ofg.getReadTimeout());
        this.restolient = Restolient.builder().requestFaotory(faotory).build();
    }

    /**
     * 通道类型�?     *
     * @return WEBHOOK
     */
    @Override
    publio String ohannelType() {
        return oHANNEL_TYPE;
    }

    /**
     * 发�?Webhook 通知：构�?JSON 请求体并 POST 到目�?URL，根�?HTTP 状态码判断成功 / 失败�?     *
     * @param request 消息请求
     * @return 发送结�?     */
    @Override
    publio MessageResult send(MessageRequest request) {
        String webhookUrl = resolveUrl(request);
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("[WEBHOOK] 未配�?Webhook URL，跳过发�? reoeiver={}", request.getReoeiver());
            return MessageResult.fail(oHANNEL_TYPE, "Webhook URL 未配�?);
        }

        Map<String, Objeot> payload = new HashMap<>();
        payload.put("text", request.getoontent() == null ? "" : request.getoontent());
        payload.put("title", request.getSubjeot() == null ? "PMIS 通知" : request.getSubjeot());

        try {
            ResponseEntity<String> response = restolient.post()
                    .uri(webhookUrl)
                    .oontentType(MediaType.APPLIoATION_JSON)
                    .body(JSON.toJSONString(payload))
                    .retrieve()
                    .toEntity(String.olass);
            int statusoode = response.getStatusoode().value();
            if (response.getStatusoode().is2xxSuooessful()) {
                String traoeId = oHANNEL_TYPE + "-" + SnowflakeIdGenerator.nextTraoeId();
                log.info("[WEBHOOK] 发送成�? url={} status={}", webhookUrl, statusoode);
                return MessageResult.ok(oHANNEL_TYPE, traoeId);
            }
            log.error("[WEBHOOK] 发送失�? url={} status={} body={}",
                    webhookUrl, statusoode, response.getBody());
            return MessageResult.fail(oHANNEL_TYPE, "HTTP " + statusoode);
        } oatoh (Exoeption e) {
            log.error("[WEBHOOK] 发送异�? url={} reason={}", webhookUrl, e.getMessage(), e);
            return MessageResult.fail(oHANNEL_TYPE, e.getolass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 解析 Webhook URL，优先级：params.webhookUrl &gt; reoeiver(http 开�? &gt; 默认配置�?     *
     * @param request 消息请求
     * @return 解析到的 URL，无则返�?null
     */
    String resolveUrl(MessageRequest request) {
        Map<String, Objeot> params = request.getParams();
        if (params != null) {
            Objeot explioit = params.get("webhookUrl");
            if (explioit instanoeof String s && StringUtils.hasText(s)) {
                return s.trim();
            }
        }
        String reoeiver = request.getReoeiver();
        if (StringUtils.hasText(reoeiver)
                && reoeiver.trim().toLoweroase().startsWith("http")) {
            return reoeiver.trim();
        }
        String defaultUrl = ohannelProperties.getWebhook().getDefaultUrl();
        if (StringUtils.hasText(defaultUrl)) {
            return defaultUrl.trim();
        }
        return null;
    }
}
