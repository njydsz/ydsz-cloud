package com.njydsz.agent.infra.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.model.ToolDefinition;
import com.njydsz.agent.server.config.AgentProperties;

/**
 * MCP 工具适配器
 *
 * <p>将 MCP Server 暴露的工具描述转换为内部 {@link ToolDefinition} 格式， 并注册到 {@link
 * com.njydsz.agent.domain.tool.ToolRegistry} 中。
 *
 * <p>支持多个 MCP Server 的工具统一管理，工具名前缀为 MCP Server 名称以避免冲突。
 *
 * <h3>集成说明</h3>
 *
 * <p>实际 MCP Client SDK（io.modelcontextprotocol:sdk）依赖由 ydsz-agent-infra 的 pom.xml 引入。 本适配器通过 {@link
 * McpClientProvider} 解耦具体 SDK 实现，便于单元测试替换。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class McpToolAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(McpToolAdapter.class);

  /** MCP 工具名分隔符 */
  private static final String TOOL_NAME_SEPARATOR = "__";

  /** MCP Client 提供者（封装具体 MCP SDK 实现） */
  private final McpClientProvider clientProvider;

  /** MCP 配置 */
  private final AgentProperties.Mcp mcpConfig;

  public McpToolAdapter(McpClientProvider clientProvider, AgentProperties.Mcp mcpConfig) {
    this.clientProvider = clientProvider;
    this.mcpConfig = mcpConfig;
  }

  /**
   * 发现所有已配置 MCP Server 的工具并转换为内部定义
   *
   * @return 工具定义列表（已添加 server 名前缀）
   */
  public List<ToolDefinition> discoverAllTools() {
    List<ToolDefinition> allTools = new ArrayList<>(16);
    if (mcpConfig == null || !mcpConfig.isEnabled()) {
      return allTools;
    }
    for (AgentProperties.ServerInfo server : mcpConfig.getServers()) {
      if (!server.isEnabled()) {
        continue;
      }
      try {
        List<ToolDefinition> serverTools = discoverServerTools(server);
        allTools.addAll(serverTools);
        LOG.info("[MCP] Server {} 发现 {} 个工具", server.getName(), serverTools.size());
      } catch (Exception e) {
        LOG.error("[MCP] Server {} 工具发现失败: {}", server.getName(), e.getMessage(), e);
      }
    }
    return allTools;
  }

  /**
   * 发现指定 MCP Server 的工具
   *
   * @param server MCP Server 配置
   * @return 转换后的工具定义列表
   */
  public List<ToolDefinition> discoverServerTools(AgentProperties.ServerInfo server) {
    List<McpToolDescriptor> descriptors = clientProvider.listTools(server);
    List<ToolDefinition> result = new ArrayList<>(descriptors.size());
    for (McpToolDescriptor descriptor : descriptors) {
      // 工具名格式：{serverName}__{toolName}，避免多 Server 间的命名冲突
      String qualifiedName = server.getName() + TOOL_NAME_SEPARATOR + descriptor.name();
      Map<String, Object> parametersSchema = buildParametersSchema(descriptor);
      result.add(new ToolDefinition(qualifiedName, descriptor.description(), parametersSchema));
    }
    return result;
  }

  /**
   * 执行 MCP 工具调用
   *
   * @param qualifiedName 完整工具名（含 server 前缀）
   * @param arguments 工具参数（JSON 对象字符串）
   * @return 工具执行结果
   */
  public String executeTool(String qualifiedName, String arguments) {
    String[] parts = qualifiedName.split(TOOL_NAME_SEPARATOR, 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException("MCP 工具名格式错误: " + qualifiedName);
    }
    String serverName = parts[0];
    String toolName = parts[1];
    AgentProperties.ServerInfo serverConfig = findServerConfig(serverName);
    return clientProvider.callTool(serverConfig, toolName, arguments);
  }

  /**
   * 构建参数 JSON Schema
   *
   * <p>将 MCP 工具的 inputSchema 转换为内部格式。
   */
  private Map<String, Object> buildParametersSchema(McpToolDescriptor descriptor) {
    if (descriptor.inputSchema() != null && !descriptor.inputSchema().isEmpty()) {
      return descriptor.inputSchema();
    }
    // 默认空 schema（object 类型，无必填参数）
    Map<String, Object> schema = new HashMap<>(8);
    schema.put("type", "object");
    schema.put("properties", Map.of());
    return schema;
  }

  /** 根据名称查找 Server 配置 */
  private AgentProperties.ServerInfo findServerConfig(String serverName) {
    return mcpConfig.getServers().stream()
        .filter(s -> serverName.equals(s.getName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("MCP Server 未配置: " + serverName));
  }

  /**
   * MCP 工具描述符（与具体 MCP SDK 解耦的中间表示）
   *
   * @param name 工具名称
   * @param description 工具描述
   * @param inputSchema 输入参数 JSON Schema
   */
  public record McpToolDescriptor(
      String name, String description, Map<String, Object> inputSchema) {}
}
