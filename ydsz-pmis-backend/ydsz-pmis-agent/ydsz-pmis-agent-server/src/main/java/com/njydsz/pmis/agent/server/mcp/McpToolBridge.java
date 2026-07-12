package com.njydsz.pmis.agent.server.mcp;

import com.njydsz.pmis.agent.server.engine.AgentContext;
import com.njydsz.pmis.agent.server.mcp.model.McpCallToolResult;
import com.njydsz.pmis.agent.server.mcp.model.McpToolDefinition;
import com.njydsz.pmis.agent.server.tool.AgentTool;
import com.njydsz.pmis.agent.server.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * MCP 工具桥接器（P3-3 落地）。
 *
 * <p>将远程 MCP 工具适配为本地 {@link AgentTool}，使 {@link com.njydsz.pmis.agent.server.tool.ToolRegistry}
 * 能统一管理本地和远程工具，ReActLoop 可透明调用。
 *
 * <p>桥接逻辑：
 * <ol>
 *   <li>{@link #name()} 返回 {@code serverName.toolName}（加前缀避免命名冲突）</li>
 *   <li>{@link #description()} 返回 MCP 工具描述</li>
 *   <li>{@link #parameterSchema()} 从 MCP inputSchema 提取</li>
 *   <li>{@link #execute(Map, AgentContext)} 转发到 {@link McpClient#callTool(String, Map)}</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Slf4j
public class McpToolBridge implements AgentTool {

    private final McpClient client;
    private final McpToolDefinition toolDefinition;
    private final String serverName;
    private final String toolName;

    /** 缓存参数 schema（从 inputSchema 提取一次） */
    private volatile Map<String, Class<?>> cachedSchema;

    /**
     * 构造 MCP 工具桥接器。
     *
     * @param client         MCP 客户端（已初始化）
     * @param toolDefinition MCP 工具定义
     * @param serverName     服务端名称（用于工具名前缀）
     */
    public McpToolBridge(McpClient client, McpToolDefinition toolDefinition, String serverName) {
        if (client == null) {
            throw new IllegalArgumentException("client 不能为空");
        }
        if (toolDefinition == null) {
            throw new IllegalArgumentException("toolDefinition 不能为空");
        }
        this.client = client;
        this.toolDefinition = toolDefinition;
        this.toolName = toolDefinition.getName();
        this.serverName = serverName != null ? serverName : "mcp";
    }

    @Override
    public String name() {
        return serverName + "." + toolName;
    }

    @Override
    public String description() {
        String desc = toolDefinition.getDescription();
        return desc != null && !desc.isBlank() ? desc : ("MCP 工具: " + toolName);
    }

    @Override
    public Map<String, Class<?>> parameterSchema() {
        if (cachedSchema == null) {
            synchronized (this) {
                if (cachedSchema == null) {
                    cachedSchema = toolDefinition.extractParameterSchema();
                }
            }
        }
        return cachedSchema;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext ctx) {
        String traceId = ctx != null ? ctx.getTraceId() : "unknown";
        try {
            log.info("[MCP-Bridge] 调用工具: name={}, args={}, traceId={}",
                    name(), parameters, traceId);

            McpCallToolResult mcpResult = client.callTool(toolName, parameters);

            String output = mcpResult.flattenText();
            if (mcpResult.isError()) {
                log.warn("[MCP-Bridge] 工具返回错误: name={}, error={}", name(), output);
                return ToolResult.failure(output != null && !output.isBlank()
                        ? output : "MCP 工具返回错误");
            }

            log.info("[MCP-Bridge] 工具调用成功: name={}, outputLen={}", name(), output.length());
            return ToolResult.success(output);

        } catch (Exception e) {
            log.error("[MCP-Bridge] 工具调用异常: name={}, error={}", name(), e.getMessage(), e);
            return ToolResult.failure("MCP 工具调用异常: " + e.getMessage());
        }
    }

    /**
     * 获取原始 MCP 工具定义。
     *
     * @return 工具定义
     */
    public McpToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    /**
     * 获取服务端名称。
     *
     * @return 服务端名称
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * 获取原始工具名（不含服务端前缀）。
     *
     * @return 工具名
     */
    public String getToolName() {
        return toolName;
    }
}
