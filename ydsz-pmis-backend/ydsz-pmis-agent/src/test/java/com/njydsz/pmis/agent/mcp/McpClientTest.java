package com.njydsz.pmis.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.agent.mcp.model.JsonRpcResponse;
import com.njydsz.pmis.agent.mcp.model.McpCallToolResult;
import com.njydsz.pmis.agent.mcp.model.McpContent;
import com.njydsz.pmis.agent.mcp.model.McpInitializeResult;
import com.njydsz.pmis.agent.mcp.model.McpToolDefinition;
import com.njydsz.pmis.agent.mcp.transport.McpTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * McpClient 单元测试（P3-3 落地）。
 *
 * <p>使用 Mock {@link McpTransport} 验证 JSON-RPC 协议交互：
 * initialize / listTools / callTool / 状态校验 / 错误处理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("McpClient MCP 客户端")
class McpClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 响应队列：每次 transport.receive() 弹出一个预置响应 */
    private Deque<String> responseQueue;

    @Mock
    private McpTransport transport;

    private McpClient client;

    @BeforeEach
    void setUp() throws Exception {
        responseQueue = new ArrayDeque<>();
        when(transport.isConnected()).thenReturn(true);
        doAnswer(inv -> responseQueue.poll()).when(transport).receive();
        client = new McpClient(transport, objectMapper);
    }

    /** 预置一个 JSON-RPC 响应 */
    private void enqueueResponse(String json) {
        responseQueue.add(json);
    }

    /** 构造成功响应 JSON */
    private String successResponse(long id, String resultJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + resultJson + "}";
    }

    /** 构造错误响应 JSON */
    private String errorResponse(long id, int code, String message) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\"}}";
    }

    @Nested
    @DisplayName("initialize 握手")
    class InitializeTest {

        @Test
        @DisplayName("正常握手返回 serverInfo 和 protocolVersion")
        void shouldInitializeSuccessfully() throws Exception {
            enqueueResponse(successResponse(1,
                    "{\"protocolVersion\":\"2024-11-05\","
                    + "\"serverInfo\":{\"name\":\"test-server\",\"version\":\"1.0.0\"},"
                    + "\"capabilities\":{\"tools\":{\"listChanged\":true}}}"));

            McpInitializeResult result = client.initialize();

            assertThat(result).isNotNull();
            assertThat(result.getProtocolVersion()).isEqualTo("2024-11-05");
            assertThat(result.getServerInfo().getName()).isEqualTo("test-server");
            assertThat(result.getServerInfo().getVersion()).isEqualTo("1.0.0");
            assertThat(result.getCapabilities()).isNotNull();
            assertThat(result.getCapabilities().getTools().getListChanged()).isTrue();
            assertThat(client.isInitialized()).isTrue();
        }

        @Test
        @DisplayName("握手响应为错误时抛异常")
        void shouldThrowWhenInitializeError() {
            enqueueResponse(errorResponse(1, -32603, "Internal error"));

            assertThatThrownBy(() -> client.initialize())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Internal error");
        }

        @Test
        @DisplayName("传输层未连接时抛异常")
        void shouldThrowWhenNotConnected() {
            when(transport.isConnected()).thenReturn(false);

            assertThatThrownBy(() -> client.initialize())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未连接");
        }
    }

    @Nested
    @DisplayName("listTools 工具发现")
    class ListToolsTest {

        @Test
        @DisplayName("正常返回工具列表")
        void shouldListTools() throws Exception {
            // 先握手
            enqueueResponse(successResponse(1,
                    "{\"protocolVersion\":\"2024-11-05\","
                    + "\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}"));
            client.initialize();

            // tools/list 响应
            enqueueResponse(successResponse(2,
                    "{\"tools\":["
                    + "{\"name\":\"read_file\",\"description\":\"读取文件\","
                    + "  \"inputSchema\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}},"
                    + "{\"name\":\"write_file\",\"description\":\"写入文件\","
                    + "  \"inputSchema\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}}}}"
                    + "]}"));

            List<McpToolDefinition> tools = client.listTools();

            assertThat(tools).hasSize(2);
            assertThat(tools.get(0).getName()).isEqualTo("read_file");
            assertThat(tools.get(0).getDescription()).isEqualTo("读取文件");
            assertThat(tools.get(1).getName()).isEqualTo("write_file");
        }

        @Test
        @DisplayName("空工具列表返回空列表")
        void shouldReturnEmptyList() throws Exception {
            enqueueResponse(successResponse(1,
                    "{\"protocolVersion\":\"2024-11-05\","
                    + "\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}"));
            client.initialize();

            enqueueResponse(successResponse(2, "{\"tools\":[]}"));

            List<McpToolDefinition> tools = client.listTools();

            assertThat(tools).isEmpty();
        }

        @Test
        @DisplayName("未初始化时抛异常")
        void shouldThrowWhenNotInitialized() {
            assertThatThrownBy(() -> client.listTools())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未初始化");
        }
    }

    @Nested
    @DisplayName("callTool 工具调用")
    class CallToolTest {

        @Test
        @DisplayName("正常调用返回文本结果")
        void shouldCallToolSuccessfully() throws Exception {
            // 握手
            enqueueResponse(successResponse(1,
                    "{\"protocolVersion\":\"2024-11-05\","
                    + "\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}"));
            client.initialize();

            // tools/call 响应
            enqueueResponse(successResponse(2,
                    "{\"content\":[{\"type\":\"text\",\"text\":\"Hello World\"}],\"isError\":false}"));

            McpCallToolResult result = client.callTool("read_file", Map.of("path", "/tmp/test.txt"));

            assertThat(result).isNotNull();
            assertThat(result.isError()).isFalse();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getText()).isEqualTo("Hello World");
            assertThat(result.flattenText()).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("工具返回错误结果（isError=true）")
        void shouldReturnErrorResult() throws Exception {
            enqueueResponse(successResponse(1,
                    "{\"protocolVersion\":\"2024-11-05\","
                    + "\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}"));
            client.initialize();

            enqueueResponse(successResponse(2,
                    "{\"content\":[{\"type\":\"text\",\"text\":\"File not found\"}],\"isError\":true}"));

            McpCallToolResult result = client.callTool("read_file", Map.of("path", "/missing"));

            assertThat(result.isError()).isTrue();
            assertThat(result.flattenText()).isEqualTo("File not found");
        }

        @Test
        @DisplayName("多内容项拼接")
        void shouldFlattenMultipleContents() throws Exception {
            enqueueResponse(successResponse(1,
                    "{\"protocolVersion\":\"2024-11-05\","
                    + "\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}"));
            client.initialize();

            enqueueResponse(successResponse(2,
                    "{\"content\":["
                    + "{\"type\":\"text\",\"text\":\"Line 1\"},"
                    + "{\"type\":\"text\",\"text\":\"Line 2\"}"
                    + "],\"isError\":false}"));

            McpCallToolResult result = client.callTool("multi", Map.of());

            assertThat(result.flattenText()).isEqualTo("Line 1\nLine 2");
        }

        @Test
        @DisplayName("空工具名抛异常")
        void shouldThrowWhenToolNameBlank() throws Exception {
            enqueueResponse(successResponse(1,
                    "{\"protocolVersion\":\"2024-11-05\","
                    + "\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}"));
            client.initialize();

            assertThatThrownBy(() -> client.callTool("", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("协议错误响应抛异常")
        void shouldThrowOnErrorResponse() throws Exception {
            enqueueResponse(successResponse(1,
                    "{\"protocolVersion\":\"2024-11-05\","
                    + "\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}"));
            client.initialize();

            enqueueResponse(errorResponse(2, -32601, "Method not found"));

            assertThatThrownBy(() -> client.callTool("ghost", Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Method not found");
        }
    }

    @Nested
    @DisplayName("状态与生命周期")
    class LifecycleTest {

        @Test
        @DisplayName("close 后 initialized 变为 false")
        void shouldResetAfterClose() throws Exception {
            enqueueResponse(successResponse(1,
                    "{\"protocolVersion\":\"2024-11-05\","
                    + "\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}"));
            client.initialize();
            assertThat(client.isInitialized()).isTrue();

            client.close();

            assertThat(client.isInitialized()).isFalse();
        }

        @Test
        @DisplayName("getTransport 返回注入的传输层")
        void shouldReturnTransport() {
            assertThat(client.getTransport()).isSameAs(transport);
        }

        @Test
        @DisplayName("getInitializeResult 未初始化返回 null")
        void shouldReturnNullWhenNotInitialized() {
            assertThat(client.getInitializeResult()).isNull();
        }
    }

    @Nested
    @DisplayName("构造参数校验")
    class ConstructorTest {

        @Test
        @DisplayName("transport 为 null 抛异常")
        void shouldThrowWhenTransportNull() {
            assertThatThrownBy(() -> new McpClient(null, objectMapper))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("objectMapper 为 null 抛异常")
        void shouldThrowWhenObjectMapperNull() {
            assertThatThrownBy(() -> new McpClient(transport, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
