package com.njydsz.agent.infra.tool;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.config.properties.McpProperties;

/**
 * MCP Client 提供者路由器
 *
 * <p>根据 MCP Server 配置中的 {@code transportType} 字段自动选择对应的 {@link McpClientProvider} 实现，为上层 {@link McpToolAdapter} 屏蔽传输层差异。
 *
 * <p>当前支持的传输类型：
 *
 * <ul>
 *   <li>{@code sse} — Server-Sent Events（HTTP 长连接）
 *   <li>{@code streamable-http} — 基于标准 HTTP 的流式传输（推荐）
 * </ul>
 *
 * <p>当配置了不支持的传输类型（如 {@code stdio}）时，调用将抛出 {@link UnsupportedOperationException}，不会出现静默降级。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public class McpClientProviderRouter implements McpClientProvider {

  /** SSE 传输提供者 */
  private final SseMcpClientProvider sseProvider;

  /** Streamable HTTP 传输提供者 */
  private final StreamableHttpMcpClientProvider streamableHttpProvider;

  /**
   * 构造 MCP Client 路由器。
   *
   * @param sseProvider SSE 传输提供者
   * @param streamableHttpProvider Streamable HTTP 传输提供者
   */
  public McpClientProviderRouter(
      SseMcpClientProvider sseProvider,
      StreamableHttpMcpClientProvider streamableHttpProvider) {
    this.sseProvider = sseProvider;
    this.streamableHttpProvider = streamableHttpProvider;
  }

  @Override
  public List<McpToolAdapter.McpToolDescriptor> listTools(McpProperties.ServerInfo server) {
    return resolveProvider(server).listTools(server);
  }

  @Override
  public String callTool(McpProperties.ServerInfo server, String toolName, String arguments) {
    return resolveProvider(server).callTool(server, toolName, arguments);
  }

  /**
   * 根据 Server 配置的传输类型解析对应的 Provider。
   *
   * @param server MCP Server 配置
   * @return 对应传输实现的 Provider
   * @throws UnsupportedOperationException 当传输类型不受支持时
   */
  private McpClientProvider resolveProvider(McpProperties.ServerInfo server) {
    String transport = server.getTransportType() != null ? server.getTransportType().toLowerCase() : "sse";
    switch (transport) {
      case "sse":
        return sseProvider;
      case "streamable-http":
        return streamableHttpProvider;
      case "stdio":
        throw new UnsupportedOperationException(
            "MCP stdio 传输暂未实现（需引入 io.modelcontextprotocol:sdk 官方依赖）。server=" + server.getName()
                + "，请在配置中将 transportType 改为 sse 或 streamable-http");
      default:
        throw new UnsupportedOperationException(
            "MCP 传输类型不受支持: " + transport + "（支持 sse / streamable-http），server=" + server.getName());
    }
  }
}
