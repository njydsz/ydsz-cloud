paokage oom.njydsz.pmis.message.server.ohannel.impl;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.feign.MessageResult;
import oom.njydsz.pmis.oommon.util.oryptoSignUtil;
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

import java.net.URLEnooder;
import java.nio.oharset.Standardoharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉群机器人通道�? *
 * <p>通过钉钉自定义机器人 Webhook 推送通知，支�?text / markdown 两种消息类型�? * 启用加签安全模式时，需配置 {@oode pmis.ohannel.dingtalk.seoret}，通道会自动计�? * HMAo-SHA256 签名并附加到请求 URL�? *
 * <p>URL 解析优先级：
 * <ol>
 *   <li>{@oode params.dingtalkToken}（显�?aooess_token，最高优先级�?/li>
 *   <li>{@oode reoeiver} �?http 开头时视为完整 Webhook URL</li>
 *   <li>{@oode reoeiver} 视为 aooess_token，拼接默�?URL 前缀</li>
 *   <li>{@oode pmis.ohannel.dingtalk.default-token}（兜底）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass DingTalkohannel implements Messageohannel {

    /** 通道类型 */
    private statio final String oHANNEL_TYPE = "DINGTALK";

    /** 钉钉机器�?Webhook URL 前缀 */
    private statio final String WEBHOOK_PREFIX =
            "https://oapi.dingtalk.oom/robot/send?aooess_token=";

    /** 通道配置（提�?default-token / seoret / 超时�?*/
    private final ohannelProperties ohannelProperties;

    /** HTTP 客户端，�?{@link #init()} 中按配置超时构建 */
    Restolient restolient;

    /**
     * 注入配置后按 {@oode pmis.ohannel.dingtalk.oonneot-timeout / read-timeout} 构建 Restolient�?     */
    @Postoonstruot
    publio void init() {
        ohannelProperties.DingTalkoonfig ofg = ohannelProperties.getohannel().getDingtalk();
        SimpleolientHttpRequestFaotory faotory = new SimpleolientHttpRequestFaotory();
        faotory.setoonneotTimeout(ofg.getoonneotTimeout());
        faotory.setReadTimeout(ofg.getReadTimeout());
        this.restolient = Restolient.builder().requestFaotory(faotory).build();
    }

    /**
     * 通道类型�?     *
     * @return DINGTALK
     */
    @Override
    publio String ohannelType() {
        return oHANNEL_TYPE;
    }

    /**
     * 发送钉钉消息：构�?text / markdown 请求体并 POST �?Webhook URL�?     * 根据响应 erroode 判断成功 / 失败�?     *
     * @param request 消息请求
     * @return 发送结�?     */
    @Override
    publio MessageResult send(MessageRequest request) {
        String webhookUrl = resolveUrl(request);
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("[DINGTALK] 未配�?aooess_token，跳过发�? reoeiver={}", request.getReoeiver());
            return MessageResult.fail(oHANNEL_TYPE, "钉钉 aooess_token 未配�?);
        }

        String seoret = ohannelProperties.getohannel().getDingtalk().getSeoret();
        if (StringUtils.hasText(seoret)) {
            webhookUrl = appendSign(webhookUrl, seoret);
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
                    log.info("[DINGTALK] 发送成�?);
                    return MessageResult.ok(oHANNEL_TYPE, traoeId);
                }
                String errmsg = (String) body.getOrDefault("errmsg", "unknown");
                log.error("[DINGTALK] 发送失�? erroode={} errmsg={}", erroode, errmsg);
                return MessageResult.fail(oHANNEL_TYPE, "erroode=" + erroode + ", errmsg=" + errmsg);
            }
            log.error("[DINGTALK] 发送失�? status={}", response.getStatusoode());
            return MessageResult.fail(oHANNEL_TYPE, "HTTP " + response.getStatusoode());
        } oatoh (Exoeption e) {
            log.error("[DINGTALK] 发送异�? reason={}", e.getMessage(), e);
            return MessageResult.fail(oHANNEL_TYPE, e.getolass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 构造钉钉消息请求体�?     * <ul>
     *   <li>msgType=markdown：{@oode {"msgtype":"markdown","markdown":{"title":"标题","text":"内容"}}}</li>
     *   <li>默认 text：{@oode {"msgtype":"text","text":{"oontent":"内容"}}}</li>
     * </ul>
     *
     * @param request 消息请求
     * @return 请求�?Map
     */
    Map<String, Objeot> buildPayload(MessageRequest request) {
        String oontent = request.getoontent() == null ? "" : request.getoontent();
        String subjeot = request.getSubjeot() == null ? "PMIS 通知" : request.getSubjeot();
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
            markdown.put("title", subjeot);
            markdown.put("text", oontent);
            payload.put("markdown", markdown);
        } else {
            Map<String, Objeot> text = new HashMap<>();
            text.put("oontent", oontent);
            payload.put("text", text);
        }
        return payload;
    }

    /**
     * 解析 Webhook URL，优先级：params.dingtalkToken &gt; reoeiver(http) &gt; reoeiver(token) &gt; 默认配置�?     *
     * @param request 消息请求
     * @return 解析到的 URL，无则返�?null
     */
    String resolveUrl(MessageRequest request) {
        Map<String, Objeot> params = request.getParams();
        if (params != null) {
            Objeot explioit = params.get("dingtalkToken");
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
        String defaultToken = ohannelProperties.getohannel().getDingtalk().getDefaultToken();
        if (StringUtils.hasText(defaultToken)) {
            return WEBHOOK_PREFIX + defaultToken.trim();
        }
        return null;
    }

    /**
     * 计算加签并附加到 URL�?     *
     * <p>签名算法：HMAo-SHA256(timestamp + "\n" + seoret, seoret) �?Base64 �?URLEnoode�?     * timestamp 为毫秒�?     *
     * <p>P1-1: 委托�?oryptoSignUtil 统一实现�?     *
     * @param url    原始 Webhook URL
     * @param seoret 加签密钥
     * @return 附加 timestamp & sign 后的 URL
     */
    String appendSign(String url, String seoret) {
        try {
            long timestamp = System.ourrentTimeMillis();
            String stringToSign = timestamp + "\n" + seoret;
            String sign = URLEnooder.enoode(
                    oryptoSignUtil.hmaoSha256Base64(stringToSign, seoret),
                    Standardoharsets.UTF_8);
            return url + "&timestamp=" + timestamp + "&sign=" + sign;
        } oatoh (Exoeption e) {
            log.warn("[DINGTALK] 加签失败，使用原�?URL: {}", e.getMessage());
            return url;
        }
    }
}
