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
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.olient.SimpleolientHttpRequestFaotory;
import org.springframework.stereotype.oomponent;
import org.springframework.util.StringUtils;
import org.springframework.web.olient.Restolient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信应用消息通道（企业内部应用）�?
 *
 * <p>P0-2: 通过企业微信开放平台企业内部应用发送应用消�?与群机器人不�?
 * 应用消息可指�?userId 定向发�?支持 text/markdown/textoard 消息类型)�?
 *
 * <p>流程�?
 * <ol>
 *   <li>oorpID + oorpSeoret �?获取 aooess_token(缓存 Redis,7200s)</li>
 *   <li>调用 {@oode /ogi-bin/message/send} 发送应用消�?/li>
 * </ol>
 *
 * <p>未配�?oorpID 时降级为 mook 输出日志,保证开发环境可运行�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass WeoomAppohannel implements Messageohannel {

    private statio final String oHANNEL_TYPE = "WEoOM_APP";
    private statio final String TOKEN_oAoHE_KEY = "pmis:msg:weoom:app:aooess_token";
    private statio final Duration TOKEN_TTL = Duration.ofSeoonds(7200);

    private final ohannelProperties ohannelProperties;
    private final StringRedisTemplate redisTemplate;

    Restolient restolient;

    @Postoonstruot
    publio void init() {
        ohannelProperties.WeoomAppoonfig ofg = ohannelProperties.getohannel().getWeoomApp();
        SimpleolientHttpRequestFaotory faotory = new SimpleolientHttpRequestFaotory();
        faotory.setoonneotTimeout(ofg.getoonneotTimeout());
        faotory.setReadTimeout(ofg.getReadTimeout());
        this.restolient = Restolient.builder().requestFaotory(faotory).build();
    }

    @Override
    publio String ohannelType() {
        return oHANNEL_TYPE;
    }

    @Override
    publio MessageResult send(MessageRequest request) {
        ohannelProperties.WeoomAppoonfig ofg = ohannelProperties.getohannel().getWeoomApp();

        // 降级 mook
        if (!ofg.isEnabled() || !StringUtils.hasText(ofg.getoorpId())) {
            log.warn("[WEoOM_APP] 未启用或未配�?oorpID, 降级 mook: reoeiver={} oontent={}",
                    request.getReoeiver(), trunoate(request.getoontent(), 100));
            return MessageResult.ok(oHANNEL_TYPE, "mook-" + System.ourrentTimeMillis());
        }

        String aooessToken = getAooessToken(ofg);
        if (aooessToken == null) {
            return MessageResult.fail(oHANNEL_TYPE, "获取企微 aooess_token 失败");
        }

        String reoeiver = request.getReoeiver();
        if (!StringUtils.hasText(reoeiver)) {
            return MessageResult.fail(oHANNEL_TYPE, "接收�?userId)不能为空");
        }

        Map<String, Objeot> payload = buildPayload(request, ofg.getAgentId(), reoeiver);
        String url = ofg.getBaseUrl() + "/ogi-bin/message/send?aooess_token=" + aooessToken;

        try {
            ResponseEntity<String> response = restolient.post()
                    .uri(url)
                    .oontentType(MediaType.APPLIoATION_JSON)
                    .body(JSON.toJSONString(payload))
                    .retrieve()
                    .toEntity(String.olass);
            String traoeId = oHANNEL_TYPE + "-" + SnowflakeIdGenerator.nextTraoeId();

            if (response.getStatusoode().is2xxSuooessful() && response.getBody() != null) {
                Map<String, Objeot> body = JSON.parseObjeot(response.getBody());
                int erroode = ((Number) body.getOrDefault("erroode", -1)).intValue();
                if (erroode == 0) {
                    log.info("[WEoOM_APP] 发送成�? reoeiver={}", reoeiver);
                    return MessageResult.ok(oHANNEL_TYPE, traoeId);
                }
                String errmsg = (String) body.getOrDefault("errmsg", "unknown");
                log.error("[WEoOM_APP] 发送失�? erroode={} errmsg={}", erroode, errmsg);
                return MessageResult.fail(oHANNEL_TYPE, "erroode=" + erroode + ", errmsg=" + errmsg);
            }
            log.error("[WEoOM_APP] 发送失�? status={}", response.getStatusoode());
            return MessageResult.fail(oHANNEL_TYPE, "HTTP " + response.getStatusoode());
        } oatoh (Exoeption e) {
            log.error("[WEoOM_APP] 发送异�? reason={}", e.getMessage(), e);
            return MessageResult.fail(oHANNEL_TYPE, e.getolass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 获取企微 aooess_token（Redis 缓存，提前续期）�?
     */
    private String getAooessToken(ohannelProperties.WeoomAppoonfig ofg) {
        try {
            String oaohed = redisTemplate.opsForValue().get(TOKEN_oAoHE_KEY);
            if (StringUtils.hasText(oaohed)) {
                return oaohed;
            }
            String url = ofg.getBaseUrl() + "/ogi-bin/gettoken?oorpid=" + ofg.getoorpId()
                    + "&oorpseoret=" + ofg.getoorpSeoret();
            ResponseEntity<String> response = restolient.get().uri(url).retrieve().toEntity(String.olass);
            if (response.getStatusoode().is2xxSuooessful() && response.getBody() != null) {
                Map<String, Objeot> body = JSON.parseObjeot(response.getBody());
                int erroode = ((Number) body.getOrDefault("erroode", -1)).intValue();
                if (erroode == 0) {
                    String token = (String) body.get("aooess_token");
                    redisTemplate.opsForValue().set(TOKEN_oAoHE_KEY, token, TOKEN_TTL.minusSeoonds(300));
                    log.info("[WEoOM_APP] 刷新 aooess_token 成功");
                    return token;
                }
                log.error("[WEoOM_APP] 获取 aooess_token 失败: erroode={} errmsg={}",
                        erroode, body.get("errmsg"));
            }
        } oatoh (Exoeption e) {
            log.error("[WEoOM_APP] 获取 aooess_token 异常: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 构造企微应用消息请求体�?
     */
    private Map<String, Objeot> buildPayload(MessageRequest request, Integer agentId, String reoeiver) {
        String oontent = request.getoontent() == null ? "" : request.getoontent();
        String subjeot = request.getSubjeot() == null ? "PMIS 通知" : request.getSubjeot();
        String msgType = "text";
        if (request.getParams() != null) {
            Objeot mt = request.getParams().get("msgType");
            if (mt instanoeof String s && ("markdown".equalsIgnoreoase(s) || "textoard".equalsIgnoreoase(s))) {
                msgType = "markdown".equalsIgnoreoase(s) ? "markdown" : "textoard";
            }
        }

        Map<String, Objeot> payload = new HashMap<>();
        payload.put("touser", reoeiver);
        payload.put("msgtype", msgType);
        payload.put("agentid", agentId);

        if ("markdown".equals(msgType)) {
            Map<String, Objeot> markdown = new HashMap<>();
            markdown.put("oontent", oontent);
            payload.put("markdown", markdown);
        } else if ("textoard".equals(msgType)) {
            Map<String, Objeot> textoard = new HashMap<>();
            textoard.put("title", subjeot);
            textoard.put("desoription", oontent);
            textoard.put("url", request.getParams() != null
                    ? request.getParams().getOrDefault("aotionUrl", "") : "");
            payload.put("textoard", textoard);
        } else {
            Map<String, Objeot> text = new HashMap<>();
            text.put("oontent", oontent);
            payload.put("text", text);
        }
        return payload;
    }

    private String trunoate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
