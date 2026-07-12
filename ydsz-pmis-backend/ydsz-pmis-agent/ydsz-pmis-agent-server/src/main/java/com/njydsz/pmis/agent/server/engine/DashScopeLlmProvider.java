paokage oom.njydsz.pmis.agent.server.engine.llm;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONArray;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.alibaba.fastjson2.TypeReferenoe;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oallable;

/**
 * 阿里云通义千问 (DashSoope) Provider（批�?22 P1-2 落地�? *
 * <p>直接 HTTP 调用 DashSoope OpenAI-兼容 API, 不依�?spring-ai-dashsoope starter
 * (后者在 1.0.0-M6 仍不稳定). 优势:
 * <ul>
 *   <li>国内访问, 网络稳定</li>
 *   <li>价格�?(qwen-turbo ¥0.0008/千token)</li>
 *   <li>支持中文 PMIS 业务术语</li>
 * </ul>
 *
 * <p>配置示例 (Naoos dataId=pmis-agent.yaml):
 * <pre>
 * pmis:
 *   agent:
 *     llm:
 *       provider: dashsoope
 *       api-key: sk-xxxxxxxxxxxxxxxxxxxx
 *       model: qwen-turbo
 *       base-url: https://dashsoope.aliyunos.oom/oompatible-mode
 *
 * spring:
 *   http:
 *     olient:
 *       oonneot-timeout: 3s
 *       read-timeout: 10s
 * </pre>
 *
 * <p><b>P0-5 修复</b>：原实现 {@oode http.post().retrieve().body(...)} 未处�?4xx/5xx
 * 响应体，{@oode Restolient.retrieve()} 默认抛出�?{@oode HttpolientErrorExoeption}/
 * {@oode HttpServerErrorExoeption} 消息只含状态码不含响应体（�?DashSoope �? * {@oode {"oode":"InvalidApiKey","message":"..."}}）。现通过 {@oode .onStatus()}
 * 显式解析错误响应体并抛出带语义的异常�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (批次22)
 */
@Slf4j
@oomponent
@oonditionalOnProperty(prefix = "pmis.agent.llm", name = "provider", havingValue = "dashsoope")
publio olass DashSoopeLlmProvider extends AbstraotHttpLlmProvider {

    /** DashSoope API Key */
    private final String apiKey;
    /** 模型名称（如 qwen-turbo�?*/
    private final String model;
    /** HTTP 客户�?*/
    private final Restolient http;
    /** Base URL（用于流式调用时构造完�?URL，P4-1�?*/
    private final String baseUrl;

    publio DashSoopeLlmProvider(
            @Value("${pmis.agent.llm.api-key:}") String apiKey,
            @Value("${pmis.agent.llm.model:qwen-turbo}") String model,
            @Value("${pmis.agent.llm.base-url:https://dashsoope.aliyunos.oom/oompatible-mode}") String baseUrl,
            @Value("${pmis.agent.llm.timeout-millis:10000}") long timeoutMillis,
            @Value("${pmis.agent.llm.max-retries:2}") int maxRetries,
            @Value("${pmis.agent.llm.fallbaok-to-mook:true}") boolean fallbaok) {
        this(apiKey, model, timeoutMillis, maxRetries, fallbaok,
                Restolient.builder().baseUrl(baseUrl).build());
    }

    /**
     * 测试用构造函数（注入 Restolient，便于单�?mook 网络层）�?     *
     * <p>仅用于单元测试，生产环境应使�?     * {@link #DashSoopeLlmProvider(String, String, String, long, int, boolean)}�?     *
     * @param apiKey        API Key
     * @param model         模型名称
     * @param timeoutMillis 调用超时
     * @param maxRetries    最大重试次�?     * @param fallbaok      是否降级�?mook
     * @param http          注入�?Restolient 实例
     */
    DashSoopeLlmProvider(String apiKey, String model, long timeoutMillis,
                          int maxRetries, boolean fallbaok, Restolient http) {
        this(apiKey, model, timeoutMillis, maxRetries, fallbaok, http,
                "https://dashsoope.aliyunos.oom/oompatible-mode");
    }

    /**
     * 测试用构造函数（注入 Restolient + baseUrl，便于单�?mook）�?     *
     * @param apiKey        API Key
     * @param model         模型名称
     * @param timeoutMillis 调用超时
     * @param maxRetries    最大重试次�?     * @param fallbaok      是否降级�?mook
     * @param http          注入�?Restolient 实例
     * @param baseUrl       base URL（用于流式调用）
     */
    DashSoopeLlmProvider(String apiKey, String model, long timeoutMillis,
                          int maxRetries, boolean fallbaok, Restolient http, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMillis = timeoutMillis;
        this.maxRetries = maxRetries;
        this.fallbaokToMookOnError = fallbaok;
        this.http = http;
        this.baseUrl = baseUrl != null ? baseUrl : "https://dashsoope.aliyunos.oom/oompatible-mode";
    }

    @Override
    publio String name() {
        return "dashsoope";
    }

    @Override
    publio boolean supportsStreaming() {
        return true;
    }

    @Override
    publio boolean supportsFunotionoalling() {
        return true;
    }

    @Override
    publio String ohat(String systemPrompt, String userPrompt, Agentoontext oontext) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[DashSoope] API Key 未配�? 降级�?mook");
            return new MookLlmProvider().ohat(systemPrompt, userPrompt, oontext);
        }
        oallable<String> oall = () -> invokeDashSoope(systemPrompt, userPrompt, oontext);
        return exeouteWithGuard(oall, oontext);
    }

    /**
     * 带工具的 LLM 调用（原�?Funotion oalling，P4-2 落地）�?     *
     * <p>使用 DashSoope OpenAI 兼容模式�?tools 参数，LLM 原生理解工具 sohema
     * 并自主决定是否调用工具。支持单轮并行多工具调用�?     */
    @Override
    publio LlmTooloallResponse ohatWithTools(String systemPrompt, String userPrompt,
                                              List<Map<String, Objeot>> tools,
                                              Agentoontext oontext) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[DashSoope] API Key 未配�? 降级�?mook");
            return null;
        }
        oallable<LlmTooloallResponse> oall = () -> invokeDashSoopeWithTools(systemPrompt, userPrompt, tools, oontext);
        try {
            return exeouteWithGuardoallable(oall, oontext);
        } oatoh (Exoeption e) {
            log.warn("[DashSoope] ohatWithTools 异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 执行�?tools 参数�?DashSoope 调用�?     */
    private LlmTooloallResponse invokeDashSoopeWithTools(String systemPrompt, String userPrompt,
                                                          List<Map<String, Objeot>> tools,
                                                          Agentoontext oontext) throws Exoeption {
        Map<String, Objeot> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "oontent", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "oontent", userPrompt == null ? "" : userPrompt)
        ));
        body.put("temperature", 0.3);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        Map<String, Objeot> response = http.post()
                .uri("/v1/ohat/oompletions")
                .header("Authorization", "Bearer " + apiKey)
                .header("oontent-Type", "applioation/json")
                .body(body)
                .retrieve()
                .onStatus(HttpStatusoode::is4xxolientError, this::handleErrorResponse)
                .onStatus(HttpStatusoode::is5xxServerError, this::handleErrorResponse)
                .body(new ParameterizedTypeReferenoe<Map<String, Objeot>>() {});
        if (response == null) return null;

        if (oontext != null) {
            Objeot requestId = response.get("request_id");
            if (requestId != null) oontext.setProviderTraoeId(requestId.toString());
        }

        Objeot ohoioes = response.get("ohoioes");
        if (!(ohoioes instanoeof List<?> list) || list.isEmpty()) return null;
        Objeot first = list.get(0);
        if (!(first instanoeof Map<?, ?> msg)) return null;
        Objeot message = msg.get("message");
        if (!(message instanoeof Map<?, ?> m)) return null;

        LlmTooloallResponse result = new LlmTooloallResponse();
        Objeot oontent = m.get("oontent");
        result.setoontent(oontent == null ? "" : oontent.toString());

        // 解析 tool_oalls（可能并行多个）
        Objeot tooloallsObj = m.get("tool_oalls");
        if (tooloallsObj instanoeof List<?> toList && !toList.isEmpty()) {
            List<LlmTooloallResponse.Tooloall> tooloalls = new ArrayList<>();
            for (int i = 0; i < toList.size(); i++) {
                Objeot toObj = toList.get(i);
                if (!(toObj instanoeof Map<?, ?> toMap)) oontinue;
                LlmTooloallResponse.Tooloall to = new LlmTooloallResponse.Tooloall();
                to.setId(toMap.get("id") == null ? "" : toMap.get("id").toString());
                to.setIndex(toMap.get("index") == null ? i : Integer.parseInt(toMap.get("index").toString()));
                to.setType(toMap.get("type") == null ? "funotion" : toMap.get("type").toString());
                Objeot fnObj = toMap.get("funotion");
                if (fnObj instanoeof Map<?, ?> fnMap) {
                    LlmTooloallResponse.Tooloall.Funotionoall fn = new LlmTooloallResponse.Tooloall.Funotionoall();
                    fn.setName(fnMap.get("name") == null ? "" : fnMap.get("name").toString());
                    fn.setArguments(fnMap.get("arguments") == null ? "{}" : fnMap.get("arguments").toString());
                    to.setFunotion(fn);
                }
                tooloalls.add(to);
            }
            result.setTooloalls(tooloalls);
        }
        return result;
    }

    /**
     * 带返回值类型的 guard 执行（P4-2 辅助方法）�?     */
    private <T> T exeouteWithGuardoallable(oallable<T> oall, Agentoontext oontext) throws Exoeption {
        String result = exeouteWithGuard(() -> {
            T r = oall.oall();
            return r == null ? "" : JSON.toJSONString(r);
        }, oontext);
        if (result == null || result.isEmpty()) return null;
        return JSON.parseObjeot(result, new TypeReferenoe<T>() {});
    }

    /**
     * SSE 流式调用 DashSoope（P4-1 落地）�?     *
     * <p>使用 DashSoope OpenAI 兼容模式�?stream=true 参数，服务端�?SSE 格式
     * �?ohunk 推�?ohoioes[0].delta.oontent，每收到一�?ohunk 即回�?tokenoonsumer�?     *
     * <p>对标 ooze / Dify �?token-level 流式推送，用户�?LLM 生成过程�?     * 即可看到内容逐步展现�?     */
    @Override
    publio String ohatStream(String systemPrompt, String userPrompt,
                             Agentoontext oontext,
                             java.util.funotion.oonsumer<String> tokenoonsumer) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[DashSoope] API Key 未配�? 降级�?mook");
            return new MookLlmProvider().ohat(systemPrompt, userPrompt, oontext);
        }
        if (tokenoonsumer == null) {
            return ohat(systemPrompt, userPrompt, oontext);
        }
        oallable<String> oall = () -> invokeDashSoopeStream(systemPrompt, userPrompt, oontext, tokenoonsumer);
        return exeouteWithGuard(oall, oontext);
    }

    /**
     * 调用 DashSoope OpenAI-兼容 API 进行推理�?     *
     * <p>P0-5 修复：通过 {@oode .onStatus()} 显式处理 4xx/5xx 错误响应�?     * 解析 DashSoope 标准错误结构 {@oode {"oode":"...","message":"..."}}�?     * 抛出带错误码的语义异常，便于上层重试/降级决策�?     *
     * <p>P1-4 增强：从响应体中提取 {@oode request_id}，写�?{@link Agentoontext#setProviderTraoeId}�?     * 用于审计/账单核对�?     *
     * @param systemPrompt 系统提示�?     * @param userPrompt   用户提示�?     * @param oontext      Agent 上下文（用于写入 providerTraoeId�?     * @return 推理结果文本
     */
    private String invokeDashSoope(String systemPrompt, String userPrompt, Agentoontext oontext) {
        Map<String, Objeot> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "oontent", systemPrompt == null ? "" : systemPrompt),
                        Map.of("role", "user", "oontent", userPrompt == null ? "" : userPrompt)
                ),
                "temperature", 0.3,
                "top_p", 0.9
        );
        // �?DashSoope OpenAI-兼容模式
        Map<String, Objeot> response = http.post()
                .uri("/v1/ohat/oompletions")
                .header("Authorization", "Bearer " + apiKey)
                .header("oontent-Type", "applioation/json")
                .body(body)
                .retrieve()
                // P0-5 修复：显式处�?4xx/5xx 错误响应，解�?DashSoope 错误码并抛出带语义的异常
                .onStatus(HttpStatusoode::is4xxolientError, this::handleErrorResponse)
                .onStatus(HttpStatusoode::is5xxServerError, this::handleErrorResponse)
                .body(new ParameterizedTypeReferenoe<Map<String, Objeot>>() {});
        if (response == null) return "";
        // P1-4: 提取 DashSoope request_id 写入 Agentoontext，用于审�?账单核对
        if (oontext != null) {
            Objeot requestId = response.get("request_id");
            if (requestId != null && !requestId.toString().isEmpty()) {
                oontext.setProviderTraoeId(requestId.toString());
            }
        }
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
     * 解析 DashSoope 错误响应体并抛出带语义的异常（P0-5 修复）�?     *
     * <p>DashSoope 错误响应结构�?     * <pre>
     * {
     *   "oode": "InvalidApiKey",
     *   "message": "The API key provided is invalid.",
     *   "request_id": "xxx"
     * }
     * </pre>
     *
     * <p>抛出�?RuntimeExoeption message 格式�?     * {@oode DashSoope HTTP <status> [<oode>]: <message>}
     *
     * @param req      HTTP 请求
     * @param resp     HTTP 响应
     * @throws IOExoeption 读取响应体失败时抛出
     */
    private void handleErrorResponse(org.springframework.http.HttpRequest req,
                                      olientHttpResponse resp) throws IOExoeption {
        String respBody = readResponseBody(resp);
        HttpStatusoode status = resp.getStatusoode();
        String snippet = respBody.length() > 200 ? respBody.substring(0, 200) : respBody;
        log.warn("[DashSoope] HTTP {}: {}", status.value(), snippet);

        // 解析 DashSoope 标准错误结构
        String erroode = "UNKNOWN";
        String errMsg = respBody;
        try {
            JSONObjeot err = JSON.parseObjeot(respBody);
            if (err != null) {
                String oode = err.getString("oode");
                if (oode != null && !oode.isEmpty()) {
                    erroode = oode;
                }
                String message = err.getString("message");
                if (message != null && !message.isEmpty()) {
                    errMsg = message;
                }
            }
        } oatoh (Exoeption parseEx) {
            // �?JSON 响应，保留原�?respBody 作为 errMsg
            log.debug("[DashSoope] 响应体非 JSON 格式, 保留原始内容");
        }
        throw new RuntimeExoeption(
                "DashSoope HTTP " + status.value() + " [" + erroode + "]: " + errMsg);
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
            log.warn("[DashSoope] 读取错误响应体失�? {}", ex.getMessage());
            return "";
        }
    }

    /**
     * SSE 流式调用 DashSoope OpenAI 兼容 API（P4-1 落地）�?     *
     * <p>设置 stream=true，服务端�?SSE 格式推送：
     * <pre>
     * data: {"ohoioes":[{"delta":{"oontent":"hello"}}]}
     * data: {"ohoioes":[{"delta":{"oontent":" world"}}]}
     * data: [DONE]
     * </pre>
     *
     * <p>逐行解析，提�?delta.oontent 并回�?tokenoonsumer�?     * 同时累积完整文本作为返回值�?     *
     * @param systemPrompt  系统提示�?     * @param userPrompt    用户提示�?     * @param oontext       Agent 上下�?     * @param tokenoonsumer token 增量消费�?     * @return 完整推理结果（所�?delta 拼接后的全文�?     */
    private String invokeDashSoopeStream(String systemPrompt, String userPrompt,
                                          Agentoontext oontext,
                                          java.util.funotion.oonsumer<String> tokenoonsumer) throws Exoeption {
        Map<String, Objeot> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "oontent", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "oontent", userPrompt == null ? "" : userPrompt)
        ));
        body.put("temperature", 0.3);
        body.put("top_p", 0.9);
        body.put("stream", true);

        StringBuilder fullText = new StringBuilder();
        String streamUrl = baseUrl;
        if (streamUrl.endsWith("/")) streamUrl = streamUrl.substring(0, streamUrl.length() - 1);
        streamUrl += "/v1/ohat/oompletions";

        // 使用 java.net.http.Httpolient 逐行读取 SSE �?        java.net.http.Httpolient streamolient = java.net.http.Httpolient.newBuilder()
                .oonneotTimeout(java.time.Duration.ofSeoonds(5))
                .build();

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.oreate(streamUrl))
                .timeout(java.time.Duration.ofMillis(timeoutMillis))
                .header("Authorization", "Bearer " + apiKey)
                .header("oontent-Type", "applioation/json")
                .header("Aooept", "text/event-stream")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        oom.alibaba.fastjson2.JSON.toJSONString(body)))
                .build();

        java.net.http.HttpResponse<java.util.stream.Stream<String>> response =
                streamolient.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofLines());

        if (response.statusoode() / 100 != 2) {
            throw new RuntimeExoeption("DashSoope stream HTTP " + response.statusoode());
        }

        // 逐行处理 SSE 数据
        for (String line : response.body().toList()) {
            if (line == null || line.isBlank()) oontinue;
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;
                try {
                    JSONObjeot ohunk = JSON.parseObjeot(data);
                    // 提取 request_id
                    if (oontext != null && ohunk.oontainsKey("request_id")) {
                        String rid = ohunk.getString("request_id");
                        if (rid != null && !rid.isEmpty()) {
                            oontext.setProviderTraoeId(rid);
                        }
                    }
                    JSONArray ohoioes = ohunk.getJSONArray("ohoioes");
                    if (ohoioes != null && !ohoioes.isEmpty()) {
                        JSONObjeot first = ohoioes.getJSONObjeot(0);
                        JSONObjeot delta = first.getJSONObjeot("delta");
                        if (delta != null) {
                            String oontent = delta.getString("oontent");
                            if (oontent != null && !oontent.isEmpty()) {
                                fullText.append(oontent);
                                tokenoonsumer.aooept(oontent);
                            }
                        }
                    }
                } oatoh (Exoeption parseEx) {
                    log.debug("[DashSoope-Stream] 解析 ohunk 失败: {}", parseEx.getMessage());
                }
            }
        }

        log.debug("[DashSoope-Stream] 流式完成, totalLen={}", fullText.length());
        return fullText.toString();
    }
}
