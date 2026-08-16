package com.njydsz.agent.infra.tool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.common.json.YdszJson;

/**
 * 基于 SSE 传输的 MCP Client 提供者实现
 *
 * <p>通过 HTTP SSE 协议连接 MCP Server，发现工具并执行调用。 使用 JDK 11+ {@link HttpClient} 实现，无需额外 MCP SDK 依赖。
 *
 * <p>连接采用懒加载策略：首次调用时建立连接并缓存，后续复用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SseMcpClientProvider implements McpClientProvider {

  private static final Logger LOG = LoggerFactory.getLogger(SseMcpClientProvider.class);

  /** HTTP Client（线程安全，可复用） */
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  /** MCP Server 会话缓存（key=serverName, value=sessionId） */
  private final Map<String, String> sessionCache = new ConcurrentHashMap<>();

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
      LOG.error("[MCP-SSE] 工具列表获取失败: server={}, error={}", server.getName(), e.getMessage(), e);
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
      LOG.error(
          "[MCP-SSE] 工具调用失败: server={}, tool={}, error={}",
          server.getName(),
          toolName,
          e.getMessage(),
          e);
      return YdszJson.toJson(Map.of("error", "MCP 工具调用失败: " + e.getMessage()));
    }
  }

  /**
   * 初始化 MCP 会话
   *
   * <p>通过 GET 请求建立 SSE 连接，从响应头获取 Mcp-Session-Id。
   */
  private String initSession(AgentProperties.ServerInfo server) {
    return sessionCache.computeIfAbsent(
        server.getName(),
        name -> {
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
                    .orElse("session-" + System.currentTimeMillis());
            LOG.info("[MCP-SSE] 会话初始化完成: server={}, sessionId={}", name, sessionId);
            return sessionId;
          } catch (Exception e) {
            throw new RuntimeException("MCP 会话初始化失败: " + name, e);
          }
        });
  }

  /** 发送 JSON-RPC 请求到 MCP Server */
  private String sendRequest(
      AgentProperties.ServerInfo server,
      String sessionId,
      String method,
      Map<String, Object> params)
      throws Exception {
    String requestId = String.valueOf(System.currentTimeMillis());
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
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new RuntimeException("MCP 请求失败: HTTP " + response.statusCode());
    }
    return response.body();
  }

  /** 解析工具列表响应 */
  @SuppressWarnings("unchecked")
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
  @SuppressWarnings("unchecked")
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
    Map<String, Object> result = new ConcurrentHashMap<>(source.size());
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      result.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return result;
  }
}
