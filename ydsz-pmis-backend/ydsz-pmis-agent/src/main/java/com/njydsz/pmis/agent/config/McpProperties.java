package com.njydsz.pmis.agent.config;

import com.njydsz.pmis.agent.mcp.McpServerConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 配置属性（P3-3 落地）。
 *
 * <p>绑定 {@code pmis.agent.mcp.*} 配置项。
 *
 * <p>YAML 示例：
 * <pre>
 * pmis:
 *   agent:
 *     mcp:
 *       enabled: true
 *       servers:
 *         - name: filesystem
 *           transport: STDIO
 *           command: ["npx", "@modelcontextprotocol/server-filesystem", "/tmp"]
 *           timeout-ms: 30000
 *         - name: remote
 *           transport: HTTP
 *           url: http://localhost:8080/mcp
 *           timeout-ms: 10000
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Data
@ConfigurationProperties(prefix = "pmis.agent.mcp")
public class McpProperties {

    /** 是否启用 MCP 客户端 */
    private boolean enabled = true;

    /** MCP 服务端列表 */
    private List<McpServerConfig> servers = new ArrayList<>();
}
