package com.njydsz.pmis.agent.server.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.agent.web.config.McpProperties;
import com.njydsz.pmis.agent.server.mcp.model.McpToolDefinition;
import com.njydsz.pmis.agent.server.mcp.transport.HttpMcpTransport;
import com.njydsz.pmis.agent.server.mcp.transport.McpTransport;
import com.njydsz.pmis.agent.server.mcp.transport.StdioMcpTransport;
import com.njydsz.pmis.agent.server.tool.ToolRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MCP 客户端管理器（P3-3 落地）。
 *
 * <p>统一管理多个 MCP 服务端连接的生命周期：
 * <ol>
 *   <li>启动时按 {@link McpProperties} 配置创建传输层 + 客户端</li>
 *   <li>对每个服务端执行握手 → 发现工具 → 注册 {@link McpToolBridge} 到 {@link ToolRegistry}</li>
 *   <li>关闭时释放所有连接</li>
 * </ol>
 *
 * <p>容错策略：单个服务端连接失败不影响其他服务端，仅记录 WARN 日志。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Slf4j
public class McpClientManager {

    private final McpProperties properties;
    private final ObjectProvider<ToolRegistry> toolRegistryProvider;
    private final ObjectMapper objectMapper;

    /** 已创建的 MCP 客户端列表（用于关闭时清理） */
    private final List<McpClient> clients = new CopyOnWriteArrayList<>();

    /** 已注册的桥接工具数量 */
    private volatile int registeredToolCount;

    /**
     * 构造 MCP 客户端管理器。
     *
     * @param properties          MCP 配置
     * @param toolRegistryProvider ToolRegistry 提供者（延迟加载，避免循环依赖）
     * @param objectMapper        JSON 序列化器
     */
    public McpClientManager(McpProperties properties,
                            ObjectProvider<ToolRegistry> toolRegistryProvider,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.toolRegistryProvider = toolRegistryProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * 启动时初始化所有 MCP 服务端连接。
     */
    @PostConstruct
    public void start() {
        if (properties == null || !properties.isEnabled()) {
            log.info("[MCP-Manager] MCP 未启用，跳过初始化");
            return;
        }
        List<McpServerConfig> servers = properties.getServers();
        if (servers == null || servers.isEmpty()) {
            log.info("[MCP-Manager] 未配置 MCP 服务端，跳过初始化");
            return;
        }

        ToolRegistry toolRegistry = toolRegistryProvider.getIfAvailable();
        if (toolRegistry == null) {
            log.warn("[MCP-Manager] ToolRegistry 不可用，MCP 工具无法注册");
            return;
        }

        int totalTools = 0;
        int connectedServers = 0;
        for (McpServerConfig serverConfig : servers) {
            if (serverConfig == null || !serverConfig.isEnabled()) {
                continue;
            }
            try {
                int toolCount = connectServer(serverConfig, toolRegistry);
                totalTools += toolCount;
                connectedServers++;
                log.info("[MCP-Manager] 服务端 {} 连接成功，注册 {} 个工具",
                        serverConfig.getName(), toolCount);
            } catch (Exception e) {
                log.warn("[MCP-Manager] 服务端 {} 连接失败: {}",
                        serverConfig.getName(), e.getMessage());
            }
        }
        registeredToolCount = totalTools;
        log.info("[MCP-Manager] 初始化完成: {}/{} 服务端连接成功，共注册 {} 个 MCP 工具",
                connectedServers, servers.size(), totalTools);
    }

    /**
     * 关闭时释放所有 MCP 客户端连接。
     */
    @PreDestroy
    public void stop() {
        for (McpClient client : clients) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("[MCP-Manager] 关闭客户端失败: {}", e.getMessage());
            }
        }
        clients.clear();
        log.info("[MCP-Manager] 已关闭所有连接");
    }

    /**
     * 获取已创建的客户端列表（只读）。
     *
     * @return 客户端列表
     */
    public List<McpClient> getClients() {
        return new ArrayList<>(clients);
    }

    /**
     * 获取已注册的桥接工具数量。
     *
     * @return 工具数量
     */
    public int getRegisteredToolCount() {
        return registeredToolCount;
    }

    // ==================== 内部方法 ====================

    /**
     * 连接单个 MCP 服务端，发现并注册工具。
     *
     * @param serverConfig 服务端配置
     * @param toolRegistry  工具注册中心
     * @return 注册的工具数量
     * @throws Exception 连接或发现失败
     */
    private int connectServer(McpServerConfig serverConfig, ToolRegistry toolRegistry) throws Exception {
        // 1. 创建传输层
        McpTransport transport = createTransport(serverConfig);
        transport.connect();

        // 2. 创建客户端并握手（初始化成功后才加入 clients 列表）
        McpClient client = new McpClient(transport, objectMapper);
        try {
            client.initialize();
        } catch (Exception e) {
            // 握手失败时关闭传输层，不保留半连接客户端
            try {
                client.close();
            } catch (Exception closeEx) {
                log.debug("[MCP-Manager] 关闭失败客户端时异常: {}", closeEx.getMessage());
            }
            throw e;
        }
        clients.add(client);

        // 3. 发现工具
        List<McpToolDefinition> tools = client.listTools();
        String serverName = serverConfig.getName() != null ? serverConfig.getName() : "mcp";

        // 4. 注册桥接工具
        for (McpToolDefinition tool : tools) {
            McpToolBridge bridge = new McpToolBridge(client, tool, serverName);
            toolRegistry.register(bridge);
        }
        return tools.size();
    }

    /**
     * 根据配置创建传输层。
     *
     * <p>protected 可见性允许测试子类覆盖，返回 Mock 传输层。
     *
     * @param config 服务端配置
     * @return 传输层实例
     */
    protected McpTransport createTransport(McpServerConfig config) {
        McpServerConfig.Transport transportType = config.getTransport();
        if (transportType == null) {
            transportType = McpServerConfig.Transport.STDIO;
        }
        long timeoutMs = config.getTimeoutMs() > 0 ? config.getTimeoutMs() : 30000L;

        return switch (transportType) {
            case STDIO -> new StdioMcpTransport(
                    config.getCommand(), config.getEnv(), config.getWorkingDir(), timeoutMs);
            case HTTP -> new HttpMcpTransport(config.getUrl(), timeoutMs);
        };
    }
}
