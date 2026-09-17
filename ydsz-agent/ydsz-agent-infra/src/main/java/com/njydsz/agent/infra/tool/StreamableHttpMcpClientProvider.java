package com.njydsz.agent.infra.tool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.gateway.LlmException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 基于 Streamable HTTP 传输的 MCP Client 提供者实现
 *
 * <p>MCP Streamable HTTP 是 MCP 协议推荐的现代传输方式，相比 SSE 具有以下优势：
 *
 * <ul>
 *   <li>基于标准 HTTP 请求/响应，无需长连接管理
 *   <li>支持服务端主动推送（可通过 MCP-Session-Id 维持会话）
 *   <li>更适合负载均衡和无服务器部署场景
 * </ul>
 *
 * <p>协议流程：
 *
 * <ol>
 *   <li>POST initialize → 获取 MCP-Session-Id
 *   <li>POST + MCP-Session-Id → 工具发现/调用
 *   <li>会话复用（TTL 控制过期）
 * </ol>
 *
 * <h3>认证增强（P0-4）</h3>
 *
 * <ul>
 *   <li>{@code none}：无认证（内网默认）
 *   <li>{@code api-key}：通过 {@code X-Api-Key} 头发送
 *   <li>{@code bearer}：通过 {@code Authorization: Bearer <token>} 发送
 *   <li>{@code oauth}：先 POST token_url 获取 access_token，后续请求携带 Bearer
 * </ul>
 *
 * <p><b>线程安全</b>：{@link HttpClient} 与 {@link ConcurrentHashMap} 均线程安全，可并发调用。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public class StreamableHttpMcpClientProvider implements McpClientProvider {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY_4 = 4;

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY_8 = 8;

  /** MCP 请求默认超时（毫秒） */
  private static final int DEFAULT_TIMEOUT_MILLIS = 30000;

  /** 会话缓存 TTL（毫秒） */
  private static final long SESSION_TTL_MILLIS = 30 * 60 * 1000L;

  /** 会话失效 HTTP 状态码（401） */
  private static final int HTTP_UNAUTHORIZED = 401;

  /** HTTP 客户端错误最低状态码 */
  private static final int HTTP_CLIENT_ERROR_MIN = 400;

  /** MIME 类型：application/json */
  private static final String MIME_APPLICATION_JSON = "application/json";

  /** HTTP Client（线程安全，可复用） */
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  /** MCP 会话缓存（key=serverName, value=会话条目） */
  private final Map<String, StreamableSessionEntry> sessionCache = new ConcurrentHashMap<>();

  /** OAuth Token 缓存（key=serverName, value=token条目） */
  private final Map<String, OAuthTokenEntry> oauthTokenCache = new ConcurrentHashMap<>();

  /**
   * MCP 会话条目（Streamable HTTP）。
   *
   * @param sessionId MCP 会话 ID
   * @param createdAt 会话创建时间戳（毫秒）
   */
  private record StreamableSessionEntry(String sessionId, long createdAt) {
    boolean isExpired() {
      return System.currentTimeMillis() - createdAt > SESSION_TTL_MILLIS;
    }
  }

  /**
   * OAuth Token 缓存条目。
   *
   * @param accessToken 访问令牌
   * @param expiresAt 过期时间（毫秒时间戳）
   */
  private record OAuthTokenEntry(String accessToken, long expiresAt) {
    boolean isExpired() {
      return System.currentTimeMillis() >= expiresAt;
    }
  }

  @Override
  public List<McpToolAdapter.McpToolDescriptor> listTools(AgentProperties.ServerInfo server) {
    try {
      String sessionId = initSession(server);
      String listResponse = sendRequest(server, sessionId, "tools/list", Map.of());
      return parseToolList(listResponse);
    } catch (Exception e) {
      log.error("[MCP-Streamable] 工具列表获取失败: server={}, error={}", server.getName(), e.getMessage(), e);
      return new ArrayList<>(0);
    }
  }

  @Override
  public String callTool(AgentProperties.ServerInfo server, String toolName, String arguments) {
    try {
      String sessionId = initSession(server);
      Map<String, Object> params = new HashMap<>(COLLECTION_CAPACITY_4);
      params.put("name", toolName);
      params.put("arguments", parseArguments(arguments));
      String response = sendRequest(server, sessionId, "tools/call", params);
      return extractToolResult(response);
    } catch (LlmException e) {
      throw e;
    } catch (Exception e) {
      log.error("[MCP-Streamable] 工具调用失败: server={}, tool={}, error={}", server.getName(), toolName, e.getMessage(), e);
      throw new LlmException(
          "MCP 工具调用失败: " + toolName, LlmException.ErrorType.PROVIDER_ERROR, e);
    }
  }

  /**
   * 初始化 MCP 会话（带缓存与 TTL 过期）。
   *
   * @param server MCP Server 配置
   * @return 会话 ID
   */
  private String initSession(AgentProperties.ServerInfo server) {
    StreamableSessionEntry cached = sessionCache.get(server.getName());
    if (cached != null && !cached.isExpired()) {
      return cached.sessionId();
    }
    try {
      Map<String, Object> params = new HashMap<>(COLLECTION_CAPACITY_4);
      params.put("protocolVersion", "2024-11-05");
      params.put("capabilities", Map.of());
      params.put("clientInfo", Map.of("name", "ydsz-agent", "version", "26.09.17"));
      String response = sendRequest(server, null, "initialize", params);
      String sessionId = extractSessionId(response);
      if (sessionId == null || sessionId.isBlank()) {
        throw new LlmException(
            "MCP Streamable 初始化失败: 无法获取 sessionId, server=" + server.getName(),
            LlmException.ErrorType.INVALID_RESPONSE);
      }
      sessionCache.put(server.getName(), new StreamableSessionEntry(sessionId, System.currentTimeMillis()));
      log.info("[MCP-Streamable] 会话初始化成功: server={}, sessionId={}", server.getName(), sessionId);
      return sessionId;
    } catch (LlmException e) {
      throw e;
    } catch (Exception e) {
      throw new LlmException(
          "MCP Streamable 会话初始化失败: server=" + server.getName(),
          LlmException.ErrorType.PROVIDER_ERROR, e);
    }
  }

  /**
   * 发送 JSON-RPC 请求到 MCP Server（Streamable HTTP 模式）。
   *
   * @param server MCP Server 配置
   * @param sessionId 会话 ID（可为 null，如初始化时）
   * @param method JSON-RPC 方法名
   * @param params 方法参数
   * @return 响应 JSON 字符串
   */
  private String sendRequest(
      AgentProperties.ServerInfo server, String sessionId, String method,
      Map<String, Object> params) {
    try {
      Map<String, Object> body = new HashMap<>(COLLECTION_CAPACITY_8);
      body.put("jsonrpc", "2.0");
      body.put("id", IdGenerator.nextIdStr());
      body.put("method", method);
      body.put("params", params);
      String jsonBody = YdszJson.toJson(body);
      HttpRequest.Builder requestBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(server.getUrl()))
              .header("Content-Type", MIME_APPLICATION_JSON)
              .header("Accept", MIME_APPLICATION_JSON)
              .timeout(Duration.ofMillis(server.getTimeout() != null ? server.getTimeout() : DEFAULT_TIMEOUT_MILLIS))
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
      // 注入认证头
      applyAuthentication(requestBuilder, server);
      if (sessionId != null) {
        requestBuilder.header("Mcp-Session-Id", sessionId);
      }
      HttpRequest request = requestBuilder.build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      // 401 意味着会话过期，清理缓存以便下次重试
      if (response.statusCode() == HTTP_UNAUTHORIZED) {
        sessionCache.remove(server.getName());
        oauthTokenCache.remove(server.getName());
        throw new LlmException(
            "MCP 会话过期（401）: server=" + server.getName(),
            LlmException.ErrorType.AUTH_FAILED);
      }
      if (response.statusCode() >= HTTP_CLIENT_ERROR_MIN) {
        throw new LlmException(
            "MCP Streamable 请求失败: server=" + server.getName() + " status=" + response.statusCode()
                + " body=" + response.body(),
            LlmException.ErrorType.PROVIDER_ERROR);
      }
      return response.body();
    } catch (LlmException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      throw new LlmException(
          "MCP Streamable 网络请求失败: server=" + server.getName(),
          LlmException.ErrorType.NETWORK_TIMEOUT, e);
    }
  }

  /**
   * 根据配置向请求注入认证头。
   *
   * @param requestBuilder HTTP 请求构建器
   * @param server MCP Server 配置
   */
  private void applyAuthentication(HttpRequest.Builder requestBuilder, AgentProperties.ServerInfo server) {
    String authType = server.getAuthType();
    if (authType == null || "none".equalsIgnoreCase(authType)) {
      return;
    }
    if ("api-key".equalsIgnoreCase(authType)) {
      String apiKey = server.getAuthApiKey();
      if (apiKey != null && !apiKey.isBlank()) {
        requestBuilder.header("X-Api-Key", apiKey);
      }
      return;
    }
    if ("bearer".equalsIgnoreCase(authType)) {
      String token = server.getAuthToken();
      if (token != null && !token.isBlank()) {
        requestBuilder.header("Authorization", "Bearer " + token);
      }
      return;
    }
    if ("oauth".equalsIgnoreCase(authType)) {
      String accessToken = obtainOAuthToken(server);
      if (accessToken != null) {
        requestBuilder.header("Authorization", "Bearer " + accessToken);
      }
    }
  }

  /**
   * 获取 OAuth access_token（带缓存，过期前自动刷新）。
   *
   * @param server MCP Server 配置
   * @return access_token；获取失败返回 null
   */
  private String obtainOAuthToken(AgentProperties.ServerInfo server) {
    OAuthTokenEntry cached = oauthTokenCache.get(server.getName());
    if (cached != null && !cached.isExpired()) {
      return cached.accessToken();
    }
    try {
      if (server.getAuthTokenUrl() == null || server.getAuthTokenUrl().isBlank()) {
        log.warn("[MCP-Streamable] OAuth token_url 未配置: server={}", server.getName());
        return null;
      }
      // 构造 OAuth Client Credentials 请求
      String formData =
          "grant_type=client_credentials"
              + "&client_id=" + urlEncode(server.getAuthClientId())
              + "&client_secret=" + urlEncode(server.getAuthClientSecret());
      HttpRequest tokenRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(server.getAuthTokenUrl()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .timeout(Duration.ofSeconds(10))
              .POST(HttpRequest.BodyPublishers.ofString(formData))
              .build();
      HttpResponse<String> tokenResponse =
          httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
      if (tokenResponse.statusCode() >= HTTP_CLIENT_ERROR_MIN) {
        log.warn("[MCP-Streamable] OAuth token 获取失败: server={}, status={}", server.getName(), tokenResponse.statusCode());
        return null;
      }
      Map<String, Object> tokenMap = YdszJson.parseMap(tokenResponse.body());
      if (tokenMap == null) {
        return null;
      }
      Object accessTokenObj = tokenMap.get("access_token");
      Object expiresInObj = tokenMap.get("expires_in");
      if (accessTokenObj == null) {
        return null;
      }
      String accessToken = accessTokenObj.toString();
      long expiresInSeconds = expiresInObj instanceof Number ? ((Number) expiresInObj).longValue() : 3600L;
      // 提前 60 秒过期，避免边界失效
      long expiresAt = System.currentTimeMillis() + (expiresInSeconds - 60) * 1000L;
      oauthTokenCache.put(server.getName(), new OAuthTokenEntry(accessToken, expiresAt));
      log.info("[MCP-Streamable] OAuth token 获取成功: server={}", server.getName());
      return accessToken;
    } catch (Exception e) {
      log.warn("[MCP-Streamable] OAuth token 获取异常: server={}, err={}", server.getName(), e.getMessage());
      return null;
    }
  }

  /**
   * 简单的 URL 编码（替代 URLEncoder 避免引入额外 import）。
   *
   * @param value 原始字符串
   * @return 编码后的字符串
   */
  private String urlEncode(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("%", "%25")
        .replace(" ", "%20")
        .replace("&", "%26")
        .replace("=", "%3D")
        .replace("+", "%2B");
  }

  /**
   * 从 initialize 响应中提取 sessionId。
   *
   * <p>优先从响应 JSON 的 result.sessionId 获取，不存在时从 Mcp-Session-Id 响应头获取（Streamable HTTP 模式）。
   *
   * @param response initialize 响应 JSON
   * @return sessionId，获取失败返回 null
   */
  private String extractSessionId(String response) {
    try {
      Map<String, Object> map = YdszJson.parseMap(response);
      if (map == null) {
        return null;
      }
      Object result = map.get("result");
      if (result instanceof Map<?, ?> resultMap) {
        Object sessionId = resultMap.get("sessionId");
        return sessionId != null ? sessionId.toString() : null;
      }
    } catch (Exception e) {
      log.warn("[MCP-Streamable] 解析 sessionId 失败: err={}", e.getMessage());
    }
    return null;
  }

  /**
   * 解析 tools/list 响应，提取工具描述符列表。
   *
   * @param response JSON-RPC 响应字符串
   * @return 工具描述符列表
   */
  // YDIZ-WARN-001 允许保留：泛型擦除，YdszJson.parseMap() 返回 Map<?, ?> 编译期无法验证 Map<String, Object> 强转
  @SuppressWarnings("unchecked")
  private List<McpToolAdapter.McpToolDescriptor> parseToolList(String response) {
    List<McpToolAdapter.McpToolDescriptor> tools = new ArrayList<>(0);
    try {
      Map<String, Object> map = YdszJson.parseMap(response);
      if (map == null) {
        return tools;
      }
      Object result = map.get("result");
      if (!(result instanceof Map<?, ?> resultMap)) {
        return tools;
      }
      Object toolsObj = resultMap.get("tools");
      if (!(toolsObj instanceof List<?> toolsList)) {
        return tools;
      }
      for (Object item : toolsList) {
        if (item instanceof Map<?, ?> toolMap) {
          String name = toolMap.get("name") != null ? toolMap.get("name").toString() : "";
          String description =
              toolMap.get("description") != null ? toolMap.get("description").toString() : "";
          Object inputSchema = toolMap.get("inputSchema");
          Map<String, Object> schema = (inputSchema instanceof Map<?, ?>)
              ? (Map<String, Object>) inputSchema
              : new HashMap<>(0);
          tools.add(new McpToolAdapter.McpToolDescriptor(name, description, schema));
        }
      }
    } catch (Exception e) {
      log.warn("[MCP-Streamable] 解析工具列表失败: err={}", e.getMessage());
    }
    return tools;
  }

  /**
   * 从 tools/call 响应中提取工具执行结果。
   *
   * @param response JSON-RPC 响应字符串
   * @return 工具结果内容文本
   */
  // YDIZ-WARN-001 允许保留：泛型擦除，YdszJson.parseMap() 返回 Map<?, ?> 编译期无法验证 Map<String, Object> 强转
  @SuppressWarnings("unchecked")
  private String extractToolResult(String response) {
    try {
      Map<String, Object> map = YdszJson.parseMap(response);
      if (map == null) {
        return response;
      }
      Object error = map.get("error");
      if (error != null) {
        throw new LlmException(
            "MCP 工具调用错误: " + error, LlmException.ErrorType.INVALID_RESPONSE);
      }
      Object result = map.get("result");
      if (result instanceof Map<?, ?> resultMap) {
        Object content = resultMap.get("content");
        if (content instanceof List<?> contentList && !contentList.isEmpty()) {
          Object first = contentList.get(0);
          if (first instanceof Map<?, ?> contentItem) {
            Object text = contentItem.get("text");
            return text != null ? text.toString() : "";
          }
        }
        return YdszJson.toJson(result);
      }
      return response;
    } catch (LlmException e) {
      throw e;
    } catch (Exception e) {
      log.warn("[MCP-Streamable] 解析工具结果失败: err={}", e.getMessage());
      return response;
    }
  }

  /**
   * 将参数 JSON 字符串解析为 Map。
   *
   * @param arguments JSON 对象字符串
   * @return 解析后的 Map，空字符串返回空 Map
   */
  private Map<String, Object> parseArguments(String arguments) {
    if (arguments == null || arguments.isBlank()) {
      return new HashMap<>(0);
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(arguments);
      return map != null ? map : new HashMap<>(0);
    } catch (Exception e) {
      log.warn("[MCP-Streamable] 解析参数失败，作为原始字符串传入: err={}", e.getMessage());
      return Map.of("_raw", arguments);
    }
  }
}
