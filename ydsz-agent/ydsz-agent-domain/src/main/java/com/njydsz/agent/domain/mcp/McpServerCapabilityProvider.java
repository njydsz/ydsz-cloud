package com.njydsz.agent.domain.mcp;

import java.util.List;

/**
 * MCP Server 能力提供者接口
 *
 * <p>定义 ydsz-agent 向外部 MCP Client 暴露自身能力（工具列表和服务器描述）的契约。
 *
 * <p>实现类位于 server 层，通过编排内部服务（Chat、NL2SQL、RAG、Agent 执行等）生成
 * {@link McpExposedTool} 列表，并由 web 层的 MCP 传输层 Controller 在协议层调用。
 *
 * <h3>DDD 分层</h3>
 *
 * <ul>
 *   <li><b>domain 层</b>：本接口定义（无外部依赖）
 *   <li><b>server 层</b>：实现类（编排内部服务生成工具描述）
 *   <li><b>web 层</b>：传输层 Controller（调用本接口响应 MCP 请求）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public interface McpServerCapabilityProvider {

  /**
   * 列出当前 ydsz-agent 向外部 MCP Client 暴露的全部工具。
   *
   * <p>工具列表由实现类根据当前配置（如是否启用 Chat / NL2SQL / RAG / Agent 执行）动态生成。
   *
   * @return 工具列表（不可为 null，无暴露工具时返回空列表）
   */
  List<McpExposedTool> listExposedTools();

  /**
   * 获取 MCP Server 描述符（名称、版本、描述、工具列表）。
   *
   * <p>供 MCP {@code initialize} 响应中的 {@code serverInfo} 字段以及校验需要。
   *
   * @return MCP Server 描述符（不可为 null）
   */
  McpServerDescriptor getDescriptor();
}
