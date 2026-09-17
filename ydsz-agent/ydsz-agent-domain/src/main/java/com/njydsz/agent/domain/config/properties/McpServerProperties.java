package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ydsz-agent 作为 MCP Server 向外暴露自身能力时的配置。
 *
 * <p>当 {@link McpProperties#isServerEnabled()} 为 {@code true} 时生效，
 * 外部 MCP Client（Claude Desktop、Cursor 等）可通过 HTTP+SSE 协议连接并调用 ydsz-agent 的核心能力。
 *
 * <p>YAML 前缀：{@code ydsz.agent.mcp-server}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpServerProperties {

  /** MCP Server 名称（initialize 响应中返回） */
  private String serverName = "ydsz-agent";

  /** MCP Server 语义版本号 */
  private String serverVersion = "26.09.07";
}
