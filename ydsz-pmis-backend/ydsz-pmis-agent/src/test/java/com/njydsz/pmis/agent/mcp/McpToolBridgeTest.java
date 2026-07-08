package com.njydsz.pmis.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.mcp.model.McpCallToolResult;
import com.njydsz.pmis.agent.mcp.model.McpContent;
import com.njydsz.pmis.agent.mcp.model.McpToolDefinition;
import com.njydsz.pmis.agent.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * McpToolBridge 单元测试（P3-3 落地）。
 *
 * <p>验证 MCP 工具桥接为本地 {@link com.njydsz.pmis.agent.tool.AgentTool} 的行为：
 * name/description/parameterSchema/execute 各方法正确性。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("McpToolBridge MCP 工具桥接器")
class McpToolBridgeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private McpClient client;

    private McpToolDefinition toolDef;
    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        // 构造工具定义
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode pathProp = props.putObject("path");
        pathProp.put("type", "string");
        ObjectNode limitProp = props.putObject("limit");
        limitProp.put("type", "integer");
        schema.set("properties", props);

        toolDef = McpToolDefinition.builder()
                .name("read_file")
                .description("读取文件内容")
                .inputSchema(schema)
                .build();

        ctx = new AgentContext("PROJECT", "P-001", "测试项目",
                "user-001", "测试用户", "MANUAL", null);
    }

    @Nested
    @DisplayName("元信息方法")
    class MetadataTest {

        @Test
        @DisplayName("name 返回 serverName.toolName 格式")
        void shouldReturnPrefixedName() {
            McpToolBridge bridge = new McpToolBridge(client, toolDef, "filesystem");

            assertThat(bridge.name()).isEqualTo("filesystem.read_file");
        }

        @Test
        @DisplayName("serverName 为 null 时使用 'mcp' 前缀")
        void shouldUseDefaultPrefixWhenServerNameNull() {
            McpToolBridge bridge = new McpToolBridge(client, toolDef, null);

            assertThat(bridge.name()).isEqualTo("mcp.read_file");
        }

        @Test
        @DisplayName("description 返回工具定义中的描述")
        void shouldReturnDescription() {
            McpToolBridge bridge = new McpToolBridge(client, toolDef, "filesystem");

            assertThat(bridge.description()).isEqualTo("读取文件内容");
        }

        @Test
        @DisplayName("description 为空时使用默认描述")
        void shouldUseDefaultWhenDescriptionBlank() {
            toolDef.setDescription("");
            McpToolBridge bridge = new McpToolBridge(client, toolDef, "fs");

            assertThat(bridge.description()).isEqualTo("MCP 工具: read_file");
        }

        @Test
        @DisplayName("parameterSchema 从 inputSchema 提取参数映射")
        void shouldExtractParameterSchema() {
            McpToolBridge bridge = new McpToolBridge(client, toolDef, "fs");

            Map<String, Class<?>> schema = bridge.parameterSchema();

            assertThat(schema).hasSize(2);
            assertThat(schema.get("path")).isEqualTo(String.class);
            assertThat(schema.get("limit")).isEqualTo(Integer.class);
        }

        @Test
        @DisplayName("inputSchema 为 null 时返回空映射")
        void shouldReturnEmptySchemaWhenNullInputSchema() {
            toolDef.setInputSchema(null);
            McpToolBridge bridge = new McpToolBridge(client, toolDef, "fs");

            assertThat(bridge.parameterSchema()).isEmpty();
        }
    }

    @Nested
    @DisplayName("execute 工具调用")
    class ExecuteTest {

        @Test
        @DisplayName("正常调用返回成功结果")
        void shouldReturnSuccessResult() throws Exception {
            McpCallToolResult mcpResult = McpCallToolResult.builder()
                    .content(List.of(McpContent.text("file content here")))
                    .error(false)
                    .build();
            when(client.callTool(eq("read_file"), anyMap())).thenReturn(mcpResult);

            McpToolBridge bridge = new McpToolBridge(client, toolDef, "fs");
            ToolResult result = bridge.execute(Map.of("path", "/tmp/test.txt"), ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("file content here");
        }

        @Test
        @DisplayName("MCP 工具返回 isError=true 时映射为失败")
        void shouldMapErrorResultToFailure() throws Exception {
            McpCallToolResult mcpResult = McpCallToolResult.builder()
                    .content(List.of(McpContent.text("File not found")))
                    .error(true)
                    .build();
            when(client.callTool(anyString(), anyMap())).thenReturn(mcpResult);

            McpToolBridge bridge = new McpToolBridge(client, toolDef, "fs");
            ToolResult result = bridge.execute(Map.of("path", "/missing"), ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("File not found");
        }

        @Test
        @DisplayName("调用异常时返回失败结果")
        void shouldReturnFailureOnException() throws Exception {
            when(client.callTool(anyString(), anyMap()))
                    .thenThrow(new RuntimeException("connection lost"));

            McpToolBridge bridge = new McpToolBridge(client, toolDef, "fs");
            ToolResult result = bridge.execute(Map.of("path", "/tmp/test.txt"), ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("connection lost");
        }

        @Test
        @DisplayName("空内容结果返回空字符串")
        void shouldReturnEmptyOutputWhenNoContent() throws Exception {
            McpCallToolResult mcpResult = McpCallToolResult.builder()
                    .content(List.of())
                    .error(false)
                    .build();
            when(client.callTool(anyString(), anyMap())).thenReturn(mcpResult);

            McpToolBridge bridge = new McpToolBridge(client, toolDef, "fs");
            ToolResult result = bridge.execute(Map.of(), ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEmpty();
        }

        @Test
        @DisplayName("ctx 为 null 时不影响执行")
        void shouldWorkWithNullContext() throws Exception {
            McpCallToolResult mcpResult = McpCallToolResult.builder()
                    .content(List.of(McpContent.text("ok")))
                    .error(false)
                    .build();
            when(client.callTool(anyString(), anyMap())).thenReturn(mcpResult);

            McpToolBridge bridge = new McpToolBridge(client, toolDef, "fs");
            ToolResult result = bridge.execute(Map.of(), null);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("构造参数校验")
    class ConstructorTest {

        @Test
        @DisplayName("client 为 null 抛异常")
        void shouldThrowWhenClientNull() {
            assertThatThrownBy(() -> new McpToolBridge(null, toolDef, "fs"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("toolDefinition 为 null 抛异常")
        void shouldThrowWhenToolDefNull() {
            assertThatThrownBy(() -> new McpToolBridge(client, null, "fs"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Getter 方法")
    class GetterTest {

        @Test
        @DisplayName("getToolDefinition 返回原始定义")
        void shouldReturnToolDefinition() {
            McpToolBridge bridge = new McpToolBridge(client, toolDef, "fs");

            assertThat(bridge.getToolDefinition()).isSameAs(toolDef);
        }

        @Test
        @DisplayName("getServerName 返回服务端名称")
        void shouldReturnServerName() {
            McpToolBridge bridge = new McpToolBridge(client, toolDef, "myserver");

            assertThat(bridge.getServerName()).isEqualTo("myserver");
        }

        @Test
        @DisplayName("getToolName 返回原始工具名")
        void shouldReturnToolName() {
            McpToolBridge bridge = new McpToolBridge(client, toolDef, "fs");

            assertThat(bridge.getToolName()).isEqualTo("read_file");
        }
    }
}
