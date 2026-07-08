package com.njydsz.pmis.agent.mcp.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MCP 模型类单元测试（P3-3 落地）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@link McpCallToolResult#flattenText()} - 文本拼接</li>
 *   <li>{@link McpToolDefinition#extractParameterSchema()} - 参数 schema 提取</li>
 *   <li>{@link JsonRpcRequest#notification(String)} - 通知工厂方法</li>
 *   <li>{@link JsonRpcResponse#isSuccess()} / {@link JsonRpcResponse#isError()} - 状态判断</li>
 *   <li>{@link JsonRpcError} - 工厂方法</li>
 *   <li>{@link McpContent#text(String)} - 工厂方法</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@DisplayName("MCP 模型类")
class McpModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("McpCallToolResult.flattenText 文本拼接")
    class FlattenTextTest {

        @Test
        @DisplayName("空 content 返回空字符串")
        void emptyContentShouldReturnEmptyString() {
            McpCallToolResult result = McpCallToolResult.builder()
                    .content(null)
                    .build();
            assertThat(result.flattenText()).isEmpty();
        }

        @Test
        @DisplayName("null content 列表返回空字符串")
        void nullContentShouldReturnEmptyString() {
            McpCallToolResult result = McpCallToolResult.builder()
                    .content(null)
                    .build();
            assertThat(result.flattenText()).isEmpty();
        }

        @Test
        @DisplayName("单个文本内容直接返回")
        void singleTextShouldReturnAsIs() {
            McpCallToolResult result = McpCallToolResult.builder()
                    .content(List.of(McpContent.text("hello")))
                    .build();
            assertThat(result.flattenText()).isEqualTo("hello");
        }

        @Test
        @DisplayName("多个文本内容以换行拼接")
        void multipleTextsShouldBeJoinedWithNewline() {
            McpCallToolResult result = McpCallToolResult.builder()
                    .content(List.of(
                            McpContent.text("line1"),
                            McpContent.text("line2"),
                            McpContent.text("line3")))
                    .build();
            assertThat(result.flattenText()).isEqualTo("line1\nline2\nline3");
        }

        @Test
        @DisplayName("跳过 null 和空 text 的内容项")
        void shouldSkipNullAndBlankTexts() {
            McpCallToolResult result = McpCallToolResult.builder()
                    .content(java.util.Arrays.asList(
                            McpContent.text("valid"),
                            null,
                            McpContent.builder().type("image").build()))
                    .build();
            assertThat(result.flattenText()).isEqualTo("valid");
        }

        @Test
        @DisplayName("isError 默认 false")
        void isErrorShouldDefaultFalse() {
            McpCallToolResult result = McpCallToolResult.builder().build();
            assertThat(result.isError()).isFalse();
        }
    }

    @Nested
    @DisplayName("McpToolDefinition.extractParameterSchema 参数提取")
    class ExtractSchemaTest {

        @Test
        @DisplayName("正常提取参数类型映射")
        void shouldExtractSchema() {
            ObjectNode schema = objectMapper.createObjectNode();
            ObjectNode props = schema.putObject("properties");
            props.putObject("name").put("type", "string");
            props.putObject("count").put("type", "integer");
            props.putObject("ratio").put("type", "number");
            props.putObject("active").put("type", "boolean");
            props.putObject("items").put("type", "array");

            McpToolDefinition tool = McpToolDefinition.builder()
                    .name("test")
                    .inputSchema(schema)
                    .build();

            Map<String, Class<?>> result = tool.extractParameterSchema();

            assertThat(result).hasSize(5);
            assertThat(result.get("name")).isEqualTo(String.class);
            assertThat(result.get("count")).isEqualTo(Integer.class);
            assertThat(result.get("ratio")).isEqualTo(Double.class);
            assertThat(result.get("active")).isEqualTo(Boolean.class);
            assertThat(result.get("items")).isEqualTo(java.util.List.class);
        }

        @Test
        @DisplayName("inputSchema 为 null 返回空映射")
        void shouldReturnEmptyWhenNullSchema() {
            McpToolDefinition tool = McpToolDefinition.builder()
                    .name("test")
                    .inputSchema(null)
                    .build();

            assertThat(tool.extractParameterSchema()).isEmpty();
        }

        @Test
        @DisplayName("无 properties 字段返回空映射")
        void shouldReturnEmptyWhenNoProperties() {
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");

            McpToolDefinition tool = McpToolDefinition.builder()
                    .name("test")
                    .inputSchema(schema)
                    .build();

            assertThat(tool.extractParameterSchema()).isEmpty();
        }

        @Test
        @DisplayName("未知类型默认映射为 String")
        void shouldMapUnknownTypeToString() {
            ObjectNode schema = objectMapper.createObjectNode();
            ObjectNode props = schema.putObject("properties");
            props.putObject("custom").put("type", "unknown_type");

            McpToolDefinition tool = McpToolDefinition.builder()
                    .name("test")
                    .inputSchema(schema)
                    .build();

            Map<String, Class<?>> result = tool.extractParameterSchema();
            assertThat(result.get("custom")).isEqualTo(String.class);
        }

        @Test
        @DisplayName("无 type 字段默认 String")
        void shouldDefaultToStringWhenNoType() {
            ObjectNode schema = objectMapper.createObjectNode();
            ObjectNode props = schema.putObject("properties");
            props.putObject("param");  // 无 type 字段

            McpToolDefinition tool = McpToolDefinition.builder()
                    .name("test")
                    .inputSchema(schema)
                    .build();

            Map<String, Class<?>> result = tool.extractParameterSchema();
            assertThat(result.get("param")).isEqualTo(String.class);
        }

        @Test
        @DisplayName("object 类型映射为 Map")
        void shouldMapObjectTypeToMap() {
            ObjectNode schema = objectMapper.createObjectNode();
            ObjectNode props = schema.putObject("properties");
            props.putObject("config").put("type", "object");

            McpToolDefinition tool = McpToolDefinition.builder()
                    .name("test")
                    .inputSchema(schema)
                    .build();

            Map<String, Class<?>> result = tool.extractParameterSchema();
            assertThat(result.get("config")).isEqualTo(Map.class);
        }
    }

    @Nested
    @DisplayName("JsonRpcRequest 通知工厂")
    class JsonRpcRequestTest {

        @Test
        @DisplayName("notification 无参数方法")
        void shouldCreateNotificationWithoutParams() {
            JsonRpcRequest req = JsonRpcRequest.notification("notifications/initialized");

            assertThat(req.getJsonrpc()).isEqualTo("2.0");
            assertThat(req.getId()).isNull();
            assertThat(req.getMethod()).isEqualTo("notifications/initialized");
            assertThat(req.getParams()).isNull();
        }

        @Test
        @DisplayName("notification 带参数方法")
        void shouldCreateNotificationWithParams() {
            Map<String, Object> params = Map.of("key", "value");
            JsonRpcRequest req = JsonRpcRequest.notification("test/method", params);

            assertThat(req.getMethod()).isEqualTo("test/method");
            assertThat(req.getParams()).isEqualTo(params);
        }

        @Test
        @DisplayName("Builder 默认 jsonrpc 版本为 2.0")
        void shouldDefaultJsonrpcVersion() {
            JsonRpcRequest req = JsonRpcRequest.builder()
                    .id(1L)
                    .method("test")
                    .build();
            assertThat(req.getJsonrpc()).isEqualTo("2.0");
        }
    }

    @Nested
    @DisplayName("JsonRpcResponse 状态判断")
    class JsonRpcResponseTest {

        @Test
        @DisplayName("有 result 无 error 时 isSuccess=true")
        void shouldReturnTrueWhenHasResult() {
            JsonRpcResponse resp = JsonRpcResponse.builder()
                    .id(1)
                    .result(objectMapper.createObjectNode().put("ok", true))
                    .build();

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.isError()).isFalse();
        }

        @Test
        @DisplayName("有 error 时 isError=true")
        void shouldReturnTrueWhenHasError() {
            JsonRpcResponse resp = JsonRpcResponse.builder()
                    .id(1)
                    .error(JsonRpcError.internalError("fail"))
                    .build();

            assertThat(resp.isError()).isTrue();
            assertThat(resp.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("result 和 error 都为 null 时 isSuccess=false")
        void shouldReturnFalseWhenBothNull() {
            JsonRpcResponse resp = JsonRpcResponse.builder()
                    .id(1)
                    .build();

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.isError()).isFalse();
        }
    }

    @Nested
    @DisplayName("JsonRpcError 工厂方法")
    class JsonRpcErrorTest {

        @Test
        @DisplayName("methodNotFound 返回 -32601")
        void shouldCreateMethodNotFound() {
            JsonRpcError error = JsonRpcError.methodNotFound();

            assertThat(error.getCode()).isEqualTo(-32601);
            assertThat(error.getMessage()).isEqualTo("Method not found");
        }

        @Test
        @DisplayName("internalError 带消息")
        void shouldCreateInternalErrorWithMessage() {
            JsonRpcError error = JsonRpcError.internalError("custom error");

            assertThat(error.getCode()).isEqualTo(-32603);
            assertThat(error.getMessage()).isEqualTo("custom error");
        }

        @Test
        @DisplayName("internalError 消息为 null 时使用默认")
        void shouldUseDefaultWhenMessageNull() {
            JsonRpcError error = JsonRpcError.internalError(null);

            assertThat(error.getCode()).isEqualTo(-32603);
            assertThat(error.getMessage()).isEqualTo("Internal error");
        }
    }

    @Nested
    @DisplayName("McpContent 工厂方法")
    class McpContentTest {

        @Test
        @DisplayName("text 工厂创建文本内容项")
        void shouldCreateTextContent() {
            McpContent content = McpContent.text("hello");

            assertThat(content.getType()).isEqualTo("text");
            assertThat(content.getText()).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("McpServerInfo 和 McpCapabilities")
    class ServerInfoTest {

        @Test
        @DisplayName("McpServerInfo 持有名称和版本")
        void shouldHoldNameAndVersion() {
            McpServerInfo info = McpServerInfo.builder()
                    .name("test-server")
                    .version("2.0.0")
                    .build();

            assertThat(info.getName()).isEqualTo("test-server");
            assertThat(info.getVersion()).isEqualTo("2.0.0");
        }

        @Test
        @DisplayName("McpCapabilities 持有工具能力")
        void shouldHoldToolCapability() {
            McpCapabilities.McpToolCapability toolCap = McpCapabilities.McpToolCapability.builder()
                    .listChanged(true)
                    .build();
            McpCapabilities caps = McpCapabilities.builder()
                    .tools(toolCap)
                    .build();

            assertThat(caps.getTools()).isNotNull();
            assertThat(caps.getTools().getListChanged()).isTrue();
        }
    }

    @Nested
    @DisplayName("McpInitializeResult")
    class InitializeResultTest {

        @Test
        @DisplayName("完整握手结果构造")
        void shouldBuildCompleteResult() {
            McpInitializeResult result = McpInitializeResult.builder()
                    .protocolVersion("2024-11-05")
                    .serverInfo(McpServerInfo.builder().name("srv").version("1.0").build())
                    .capabilities(McpCapabilities.builder().build())
                    .instructions("Use this server for file operations")
                    .build();

            assertThat(result.getProtocolVersion()).isEqualTo("2024-11-05");
            assertThat(result.getServerInfo().getName()).isEqualTo("srv");
            assertThat(result.getInstructions()).contains("file operations");
        }
    }
}
