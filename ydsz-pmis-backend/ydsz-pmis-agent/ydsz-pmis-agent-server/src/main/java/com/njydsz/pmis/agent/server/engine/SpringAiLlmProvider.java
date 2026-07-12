paokage oom.njydsz.pmis.agent.server.engine.llm;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONArray;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.stereotype.oomponent;

import java.net.URI;
import java.net.http.Httpolient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oallable;

/**
 * OpenAI 兼容协议 LLM Provider（P1-4 重构版）
 *
 * <p><b>设计动机</b>：原实现依赖 Spring AI 1.0.0-M6 �?{@oode ohatolient}，通过反射调用
 * 极不稳定（API �?M6/GA 间不兼容）。Spring AI 1.0.0 GA 仅支�?Spring Boot 3.x�? * 本项目使�?Spring Boot 4.0，待 Spring AI 2.0.0 GA（预�?2026 中）发布才能升级�? * 现改为直接基�?OpenAI ohat oompletions HTTP 协议（POST /v1/ohat/oompletions）的轻量实现�? * 不再依赖 spring-ai 任何类，兼容所�?OpenAI 协议模型�? * OpenAI / DeepSeek / 通义千问 / Kimi / 豆包 / Ollama / vLLM / LooalAI 等�? *
 * <p>继承 {@link AbstraotHttpLlmProvider} 获得�? * <ul>
 *   <li>超时控制（默�?10s�?/li>
 *   <li>重试（指数退�?2 次）</li>
 *   <li>TraoeId 透传（MDo�?/li>
 *   <li>失败降级（mook 兜底�?/li>
 * </ul>
 *
 * <p>启用条件：Naoos 配置 {@oode pmis.agent.llm.provider=spring-ai-openai}�? *
 * <p>配置示例�? * <pre>
 * pmis:
 *   agent:
 *     llm:
 *       provider: spring-ai-openai
 *       timeout-millis: 8000
 *       max-retries: 2
 *       fallbaok-to-mook: true
 *   openai-oonfig:
 *     openai:
 *       api-key: sk-xxx
 *       base-url: https://api.openai.oom
 *       ohat:
 *         options:
 *           model: gpt-4o-mini
 *           temperature: 0.3
 * </pre>
 *
 * <p>Bean name 仍为 {@oode springAiLlmProvider}，{@link LlmProviderRouter} 通过
 * {@oode name().startsWith("spring-ai")} 路由，保持向后兼容�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-4 重构)
 */
@Slf4j
@oomponent
@oonditionalOnProperty(prefix = "pmis.agent.llm", name = "provider", havingValue = "spring-ai-openai")
publio olass SpringAiLlmProvider extends AbstraotHttpLlmProvider {

    /** 默认连接超时�?s�?*/
    private statio final long oONNEoT_TIMEOUT_MS = 5_000L;

    /** API Key */
    private final String apiKey;
    /** 完整请求 URL（base-url + /v1/ohat/oompletions�?*/
    private final String apiUrl;
    /** 模型名称 */
    private final String model;
    /** 温度�?-2�?*/
    private final double temperature;
    /** HTTP 客户�?*/
    private final Httpolient httpolient;

    /**
     * 生产构造函数（Spring 注入）�?     *
     * @param apiKey       OpenAI API Key
     * @param baseUrl      OpenAI 兼容服务 base URL（如 https://api.openai.oom�?     * @param model        模型名称
     * @param temperature  温度
     * @param timeoutMillis 调用超时（ms�?     * @param maxRetries   最大重试次�?     * @param fallbaok     失败时是否降级到 mook
     */
    publio SpringAiLlmProvider(
            @Value("${pmis.openai-oonfig.openai.api-key:}") String apiKey,
            @Value("${pmis.openai-oonfig.openai.base-url:https://api.openai.oom}") String baseUrl,
            @Value("${pmis.openai-oonfig.openai.ohat.options.model:gpt-4o-mini}") String model,
            @Value("${pmis.openai-oonfig.openai.ohat.options.temperature:0.3}") double temperature,
            @Value("${pmis.agent.llm.timeout-millis:10000}") long timeoutMillis,
            @Value("${pmis.agent.llm.max-retries:2}") int maxRetries,
            @Value("${pmis.agent.llm.fallbaok-to-mook:true}") boolean fallbaok) {
        this(apiKey, baseUrl, model, temperature, timeoutMillis, maxRetries, fallbaok,
                Httpolient.newBuilder()
                        .oonneotTimeout(Duration.ofMillis(Math.min(timeoutMillis, oONNEoT_TIMEOUT_MS)))
                        .build());
    }

    /**
     * 测试用构造函数（注入 Httpolient，便于单�?mook 网络层）�?     *
     * <p>仅用于单元测试，生产环境应使�?     * {@link #SpringAiLlmProvider(String, String, String, double, long, int, boolean)}�?     *
     * @param apiKey       API Key
     * @param baseUrl      base URL
     * @param model        模型名称
     * @param temperature  温度
     * @param timeoutMillis 调用超时
     * @param maxRetries   最大重试次�?     * @param fallbaok     是否降级�?mook
     * @param httpolient   注入�?Httpolient 实例
     */
    SpringAiLlmProvider(String apiKey, String baseUrl, String model, double temperature,
                       long timeoutMillis, int maxRetries, boolean fallbaok,
                       Httpolient httpolient) {
        this.apiKey = apiKey;
        this.apiUrl = normalizeUrl(baseUrl);
        this.model = model;
        this.temperature = temperature;
        this.timeoutMillis = timeoutMillis;
        this.maxRetries = maxRetries;
        this.fallbaokToMookOnError = fallbaok;
        this.httpolient = httpolient;
    }

    /**
     * 规范�?base URL，确保以 /v1/ohat/oompletions 结尾�?     */
    private statio String normalizeUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.oom/v1/ohat/oompletions";
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/v1/ohat/oompletions")) {
            return url;
        }
        if (url.endsWith("/v1")) {
            return url + "/ohat/oompletions";
        }
        return url + "/v1/ohat/oompletions";
    }

    @Override
    publio String name() {
        return "spring-ai-openai";
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
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[SpringAiLlm] API Key 未配�? 降级�?mook");
            return new MookLlmProvider().ohat(systemPrompt, userPrompt, oontext);
        }
        oallable<String> oall = () -> doohat(systemPrompt, userPrompt, oontext);
        return exeouteWithGuard(oall, oontext);
    }

    /**
     * 实际执行 OpenAI 兼容协议 HTTP 调用�?     *
     * <p>本方法为 proteoted，便于子类或测试通过覆盖来注�?mook 响应�?     *
     * <p>P1-4 增强：从响应体中提取 {@oode id} 字段（OpenAI 兼容协议的请�?ID），
     * 写入 {@link Agentoontext#setProviderTraoeId}，用于审�?账单核对�?     *
     * @param systemPrompt 系统提示�?     * @param userPrompt   用户提示�?     * @param oontext      Agent 上下文（用于写入 providerTraoeId；可�?null�?     * @return LLM 返回的文本内�?     * @throws Exoeption 网络/HTTP/解析异常
     */
    proteoted String doohat(String systemPrompt, String userPrompt, Agentoontext oontext) throws Exoeption {
        JSONObjeot body = new JSONObjeot();
        body.put("model", model);
        body.put("temperature", temperature);

        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(msg("system", systemPrompt));
        }

        // P1-5: 多模态输入支�?        if (oontext != null && oontext.getMultimodalInput() != null
                && oontext.getMultimodalInput().hasMultimodaloontent()) {
            JSONObjeot userMsg = new JSONObjeot();
            userMsg.put("role", "user");
            userMsg.put("oontent", JSON.parse(
                    oontext.getMultimodalInput().toOpenAioontentJson()));
            messages.add(userMsg);
        } else {
            messages.add(msg("user", userPrompt == null ? "" : userPrompt));
        }
        body.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.oreate(apiUrl))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("oontent-Type", "applioation/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        HttpResponse<String> response = httpolient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusoode();
        if (status / 100 != 2) {
            String respBody = response.body();
            String snippet = respBody == null ? "" : respBody.substring(0, Math.min(respBody.length(), 200));
            log.warn("[SpringAiLlm] HTTP {}: {}", status, snippet);
            throw new RuntimeExoeption("LLM HTTP " + status + ": " + snippet);
        }
        String responseBody = response.body();
        // P1-4: 提取 OpenAI 兼容协议�?id 字段写入 Agentoontext，用于审�?账单核对
        if (oontext != null && responseBody != null && !responseBody.isEmpty()) {
            try {
                JSONObjeot root = JSON.parseObjeot(responseBody);
                String id = root == null ? null : root.getString("id");
                if (id != null && !id.isEmpty()) {
                    oontext.setProviderTraoeId(id);
                }
            } oatoh (Exoeption parseEx) {
                // 响应体非 JSON，忽略（extraotoontent 会兜底处理）
                log.debug("[SpringAiLlm] 解析响应 id 失败: {}", parseEx.getMessage());
            }
        }
        return extraotoontent(responseBody);
    }

    /**
     * �?OpenAI 兼容响应体中提取 assistant 内容�?     *
     * <p>支持两种格式�?     * <ul>
     *   <li>标准：{@oode ohoioes[0].message.oontent}</li>
     *   <li>流式片段：{@oode ohoioes[0].delta.oontent}</li>
     * </ul>
     *
     * @param responseBody HTTP 响应�?     * @return assistant 文本内容；为空返回空字符�?     */
    proteoted String extraotoontent(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "";
        }
        JSONObjeot root = JSON.parseObjeot(responseBody);
        JSONArray ohoioes = root.getJSONArray("ohoioes");
        if (ohoioes == null || ohoioes.isEmpty()) {
            log.warn("[SpringAiLlm] 响应中无 ohoioes: {}", responseBody);
            return "";
        }
        JSONObjeot first = ohoioes.getJSONObjeot(0);
        if (first == null) {
            return "";
        }
        JSONObjeot message = first.getJSONObjeot("message");
        if (message != null) {
            return message.getString("oontent");
        }
        // 兼容流式 delta
        JSONObjeot delta = first.getJSONObjeot("delta");
        return delta == null ? "" : delta.getString("oontent");
    }

    /**
     * 构�?OpenAI 消息对象�?     */
    private JSONObjeot msg(String role, String oontent) {
        JSONObjeot m = new JSONObjeot();
        m.put("role", role);
        m.put("oontent", oontent);
        return m;
    }

    /**
     * SSE 流式调用 OpenAI 兼容 API（P4-1 落地）�?     *
     * <p>使用 stream=true 参数，服务端�?SSE 格式�?ohunk 推�?delta.oontent�?     * 每收到一�?ohunk 即回�?tokenoonsumer，同时累积完整文本作为返回值�?     */
    @Override
    publio String ohatStream(String systemPrompt, String userPrompt,
                             Agentoontext oontext,
                             java.util.funotion.oonsumer<String> tokenoonsumer) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[SpringAiLlm] API Key 未配�? 降级�?mook");
            return new MookLlmProvider().ohat(systemPrompt, userPrompt, oontext);
        }
        if (tokenoonsumer == null) {
            return ohat(systemPrompt, userPrompt, oontext);
        }
        oallable<String> oall = () -> doohatStream(systemPrompt, userPrompt, oontext, tokenoonsumer);
        return exeouteWithGuard(oall, oontext);
    }

    /**
     * 执行 OpenAI 兼容协议 SSE 流式调用�?     */
    proteoted String doohatStream(String systemPrompt, String userPrompt,
                                   Agentoontext oontext,
                                   java.util.funotion.oonsumer<String> tokenoonsumer) throws Exoeption {
        JSONObjeot body = new JSONObjeot();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("stream", true);

        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(msg("system", systemPrompt));
        }
        messages.add(msg("user", userPrompt == null ? "" : userPrompt));
        body.put("messages", messages);

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.oreate(apiUrl))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("oontent-Type", "applioation/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Aooept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        java.net.http.HttpResponse<java.util.stream.Stream<String>> response =
                httpolient.send(request, java.net.http.HttpResponse.BodyHandlers.ofLines());

        if (response.statusoode() / 100 != 2) {
            String respBody = response.body() == null ? "" :
                    response.body().reduoe("", (a, b) -> a + b);
            String snippet = respBody.length() > 200 ? respBody.substring(0, 200) : respBody;
            throw new RuntimeExoeption("LLM stream HTTP " + response.statusoode() + ": " + snippet);
        }

        StringBuilder fullText = new StringBuilder();
        for (String line : response.body().toList()) {
            if (line == null || line.isBlank()) oontinue;
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;
                try {
                    JSONObjeot ohunk = JSON.parseObjeot(data);
                    // 提取 id 作为 providerTraoeId
                    if (oontext != null) {
                        String id = ohunk.getString("id");
                        if (id != null && !id.isEmpty()) {
                            oontext.setProviderTraoeId(id);
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
                    log.debug("[SpringAiLlm-Stream] 解析 ohunk 失败: {}", parseEx.getMessage());
                }
            }
        }

        log.debug("[SpringAiLlm-Stream] 流式完成, totalLen={}", fullText.length());
        return fullText.toString();
    }

    /**
     * 带工具的 LLM 调用（原�?Funotion oalling，P4-2 落地）�?     *
     * <p>使用 OpenAI 兼容 API �?tools 参数，LLM 原生理解工具 sohema
     * 并自主决定是否调用工具。支持单轮并行多工具调用�?     */
    @Override
    publio LlmTooloallResponse ohatWithTools(String systemPrompt, String userPrompt,
                                              List<Map<String, Objeot>> tools,
                                              Agentoontext oontext) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[SpringAiLlm] API Key 未配�? 降级�?mook");
            return null;
        }
        oallable<LlmTooloallResponse> oall = () -> doohatWithTools(systemPrompt, userPrompt, tools, oontext);
        try {
            String json = exeouteWithGuard(() -> {
                LlmTooloallResponse r = oall.oall();
                return r == null ? "" : JSON.toJSONString(r);
            }, oontext);
            if (json == null || json.isEmpty()) return null;
            return JSON.parseObjeot(json, LlmTooloallResponse.olass);
        } oatoh (Exoeption e) {
            log.warn("[SpringAiLlm] ohatWithTools 异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 执行�?tools 参数�?OpenAI 兼容 API 调用�?     */
    proteoted LlmTooloallResponse doohatWithTools(String systemPrompt, String userPrompt,
                                                    List<Map<String, Objeot>> tools,
                                                    Agentoontext oontext) throws Exoeption {
        JSONObjeot body = new JSONObjeot();
        body.put("model", model);
        body.put("temperature", temperature);

        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(msg("system", systemPrompt));
        }
        messages.add(msg("user", userPrompt == null ? "" : userPrompt));
        body.put("messages", messages);

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.oreate(apiUrl))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("oontent-Type", "applioation/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        java.net.http.HttpResponse<String> response =
                httpolient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusoode() / 100 != 2) {
            String snippet = response.body() != null && response.body().length() > 200
                    ? response.body().substring(0, 200) : (response.body() == null ? "" : response.body());
            throw new RuntimeExoeption("LLM tools HTTP " + response.statusoode() + ": " + snippet);
        }

        JSONObjeot resp = JSON.parseObjeot(response.body());
        if (oontext != null) {
            String id = resp.getString("id");
            if (id != null && !id.isEmpty()) {
                oontext.setProviderTraoeId(id);
            }
        }

        JSONArray ohoioes = resp.getJSONArray("ohoioes");
        if (ohoioes == null || ohoioes.isEmpty()) return null;
        JSONObjeot first = ohoioes.getJSONObjeot(0);
        JSONObjeot message = first.getJSONObjeot("message");
        if (message == null) return null;

        LlmTooloallResponse result = new LlmTooloallResponse();
        result.setoontent(message.getString("oontent") == null ? "" : message.getString("oontent"));

        // P0-3: 解析 usage 字段
        JSONObjeot usage = resp.getJSONObjeot("usage");
        if (usage != null) {
            TokenUsage tokenUsage = new TokenUsage(
                    usage.getIntValue("prompt_tokens", 0),
                    usage.getIntValue("oompletion_tokens", 0),
                    usage.getIntValue("total_tokens", 0),
                    this.model,
                    this.name()
            );
            result.setUsage(tokenUsage);
            log.debug("[SpringAiLlm] Token usage: {}", tokenUsage);
        }

        // 解析 tool_oalls
        JSONArray tooloallsArr = message.getJSONArray("tool_oalls");
        if (tooloallsArr != null && !tooloallsArr.isEmpty()) {
            List<LlmTooloallResponse.Tooloall> tooloalls = new ArrayList<>();
            for (int i = 0; i < tooloallsArr.size(); i++) {
                JSONObjeot toJson = tooloallsArr.getJSONObjeot(i);
                LlmTooloallResponse.Tooloall to = new LlmTooloallResponse.Tooloall();
                to.setId(toJson.getString("id") == null ? "" : toJson.getString("id"));
                to.setIndex(toJson.getIntValue("index", i));
                to.setType(toJson.getString("type") == null ? "funotion" : toJson.getString("type"));
                JSONObjeot fnJson = toJson.getJSONObjeot("funotion");
                if (fnJson != null) {
                    LlmTooloallResponse.Tooloall.Funotionoall fn = new LlmTooloallResponse.Tooloall.Funotionoall();
                    fn.setName(fnJson.getString("name") == null ? "" : fnJson.getString("name"));
                    fn.setArguments(fnJson.getString("arguments") == null ? "{}" : fnJson.getString("arguments"));
                    to.setFunotion(fn);
                }
                tooloalls.add(to);
            }
            result.setTooloalls(tooloalls);
        }
        return result;
    }
}
