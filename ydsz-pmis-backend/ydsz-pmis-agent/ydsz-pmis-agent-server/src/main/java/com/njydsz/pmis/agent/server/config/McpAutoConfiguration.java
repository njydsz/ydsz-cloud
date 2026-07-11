package com.njydsz.pmis.agent.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.agent.server.mcp.McpClientManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.pmis.agent.server.tool.ToolRegistry;

/**
 * MCP 自动配置（P3-3 落地）。
 *
 * <p>在 {@code pmis.agent.mcp.enabled=true}（默认）时注册 {@link McpClientManager}，
 * 启动时自动连接配置的 MCP 服务端并注册工具到 {@link ToolRegistry}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(McpProperties.class)
@ConditionalOnProperty(prefix = "pmis.agent.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpAutoConfiguration {

    /**
     * 注册 MCP 客户端管理器。
     *
     * <p>使用 {@link ObjectProvider} 延迟获取 {@link ToolRegistry}，避免循环依赖。
     * 使用 {@link ObjectProvider} 获取 {@link ObjectMapper}，确保使用全局实例。
     *
     * @param mcpProperties      MCP 配置
     * @param toolRegistryProvider ToolRegistry 提供者
     * @param objectMapperProvider ObjectMapper 提供者
     * @return MCP 客户端管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public McpClientManager mcpClientManager(McpProperties mcpProperties,
                                              ObjectProvider<ToolRegistry> toolRegistryProvider,
                                              ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable();
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
            objectMapper.findAndRegisterModules();
        }
        log.info("[MCP-AutoConfig] McpClientManager 已注册，enabled={}, servers={}",
                mcpProperties.isEnabled(),
                mcpProperties.getServers() != null ? mcpProperties.getServers().size() : 0);
        return new McpClientManager(mcpProperties, toolRegistryProvider, objectMapper);
    }
}
