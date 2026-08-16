package com.njydsz.agent.infra.tool;

import java.util.List;
import com.njydsz.agent.server.config.AgentProperties;

/**
 * MCP Client 提供者接口
 *
 * <p>封装 MCP SDK 的具体实现，提供工具发现和调用能力。
 * 通过此接口解耦 {@link McpToolAdapter} 与具体 MCP SDK，
 * 便于单元测试使用 Mock 实现。
 *
 * <p>生产实现 {@code SseMcpClientProvider} 基于 MCP Java SDK
 * （io.modelcontextprotocol:sdk）的 SSE 传输协议连接 MCP Server。
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
     * @param server   MCP Server 连接配置
     * @param toolName 工具名称（不带 server 前缀）
     * @param arguments 工具参数（JSON 对象字符串）
     * @return 工具执行结果内容
     */
    String callTool(AgentProperties.ServerInfo server, String toolName, String arguments);
}
