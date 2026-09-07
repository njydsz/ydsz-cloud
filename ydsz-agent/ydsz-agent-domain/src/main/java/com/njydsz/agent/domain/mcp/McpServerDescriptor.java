package com.njydsz.agent.domain.mcp;

import java.util.List;

/**
 * MCP Server 能力描述符
 *
 * <p>描述 ydsz-agent 作为 MCP Server 的元信息：名称、描述、版本号以及当前暴露的工具集合。
 * 对应 MCP 协议 {@code initialize} 响应中的 {@code serverInfo} 与 {@code tools/list} 结果。
 *
 * @param name MCP Server 名称（{@code initialize} 响应中返回的 serverInfo.name）
 * @param description MCP Server 自然语言描述
 * @param version MCP Server 语义版本号
 * @param tools 当前暴露的工具列表
 * @author ydsz-team
 * @since 26.09.07
 */
public record McpServerDescriptor(
    String name, String description, String version, List<McpExposedTool> tools) {
}
