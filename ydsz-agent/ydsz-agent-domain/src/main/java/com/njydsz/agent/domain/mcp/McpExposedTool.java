package com.njydsz.agent.domain.mcp;

import java.util.Map;

/**
 * MCP 暴露工具描述
 *
 * <p>描述 ydsz-agent 向外部 MCP Client 暴露的单个工具能力，包含工具名、描述以及 JSON Schema 格式的输入参数定义。
 *
 * <p>该 record 仅承载元数据，不包含执行逻辑；执行由 server 层通过 {@code McpServerCapabilityProvider} 分发完成。
 *
 * @param name 工具名称（MCP 全局唯一，建议使用 snake_case）
 * @param description 工具功能描述（自然语言，供外部 Client 理解工具用途）
 * @param inputSchema JSON Schema 格式的输入参数定义（type: object + properties）
 * @author ydsz-team
 * @since 26.09.07
 */
public record McpExposedTool(String name, String description, Map<String, Object> inputSchema) {
}
