package com.njydsz.agent.domain.config.properties;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Server 连接配置（ydsz-agent 作为 MCP Client 连接外部 Server 的配置）。
 *
 * <p>YAML 前缀：{@code ydsz.agent.mcp}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpServerProperties {

  /** 是否启用 MCP */
  private boolean isEnabled = true;

  /** MCP Server 列表 */
  private List<ServerInfo> servers;

  /** 默认超时时间（毫秒） */
  private Integer defaultTimeout;

  /**
   * MCP Server 连接配置（列表元素）。
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ServerInfo {
    /** 服务器名称 */
    private String name;

    /** 传输类型：sse / streamable-http / stdio */
    private String transportType;

    /** 服务器 URL */
    private String url;

    /** 超时时间（毫秒） */
    private Integer timeout;

    /** 是否启用（默认 true） */
    private boolean isEnabled = true;

    /** 认证类型：none / api-key / bearer / oauth */
    private String authType = "none";

    /** API Key 值 */
    private String authApiKey;

    /** Bearer Token */
    private String authToken;

    /** OAuth Client ID */
    private String authClientId;

    /** OAuth Client Secret */
    private String authClientSecret;

    /** OAuth Token URL */
    private String authTokenUrl;

    /** Stdio 模式启动命令 */
    private String command;

    /** Stdio 模式命令参数 */
    private List<String> args;

    /** Stdio 模式环境变量 */
    private Map<String, String> envVars;
  }
}
