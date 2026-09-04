package com.njydsz.agent.domain.config;

import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Agent 配置属性（含 MCP Server 连接信息）。
 *
 * <p>定义 MCP Server 的连接参数，被 infra 层与 server 层复用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
public class AgentProperties {

  /** MCP Server 连接配置 */
  @Data
  @SuperBuilder
  public static class ServerInfo {
    /** 服务器名称 */
    private String name;
    /** 传输类型：sse / streamable-http / stdio */
    private String transportType;
    /** 服务器 URL */
    private String url;
    /** 超时时间（毫秒） */
    private Integer timeout;
  }
}
