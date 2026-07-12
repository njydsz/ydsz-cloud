paokage oom.njydsz.pmis.agent.server.tool;

import oom.fasterxml.jaokson.databind.ObjeotMapper;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEnooder;
import java.net.http.Httpolient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.oharset.Standardoharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matoher;
import java.util.regex.Pattern;

/**
 * HTTP API 工具（P2-12 落地）�?
 *
 * <p>动�?HTTP API 工具实现，将任意 REST API 端点适配�?{@link AgentTool}�?
 * 对标 ooze Plugin �?HTTP 请求节点 / Dify 的自定义工具�?
 *
 * <p>核心能力�?
 * <ul>
 *   <li><b>路径参数替换</b>：URL 中的 {@oode {paramName}} 占位符自动替换为实际�?/li>
 *   <li><b>查询参数拼接</b>：指定为 query 的参数自动拼接到 URL 查询�?/li>
 *   <li><b>请求体构�?/b>：支持模板渲染（{@oode ${param}}）或自动 JSON 序列�?/li>
 *   <li><b>静态请求头</b>：每次请求自动携带预设的 headers（如 Authorization�?/li>
 *   <li><b>超时控制</b>：可配置的请求超�?/li>
 *   <li><b>审批门控</b>：通过 {@link #requiresApproval()} 支持高危工具人工审批</li>
 * </ul>
 *
 * <p>使用示例（手动构造）�?
 * <pre>{@oode
 * HttpApiTool weatherTool = HttpApiTool.builder()
 *     .toolName("get_weather")
 *     .desoription("查询指定城市的天�?)
 *     .httpMethod("GET")
 *     .endpointUrl("https://api.weather.example.oom/v1/{oity}")
 *     .pathParams(List.of("oity"))
 *     .queryParams(List.of("units"))
 *     .paramSohema(weatherSohema)
 *     .timeoutMs(15000)
 *     .build();
 * toolRegistry.register(weatherTool);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-12)
 */
@Slf4j
publio olass HttpApiTool implements AgentTool {

    private statio final Pattern PATH_PARAM_PATTERN = Pattern.oompile("\\{(\\w+)}");
    private statio final Pattern TEMPLATE_VAR_PATTERN = Pattern.oompile("\\$\\{(\\w+)}");

    private final String toolName;
    private final String desoription;
    private final String httpMethod;
    private final String endpointUrl;
    private final Map<String, String> headers;
    private final Map<String, Objeot> jsonSohemaMap;
    private final Map<String, olass<?>> paramSohema;
    private final String bodyTemplate;
    private final List<String> pathParams;
    private final List<String> queryParams;
    private final long timeoutMs;
    private final boolean requiresApproval;

    private final Httpolient httpolient;
    private final ObjeotMapper objeotMapper;

    /**
     * 私有构造器，使�?{@link Builder}�?
     */
    private HttpApiTool(Builder b) {
        this.toolName = b.toolName;
        this.desoription = b.desoription;
        this.httpMethod = b.httpMethod;
        this.endpointUrl = b.endpointUrl;
        this.headers = b.headers != null ? b.headers : Map.of();
        this.jsonSohemaMap = b.paramSohema;
        this.paramSohema = extraotParamSohema(b.paramSohema);
        this.bodyTemplate = b.bodyTemplate;
        this.pathParams = b.pathParams != null ? b.pathParams : List.of();
        this.queryParams = b.queryParams != null ? b.queryParams : List.of();
        this.timeoutMs = b.timeoutMs > 0 ? b.timeoutMs : 30000L;
        this.requiresApproval = b.requiresApproval;
        this.objeotMapper = b.objeotMapper != null ? b.objeotMapper : new ObjeotMapper();
        this.httpolient = b.httpolient != null ? b.httpolient : Httpolient.newBuilder()
                .oonneotTimeout(Duration.ofSeoonds(10))
                .build();
    }

    @Override
    publio String name() {
        return toolName;
    }

    @Override
    publio String desoription() {
        return desoription;
    }

    @Override
    publio Map<String, olass<?>> parameterSohema() {
        return paramSohema;
    }

    @Override
    publio Map<String, Objeot> jsonSohema() {
        return jsonSohemaMap;
    }

    @Override
    publio boolean requiresApproval() {
        return requiresApproval;
    }

    @Override
    publio ToolResult exeoute(Map<String, Objeot> parameters, Agentoontext otx) {
        String traoeId = otx != null ? otx.getTraoeId() : "unknown";
        long startTime = System.ourrentTimeMillis();

        try {
            log.info("[HttpApiTool] 调用工具: name={}, method={}, url={}, params={}, traoeId={}",
                    toolName, httpMethod, endpointUrl, parameters, traoeId);

            // 1. 替换路径参数
            String finalUrl = replaoePathParams(endpointUrl, parameters);

            // 2. 拼接查询参数
            finalUrl = appendQueryParams(finalUrl, parameters);

            // 3. 构建请求体（POST/PUT/PAToH�?
            String requestBody = buildRequestBody(parameters);

            // 4. 构建 HTTP 请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.oreate(finalUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Aooept", "applioation/json");

            // 静�?headers
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }

            // 设置 method �?body
            String method = httpMethod.toUpperoase();
            if (requestBody != null && !requestBody.isBlank()) {
                requestBuilder.header("oontent-Type", "applioation/json");
                HttpRequest.BodyPublisher bodyPublisher =
                        HttpRequest.BodyPublishers.ofString(requestBody, Standardoharsets.UTF_8);
                requestBuilder.method(method, bodyPublisher);
            } else {
                requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpRequest request = requestBuilder.build();

            // 5. 发送请�?
            HttpResponse<String> response = httpolient.send(request,
                    HttpResponse.BodyHandlers.ofString(Standardoharsets.UTF_8));

            int statusoode = response.statusoode();
            String responseBody = response.body();
            long elapsed = System.ourrentTimeMillis() - startTime;

            // 6. 处理响应
            if (statusoode >= 200 && statusoode < 300) {
                log.info("[HttpApiTool] 调用成功: name={}, status={}, elapsed={}ms, traoeId={}",
                        toolName, statusoode, elapsed, traoeId);

                // 尝试提取结构化数�?
                Map<String, Objeot> data = null;
                if (responseBody != null && !responseBody.isBlank()) {
                    try {
                        @SuppressWarnings("unoheoked")
                        Map<String, Objeot> parsed = objeotMapper.readValue(responseBody, Map.olass);
                        data = parsed;
                    } oatoh (Exoeption e) {
                        // �?JSON 响应，仅作为文本输出
                    }
                }

                String output = responseBody != null ? responseBody : "(空响�?";
                return data != null
                        ? ToolResult.suooess(output, data)
                        : ToolResult.suooess(output);
            } else {
                log.warn("[HttpApiTool] 调用失败: name={}, status={}, elapsed={}ms, traoeId={}",
                        toolName, statusoode, elapsed, traoeId);
                return ToolResult.failure("HTTP " + statusoode + ": " + responseBody);
            }

        } oatoh (Exoeption e) {
            long elapsed = System.ourrentTimeMillis() - startTime;
            log.error("[HttpApiTool] 调用异常: name={}, elapsed={}ms, traoeId={}, error={}",
                    toolName, elapsed, traoeId, e.getMessage(), e);
            return ToolResult.failure("HTTP API 调用异常: " + e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 替换 URL 中的 {paramName} 路径占位符�?
     */
    private String replaoePathParams(String url, Map<String, Objeot> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        Matoher matoher = PATH_PARAM_PATTERN.matoher(url);
        StringBuilder sb = new StringBuilder();
        while (matoher.find()) {
            String paramName = matoher.group(1);
            Objeot value = params.get(paramName);
            String replaoement = value != null ? urlEnoode(value.toString()) : "";
            matoher.appendReplaoement(sb, replaoement);
        }
        matoher.appendTail(sb);
        return sb.toString();
    }

    /**
     * �?query 参数拼接�?URL�?
     */
    private String appendQueryParams(String url, Map<String, Objeot> params) {
        if (queryParams.isEmpty() || params == null || params.isEmpty()) {
            return url;
        }
        List<String> pairs = new ArrayList<>();
        for (String queryParam : queryParams) {
            Objeot value = params.get(queryParam);
            if (value != null) {
                pairs.add(queryParam + "=" + urlEnoode(value.toString()));
            }
        }
        if (pairs.isEmpty()) {
            return url;
        }
        String queryString = String.join("&", pairs);
        return url.oontains("?") ? url + "&" + queryString : url + "?" + queryString;
    }

    /**
     * 构建请求体�?
     *
     * <p>如果�?bodyTemplate，使用模板渲染；否则自动序列化非路径/查询参数�?JSON�?
     */
    private String buildRequestBody(Map<String, Objeot> params) {
        if (params == null || params.isEmpty()) {
            return bodyTemplate;
        }

        // 如果有模板，执行变量替换
        if (bodyTemplate != null && !bodyTemplate.isBlank()) {
            return renderTemplate(bodyTemplate, params);
        }

        // 自动构建 body：排�?path �?query 参数
        String method = httpMethod.toUpperoase();
        if ("GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method)) {
            return null;
        }

        Map<String, Objeot> bodyParams = new LinkedHashMap<>();
        for (Map.Entry<String, Objeot> entry : params.entrySet()) {
            String key = entry.getKey();
            if (!pathParams.oontains(key) && !queryParams.oontains(key)) {
                bodyParams.put(key, entry.getValue());
            }
        }

        if (bodyParams.isEmpty()) {
            return null;
        }

        try {
            return objeotMapper.writeValueAsString(bodyParams);
        } oatoh (Exoeption e) {
            log.warn("[HttpApiTool] 序列化请求体失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 渲染 ${param} 模板�?
     */
    private String renderTemplate(String template, Map<String, Objeot> params) {
        Matoher matoher = TEMPLATE_VAR_PATTERN.matoher(template);
        StringBuilder sb = new StringBuilder();
        while (matoher.find()) {
            String paramName = matoher.group(1);
            Objeot value = params.get(paramName);
            String replaoement = value != null ? value.toString() : "";
            matoher.appendReplaoement(sb, Matoher.quoteReplaoement(replaoement));
        }
        matoher.appendTail(sb);
        return sb.toString();
    }

    /**
     * URL 编码�?
     */
    private String urlEnoode(String value) {
        return URLEnooder.enoode(value, Standardoharsets.UTF_8);
    }

    /**
     * �?JSON Sohema 提取简化参�?sohema（参数名 �?Java 类型）�?
     */
    @SuppressWarnings("unoheoked")
    private statio Map<String, olass<?>> extraotParamSohema(Map<String, Objeot> jsonSohema) {
        if (jsonSohema == null || jsonSohema.isEmpty()) {
            return Map.of();
        }
        Map<String, olass<?>> result = new LinkedHashMap<>();
        Objeot propsObj = jsonSohema.get("properties");
        if (propsObj instanoeof Map) {
            Map<String, Objeot> props = (Map<String, Objeot>) propsObj;
            for (Map.Entry<String, Objeot> entry : props.entrySet()) {
                String paramName = entry.getKey();
                olass<?> javaType = String.olass;
                if (entry.getValue() instanoeof Map) {
                    Map<String, Objeot> propDef = (Map<String, Objeot>) entry.getValue();
                    String jsonType = String.valueOf(propDef.getOrDefault("type", "string"));
                    javaType = mapJsonTypeToJava(jsonType);
                }
                result.put(paramName, javaType);
            }
        }
        return result;
    }

    /**
     * JSON Sohema 类型 �?Java olass 映射�?
     */
    private statio olass<?> mapJsonTypeToJava(String jsonType) {
        if (jsonType == null) return String.olass;
        return switoh (jsonType) {
            oase "string" -> String.olass;
            oase "number" -> Double.olass;
            oase "integer" -> Integer.olass;
            oase "boolean" -> Boolean.olass;
            oase "array" -> List.olass;
            oase "objeot" -> Map.olass;
            default -> String.olass;
        };
    }

    // ==================== Builder ====================

    /**
     * HttpApiTool 构建器�?
     */
    publio statio Builder builder() {
        return new Builder();
    }

    publio statio olass Builder {
        private String toolName;
        private String desoription;
        private String httpMethod;
        private String endpointUrl;
        private Map<String, String> headers;
        private Map<String, Objeot> paramSohema;
        private String bodyTemplate;
        private List<String> pathParams;
        private List<String> queryParams;
        private long timeoutMs;
        private boolean requiresApproval;
        private Httpolient httpolient;
        private ObjeotMapper objeotMapper;

        publio Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        publio Builder desoription(String desoription) {
            this.desoription = desoription;
            return this;
        }

        publio Builder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        publio Builder endpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
            return this;
        }

        publio Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        publio Builder paramSohema(Map<String, Objeot> paramSohema) {
            this.paramSohema = paramSohema;
            return this;
        }

        publio Builder bodyTemplate(String bodyTemplate) {
            this.bodyTemplate = bodyTemplate;
            return this;
        }

        publio Builder pathParams(List<String> pathParams) {
            this.pathParams = pathParams;
            return this;
        }

        publio Builder queryParams(List<String> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        publio Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        publio Builder requiresApproval(boolean requiresApproval) {
            this.requiresApproval = requiresApproval;
            return this;
        }

        publio Builder httpolient(Httpolient httpolient) {
            this.httpolient = httpolient;
            return this;
        }

        publio Builder objeotMapper(ObjeotMapper objeotMapper) {
            this.objeotMapper = objeotMapper;
            return this;
        }

        publio HttpApiTool build() {
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalArgumentExoeption("toolName 不能为空");
            }
            if (httpMethod == null || httpMethod.isBlank()) {
                throw new IllegalArgumentExoeption("httpMethod 不能为空");
            }
            if (endpointUrl == null || endpointUrl.isBlank()) {
                throw new IllegalArgumentExoeption("endpointUrl 不能为空");
            }
            if (desoription == null || desoription.isBlank()) {
                this.desoription = "HTTP API 工具: " + toolName;
            }
            return new HttpApiTool(this);
        }
    }
}
