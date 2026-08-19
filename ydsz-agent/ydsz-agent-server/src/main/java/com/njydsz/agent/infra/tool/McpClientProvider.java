package com.njydsz.agent.infra.tool;

import java.util.List;

import com.njydsz.agent.server.config.AgentProperties;

/**
 * MCP Client 提供者接口
 *
 * <p>封装 MCP 协议的具体实现，提供工具发现和调用能力。 通过此接口解耦 {@link McpToolAdapter} 与具体传输实现， 便于单元测试使用 Mock 实现。
 *
 * <p>生产实现 {@code SseMcpClientProvider} 基于 JDK HttpClient 手写 JSON-RPC over HTTP/SSE（未引入官方
 * io.modelcontextprotocol:sdk 依赖，注释与实现保持一致）； 未来接入官方 SDK 或 stdio/streamable-http 传输时新增实现类即可。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface McpClientProvider {

  /**
   * 列出 MCP Server 提供的所有工具
   *
   * @param server MCP Server 连接配置
   * @return 工具描述符列表
   */
  List<McpToolAdapter.McpToolDescriptor> listTools(AgentProperties.ServerInfo server);

  /**
   * 调用 MCP Server 上的工具
   *
   * @param server MCP Server 连接配置
   * @param toolName 工具名称（不带 server 前缀）
   * @param arguments 工具参数（JSON 对象字符串）
   * @return 工具执行结果内容
   */
  String callTool(AgentProperties.ServerInfo server, String toolName, String arguments);
}
