package com.njydsz.pmis.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.agent.config.McpProperties;
import com.njydsz.pmis.agent.mcp.transport.McpTransport;
import com.njydsz.pmis.agent.tool.AgentTool;
import com.njydsz.pmis.agent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * McpClientManager 单元测试（P3-3 落地）。
 *
 * <p>验证多服务端连接生命周期管理：启动 / 容错 / 关闭 / 工具注册。
 * 通过覆盖 {@link McpClientManager#createTransport} 注入 Mock 传输层。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("McpClientManager MCP 客户端管理器")
class McpClientManagerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private ObjectProvider<ToolRegistry> toolRegistryProvider;
    @Mock
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        when(toolRegistryProvider.getIfAvailable()).thenReturn(toolRegistry);
    }

    /** 创建可注入 Mock 传输层的管理器子类 */
    private McpClientManager createManagerWithMockTransport(McpProperties properties,
                                                             Deque<String> responses) {
        return new McpClientManager(properties, toolRegistryProvider, objectMapper) {
            @Override
            protected McpTransport createTransport(McpServerConfig config) {
                return new MockTransport(responses);
            }
        };
    }

    /** 简单 Mock 传输层：connect 设为已连接，receive 弹出预置响应 */
    static class MockTransport implements McpTransport {
        private final Deque<String> responses;
        private volatile boolean connected = false;

        MockTransport(Deque<String> responses) {
            this.responses = responses;
        }

        @Override
        public void connect() { connected = true; }

        @Override
        public void send(String json) throws Exception {
            // 忽略请求内容，仅消费响应队列
        }

        @Override
        public String receive() throws Exception {
            String resp = responses.poll();
            if (resp == null) {
                throw new java.io.IOException("响应队列为空");
            }
            return resp;
        }

        @Override
        public boolean isConnected() { return connected; }

        @Override
        public void close() { connected = false; }
    }

    private String initResponse() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":"
                + "{\"protocolVersion\":\"2024-11-05\","
                + "\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";
    }

    private String toolsListResponse() {
        return "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":["
                + "{\"name\":\"read_file\",\"description\":\"读取文件\","
                + "  \"inputSchema\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}}"
                + "]}}";
    }

    @Nested
    @DisplayName("start 启动流程")
    class StartTest {

        @Test
        @DisplayName("未启用时跳过初始化")
        void shouldSkipWhenDisabled() {
            McpProperties props = new McpProperties();
            props.setEnabled(false);

            McpClientManager manager = new McpClientManager(props, toolRegistryProvider, objectMapper);
            manager.start();

            assertThat(manager.getClients()).isEmpty();
            assertThat(manager.getRegisteredToolCount()).isZero();
        }

        @Test
        @DisplayName("无服务端配置时跳过初始化")
        void shouldSkipWhenNoServers() {
            McpProperties props = new McpProperties();
            props.setEnabled(true);
            props.setServers(List.of());

            McpClientManager manager = new McpClientManager(props, toolRegistryProvider, objectMapper);
            manager.start();

            assertThat(manager.getClients()).isEmpty();
        }

        @Test
        @DisplayName("ToolRegistry 不可用时跳过连接")
        void shouldSkipWhenToolRegistryUnavailable() {
            when(toolRegistryProvider.getIfAvailable()).thenReturn(null);
            McpProperties props = new McpProperties();
            props.setEnabled(true);
            McpServerConfig server = new McpServerConfig();
            server.setName("test");
            server.setTransport(McpServerConfig.Transport.STDIO);
            server.setCommand(List.of("echo"));
            props.setServers(List.of(server));

            McpClientManager manager = new McpClientManager(props, toolRegistryProvider, objectMapper);
            manager.start();

            assertThat(manager.getClients()).isEmpty();
        }

        @Test
        @DisplayName("正常连接并注册工具")
        void shouldConnectAndRegisterTools() {
            McpProperties props = new McpProperties();
            props.setEnabled(true);
            McpServerConfig server = new McpServerConfig();
            server.setName("filesystem");
            server.setTransport(McpServerConfig.Transport.STDIO);
            server.setCommand(List.of("test"));
            props.setServers(List.of(server));

            // 预置 2 个响应：initialize + tools/list
            Deque<String> responses = new ArrayDeque<>();
            responses.add(initResponse());
            responses.add(toolsListResponse());

            McpClientManager manager = createManagerWithMockTransport(props, responses);
            manager.start();

            // 验证：1 个客户端，1 个工具注册
            assertThat(manager.getClients()).hasSize(1);
            assertThat(manager.getRegisteredToolCount()).isEqualTo(1);

            // 验证：ToolRegistry.register 被调用，且注册的是 McpToolBridge
            ArgumentCaptor<AgentTool> captor =
                    ArgumentCaptor.forClass(AgentTool.class);
            verify(toolRegistry, atLeastOnce()).register(captor.capture());
            AgentTool registered = captor.getValue();
            assertThat(registered).isInstanceOf(McpToolBridge.class);
            assertThat(registered.name()).isEqualTo("filesystem.read_file");
        }

        @Test
        @DisplayName("单个服务端连接失败不影响其他")
        void shouldTolerateServerFailure() {
            McpProperties props = new McpProperties();
            props.setEnabled(true);

            // 服务端 1：正常
            McpServerConfig server1 = new McpServerConfig();
            server1.setName("good");
            server1.setTransport(McpServerConfig.Transport.STDIO);
            server1.setCommand(List.of("good"));

            // 服务端 2：连接失败（空响应队列导致 receive 抛异常）
            McpServerConfig server2 = new McpServerConfig();
            server2.setName("bad");
            server2.setTransport(McpServerConfig.Transport.HTTP);
            server2.setUrl("http://invalid");

            // 服务端 3：禁用
            McpServerConfig server3 = new McpServerConfig();
            server3.setName("disabled");
            server3.setEnabled(false);

            props.setServers(List.of(server1, server2, server3));

            // 预置服务端 1 的 2 个响应
            Deque<String> responses = new ArrayDeque<>();
            responses.add(initResponse());
            responses.add(toolsListResponse());

            McpClientManager manager = createManagerWithMockTransport(props, responses);
            manager.start();

            // 验证：仅服务端 1 成功连接
            assertThat(manager.getClients()).hasSize(1);
            assertThat(manager.getRegisteredToolCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("stop 关闭流程")
    class StopTest {

        @Test
        @DisplayName("stop 清理所有客户端连接")
        void shouldCloseAllClients() {
            McpProperties props = new McpProperties();
            props.setEnabled(true);
            McpServerConfig server = new McpServerConfig();
            server.setName("test");
            server.setTransport(McpServerConfig.Transport.STDIO);
            server.setCommand(List.of("test"));
            props.setServers(List.of(server));

            Deque<String> responses = new ArrayDeque<>();
            responses.add(initResponse());
            responses.add(toolsListResponse());

            McpClientManager manager = createManagerWithMockTransport(props, responses);
            manager.start();
            assertThat(manager.getClients()).hasSize(1);

            manager.stop();

            assertThat(manager.getClients()).isEmpty();
        }
    }

    @Nested
    @DisplayName("配置解析")
    class ConfigTest {

        @Test
        @DisplayName("STDIO 传输使用 command 参数")
        void shouldCreateStdioTransport() {
            McpProperties props = new McpProperties();
            McpServerConfig server = new McpServerConfig();
            server.setName("test");
            server.setTransport(McpServerConfig.Transport.STDIO);
            server.setCommand(List.of("npx", "server"));
            server.setEnv(Map.of("NODE_ENV", "production"));
            server.setWorkingDir("/tmp");
            server.setTimeoutMs(15000L);
            props.setServers(List.of(server));

            Deque<String> responses = new ArrayDeque<>();
            responses.add(initResponse());
            responses.add(toolsListResponse());

            McpClientManager manager = createManagerWithMockTransport(props, responses);
            manager.start();

            // 验证：通过 Mock 传输层，配置被正确解析（1 个工具注册）
            assertThat(manager.getRegisteredToolCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("HTTP 传输使用 url 参数")
        void shouldCreateHttpTransport() {
            McpProperties props = new McpProperties();
            McpServerConfig server = new McpServerConfig();
            server.setName("remote");
            server.setTransport(McpServerConfig.Transport.HTTP);
            server.setUrl("http://localhost:8080/mcp");
            server.setTimeoutMs(5000L);
            props.setServers(List.of(server));

            Deque<String> responses = new ArrayDeque<>();
            responses.add(initResponse());
            responses.add(toolsListResponse());

            McpClientManager manager = createManagerWithMockTransport(props, responses);
            manager.start();

            assertThat(manager.getClients()).hasSize(1);
        }

        @Test
        @DisplayName("transport 为 null 时默认 STDIO")
        void shouldDefaultToStdioWhenTransportNull() {
            McpProperties props = new McpProperties();
            McpServerConfig server = new McpServerConfig();
            server.setName("test");
            server.setTransport(null);
            server.setCommand(List.of("test"));
            props.setServers(List.of(server));

            Deque<String> responses = new ArrayDeque<>();
            responses.add(initResponse());
            responses.add(toolsListResponse());

            McpClientManager manager = createManagerWithMockTransport(props, responses);
            manager.start();

            assertThat(manager.getClients()).hasSize(1);
        }
    }
}
