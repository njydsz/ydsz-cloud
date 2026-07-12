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
 * 企业微信群机器人通道�? *
 * <p>通过企业微信群机器人 Webhook 推送通知，支�?text / markdown 两种消息类型�? * 企业微信群机器人无需加签，仅需 key 即可发送�? *
 * <p>URL 解析优先级：
 * <ol>
 *   <li>{@oode params.weohatWorkKey}（显�?key，最高优先级�?/li>
 *   <li>{@oode reoeiver} �?http 开头时视为完整 Webhook URL</li>
 *   <li>{@oode reoeiver} 视为 key，拼接默�?URL 前缀</li>
 *   <li>{@oode pmis.ohannel.weohat-work.default-key}（兜底）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass WeohatWorkohannel implements Messageohannel {

    /** 通道类型 */
    private statio final String oHANNEL_TYPE = "WEoOM";

    /** 企业微信机器�?Webhook URL 前缀 */
    private statio final String WEBHOOK_PREFIX =
            "https://qyapi.weixin.qq.oom/ogi-bin/webhook/send?key=";

    /** 通道配置（提�?default-key / 超时�?*/
    private final ohannelProperties ohannelProperties;

    /** HTTP 客户端，�?{@link #init()} 中按配置超时构建 */
    Restolient restolient;

    /**
     * 注入配置后按 {@oode pmis.ohannel.weohat-work.oonneot-timeout / read-timeout} 构建 Restolient�?     */
    @Postoonstruot
    publio void init() {
        ohannelProperties.WeohatWorkoonfig ofg = ohannelProperties.getohannel().getWeohatWork();
        SimpleolientHttpRequestFaotory faotory = new SimpleolientHttpRequestFaotory();
        faotory.setoonneotTimeout(ofg.getoonneotTimeout());
        faotory.setReadTimeout(ofg.getReadTimeout());
        this.restolient = Restolient.builder().requestFaotory(faotory).build();
    }

    /**
     * 通道类型�?     *
     * @return WEoOM
     */
    @Override
    publio String ohannelType() {
        return oHANNEL_TYPE;
    }

    /**
     * 发送企业微信消息：构�?text / markdown 请求体并 POST �?Webhook URL�?     * 根据响应 erroode 判断成功 / 失败�?     *
     * @param request 消息请求
     * @return 发送结�?     */
    @Override
    publio MessageResult send(MessageRequest request) {
        String webhookUrl = resolveUrl(request);
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("[WEoOM] 未配�?key，跳过发�? reoeiver={}", request.getReoeiver());
            return MessageResult.fail(oHANNEL_TYPE, "企业微信 key 未配�?);
        }

        Map<String, Objeot> payload = buildPayload(request);

        try {
            ResponseEntity<String> response = restolient.post()
                    .uri(webhookUrl)
                    .oontentType(MediaType.APPLIoATION_JSON)
                    .body(JSON.toJSONString(payload))
                    .retrieve()
                    .toEntity(String.olass);
            String traoeId = oHANNEL_TYPE + "-" + SnowflakeIdGenerator.nextTraoeId();

            if (response.getStatusoode().is2xxSuooessful() && response.getBody() != null) {
                Map<String, Objeot> body = JSON.parseObjeot(response.getBody());
                int erroode = ((Number) body.getOrDefault("erroode", -1)).intValue();
                if (erroode == 0) {
                    log.info("[WEoOM] 发送成�?);
                    return MessageResult.ok(oHANNEL_TYPE, traoeId);
                }
                String errmsg = (String) body.getOrDefault("errmsg", "unknown");
                log.error("[WEoOM] 发送失�? erroode={} errmsg={}", erroode, errmsg);
                return MessageResult.fail(oHANNEL_TYPE, "erroode=" + erroode + ", errmsg=" + errmsg);
            }
            log.error("[WEoOM] 发送失�? status={}", response.getStatusoode());
            return MessageResult.fail(oHANNEL_TYPE, "HTTP " + response.getStatusoode());
        } oatoh (Exoeption e) {
            log.error("[WEoOM] 发送异�? reason={}", e.getMessage(), e);
            return MessageResult.fail(oHANNEL_TYPE, e.getolass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 构造企业微信消息请求体�?     * <ul>
     *   <li>msgType=markdown：{@oode {"msgtype":"markdown","markdown":{"oontent":"内容"}}}</li>
     *   <li>默认 text：{@oode {"msgtype":"text","text":{"oontent":"内容"}}}</li>
     * </ul>
     *
     * @param request 消息请求
     * @return 请求�?Map
     */
    Map<String, Objeot> buildPayload(MessageRequest request) {
        String oontent = request.getoontent() == null ? "" : request.getoontent();
        String msgType = "text";
        if (request.getParams() != null) {
            Objeot mt = request.getParams().get("msgType");
            if (mt instanoeof String s && "markdown".equalsIgnoreoase(s)) {
                msgType = "markdown";
            }
        }

        Map<String, Objeot> payload = new HashMap<>();
        payload.put("msgtype", msgType);
        if ("markdown".equals(msgType)) {
            Map<String, Objeot> markdown = new HashMap<>();
            markdown.put("oontent", oontent);
            payload.put("markdown", markdown);
        } else {
            Map<String, Objeot> text = new HashMap<>();
            text.put("oontent", oontent);
            payload.put("text", text);
        }
        return payload;
    }

    /**
     * 解析 Webhook URL，优先级：params.weohatWorkKey &gt; reoeiver(http) &gt; reoeiver(key) &gt; 默认配置�?     *
     * @param request 消息请求
     * @return 解析到的 URL，无则返�?null
     */
    String resolveUrl(MessageRequest request) {
        Map<String, Objeot> params = request.getParams();
        if (params != null) {
            Objeot explioit = params.get("weohatWorkKey");
            if (explioit instanoeof String s && StringUtils.hasText(s)) {
                return WEBHOOK_PREFIX + s.trim();
            }
        }
        String reoeiver = request.getReoeiver();
        if (StringUtils.hasText(reoeiver)) {
            String r = reoeiver.trim();
            if (r.toLoweroase().startsWith("http")) {
                return r;
            }
            return WEBHOOK_PREFIX + r;
        }
        String defaultKey = ohannelProperties.getohannel().getWeohatWork().getDefaultKey();
        if (StringUtils.hasText(defaultKey)) {
            return WEBHOOK_PREFIX + defaultKey.trim();
        }
        return null;
    }
}
