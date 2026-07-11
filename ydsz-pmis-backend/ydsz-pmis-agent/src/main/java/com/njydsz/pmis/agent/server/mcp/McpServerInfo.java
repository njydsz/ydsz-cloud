package com.njydsz.pmis.agent.server.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 服务端信息（P3-3 落地）。
 *
 * <p>在 initialize 握手响应中返回，标识服务端名称和版本。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpServerInfo {

    /** 服务端名称，如 "filesystem-mcp-server" */
    private String name;

    /** 服务端版本，如 "1.0.0" */
    private String version;
}
