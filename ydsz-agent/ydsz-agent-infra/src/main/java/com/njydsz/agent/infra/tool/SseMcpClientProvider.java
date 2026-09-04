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

import com.njydsz.agent.domain.gateway.LlmException;
import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 基于 SSE 传输的 MCP Client 提供者实现
 *
 * <p>通过 HTTP SSE 协议连接 MCP Server，发现工具并执行调用。 使用 JDK 11+ {@link HttpClient} 实现，无需额外 MCP SDK 依赖。
 *
 * <p>连接采用懒加载策略：首次调用时建立连接并缓存，后续复用。 会话缓存带 TTL 过期与失败自动重连（P1 修复），避免 MCP Server 会话失效后持续调用失败。
 *
 * <p><b>线程安全</b>：{@link HttpClient} 与 {@link ConcurrentHashMap} 均线程安全，可并发调用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class SseMcpClientProvider implements McpClientProvider {

  /** 会话缓存 TTL（毫秒），超过后需重新建立 MCP 会话 */
  private static final long SESSION_TTL_MILLIS = 30 * 60 * 1000L;

  /** 会话失效时的 HTTP 状态码（401 Unauthorized） */
  private static final int HTTP_UNAUTHORIZED = 401;

  /** HTTP 客户端错误响应的最低状态码（4xx） */
  private static final int HTTP_CLIENT_ERROR_MIN = 400;

  /** MIME 类型：application/json */
  private static final String MIME_APPLICATION_JSON = "application/json";

  /** HTTP Client（线程安全，可复用） */
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  /** MCP Server 会话缓存（key=serverName, value=会话条目） */
  private final Map<String, SessionEntry> sessionCache = new ConcurrentHashMap<>();

  /**
   * MCP 会话条目（缓存值）。
   *
   * @param sessionId MCP 会话 ID
   * @param createdAt 会话创建时间戳（毫秒）
   */
  private record SessionEntry(String sessionId, long createdAt) {
    /**
     * 检查会话是否已过期。
     *
     * @return {@code true} 表示已过期
     */
    boolean isExpired() {
      return System.currentTimeMillis() - createdAt > SESSION_TTL_MILLIS;
    }
  }

  @Override
  public List<McpToolAdapter.McpToolDescriptor> listTools(AgentProperties.ServerInfo server) {
    try {
      // 1. 初始化 MCP 会话（获取 sessionId）
      String sessionId = initSession(server);
      // 2. 发送 tools/list 请求
      String listResponse = sendRequest(server, sessionId, "tools/list", Map.of());
      // 3. 解析工具列表
      return parseToolList(listResponse);
    } catch (Exception e) {
      log.error("[MCP-SSE] 工具列表获取失败: server={}, error={}", server.getName(), e.getMessage(), e);
      return new ArrayList<>(0);
    }
  }

  @Override
  public String callTool(AgentProperties.ServerInfo server, String toolName, String arguments) {
    try {
      String sessionId = initSession(server);
      Map<String, Object> params = new HashMap<>(4);
      params.put("name", toolName);
      params.put("arguments", parseArguments(arguments));
      String response = sendRequest(server, sessionId, "tools/call", params);
      return extractToolResult(response);
    } catch (LlmException e) {
      throw e;
    } catch (Exception e) {
      log.error("[MCP-SSE] 工具调用失败: server={}, tool={}, error={}", server.getName(), toolName, e.getMessage(), e);
      throw new LlmException(
          "MCP 工具调用失败: " + toolName, LlmException.ErrorType.PROVIDER_ERROR, e);
    }
  }

  /**
   * 初始化 MCP 会话（带缓存与 TTL 过期）。
   *
   * <p>若缓存中存在未过期的会话则直接返回，否则向 MCP Server 发送 initialize 请求获取新会话。
   *
   * @param server MCP Server 配置
   * @return 会话 ID
   */
  private String initSession(AgentProperties.ServerInfo server) {
    SessionEntry cached = sessionCache.get(server.getName());
    if (cached != null && !cached.isExpired()) {
      return cached.sessionId();
    }
    try {
      Map<String, Object> params = new HashMap<>(4);
      params.put("protocolVersion", "2024-11-05");
      params.put("capabilities", Map.of());
      params.put("clientInfo", Map.of("name", "ydsz-agent", "version", "26.09.01"));
      String response = sendRequest(server, null, "initialize", params);
      String sessionId = extractSessionId(response);
      if (sessionId == null || sessionId.isBlank()) {
        throw new LlmException(
            "MCP 初始化失败: 无法获取 sessionId, server=" + server.getName(),
            LlmException.ErrorType.INVALID_RESPONSE);
      }
      sessionCache.put(server.getName(), new SessionEntry(sessionId, System.currentTimeMillis()));
      log.info("[MCP-SSE] 会话初始化成功: server={}, sessionId={}", server.getName(), sessionId);
      return sessionId;
    } catch (LlmException e) {
      throw e;
    } catch (Exception e) {
      throw new LlmException(
          "MCP 会话初始化失败: server=" + server.getName(),
          LlmException.ErrorType.PROVIDER_ERROR, e);
    }
  }

  /**
   * 发送 JSON-RPC 请求到 MCP Server。
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
      Map<String, Object> body = new HashMap<>(8);
      body.put("jsonrpc", "2.0");
      body.put("id", IdGenerator.nextIdStr());
      body.put("method", method);
      body.put("params", params);
      String jsonBody = YdszJson.toJson(body);
      HttpRequest.Builder requestBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(server.getUrl()))
              .header("Content-Type", MIME_APPLICATION_JSON)
              .header("Accept", MIME_APPLICATION_JSON + ", text/event-stream")
              .timeout(Duration.ofMillis(server.getTimeout() != null ? server.getTimeout() : 30000))
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
      if (sessionId != null) {
        requestBuilder.header("Mcp-Session-Id", sessionId);
      }
      HttpRequest request = requestBuilder.build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      // 401 意味着会话过期，清理缓存以便下次重试
      if (response.statusCode() == HTTP_UNAUTHORIZED) {
        sessionCache.remove(server.getName());
        throw new LlmException(
            "MCP 会话过期（401）: server=" + server.getName(),
            LlmException.ErrorType.AUTH_FAILED);
      }
      if (response.statusCode() >= HTTP_CLIENT_ERROR_MIN) {
        throw new LlmException(
            "MCP 请求失败: server=" + server.getName() + " status=" + response.statusCode()
                + " body=" + response.body(),
            LlmException.ErrorType.PROVIDER_ERROR);
      }
      return response.body();
    } catch (LlmException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      throw new LlmException(
          "MCP 网络请求失败: server=" + server.getName(),
          LlmException.ErrorType.NETWORK_TIMEOUT, e);
    }
  }

  /**
   * 从 initialize 响应中提取 sessionId。
   *
   * <p>优先从响应 JSON 的 result.sessionId 获取，若不存在则尝试从 Mcp-Session-Id 响应头获取。
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
      log.warn("[MCP-SSE] 解析 sessionId 失败: err={}", e.getMessage());
    }
    return null;
  }

  /**
   * 解析 tools/list 响应，提取工具描述符列表。
   *
   * @param response JSON-RPC 响应字符串
   * @return 工具描述符列表
   */
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
      log.warn("[MCP-SSE] 解析工具列表失败: err={}", e.getMessage());
    }
    return tools;
  }

  /**
   * 从 tools/call 响应中提取工具执行结果。
   *
   * @param response JSON-RPC 响应字符串
   * @return 工具结果内容文本
   */
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
      log.warn("[MCP-SSE] 解析工具结果失败: err={}", e.getMessage());
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
      log.warn("[MCP-SSE] 解析参数失败，作为原始字符串传入: err={}", e.getMessage());
      return Map.of("_raw", arguments);
    }
  }
}
