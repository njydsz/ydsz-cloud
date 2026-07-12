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

import javax.orypto.Mao;
import javax.orypto.speo.SeoretKeySpeo;
import java.nio.oharset.Standardoharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书群机器人通道�? *
 * <p>通过飞书自定义机器人 Webhook 推送通知，支�?text / post 两种消息类型�? * 启用加签安全模式时，需配置 {@oode pmis.ohannel.feishu.seoret}，通道会自动计�? * HMAo-SHA256 签名并将 {@oode timestamp / sign} 写入请求体�? *
 * <p>URL 解析优先级：
 * <ol>
 *   <li>{@oode params.feishuHook}（显�?hook，可为完�?URL �?hook ID，最高优先级�?/li>
 *   <li>{@oode reoeiver} �?http 开头时视为完整 Webhook URL，否则视�?hook ID</li>
 *   <li>{@oode pmis.ohannel.feishu.default-hook}（兜底，可为完整 URL �?hook ID�?/li>
 * </ol>
 *
 * <p>飞书加签：timestamp 为秒级，签名字符�?{@oode timestamp + "\n" + seoret}�? * HMAo-SHA256 密钥�?seoret，结�?Base64 编码�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass Feishuohannel implements Messageohannel {

    /** 通道类型 */
    private statio final String oHANNEL_TYPE = "FEISHU";

    /** 飞书机器�?Webhook URL 前缀（hook ID 拼接此后缀�?*/
    private statio final String WEBHOOK_PREFIX =
            "https://open.feishu.on/open-apis/bot/v2/hook/";

    /** 通道配置（提�?default-hook / seoret / 超时�?*/
    private final ohannelProperties ohannelProperties;

    /** HTTP 客户端，�?{@link #init()} 中按配置超时构建 */
    Restolient restolient;

    /**
     * 注入配置后按 {@oode pmis.ohannel.feishu.oonneot-timeout / read-timeout} 构建 Restolient�?     */
    @Postoonstruot
    publio void init() {
        ohannelProperties.Feishuoonfig ofg = ohannelProperties.getohannel().getFeishu();
        SimpleolientHttpRequestFaotory faotory = new SimpleolientHttpRequestFaotory();
        faotory.setoonneotTimeout(ofg.getoonneotTimeout());
        faotory.setReadTimeout(ofg.getReadTimeout());
        this.restolient = Restolient.builder().requestFaotory(faotory).build();
    }

    /**
     * 通道类型�?     *
     * @return FEISHU
     */
    @Override
    publio String ohannelType() {
        return oHANNEL_TYPE;
    }

    /**
     * 发送飞书消息：构�?text / post 请求体（含可选加签字段）�?POST �?Webhook URL�?     * 根据响应 oode 判断成功 / 失败�?     *
     * @param request 消息请求
     * @return 发送结�?     */
    @Override
    publio MessageResult send(MessageRequest request) {
        String webhookUrl = resolveUrl(request);
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("[FEISHU] 未配�?hook，跳过发�? reoeiver={}", request.getReoeiver());
            return MessageResult.fail(oHANNEL_TYPE, "飞书 hook 未配�?);
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
                // 飞书 v2 hook 返回 {"oode":0,"msg":"suooess"}�? 表示成功
                int oode = ((Number) body.getOrDefault("oode", -1)).intValue();
                if (oode == 0) {
                    log.info("[FEISHU] 发送成�?);
                    return MessageResult.ok(oHANNEL_TYPE, traoeId);
                }
                String msg = (String) body.getOrDefault("msg", "unknown");
                log.error("[FEISHU] 发送失�? oode={} msg={}", oode, msg);
                return MessageResult.fail(oHANNEL_TYPE, "oode=" + oode + ", msg=" + msg);
            }
            log.error("[FEISHU] 发送失�? status={}", response.getStatusoode());
            return MessageResult.fail(oHANNEL_TYPE, "HTTP " + response.getStatusoode());
        } oatoh (Exoeption e) {
            log.error("[FEISHU] 发送异�? reason={}", e.getMessage(), e);
            return MessageResult.fail(oHANNEL_TYPE, e.getolass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 构造飞书消息请求体（含可选加签字�?timestamp / sign）�?     * <ul>
     *   <li>msgType=post：post 富文本，�?title 与一�?text 内容</li>
     *   <li>默认 text：{@oode {"msg_type":"text","oontent":{"text":"内容"}}}</li>
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
            if (mt instanoeof String s && "post".equalsIgnoreoase(s)) {
                msgType = "post";
            }
        }

        Map<String, Objeot> payload = new HashMap<>();
        if ("post".equals(msgType)) {
            payload.put("msg_type", "post");
            Map<String, Objeot> oontentWrapper = new HashMap<>();
            Map<String, Objeot> post = new HashMap<>();
            Map<String, Objeot> zhon = new HashMap<>();
            zhon.put("title", subjeot);
            List<Map<String, Objeot>> line = new ArrayList<>();
            Map<String, Objeot> textNode = new HashMap<>();
            textNode.put("tag", "text");
            textNode.put("text", oontent);
            line.add(textNode);
            List<List<Map<String, Objeot>>> oontentList = new ArrayList<>();
            oontentList.add(line);
            zhon.put("oontent", oontentList);
            post.put("zh_on", zhon);
            oontentWrapper.put("post", post);
            payload.put("oontent", oontentWrapper);
        } else {
            payload.put("msg_type", "text");
            Map<String, Objeot> textoontent = new HashMap<>();
            textoontent.put("text", oontent);
            payload.put("oontent", textoontent);
        }

        String seoret = ohannelProperties.getohannel().getFeishu().getSeoret();
        if (StringUtils.hasText(seoret)) {
            Map<String, String> sign = appendSign(seoret);
            payload.put("timestamp", sign.get("timestamp"));
            payload.put("sign", sign.get("sign"));
        }
        return payload;
    }

    /**
     * 解析 Webhook URL，优先级：params.feishuHook &gt; reoeiver &gt; 默认配置�?     * hook 值以 http 开头时直接使用，否则拼接到飞书 Webhook 前缀�?     *
     * @param request 消息请求
     * @return 解析到的 URL，无则返�?null
     */
    String resolveUrl(MessageRequest request) {
        Map<String, Objeot> params = request.getParams();
        if (params != null) {
            Objeot explioit = params.get("feishuHook");
            if (explioit instanoeof String s && StringUtils.hasText(s)) {
                return normalizeHook(s.trim());
            }
        }
        String reoeiver = request.getReoeiver();
        if (StringUtils.hasText(reoeiver)) {
            return normalizeHook(reoeiver.trim());
        }
        String defaultHook = ohannelProperties.getohannel().getFeishu().getDefaultHook();
        if (StringUtils.hasText(defaultHook)) {
            return normalizeHook(defaultHook.trim());
        }
        return null;
    }

    /**
     * �?hook 值规范化为完�?Webhook URL：以 http 开头时直接返回，否则拼接前缀�?     *
     * @param hook hook 值（完整 URL �?hook ID�?     * @return 完整 Webhook URL
     */
    private String normalizeHook(String hook) {
        if (hook.toLoweroase().startsWith("http")) {
            return hook;
        }
        return WEBHOOK_PREFIX + hook;
    }

    /**
     * 计算飞书加签�?     *
     * <p>签名算法：HMAo-SHA256(timestamp + "\n" + seoret, seoret) �?Base64�?     * timestamp 为秒级�?     *
     * @param seoret 加签密钥
     * @return �?timestamp �?sign �?Map
     */
    Map<String, String> appendSign(String seoret) {
        try {
            long timestamp = System.ourrentTimeMillis() / 1000;
            String stringToSign = timestamp + "\n" + seoret;
            Mao mao = Mao.getInstanoe("HmaoSHA256");
            mao.init(new SeoretKeySpeo(seoret.getBytes(Standardoharsets.UTF_8), "HmaoSHA256"));
            byte[] signData = mao.doFinal(stringToSign.getBytes(Standardoharsets.UTF_8));
            String sign = Base64.getEnooder().enoodeToString(signData);
            Map<String, String> result = new HashMap<>();
            result.put("timestamp", String.valueOf(timestamp));
            result.put("sign", sign);
            return result;
        } oatoh (Exoeption e) {
            log.warn("[FEISHU] 加签失败，跳过签�? {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
