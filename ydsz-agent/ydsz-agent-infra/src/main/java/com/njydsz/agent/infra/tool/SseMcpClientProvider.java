package com.njydsz.agent.infra.tool;

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
import com.njydsz.agent.server.config.AgentProperties;
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
 * @since 1.0.0
 */
@Slf4j
public class SseMcpClientProvider implements McpClientProvider {

  /** 会话缓存 TTL（毫秒），超过后需重新建立 MCP 会话 */
  private static final long SESSION_TTL_MILLIS = 30 * 60 * 1000L;

  /** 会话失效时的 HTTP 状态码（401 Unauthorized） */
  private static final int HTTP_UNAUTHORIZED = 401;

  /** HTTP Client（线程安全，可复用） */
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  /** MCP Server 会话缓存（key=serverName, value=会话条目） */
  private final Map<String, SessionEntry> sessionCache = new ConcurrentHashMap<>();

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
      return new ArrayList<>();
    }
  }

  @Override
  public String callTool(AgentProperties.ServerInfo server, String toolName, String arguments) {
    try {
      String sessionId = initSession(server);
      Map<String, Object> params = Map.of("name", toolName, "arguments", parseArguments(arguments));
      String callResponse = sendRequest(server, sessionId, "tools/call", params);
      return extractCallResult(callResponse);
    } catch (Exception e) {
      log.error(
          "[MCP-SSE] 工具调用失败: server={}, tool={}, error={}",
          server.getName(),
          toolName,
          e.getMessage(),
          e);
      // P1 修复：错误以未检查异常向上传播（由 ToolRegistry 统一兜底为结构化错误结果），
      // 替代原实现"catch 后返回 error JSON 字符串"导致的错误静默吞掉
      throw new IllegalStateException("MCP 工具调用失败: " + e.getMessage(), e);
    }
  }

  /**
   * 初始化 MCP 会话
   *
   * <p>通过 GET 请求建立 SSE 连接，从响应头获取 Mcp-Session-Id。 会话缓存条目过期时自动重建；调用方在请求失败时调用 {@link
   * #invalidateSession(String)} 触发下一次重连。
   *
   * @param server MCP Server 配置
   * @return 有效的 sessionId
   */
  private String initSession(AgentProperties.ServerInfo server) {
    return sessionCache
        .compute(
            server.getName(),
            (name, existing) -> {
              if (existing != null && !isExpired(existing)) {
                return existing;
              }
              try {
                HttpRequest request =
                    HttpRequest.newBuilder()
                        .uri(URI.create(server.getUrl()))
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofSeconds(server.getTimeoutSeconds()))
                        .GET()
                        .build();
                HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                // 从响应头或响应体中提取 sessionId
                String sessionId =
                    response
                        .headers()
                        .firstValue("Mcp-Session-Id")
                        .orElse("session-" + IdGenerator.nextIdStr());
                log.info(
                    "[MCP-SSE] 会话初始化完成: server={}, sessionId={}", name, sessionId);
                return new SessionEntry(sessionId, System.currentTimeMillis());
              } catch (Exception e) {
                throw new LlmException(
                    "MCP 会话初始化失败: server=" + name + ", error=" + e.getMessage(),
                    LlmException.ErrorType.PROVIDER_ERROR,
                    e);
              }
            })
        .sessionId();
  }

  /**
   * 使指定 Server 的会话缓存失效（调用失败时触发，下次调用自动重连）。
   *
   * @param serverName Server 名称
   */
  private void invalidateSession(String serverName) {
    sessionCache.remove(serverName);
  }

  /**
   * 判断会话条目是否已过期。
   *
   * @param entry 会话条目
   * @return {@code true} 表示已过期，需重建会话
   */
  private boolean isExpired(SessionEntry entry) {
    return System.currentTimeMillis() - entry.createdAtMillis() > SESSION_TTL_MILLIS;
  }

  /** 发送 JSON-RPC 请求到 MCP Server */
  private String sendRequest(
      AgentProperties.ServerInfo server,
      String sessionId,
      String method,
      Map<String, Object> params)
      throws LlmException {
    // P0 修复：使用雪花 ID 保证 JSON-RPC id 全局唯一，替代毫秒时间戳（高并发下同毫秒碰撞导致响应错配）
    String requestId = IdGenerator.nextIdStr();
    Map<String, Object> jsonRpcRequest =
        Map.of(
            "jsonrpc", "2.0",
            "id", requestId,
            "method", method,
            "params", params);
    String requestBody = YdszJson.toJson(jsonRpcRequest);
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(server.getUrl()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("Mcp-Session-Id", sessionId)
            .timeout(Duration.ofSeconds(server.getTimeoutSeconds()))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (java.io.IOException e) {
      throw new LlmException(
          "MCP 网络请求失败: server=" + server.getName() + ", error=" + e.getMessage(),
          LlmException.ErrorType.NETWORK_TIMEOUT,
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LlmException(
          "MCP 请求被中断: server=" + server.getName(),
          LlmException.ErrorType.CANCELED,
          e);
    }
    if (response.statusCode() == HTTP_UNAUTHORIZED) {
      // 会话可能已被服务端销毁，失效缓存并在下次调用时重连
      invalidateSession(server.getName());
      throw new LlmException(
          "MCP 会话已失效（401），将自动重连: server=" + server.getName(),
          LlmException.ErrorType.AUTH_FAILED);
    }
    if (response.statusCode() >= 400) {
      throw new LlmException(
          "MCP 请求失败: HTTP " + response.statusCode() + ", server=" + server.getName(),
          LlmException.ErrorType.PROVIDER_ERROR);
    }
    return response.body();
  }

  /** 解析工具列表响应 */
  private List<McpToolAdapter.McpToolDescriptor> parseToolList(String responseBody) {
    Map<String, Object> responseMap = YdszJson.parseMap(responseBody);
    Object resultObj = responseMap.get("result");
    if (!(resultObj instanceof Map<?, ?> resultMap)) {
      return new ArrayList<>();
    }
    Object toolsObj = resultMap.get("tools");
    if (!(toolsObj instanceof List<?> toolsList)) {
      return new ArrayList<>();
    }
    List<McpToolAdapter.McpToolDescriptor> descriptors = new ArrayList<>(toolsList.size());
    for (Object tool : toolsList) {
      if (tool instanceof Map<?, ?> toolMap) {
        String name = String.valueOf(toolMap.get("name"));
        String description =
            toolMap.get("description") != null ? String.valueOf(toolMap.get("description")) : "";
        Map<String, Object> inputSchema =
            toolMap.get("inputSchema") instanceof Map<?, ?> schema
                ? convertToStringObjectMap(schema)
                : Map.of();
        descriptors.add(new McpToolAdapter.McpToolDescriptor(name, description, inputSchema));
      }
    }
    return descriptors;
  }

  /** 提取工具调用结果 */
  private String extractCallResult(String responseBody) {
    Map<String, Object> responseMap = YdszJson.parseMap(responseBody);
    Object resultObj = responseMap.get("result");
    if (resultObj instanceof Map<?, ?> resultMap) {
      Object content = resultObj;
      if (resultMap.containsKey("content")) {
        content = resultMap.get("content");
      }
      return YdszJson.toJson(content);
    }
    return responseBody;
  }

  /** 解析参数 JSON 字符串 */
  private Object parseArguments(String arguments) {
    if (arguments == null || arguments.isBlank()) {
      return Map.of();
    }
    try {
      return YdszJson.parseMap(arguments);
    } catch (Exception e) {
      return arguments;
    }
  }

  /** 将 Map<?, ?> 转换为 Map<String, Object> */
  private Map<String, Object> convertToStringObjectMap(Map<?, ?> source) {
    Map<String, Object> result = new HashMap<>(source.size());
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      result.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return result;
  }

  /**
   * MCP 会话缓存条目。
   *
   * @param sessionId 会话 ID
   * @param createdAtMillis 创建时间戳（毫秒）
   */
  private record SessionEntry(String sessionId, long createdAtMillis) {}
}
