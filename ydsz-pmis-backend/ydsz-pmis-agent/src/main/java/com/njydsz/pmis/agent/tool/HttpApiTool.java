package com.njydsz.pmis.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP API 工具（P2-12 落地）。
 *
 * <p>动态 HTTP API 工具实现，将任意 REST API 端点适配为 {@link AgentTool}。
 * 对标 Coze Plugin 的 HTTP 请求节点 / Dify 的自定义工具。
 *
 * <p>核心能力：
 * <ul>
 *   <li><b>路径参数替换</b>：URL 中的 {@code {paramName}} 占位符自动替换为实际值</li>
 *   <li><b>查询参数拼接</b>：指定为 query 的参数自动拼接到 URL 查询串</li>
 *   <li><b>请求体构建</b>：支持模板渲染（{@code ${param}}）或自动 JSON 序列化</li>
 *   <li><b>静态请求头</b>：每次请求自动携带预设的 headers（如 Authorization）</li>
 *   <li><b>超时控制</b>：可配置的请求超时</li>
 *   <li><b>审批门控</b>：通过 {@link #requiresApproval()} 支持高危工具人工审批</li>
 * </ul>
 *
 * <p>使用示例（手动构造）：
 * <pre>{@code
 * HttpApiTool weatherTool = HttpApiTool.builder()
 *     .toolName("get_weather")
 *     .description("查询指定城市的天气")
 *     .httpMethod("GET")
 *     .endpointUrl("https://api.weather.example.com/v1/{city}")
 *     .pathParams(List.of("city"))
 *     .queryParams(List.of("units"))
 *     .paramSchema(weatherSchema)
 *     .timeoutMs(15000)
 *     .build();
 * toolRegistry.register(weatherTool);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-12)
 */
@Slf4j
public class HttpApiTool implements AgentTool {

    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{(\\w+)}");
    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private final String toolName;
    private final String description;
    private final String httpMethod;
    private final String endpointUrl;
    private final Map<String, String> headers;
    private final Map<String, Object> jsonSchemaMap;
    private final Map<String, Class<?>> paramSchema;
    private final String bodyTemplate;
    private final List<String> pathParams;
    private final List<String> queryParams;
    private final long timeoutMs;
    private final boolean requiresApproval;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 私有构造器，使用 {@link Builder}。
     */
    private HttpApiTool(Builder b) {
        this.toolName = b.toolName;
        this.description = b.description;
        this.httpMethod = b.httpMethod;
        this.endpointUrl = b.endpointUrl;
        this.headers = b.headers != null ? b.headers : Map.of();
        this.jsonSchemaMap = b.paramSchema;
        this.paramSchema = extractParamSchema(b.paramSchema);
        this.bodyTemplate = b.bodyTemplate;
        this.pathParams = b.pathParams != null ? b.pathParams : List.of();
        this.queryParams = b.queryParams != null ? b.queryParams : List.of();
        this.timeoutMs = b.timeoutMs > 0 ? b.timeoutMs : 30000L;
        this.requiresApproval = b.requiresApproval;
        this.objectMapper = b.objectMapper != null ? b.objectMapper : new ObjectMapper();
        this.httpClient = b.httpClient != null ? b.httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String name() {
        return toolName;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Map<String, Class<?>> parameterSchema() {
        return paramSchema;
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return jsonSchemaMap;
    }

    @Override
    public boolean requiresApproval() {
        return requiresApproval;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext ctx) {
        String traceId = ctx != null ? ctx.getTraceId() : "unknown";
        long startTime = System.currentTimeMillis();

        try {
            log.info("[HttpApiTool] 调用工具: name={}, method={}, url={}, params={}, traceId={}",
                    toolName, httpMethod, endpointUrl, parameters, traceId);

            // 1. 替换路径参数
            String finalUrl = replacePathParams(endpointUrl, parameters);

            // 2. 拼接查询参数
            finalUrl = appendQueryParams(finalUrl, parameters);

            // 3. 构建请求体（POST/PUT/PATCH）
            String requestBody = buildRequestBody(parameters);

            // 4. 构建 HTTP 请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(finalUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json");

            // 静态 headers
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }

            // 设置 method 和 body
            String method = httpMethod.toUpperCase();
            if (requestBody != null && !requestBody.isBlank()) {
                requestBuilder.header("Content-Type", "application/json");
                HttpRequest.BodyPublisher bodyPublisher =
                        HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8);
                requestBuilder.method(method, bodyPublisher);
            } else {
                requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpRequest request = requestBuilder.build();

            // 5. 发送请求
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int statusCode = response.statusCode();
            String responseBody = response.body();
            long elapsed = System.currentTimeMillis() - startTime;

            // 6. 处理响应
            if (statusCode >= 200 && statusCode < 300) {
                log.info("[HttpApiTool] 调用成功: name={}, status={}, elapsed={}ms, traceId={}",
                        toolName, statusCode, elapsed, traceId);

                // 尝试提取结构化数据
                Map<String, Object> data = null;
                if (responseBody != null && !responseBody.isBlank()) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsed = objectMapper.readValue(responseBody, Map.class);
                        data = parsed;
                    } catch (Exception e) {
                        // 非 JSON 响应，仅作为文本输出
                    }
                }

                String output = responseBody != null ? responseBody : "(空响应)";
                return data != null
                        ? ToolResult.success(output, data)
                        : ToolResult.success(output);
            } else {
                log.warn("[HttpApiTool] 调用失败: name={}, status={}, elapsed={}ms, traceId={}",
                        toolName, statusCode, elapsed, traceId);
                return ToolResult.failure("HTTP " + statusCode + ": " + responseBody);
            }

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[HttpApiTool] 调用异常: name={}, elapsed={}ms, traceId={}, error={}",
                    toolName, elapsed, traceId, e.getMessage(), e);
            return ToolResult.failure("HTTP API 调用异常: " + e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 替换 URL 中的 {paramName} 路径占位符。
     */
    private String replacePathParams(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        Matcher matcher = PATH_PARAM_PATTERN.matcher(url);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = params.get(paramName);
            String replacement = value != null ? urlEncode(value.toString()) : "";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 将 query 参数拼接到 URL。
     */
    private String appendQueryParams(String url, Map<String, Object> params) {
        if (queryParams.isEmpty() || params == null || params.isEmpty()) {
            return url;
        }
        List<String> pairs = new ArrayList<>();
        for (String queryParam : queryParams) {
            Object value = params.get(queryParam);
            if (value != null) {
                pairs.add(queryParam + "=" + urlEncode(value.toString()));
            }
        }
        if (pairs.isEmpty()) {
            return url;
        }
        String queryString = String.join("&", pairs);
        return url.contains("?") ? url + "&" + queryString : url + "?" + queryString;
    }

    /**
     * 构建请求体。
     *
     * <p>如果有 bodyTemplate，使用模板渲染；否则自动序列化非路径/查询参数为 JSON。
     */
    private String buildRequestBody(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return bodyTemplate;
        }

        // 如果有模板，执行变量替换
        if (bodyTemplate != null && !bodyTemplate.isBlank()) {
            return renderTemplate(bodyTemplate, params);
        }

        // 自动构建 body：排除 path 和 query 参数
        String method = httpMethod.toUpperCase();
        if ("GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method)) {
            return null;
        }

        Map<String, Object> bodyParams = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (!pathParams.contains(key) && !queryParams.contains(key)) {
                bodyParams.put(key, entry.getValue());
            }
        }

        if (bodyParams.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(bodyParams);
        } catch (Exception e) {
            log.warn("[HttpApiTool] 序列化请求体失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 渲染 ${param} 模板。
     */
    private String renderTemplate(String template, Map<String, Object> params) {
        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = params.get(paramName);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * URL 编码。
     */
    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 从 JSON Schema 提取简化参数 schema（参数名 → Java 类型）。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Class<?>> extractParamSchema(Map<String, Object> jsonSchema) {
        if (jsonSchema == null || jsonSchema.isEmpty()) {
            return Map.of();
        }
        Map<String, Class<?>> result = new LinkedHashMap<>();
        Object propsObj = jsonSchema.get("properties");
        if (propsObj instanceof Map) {
            Map<String, Object> props = (Map<String, Object>) propsObj;
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                String paramName = entry.getKey();
                Class<?> javaType = String.class;
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                    String jsonType = String.valueOf(propDef.getOrDefault("type", "string"));
                    javaType = mapJsonTypeToJava(jsonType);
                }
                result.put(paramName, javaType);
            }
        }
        return result;
    }

    /**
     * JSON Schema 类型 → Java Class 映射。
     */
    private static Class<?> mapJsonTypeToJava(String jsonType) {
        if (jsonType == null) return String.class;
        return switch (jsonType) {
            case "string" -> String.class;
            case "number" -> Double.class;
            case "integer" -> Integer.class;
            case "boolean" -> Boolean.class;
            case "array" -> List.class;
            case "object" -> Map.class;
            default -> String.class;
        };
    }

    // ==================== Builder ====================

    /**
     * HttpApiTool 构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String toolName;
        private String description;
        private String httpMethod;
        private String endpointUrl;
        private Map<String, String> headers;
        private Map<String, Object> paramSchema;
        private String bodyTemplate;
        private List<String> pathParams;
        private List<String> queryParams;
        private long timeoutMs;
        private boolean requiresApproval;
        private HttpClient httpClient;
        private ObjectMapper objectMapper;

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder endpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder paramSchema(Map<String, Object> paramSchema) {
            this.paramSchema = paramSchema;
            return this;
        }

        public Builder bodyTemplate(String bodyTemplate) {
            this.bodyTemplate = bodyTemplate;
            return this;
        }

        public Builder pathParams(List<String> pathParams) {
            this.pathParams = pathParams;
            return this;
        }

        public Builder queryParams(List<String> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder requiresApproval(boolean requiresApproval) {
            this.requiresApproval = requiresApproval;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public HttpApiTool build() {
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalArgumentException("toolName 不能为空");
            }
            if (httpMethod == null || httpMethod.isBlank()) {
                throw new IllegalArgumentException("httpMethod 不能为空");
            }
            if (endpointUrl == null || endpointUrl.isBlank()) {
                throw new IllegalArgumentException("endpointUrl 不能为空");
            }
            if (description == null || description.isBlank()) {
                this.description = "HTTP API 工具: " + toolName;
            }
            return new HttpApiTool(this);
        }
    }
}
