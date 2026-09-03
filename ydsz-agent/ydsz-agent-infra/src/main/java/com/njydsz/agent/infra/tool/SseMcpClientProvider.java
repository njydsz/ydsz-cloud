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
      return new ArrayList<>(0);
}
}
}