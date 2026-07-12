package com.njydsz.pmis.agent.server.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.njydsz.pmis.agent.domain.dto.tool.ToolRegisterDTO;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 3.x 规范解析器（P2-12 落地）。
 *
 * <p>对标 Coze 插件市场的 OpenAPI 导入能力，将 OpenAPI 3.x 规范（JSON/YAML）
 * 自动转换为 {@link ToolRegisterDTO} 列表，实现一键导入外部 API 为 Agent 工具。
 *
 * <p>解析逻辑：
 * <ol>
 *   <li>获取规范内容（URL 拉取或直接传入文本）</li>
 *   <li>解析为 JSON 树（自动识别 JSON / YAML 格式）</li>
 *   <li>遍历 paths → 每个 HTTP method → 提取 operationId / summary / parameters / requestBody</li>
 *   <li>解析 $ref 引用（支持 #/components/schemas/... 和 #/components/parameters/...）</li>
 *   <li>合并 path / query / body 参数为统一 JSON Schema</li>
 *   <li>生成 {@link ToolRegisterDTO}（工具名取 operationId 或 path_method 生成）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-12)
 */
@Slf4j
public class OpenApiSpecParser {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** OpenAPI 支持的 HTTP 方法 */
    private static final List<String> HTTP_METHODS = List.of("get", "post", "put", "delete", "patch", "head", "options");

    /**
     * 构造解析器。
     *
     * @param objectMapper JSON 序列化器
     * @param httpClient   HTTP 客户端（用于拉取远程规范）
     */
    public OpenApiSpecParser(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 从 URL 拉取并解析 OpenAPI 规范。
     *
     * @param specUrl OpenAPI 规范 URL
     * @return 工具注册 DTO 列表
     * @throws Exception 拉取或解析失败
     */
    public List<ToolRegisterDTO> parseFromUrl(String specUrl) throws Exception {
        if (specUrl == null || specUrl.isBlank()) {
            throw new IllegalArgumentException("specUrl 不能为空");
        }

        log.info("[OpenAPI-Parser] 拉取规范: {}", specUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(specUrl))
                .header("Accept", "application/json, application/yaml, text/yaml, */*")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("拉取 OpenAPI 规范失败: HTTP " + status);
        }

        return parseFromContent(response.body(), specUrl);
    }

    /**
     * 解析 OpenAPI 规范文本。
     *
     * @param specContent 规范内容（JSON 或 YAML）
     * @param specUrl     来源 URL（用于记录来源，可为 null）
     * @return 工具注册 DTO 列表
     * @throws Exception 解析失败
     */
    public List<ToolRegisterDTO> parseFromContent(String specContent, String specUrl) throws Exception {
        if (specContent == null || specContent.isBlank()) {
            throw new IllegalArgumentException("specContent 不能为空");
        }

        JsonNode spec = parseToJsonNode(specContent);
        log.info("[OpenAPI-Parser] 解析成功, openapi={}, title={}",
                spec.path("openapi").asText("unknown"),
                spec.path("info").path("title").asText("unknown"));

        // 提取 servers 基础 URL
        String baseUrl = extractBaseUrl(spec);

        return extractOperations(spec, baseUrl, specUrl);
    }

    // ==================== 内部方法 ====================

    /**
     * 将文本解析为 JsonNode（自动识别 JSON / YAML）。
     */
    private JsonNode parseToJsonNode(String content) throws Exception {
        String trimmed = content.trim();
        // JSON 以 { 或 [ 开头
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return objectMapper.readTree(content);
        }
        // 尝试 YAML 解析（使用 SnakeYAML + Jackson）
        try {
            YAMLMapper yamlMapper = new YAMLMapper();
            return yamlMapper.readTree(content);
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException("YAML 格式需要 jackson-dataformat-yaml 依赖", e);
        }
    }

    /**
     * 提取 servers 数组中的第一个 URL 作为基础 URL。
     */
    private String extractBaseUrl(JsonNode spec) {
        JsonNode servers = spec.path("servers");
        if (servers.isArray() && !servers.isEmpty()) {
            String url = servers.get(0).path("url").asText("");
            if (!url.isBlank()) {
                // 去掉末尾斜杠
                return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            }
        }
        // OpenAPI 2.x 兼容：basePath + host
        String host = spec.path("host").asText("");
        String basePath = spec.path("basePath").asText("");
        if (!host.isBlank()) {
            String scheme = "https";
            JsonNode schemes = spec.path("schemes");
            if (schemes.isArray() && !schemes.isEmpty()) {
                scheme = schemes.get(0).asText("https");
            }
            return scheme + "://" + host + (basePath.isBlank() ? "" : basePath);
        }
        return "";
    }

    /**
     * 遍历 paths 提取所有操作。
     */
    private List<ToolRegisterDTO> extractOperations(JsonNode spec, String baseUrl, String specUrl) {
        List<ToolRegisterDTO> result = new ArrayList<>();
        JsonNode paths = spec.path("paths");
        if (!paths.isObject()) {
            log.warn("[OpenAPI-Parser] 规范中未找到 paths 对象");
            return result;
        }

        Iterator<Map.Entry<String, JsonNode>> pathIter = paths.fields();
        while (pathIter.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathIter.next();
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();

            for (String method : HTTP_METHODS) {
                JsonNode operation = pathItem.path(method);
                if (!operation.isObject()) {
                    continue;
                }

                try {
                    ToolRegisterDTO dto = buildDtoFromOperation(
                            spec, operation, method, path, baseUrl, specUrl);
                    result.add(dto);
                    log.debug("[OpenAPI-Parser] 提取操作: {} {} -> tool={}",
                            method.toUpperCase(), path, dto.getToolName());
                } catch (Exception e) {
                    log.warn("[OpenAPI-Parser] 跳过操作 {} {}: {}", method.toUpperCase(), path, e.getMessage());
                }
            }
        }

        log.info("[OpenAPI-Parser] 共提取 {} 个操作", result.size());
        return result;
    }

    /**
     * 从单个操作构建 ToolRegisterDTO。
     */
    private ToolRegisterDTO buildDtoFromOperation(
            JsonNode spec, JsonNode operation, String method,
            String path, String baseUrl, String specUrl) {

        ToolRegisterDTO dto = new ToolRegisterDTO();

        // 工具名：优先 operationId，否则从 path + method 生成
        String operationId = operation.path("operationId").asText("");
        if (operationId.isBlank()) {
            operationId = generateToolName(method, path);
        }
        // 确保 toolName 符合小写蛇形规范
        dto.setToolName(sanitizeToolName(operationId));

        // 描述：优先 summary，其次 description
        String summary = operation.path("summary").asText("");
        String description = operation.path("description").asText("");
        dto.setDisplayName(summary.isBlank() ? operationId : summary);
        dto.setDescription(description.isBlank() ? summary : description);

        // HTTP 方法
        dto.setHttpMethod(method.toUpperCase());

        // 端点 URL = baseUrl + path
        String endpointUrl = baseUrl + path;
        dto.setEndpointUrl(endpointUrl);

        // 版本
        String apiVersion = spec.path("info").path("version").asText("1.0.0");
        dto.setVersion(apiVersion);

        // 分类：从 tags 取第一个
        JsonNode tags = operation.path("tags");
        if (tags.isArray() && !tags.isEmpty()) {
            dto.setCategory(tags.get(0).asText("default"));
        } else {
            dto.setCategory("default");
        }

        // 解析参数
        List<String> pathParams = new ArrayList<>();
        List<String> queryParams = new ArrayList<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        // path 级别参数
        JsonNode pathParameters = operation.path("parameters");
        if (!pathParameters.isArray()) {
            // 可能在 pathItem 级别
            pathParameters = spec.path("paths").path(path).path("parameters");
        }

        if (pathParameters.isArray()) {
            for (JsonNode paramNode : pathParameters) {
                JsonNode param = resolveRef(spec, paramNode);
                String paramName = param.path("name").asText("");
                String paramIn = param.path("in").asText("");
                if (paramName.isBlank()) continue;

                String pType = extractTypeFromSchema(spec, param.path("schema"));
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", pType);
                String pDesc = param.path("description").asText(paramName);
                prop.put("description", pDesc);
                properties.put(paramName, prop);

                if ("path".equals(paramIn)) {
                    pathParams.add(paramName);
                    required.add(paramName);
                } else if ("query".equals(paramIn)) {
                    queryParams.add(paramName);
                }

                if (param.path("required").asBoolean(false) && !required.contains(paramName)) {
                    required.add(paramName);
                }
            }
        }

        // 解析 requestBody
        JsonNode requestBody = resolveRef(spec, operation.path("requestBody"));
        if (requestBody.isObject()) {
            JsonNode content = requestBody.path("content");
            JsonNode appJson = content.path("application/json");
            if (appJson.isObject()) {
                JsonNode schema = resolveRef(spec, appJson.path("schema"));
                if (schema.isObject()) {
                    extractSchemaProperties(spec, schema, properties, required);
                }
            }
        }

        dto.setPathParams(pathParams);
        dto.setQueryParams(queryParams);

        // 构建 JSON Schema
        Map<String, Object> paramSchema = new LinkedHashMap<>();
        paramSchema.put("type", "object");
        paramSchema.put("properties", properties);
        if (!required.isEmpty()) {
            paramSchema.put("required", required);
        }
        dto.setParamSchema(paramSchema);

        return dto;
    }

    /**
     * 从 schema 节点提取 properties 到目标 Map。
     */
    @SuppressWarnings("unchecked")
    private void extractSchemaProperties(JsonNode spec, JsonNode schema,
                                          Map<String, Object> properties, List<String> required) {
        JsonNode schemaRef = resolveRef(spec, schema);
        JsonNode props = schemaRef.path("properties");
        if (props.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iter = props.fields();
            while (iter.hasNext()) {
                Map.Entry<String, JsonNode> entry = iter.next();
                String propName = entry.getKey();
                JsonNode propSchema = resolveRef(spec, entry.getValue());
                Map<String, Object> prop = objectMapper.convertValue(propSchema, Map.class);
                properties.put(propName, prop);
            }
        }

        JsonNode reqArr = schemaRef.path("required");
        if (reqArr.isArray()) {
            for (JsonNode reqItem : reqArr) {
                String reqName = reqItem.asText();
                if (!required.contains(reqName)) {
                    required.add(reqName);
                }
            }
        }
    }

    /**
     * 解析 $ref 引用（支持 #/components/schemas/... 和 #/components/parameters/...）。
     */
    private JsonNode resolveRef(JsonNode spec, JsonNode node) {
        if (node == null || !node.isObject()) {
            return node;
        }
        JsonNode refNode = node.path("$ref");
        if (!refNode.isTextual()) {
            return node;
        }
        String ref = refNode.asText();
        if (!ref.startsWith("#/")) {
            // 外部引用，暂不支持
            return node;
        }
        // 按 / 分割路径
        String[] parts = ref.substring(2).split("/");
        JsonNode current = spec;
        for (String part : parts) {
            current = current.path(part);
            if (current.isMissingNode()) {
                return node;
            }
        }
        return current;
    }

    /**
     * 从 schema 节点提取 type 字符串。
     */
    private String extractTypeFromSchema(JsonNode spec, JsonNode schema) {
        JsonNode resolved = resolveRef(spec, schema);
        if (resolved.has("type")) {
            return resolved.get("type").asText("string");
        }
        // 如果有 $ref 但无 type，默认 object
        if (resolved.has("$ref") || resolved.has("properties")) {
            return "object";
        }
        return "string";
    }

    /**
     * 从 method + path 生成工具名。
     */
    private String generateToolName(String method, String path) {
        // /users/{userId}/posts -> users_userId_posts
        String cleaned = path.replaceAll("[{}]", "")
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return method + "_" + cleaned;
    }

    /**
     * 将工具名标准化为小写蛇形。
     */
    private String sanitizeToolName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed_tool";
        }
        return name.replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}
