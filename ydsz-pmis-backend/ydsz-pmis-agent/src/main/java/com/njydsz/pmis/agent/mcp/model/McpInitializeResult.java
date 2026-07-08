package com.njydsz.pmis.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP initialize 握手结果（P3-3 落地）。
 *
 * <p>客户端发送 initialize 请求后，服务端返回此对象，包含：
 * <ul>
 *   <li>protocolVersion - 服务端支持的协议版本</li>
 *   <li>capabilities - 服务端能力声明</li>
 *   <li>serverInfo - 服务端名称和版本</li>
 *   <li>instructions - 服务端使用说明（可选）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpInitializeResult {

    /** 协议版本，如 "2024-11-05" */
    private String protocolVersion;

    /** 服务端能力 */
    private McpCapabilities capabilities;

    /** 服务端信息 */
    private McpServerInfo serverInfo;

    /** 使用说明（可选，展示给用户或 LLM） */
    private String instructions;
}
