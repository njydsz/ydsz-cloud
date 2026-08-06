package com.remisoft.cronjob.server.core.handler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.tree.JsonNode;
import com.remisoft.common.json.tree.ObjectNode;
import com.remisoft.common.json.schema.JsonSchema;
import com.remisoft.common.json.schema.JsonSchemaValidator;
import com.remisoft.common.json.schema.ValidationResult;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;

import com.remisoft.cronjob.domain.job.JobHandler;
import com.remisoft.cronjob.server.config.CronjobProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * HTTP 任务处理器（P1-5）。
 *
 * <p>支持 {@code jobType=HTTP} 的任务，通过 HTTP 调用外部 API 执行业务逻辑。
 * 对标 XXL-Job 的 HTTP 任务类型和 PowerJob 的 HTTP 任务处理器。
 *
 * <h3>paramsJson 格式</h3>
 * <pre>{@code
 * {
 *   "url": "https://api.example.com/endpoint",
 *   "method": "POST",
 *   "headers": {
 *     "Content-Type": "application/json",
 *     "Authorization": "Bearer xxx"
 *   },
 *   "body": "{\"key\":\"value\"}",
 *   "timeoutMs": 30000,
 *   "successStatus": "200-299"
 * }
 * }</pre>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code url}（必填）: 目标 URL</li>
 *   <li>{@code method}（可选）: HTTP 方法，默认 GET</li>
 *   <li>{@code headers}（可选）: 请求头键值对</li>
 *   <li>{@code body}（可选）: 请求体（POST/PUT/PATCH 时使用）</li>
 *   <li>{@code timeoutMs}（可选）: 请求超时毫秒，覆盖全局默认值</li>
 *   <li>{@code successStatus}（可选）: 成功状态码范围，如 "200-299" 或 "200,201,204"</li>
 * </ul>
 *
 * <p>使用 JDK 内置 {@link HttpClient}，避免引入第三方 HTTP 客户端依赖。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnMissingBean(HttpJobHandler.class)
public class HttpJobHandler implements JobHandler {

    /** Bean 名称，dispatcher 在 jobType=HTTP 时路由到此 handler */
    public static final String BEAN_NAME = "httpJobHandler";

    /**
     * P2-1: HTTP 任务参数 JsonSchema —— 试点验证自研 schema 引擎成熟度。
     *
     * <p>{@code JsonSchema} 类已通过 1.0 评估保留为自研 schema 实现（标注 {@code @Experimental}），
     * 此处试点旨在验证其运行时正确性与 API 可用性。
     */
    private static final JsonSchema HTTP_PARAMS_SCHEMA = JsonSchema.object()
            .addProperty("url",
                    JsonSchema.string().required().minLength(1)
                            .description("目标 URL（必填）"))
            .addProperty("method",
                    JsonSchema.string()
                            .enumValues("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
                            .description("HTTP 方法，默认 GET"))
            .addProperty("headers",
                    JsonSchema.object()
                            .description("请求头键值对"))
            .addProperty("body",
                    JsonSchema.string()
                            .description("请求体"))
            .addProperty("timeoutMs",
                    JsonSchema.integer().minimum(1000)
                            .description("请求超时毫秒"))
            .addProperty("successStatus",
                    JsonSchema.string()
                            .description("成功状态码范围，如 200-299"))
            .addRequired("url");

    private final CronjobProperties cronjobProperties;
    private final HttpClient httpClient;

    public HttpJobHandler(CronjobProperties cronjobProperties) {
        this.cronjobProperties = cronjobProperties;
        CronjobProperties.Http httpConfig = cronjobProperties.getHttp();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(httpConfig.getConnectTimeoutSeconds()))
                .followRedirects(httpConfig.isFollowRedirects()
                        ? HttpClient.Redirect.NORMAL
                        : HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Object execute(String paramsJson) throws Exception {
        if (paramsJson == null || paramsJson.isBlank()) {
            throw new IllegalArgumentException("HTTP 任务参数(paramsJson)为空");
        }

        ObjectNode params = RemiJson.parseObject(paramsJson);

        // P2-1: JsonSchema 参数结构校验（试点验证自研 schema 引擎）
        List<String> schemaErrors = validateParams((Map<String, Object>) params.asValue());
        if (!schemaErrors.isEmpty()) {
            log.warn("[HttpJobHandler] 参数 schema 校验失败: {} errors={}", schemaErrors.size(), schemaErrors);
        }

        String url = params.getString("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("HTTP 任务参数缺少 url");
        }

        String method = params.getString("method");
        if (method == null || method.isBlank()) {
            method = "GET";
        }
        method = method.toUpperCase();

        String body = params.getString("body");
        Integer timeoutMs = params.getInteger("timeoutMs");
        String successStatus = params.getString("successStatus");
        if (successStatus == null || successStatus.isBlank()) {
            successStatus = cronjobProperties.getHttp().getSuccessStatusRange();
        }

        // 构建请求
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url));

        // 设置超时
        Duration timeout = timeoutMs != null && timeoutMs > 0
                ? Duration.ofMillis(timeoutMs)
                : Duration.ofSeconds(cronjobProperties.getHttp().getRequestTimeoutSeconds());
        requestBuilder.timeout(timeout);

        // 设置请求头
        ObjectNode headers = params.getObjectNode("headers");
        if (headers != null) {
            for (Map.Entry<String, JsonNode> entry : headers.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isNull()) {
                    requestBuilder.header(entry.getKey(), entry.getValue().asText());
                }
            }
        }

        // 设置 HTTP 方法和请求体
        HttpRequest.BodyPublisher bodyPublisher = body != null && !body.isBlank()
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();
        switch (method) {
            case "GET" -> requestBuilder.GET();
            case "POST" -> requestBuilder.POST(bodyPublisher);
            case "PUT" -> requestBuilder.PUT(bodyPublisher);
            case "PATCH" -> requestBuilder.method("PATCH", bodyPublisher);
            case "DELETE" -> requestBuilder.DELETE();
            case "HEAD" -> requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            default -> throw new IllegalArgumentException("不支持的 HTTP 方法: " + method);
        }

        // 执行请求
        log.info("[HttpJobHandler] 发送请求: method={} url={} timeoutMs={}",
                method, url, timeout.toMillis());
        HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        String responseBody = response.body();

        // 校验响应状态码
        if (!isSuccessStatus(status, successStatus)) {
            throw new RuntimeException("HTTP 请求失败: status=" + status
                    + " url=" + url
                    + " body=" + truncate(responseBody));
        }

        log.info("[HttpJobHandler] 请求成功: method={} url={} status={} bodyLen={}",
                method, url, status, responseBody == null ? 0 : responseBody.length());

        // 返回结构化结果
        ObjectNode result = new ObjectNode();
        result.put("status", status);
        result.put("body", responseBody);
        result.put("url", url);
        result.put("method", method);
        return result;
    }

    /**
     * 判断 HTTP 状态码是否在成功范围内。
     *
     * <p>支持两种格式：
     * <ul>
     *   <li>范围格式: "200-299"</li>
     *   <li>列表格式: "200,201,204"</li>
     * </ul>
     */
    private boolean isSuccessStatus(int status, String successStatus) {
        if (successStatus == null || successStatus.isBlank()) {
            return status >= 200 && status < 300;
        }
        String trimmed = successStatus.trim();
        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-");
            if (parts.length == 2) {
                try {
                    int min = Integer.parseInt(parts[0].trim());
                    int max = Integer.parseInt(parts[1].trim());
                    return status >= min && status <= max;
                } catch (NumberFormatException e) {
                    log.warn("[HttpJobHandler] 无效的成功状态码范围: {}", successStatus);
                    return status >= 200 && status < 300;
                }
            }
        }
        if (trimmed.contains(",")) {
            String[] codes = trimmed.split(",");
            for (String code : codes) {
                try {
                    if (status == Integer.parseInt(code.trim())) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // skip invalid code
                }
            }
            return false;
        }
        try {
            return status == Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return status >= 200 && status < 300;
        }
    }

    /**
     * 截断字符串，避免日志过长。
     */
    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    /**
     * P2-1: 使用自研 JsonSchema 引擎校验 HTTP 任务参数结构。
     *
     * <p>试点目的：验证 {@link JsonSchema} + {@link JsonSchemaValidator} 运行时正确性。
     * 当前仅记录告警日志，不阻断任务执行——待 schema 引擎成熟度验证通过后再提升为硬校验。
     *
     * @param params 已解析的任务参数 Map
     * @return 校验错误列表（空列表表示通过）
     */
    private List<String> validateParams(Map<String, Object> params) {
        try {
            ValidationResult result = JsonSchemaValidator.validate(HTTP_PARAMS_SCHEMA, params);
            if (result.isValid()) {
                return List.of();
            }
            return result.getErrors();
        } catch (Exception e) {
            log.warn("[HttpJobHandler] JsonSchema 校验异常: {}", e.getMessage());
            return List.of("schema 校验引擎异常: " + e.getMessage());
        }
    }
}
