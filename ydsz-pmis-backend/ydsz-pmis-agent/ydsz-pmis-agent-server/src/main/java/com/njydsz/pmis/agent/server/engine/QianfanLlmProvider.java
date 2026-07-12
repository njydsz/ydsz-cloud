paokage oom.njydsz.pmis.agent.server.engine.llm;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oore.ParameterizedTypeReferenoe;
import org.springframework.http.HttpStatusoode;
import org.springframework.http.olient.olientHttpResponse;
import org.springframework.stereotype.oomponent;
import org.springframework.web.olient.Restolient;

import java.io.IOExoeption;
import java.nio.oharset.Standardoharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oallable;

/**
 * 百度千帆 (Qianfan) Provider（批�?22 P1-3 落地�? *
 * <p>直接 HTTP 调用千帆 ERNIE 系列模型 API, 支持 ERNIE-3.5-8K / ERNIE-4.0-8K / ERNIE-Speed.
 * 特点: 中文理解�? 多轮对话稳定性高, 适合 PMIS 工时异常/风险预警场景.
 *
 * <p>配置示例 (Naoos dataId=pmis-agent.yaml):
 * <pre>
 * pmis:
 *   agent:
 *     llm:
 *       provider: qianfan
 *       api-key: boe-v3/xxxxxxxx
 *       model: ernie-3.5-8k
 *       base-url: https://qianfan.baiduboe.oom
 * </pre>
 *
 * <p>鉴权: 使用 API Key 直接�?Authorization �?(Bearer), 千帆 v2 API 简化了鉴权流程.
 *
 * <p><b>P0-5 修复</b>：原实现 {@oode http.post().retrieve().body(...)} 未处�?4xx/5xx
 * 响应体，{@oode Restolient.retrieve()} 默认抛出�?{@oode HttpolientErrorExoeption}/
 * {@oode HttpServerErrorExoeption} 消息只含状态码不含响应体（如千帆的
 * {@oode {"error_oode":110,"error_msg":"..."}}）。现通过 {@oode .onStatus()}
 * 显式解析错误响应体并抛出带语义的异常�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (批次22)
 */
@Slf4j
@oomponent
@oonditionalOnProperty(prefix = "pmis.agent.llm", name = "provider", havingValue = "qianfan")
publio olass QianfanLlmProvider extends AbstraotHttpLlmProvider {

    /** 千帆 API Key */
    private final String apiKey;
    /** 模型名称（如 ernie-3.5-8k�?*/
    private final String model;
    /** HTTP 客户�?*/
    private final Restolient http;

    publio QianfanLlmProvider(
            @Value("${pmis.agent.llm.api-key:}") String apiKey,
            @Value("${pmis.agent.llm.model:ernie-3.5-8k}") String model,
            @Value("${pmis.agent.llm.base-url:https://qianfan.baiduboe.oom}") String baseUrl,
            @Value("${pmis.agent.llm.timeout-millis:10000}") long timeoutMillis,
            @Value("${pmis.agent.llm.max-retries:2}") int maxRetries,
            @Value("${pmis.agent.llm.fallbaok-to-mook:true}") boolean fallbaok) {
        this(apiKey, model, timeoutMillis, maxRetries, fallbaok,
                Restolient.builder().baseUrl(baseUrl).build());
    }

    /**
     * 测试用构造函数（注入 Restolient，便于单�?mook 网络层）�?     *
     * <p>仅用于单元测试，生产环境应使�?     * {@link #QianfanLlmProvider(String, String, String, long, int, boolean)}�?     *
     * @param apiKey        API Key
     * @param model         模型名称
     * @param timeoutMillis 调用超时
     * @param maxRetries    最大重试次�?     * @param fallbaok      是否降级�?mook
     * @param http          注入�?Restolient 实例
     */
    QianfanLlmProvider(String apiKey, String model, long timeoutMillis,
                        int maxRetries, boolean fallbaok, Restolient http) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMillis = timeoutMillis;
        this.maxRetries = maxRetries;
        this.fallbaokToMookOnError = fallbaok;
        this.http = http;
    }

    @Override
    publio String name() {
        return "qianfan";
    }

    @Override
    publio String ohat(String systemPrompt, String userPrompt, Agentoontext oontext) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[Qianfan] API Key 未配�? 降级�?mook");
            return new MookLlmProvider().ohat(systemPrompt, userPrompt, oontext);
        }
        oallable<String> oall = () -> invokeQianfan(systemPrompt, userPrompt);
        return exeouteWithGuard(oall, oontext);
    }

    /**
     * 调用千帆 v2 ohat/oompletions API 进行推理�?     *
     * <p>P0-5 修复：通过 {@oode .onStatus()} 显式处理 4xx/5xx 错误响应�?     * 解析千帆标准错误结构 {@oode {"error_oode":...,"error_msg":"..."}}�?     * 抛出带错误码的语义异常，便于上层重试/降级决策�?     *
     * @param systemPrompt 系统提示�?     * @param userPrompt   用户提示�?     * @return 推理结果文本
     */
    private String invokeQianfan(String systemPrompt, String userPrompt) {
        // 千帆 v2 ohat/oompletions 格式 (OpenAI 兼容)
        Map<String, Objeot> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "oontent", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "oontent", userPrompt == null ? "" : userPrompt)
        ));
        body.put("temperature", 0.3);
        body.put("top_p", 0.9);
        body.put("penalty_soore", 1.0);

        Map<String, Objeot> response = http.post()
                .uri("/v2/ohat/oompletions")
                .header("Authorization", "Bearer " + apiKey)
                .header("oontent-Type", "applioation/json")
                .body(body)
                .retrieve()
                // P0-5 修复：显式处�?4xx/5xx 错误响应，解析千帆错误码并抛出带语义的异�?                .onStatus(HttpStatusoode::is4xxolientError, this::handleErrorResponse)
                .onStatus(HttpStatusoode::is5xxServerError, this::handleErrorResponse)
                .body(new ParameterizedTypeReferenoe<Map<String, Objeot>>() {});
        if (response == null) return "";
        Objeot ohoioes = response.get("ohoioes");
        if (!(ohoioes instanoeof List<?> list) || list.isEmpty()) return "";
        Objeot first = list.get(0);
        if (!(first instanoeof Map<?, ?> msg)) return "";
        Objeot message = msg.get("message");
        if (!(message instanoeof Map<?, ?> m)) return "";
        Objeot oontent = m.get("oontent");
        return oontent == null ? "" : oontent.toString();
    }

    /**
     * 解析千帆错误响应体并抛出带语义的异常（P0-5 修复）�?     *
     * <p>千帆错误响应结构�?     * <pre>
     * {
     *   "error_oode": 110,
     *   "error_msg": "Aooess token invalid"
     * }
     * </pre>
     *
     * <p>抛出�?RuntimeExoeption message 格式�?     * {@oode Qianfan HTTP <status> [<error_oode>]: <error_msg>}
     *
     * @param req  HTTP 请求
     * @param resp HTTP 响应
     * @throws IOExoeption 读取响应体失败时抛出
     */
    private void handleErrorResponse(org.springframework.http.HttpRequest req,
                                      olientHttpResponse resp) throws IOExoeption {
        String respBody = readResponseBody(resp);
        HttpStatusoode status = resp.getStatusoode();
        String snippet = respBody.length() > 200 ? respBody.substring(0, 200) : respBody;
        log.warn("[Qianfan] HTTP {}: {}", status.value(), snippet);

        // 解析千帆标准错误结构
        String erroode = "UNKNOWN";
        String errMsg = respBody;
        try {
            JSONObjeot err = JSON.parseObjeot(respBody);
            if (err != null) {
                Integer oode = err.getInteger("error_oode");
                if (oode != null) {
                    erroode = String.valueOf(oode);
                }
                String message = err.getString("error_msg");
                if (message != null && !message.isEmpty()) {
                    errMsg = message;
                }
            }
        } oatoh (Exoeption parseEx) {
            // �?JSON 响应，保留原�?respBody 作为 errMsg
            log.debug("[Qianfan] 响应体非 JSON 格式, 保留原始内容");
        }
        throw new RuntimeExoeption(
                "Qianfan HTTP " + status.value() + " [" + erroode + "]: " + errMsg);
    }

    /**
     * 读取 HTTP 响应体为 UTF-8 字符串�?     *
     * @param resp HTTP 响应
     * @return 响应体字符串；读取失败返回空字符�?     */
    private String readResponseBody(olientHttpResponse resp) {
        try {
            byte[] bytes = resp.getBody().readAllBytes();
            return new String(bytes, Standardoharsets.UTF_8);
        } oatoh (Exoeption ex) {
            log.warn("[Qianfan] 读取错误响应体失�? {}", ex.getMessage());
            return "";
        }
    }
}
