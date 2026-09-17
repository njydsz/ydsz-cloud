package com.njydsz.agent.infra.a2a;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.config.properties.A2aProperties;
import com.njydsz.agent.domain.gateway.A2aClient;
import com.njydsz.agent.domain.gateway.A2aException;
import com.njydsz.agent.domain.model.a2a.A2aAgentCard;
import com.njydsz.agent.domain.model.a2a.A2aTask;
import com.njydsz.agent.domain.model.a2a.A2aTask.A2aTaskStatus;
import com.njydsz.common.json.YdszJson;

/**
 * A2A 协议的 HTTP JSON-RPC 客户端实现。
 *
 * <p>基于 JDK 11+ {@link HttpClient} 实现 A2A 规范的 JSON-RPC 2.0 接口：
 *
 * <ul>
 *   <li>{@code agent.getAuthenticatedExtendedCard} — 获取 AgentCard</li>
 *   <li>{@code message.send} — 发送消息创建 Task</li>
 *   <li>{@code tasks.get} — 获取 Task 状态</li>
 *   <li>{@code tasks.cancel} — 取消 Task</li>
 * </ul>
 *
 * <p>配置键：{@code ydsz.agent.a2a.*}
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Component
public class HttpA2aClient implements A2aClient {

  /** A2A 协议 JSON-RPC 方法名常量 */
  private static final String METHOD_SEND_MESSAGE = "message.send";
  private static final String METHOD_GET_TASK = "tasks.get";
  private static final String METHOD_CANCEL_TASK = "tasks.cancel";
  private static final String METHOD_AGENT_CARD = "agent.getAuthenticatedExtendedCard";

  /** HTTP Content-Type */
  private static final String CONTENT_TYPE_JSON = "application/json";

  /** JSON 请求体字段 */
  private static final String JSONRPC_VERSION = "2.0";

  /** A2A 配置 */
  private final A2aProperties a2aProperties;

  /** HTTP 客户端 */
  private final HttpClient httpClient;

  public HttpA2aClient(A2aProperties a2aProperties) {
    this.a2aProperties = a2aProperties;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(a2aProperties.getTimeout())
            .build();
  }

  @Override
  public A2aAgentCard fetchAgentCard(String agentUrl) {
    if (agentUrl == null || agentUrl.isBlank()) {
      throw new A2aException(
          "A2A Agent URL 不能为空", A2aException.A2aErrorType.PROTOCOL_ERROR);
    }
    String discoveryUrl = agentUrl.endsWith("/")
        ? agentUrl + ".well-known/agent.json"
        : agentUrl + "/.well-known/agent.json";
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(discoveryUrl))
          .timeout(a2aProperties.getTimeout())
          .header("Accept", CONTENT_TYPE_JSON)
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return parseAgentCard(response.body());
      }
      throw new A2aException(
          "获取 A2A AgentCard 失败 (HTTP " + response.statusCode() + "): " + discoveryUrl,
          A2aException.A2aErrorType.AGENT_UNAVAILABLE);
    } catch (A2aException e) {
      throw e;
    } catch (java.io.IOException e) {
      throw new A2aException(
          "A2A 网络异常: " + e.getMessage(),
          A2aException.A2aErrorType.NETWORK_TIMEOUT, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new A2aException(
          "A2A 调用被中断",
          A2aException.A2aErrorType.CANCELED, e);
    }
  }

  @Override
  public CompletableFuture<A2aAgentCard> fetchAgentCardAsync(String agentUrl) {
    return CompletableFuture.supplyAsync(() -> fetchAgentCard(agentUrl));
  }

  @Override
  public A2aTask sendMessage(String agentUrl, String message,
      Map<String, Object> metadata) {
    validateAgentUrl(agentUrl);
    Map<String, Object> params = new HashMap<>(4);
    params.put("message", buildTextMessage(message));
    if (metadata != null && !metadata.isEmpty()) {
      params.put("metadata", metadata);
    }
    Map<String, Object> response = callJsonRpc(agentUrl, METHOD_SEND_MESSAGE, params);
    return parseTaskResponse(response);
  }

  @Override
  public CompletableFuture<A2aTask> sendMessageAsync(String agentUrl, String message,
      Map<String, Object> metadata) {
    return CompletableFuture.supplyAsync(() -> sendMessage(agentUrl, message, metadata));
  }

  @Override
  public A2aTask getTask(String agentUrl, String taskId) {
    validateAgentUrl(agentUrl);
    Map<String, Object> params = new HashMap<>(4);
    params.put("id", taskId);
    Map<String, Object> response = callJsonRpc(agentUrl, METHOD_GET_TASK, params);
    return parseTaskResponse(response);
  }

  @Override
  public A2aTask pollTaskUntilCompleted(String agentUrl, String taskId, Duration timeout) {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    int maxRetries = a2aProperties.getMaxPollRetries();
    int attempts = 0;
    while (System.currentTimeMillis() < deadline && attempts < maxRetries) {
      A2aTask task = getTask(agentUrl, taskId);
      if (task.getStatus() != null && task.getStatus().isTerminal()) {
        return task;
      }
      attempts++;
      try {
        Thread.sleep(a2aProperties.getPollInterval().toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new A2aException(
            "A2A 轮询被中断",
            A2aException.A2aErrorType.CANCELED, e);
      }
    }
    throw new A2aException(
        "A2A 轮询超时: taskId=" + taskId + ", attempts=" + attempts,
        A2aException.A2aErrorType.POLL_TIMEOUT);
  }

  @Override
  public A2aTask cancelTask(String agentUrl, String taskId) {
    validateAgentUrl(agentUrl);
    Map<String, Object> params = new HashMap<>(4);
    params.put("id", taskId);
    Map<String, Object> response = callJsonRpc(agentUrl, METHOD_CANCEL_TASK, params);
    return parseTaskResponse(response);
  }

  @Override
  public boolean isAgentAvailable(String agentUrl) {
    try {
      fetchAgentCard(agentUrl);
      return true;
    } catch (Exception e) {
      log.debug("[A2A] Agent 不可用: url={}, reason={}", agentUrl, e.getMessage());
      return false;
    }
  }

  // ======================== 内部实现 ========================

  /**
   * 发送 JSON-RPC 2.0 请求。
   */
  private Map<String, Object> callJsonRpc(String agentUrl, String method,
      Map<String, Object> params) {
    try {
      Map<String, Object> requestBody = new HashMap<>(8);
      requestBody.put("jsonrpc", JSONRPC_VERSION);
      requestBody.put("method", method);
      requestBody.put("params", params);
      requestBody.put("id", java.util.UUID.randomUUID().toString());
      String jsonBody = YdszJson.toJson(requestBody);
      String endpointUrl = agentUrl.endsWith("/")
          ? agentUrl + "api/v1"
          : agentUrl + "/api/v1";
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(endpointUrl))
          .timeout(a2aProperties.getTimeout())
          .header("Content-Type", CONTENT_TYPE_JSON)
          .header("Accept", CONTENT_TYPE_JSON)
          .header("Authorization", "Bearer " + a2aProperties.getDefaultAuthToken())
          .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
          .build();
      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new A2aException(
            "A2A JSON-RPC 调用失败 (HTTP " + response.statusCode() + "): " + method,
            A2aException.A2aErrorType.PROTOCOL_ERROR);
      }
      return parseJsonRpcResponse(response.body());
    } catch (A2aException e) {
      throw e;
    } catch (java.io.IOException e) {
      throw new A2aException(
          "A2A 网络异常: " + e.getMessage(),
          A2aException.A2aErrorType.NETWORK_TIMEOUT, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new A2aException(
          "A2A 调用被中断",
          A2aException.A2aErrorType.CANCELED, e);
    }
  }

  /**
   * 构建文本消息体。
   */
  private Map<String, Object> buildTextMessage(String text) {
    Map<String, Object> message = new HashMap<>(4);
    message.put("role", "user");
    Map<String, Object> part = new HashMap<>(2);
    part.put("type", "text");
    part.put("text", text);
    message.put("parts", java.util.List.of(part));
    return message;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseJsonRpcResponse(String responseBody) {
    try {
      Map<String, Object> response = YdszJson.parseMap(responseBody);
      if (response.containsKey("error")) {
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        String errorMsg = (String) error.getOrDefault("message", "Unknown A2A error");
        throw new A2aException(errorMsg, A2aException.A2aErrorType.PROTOCOL_ERROR);
      }
      Object result = response.get("result");
      if (result instanceof Map) {
        return (Map<String, Object>) result;
      }
      throw new A2aException(
          "A2A 响应格式异常：缺少 result 字段",
          A2aException.A2aErrorType.PROTOCOL_ERROR);
    } catch (ClassCastException e) {
      throw new A2aException(
          "A2A 响应解析失败",
          A2aException.A2aErrorType.PROTOCOL_ERROR, e);
    }
  }

  /**
   * 解析 Task 响应 JSON 为 A2aTask 对象。
   */
  @SuppressWarnings("unchecked")
  private A2aTask parseTaskResponse(Map<String, Object> result) {
    A2aTask task = new A2aTask();
    task.setId((String) result.get("id"));
    task.setContextId((String) result.get("contextId"));
    if (result.containsKey("status")) {
      Object statusObj = result.get("status");
      if (statusObj instanceof Map) {
        String state = (String) ((Map<String, Object>) statusObj).get("state");
        task.setStatus(A2aTaskStatus.fromProtocolValue(state));
      } else if (statusObj instanceof String) {
        task.setStatus(A2aTaskStatus.fromProtocolValue((String) statusObj));
      }
    }
    task.setMetadata((Map<String, Object>) result.get("metadata"));
    return task;
  }

  /**
   * 解析 AgentCard JSON 为 A2aAgentCard 对象。
   */
  private A2aAgentCard parseAgentCard(String json) {
    try {
      com.njydsz.common.json.tree.ObjectNode node = YdszJson.parseObject(json);
      A2aAgentCard card = new A2aAgentCard();
      card.setName(node.get("name") != null ? node.get("name").asText() : null);
      card.setDescription(node.get("description") != null ? node.get("description").asText() : null);
      card.setUrl(node.get("url") != null ? node.get("url").asText() : null);
      card.setVersion(node.get("version") != null ? node.get("version").asText() : null);
      if (node.get("capabilities") != null && node.get("capabilities").isArray()) {
        java.util.List<String> caps = new java.util.ArrayList<>();
        var elems = node.get("capabilities").elements();
        while (elems.hasNext()) {
          caps.add(elems.next().asText());
        }
        card.setCapabilities(caps);
      }
      card.setRequiresAuthentication(
          node.get("requiresAuthentication") != null && node.get("requiresAuthentication").asBoolean());
      return card;
    } catch (Exception e) {
      throw new A2aException(
          "A2A AgentCard 解析失败",
          A2aException.A2aErrorType.PROTOCOL_ERROR, e);
    }
  }

  private void validateAgentUrl(String agentUrl) {
    if (agentUrl == null || agentUrl.isBlank()) {
      throw new A2aException(
          "A2A Agent URL 不能为空", A2aException.A2aErrorType.PROTOCOL_ERROR);
    }
  }
}
