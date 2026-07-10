package com.njydsz.pmis.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.agent.mcp.model.JsonRpcError;
import com.njydsz.pmis.agent.mcp.model.JsonRpcRequest;
import com.njydsz.pmis.agent.mcp.model.JsonRpcResponse;
import com.njydsz.pmis.agent.mcp.model.McpCallToolResult;
import com.njydsz.pmis.agent.mcp.model.McpInitializeResult;
import com.njydsz.pmis.agent.mcp.model.McpToolDefinition;
import com.njydsz.pmis.agent.mcp.transport.McpTransport;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 客户端（P3-3 落地）。
 *
 * <p>封装 MCP 协议交互，提供以下能力：
 * <ol>
 *   <li>{@link #initialize()} - 握手</li>
 *   <li>{@link #listTools()} - 发现工具</li>
 *   <li>{@link #callTool(String, Map)} - 调用工具</li>
 *   <li>{@link #close()} - 关闭连接</li>
 * </ol>
 *
 * <p>使用方式：
 * <pre>
 * McpClient client = new McpClient(transport, objectMapper);
 * client.initialize();
 * List&lt;McpToolDefinition&gt; tools = client.listTools();
 * McpCallToolResult result = client.callTool("read_file", Map.of("path", "/tmp/test.txt"));
 * client.close();
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Slf4j
public class McpClient implements AutoCloseable {

    /** MCP 协议版本 */
    public static final String PROTOCOL_VERSION = "2024-11-05";

    private final McpTransport transport;
    private final ObjectMapper objectMapper;

    private final AtomicLong requestCounter = new AtomicLong(0);

    /** 握手结果（initialize 成功后填充） */
    private volatile McpInitializeResult initializeResult;

    /** 是否已初始化 */
    private volatile boolean initialized;

    /**
     * 构造 MCP 客户端。
     *
     * @param transport   传输层
     * @param objectMapper JSON 序列化器
     */
    public McpClient(McpTransport transport, ObjectMapper objectMapper) {
        if (transport == null) {
            throw new IllegalArgumentException("transport 不能为空");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper 不能为空");
        }
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 MCP 握手。
     *
     * <p>发送 initialize 请求，验证协议版本，发送 initialized 通知。
     *
     * @return 握手结果
     * @throws Exception 握手失败
     */
    public McpInitializeResult initialize() throws Exception {
        ensureConnected();

        Map<String, Object> params = new HashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", Map.of());
        params.put("clientInfo", Map.of("name", "pmis-agent", "version", "1.0.0"));

        JsonRpcResponse response = sendRequest("initialize", params);
        if (response.isError()) {
            throw new IllegalStateException("MCP initialize 失败: " + response.getError().getMessage());
        }

        initializeResult = objectMapper.treeToValue(response.getResult(), McpInitializeResult.class);
        initialized = true;

        // 发送 initialized 通知
        sendNotification("notifications/initialized");

        log.info("[MCP-Client] 握手成功: server={}, protocol={}",
                initializeResult.getServerInfo() != null ? initializeResult.getServerInfo().getName() : "unknown",
                initializeResult.getProtocolVersion());
        return initializeResult;
    }

    /**
     * 发现服务端工具列表。
     *
     * @return 工具定义列表
     * @throws Exception 请求失败或未初始化
     */
    public List<McpToolDefinition> listTools() throws Exception {
        ensureInitialized();

        JsonRpcResponse response = sendRequest("tools/list", null);
        checkResponse(response, "tools/list");

        JsonNode result = response.getResult();
        if (result == null || !result.has("tools")) {
            return List.of();
        }
        JsonNode toolsNode = result.get("tools");
        List<McpToolDefinition> tools = new ArrayList<>();
        for (JsonNode toolNode : toolsNode) {
            McpToolDefinition tool = objectMapper.treeToValue(toolNode, McpToolDefinition.class);
            tools.add(tool);
        }
        log.info("[MCP-Client] 发现 {} 个工具", tools.size());
        return tools;
    }

    /**
     * 调用 MCP 工具。
     *
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @return 调用结果
     * @throws Exception 调用失败
     */
    public McpCallToolResult callTool(String toolName, Map<String, Object> arguments) throws Exception {
        ensureInitialized();
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName 不能为空");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments != null ? arguments : Map.of());

        JsonRpcResponse response = sendRequest("tools/call", params);
        checkResponse(response, "tools/call");

        McpCallToolResult result = objectMapper.treeToValue(response.getResult(), McpCallToolResult.class);
        log.debug("[MCP-Client] 工具调用完成: tool={}, isError={}", toolName, result.isError());
        return result;
    }

    /**
     * 获取握手结果。
     *
     * @return 握手结果（未初始化返回 null）
     */
    public McpInitializeResult getInitializeResult() {
        return initializeResult;
    }

    /**
     * 是否已初始化。
     *
     * @return true 表示已完成握手
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 获取传输层实例。
     *
     * @return 传输层
     */
    public McpTransport getTransport() {
        return transport;
    }

    @Override
    public void close() {
        transport.close();
        initialized = false;
        log.info("[MCP-Client] 已关闭");
    }

    // ==================== 内部方法 ====================

    /**
     * 发送 JSON-RPC 请求并等待响应。
     */
    private JsonRpcResponse sendRequest(String method, Map<String, Object> params) throws Exception {
        long id = requestCounter.incrementAndGet();
        JsonRpcRequest request = JsonRpcRequest.builder()
                .id(id)
                .method(method)
                .params(params)
                .build();
        String json = objectMapper.writeValueAsString(request);
        log.debug("[MCP-Client] → {}", json);

        transport.send(json);
        String responseJson = transport.receive();
        log.debug("[MCP-Client] ← {}", responseJson);

        JsonRpcResponse response = objectMapper.readValue(responseJson, JsonRpcResponse.class);
        // 校验 id 匹配
        if (response.getId() != null && !String.valueOf(response.getId()).equals(String.valueOf(id))) {
            log.warn("[MCP-Client] 响应 id 不匹配: expected={}, actual={}", id, response.getId());
        }
        return response;
    }

    /**
     * 发送 JSON-RPC 通知（无 id，无响应）。
     */
    private void sendNotification(String method) throws Exception {
        JsonRpcRequest notification = JsonRpcRequest.notification(method);
        String json = objectMapper.writeValueAsString(notification);
        log.debug("[MCP-Client] → (notification) {}", json);
        transport.send(json);
    }

    /**
     * 校验响应是否为错误。
     */
    private void checkResponse(JsonRpcResponse response, String method) {
        if (response.isError()) {
            JsonRpcError error = response.getError();
            throw new IllegalStateException(
                    "MCP " + method + " 失败: [" + error.getCode() + "] " + error.getMessage());
        }
    }

    private void ensureConnected() {
        if (!transport.isConnected()) {
            throw new IllegalStateException("传输层未连接");
        }
    }

    private void ensureInitialized() {
        ensureConnected();
        if (!initialized) {
            throw new IllegalStateException("MCP 客户端未初始化，请先调用 initialize()");
        }
    }
}
