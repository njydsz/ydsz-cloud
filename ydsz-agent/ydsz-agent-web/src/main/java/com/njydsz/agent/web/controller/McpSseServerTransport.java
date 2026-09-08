package com.njydsz.agent.web.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.mcp.McpExposedTool;
import com.njydsz.agent.domain.mcp.McpServerCapabilityProvider;
import com.njydsz.agent.domain.mcp.McpServerDescriptor;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.infra.mcp.McpJsonRpcMessage;
import com.njydsz.agent.server.agent.AgentFacade;
import com.njydsz.common.base.api.ApiVersion;

import jakarta.annotation.PostConstruct;

/**
 * MCP HTTP+SSE 服务端传输层 Controller
 *
 * <p>实现 MCP 规范中基于 HTTP + SSE 的轻量级传输协议，让外部 MCP Client（Claude Desktop、Cursor 等）
 * 能够发现并调用 ydsz-agent 的核心能力（Chat、NL2SQL、RAG、Agent 执行）。
 *
 * <h3>协议交互流程</h3>
 *
 * <ol>
 *   <li>外部 Client 发起 {@code GET /api/mcp} → 建立 SSE 长连接</li>
 *   <li>服务端推送 {@code endpoint} 事件，告知 POST 地址（含 sessionId 查询参数）</li>
 *   <li>外部 Client {@code POST /api/mcp?sessionId=xxx} 发送 JSON-RPC 请求</li>
 *   <li>服务端在当前 POST 请求中直接返回 JSON-RPC 响应（简化模式）</li>
 * </ol>
 *
 * <h3>支持的 MCP 方法</h3>
 *
 * <ul>
 *   <li>{@code initialize} — 握手，返回 serverInfo + capabilities</li>
 *   <li>{@code tools/list} — 获取暴露的工具列表</li>
 *   <li>{@code tools/call} — 执行指定工具并返回结果</li>
 * </ul>
 *
 * <h3>DDD 分层</h3>
 *
 * <p>本 Controller 属于 web 层（传输层），依赖：
 *
 * <ul>
 *   <li>{@link McpServerCapabilityProvider}（domain 接口，server 层实现）— 获取工具描述</li>
 *   <li>{@link AgentFacade}（server 层）— 实际执行 chat / execute_agent 等能力</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/api/mcp")
@ConditionalOnProperty(prefix = "ydsz.agent.mcp", name = "serverEnabled", havingValue = "true")
@RequiredArgsConstructor
public class McpSseServerTransport {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** application/json MIME 类型 */
  private static final String MIME_APPLICATION_JSON = "application/json";

  /** JSON-RPC 版本 */
  private static final String JSONRPC_VERSION = "2.0";

  /** 已建立的 SSE 会话（sessionId → SseEmitter） */
  private final Map<String, SseEmitter> sessions = new ConcurrentHashMap<>();

  /** MCP Server 能力提供者 */
  private final McpServerCapabilityProvider capabilityProvider;

  /** Agent 应用门面（执行工具调用） */
  private final AgentFacade agentFacade;

  /** Agent 配置 */
  private final AgentProperties agentProperties;

  /** MCP Server 描述符 */
  private McpServerDescriptor serverDescriptor;

  /** 启动时构建 Server 描述符 */
  @PostConstruct
  public void init() {
    this.serverDescriptor = capabilityProvider.getDescriptor();
    log.info("[MCP-Server] 传输层已初始化: serverName={}, toolCount={}",
        serverDescriptor.name(), serverDescriptor.tools().size());
  }

  /**
   * GET 请求 — 建立 MCP SSE 长连接。
   *
   * <p>按照 MCP over SSE 规范，响应 Content-Type 为 {@code text/event-stream}，
   * 并在连接建立后推送 {@code endpoint} 事件告知客户端后续 POST 地址。
   *
   * @return SseEmitter 实例（Spring MVC 管理 SSE 生命周期）
   */
  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter connect() {
    String sessionId = UUID.randomUUID().toString().replace("-", "");
    SseEmitter emitter = new SseEmitter(0L); // 0 = 无超时（长连接保持）
    sessions.put(sessionId, emitter);

    // 清理：连接断开或超时时移除会话
    emitter.onCompletion(() -> sessions.remove(sessionId));
    emitter.onTimeout(() -> sessions.remove(sessionId));
    emitter.onError(e -> sessions.remove(sessionId));

    log.info("[MCP-Server] SSE 连接已建立: sessionId={}", sessionId);

    // 发送 endpoint 事件（MCP 标准：告知客户端 POST 地址）
    try {
      String endpointUrl = "/api/mcp?sessionId=" + sessionId;
      emitter.send(SseEmitter.event()
          .name("endpoint")
          .data(endpointUrl, MediaType.TEXT_PLAIN));
    } catch (IOException e) {
      log.warn("[MCP-Server] 发送 endpoint 事件失败: sessionId={}, err={}", sessionId, e.getMessage());
      sessions.remove(sessionId);
    }

    return emitter;
  }

  /**
   * POST 请求 — 处理 MCP JSON-RPC 消息。
   *
   * <p>解析请求中的 {@code method} 字段，分发到对应处理器。响应以 JSON-RPC 2.0 格式直接返回。
   *
   * <p>简化模式：不通过 SSE 回传响应，而是直接在 HTTP Response Body 中返回 JSON-RPC Response。
   * 这种方式兼容大部分 MCP Client（Claude Desktop、Cursor 等均支持同步 POST 响应）。
   *
   * @param body 请求体（支持单条 JSON-RPC 消息或 JSON 数组 batch）
   * @return JSON-RPC 响应（单条消息返回对象，batch 返回数组）
   */
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Object handleJsonRpc(@RequestBody Map<String, Object> body) {
    log.debug("[MCP-Server] 收到 JSON-RPC 请求: {}", body);
    try {
      String method = body.get("method") != null ? body.get("method").toString() : null;
      Object id = body.get("id");
      @SuppressWarnings("unchecked")
      Map<String, Object> params = body.get("params") instanceof Map<?, ?> map
          ? (Map<String, Object>) map : new HashMap<>(0);

      if (method == null || method.isBlank()) {
        return errorResponse(id, McpJsonRpcMessage.ERROR_INVALID_REQUEST, "缺少 method 字段");
      }

      return switch (method) {
        case "initialize" -> handleInitialize(id, params);
        case "tools/list" -> handleToolsList(id, params);
        case "tools/call" -> handleToolsCall(id, params);
        case "notifications/initialized" -> {
          // 客户端握手完成通知，无需响应
          yield null;
        }
        default -> errorResponse(id, McpJsonRpcMessage.ERROR_METHOD_NOT_FOUND,
            "未知方法: " + method);
      };
    } catch (Exception e) {
      log.error("[MCP-Server] JSON-RPC 处理异常: err={}", e.getMessage(), e);
      Object id = body != null ? body.get("id") : null;
      return errorResponse(id, McpJsonRpcMessage.ERROR_INTERNAL,
          "内部错误: " + e.getMessage());
    }
  }

  // ==========================================================================
  // MCP 方法处理器
  // ==========================================================================

  /**
   * 处理 MCP initialize 握手请求。
   *
   * <p>返回协议版本、服务端信息和能力声明。客户端通过此响应确认双方协议版本兼容性。
   *
   * @param id 请求 ID
   * @param params 请求参数（含 clientInfo 等，可忽略）
   * @return initialize 响应
   */
  private Map<String, Object> handleInitialize(Object id, Map<String, Object> params) {
    McpServerDescriptor descriptor = serverDescriptor;
    Map<String, Object> serverInfo = new HashMap<>(COLLECTION_CAPACITY);
    serverInfo.put("name", descriptor.name());
    serverInfo.put("version", descriptor.version());

    Map<String, Object> capabilities = new HashMap<>(COLLECTION_CAPACITY);
    // 声明 tools 能力
    Map<String, Object> toolsCapability = new HashMap<>(COLLECTION_CAPACITY);
    toolsCapability.put("listChanged", false);
    capabilities.put("tools", toolsCapability);

    Map<String, Object> result = new HashMap<>(COLLECTION_CAPACITY);
    result.put("protocolVersion", "2024-11-05");
    result.put("capabilities", capabilities);
    result.put("serverInfo", serverInfo);

    return successResponse(id, result);
  }

  /**
   * 处理 tools/list 请求。
   *
   * <p>返回当前 ydsz-agent 暴露的全部工具列表，按 MCP 标准格式组织。
   *
   * @param id 请求 ID
   * @param params 请求参数（可选，含 cursor 分页，当前未实现分页）
   * @return tools/list 响应
   */
  private Map<String, Object> handleToolsList(Object id, Map<String, Object> params) {
    List<McpExposedTool> tools = capabilityProvider.listExposedTools();
    List<Map<String, Object>> toolResults = new ArrayList<>(tools.size());

    for (McpExposedTool tool : tools) {
      Map<String, Object> toolMap = new LinkedHashMap<>(COLLECTION_CAPACITY);
      toolMap.put("name", tool.name());
      toolMap.put("description", tool.description());
      toolMap.put("inputSchema", tool.inputSchema() != null ? tool.inputSchema() : Map.of());
      toolResults.add(toolMap);
    }

    return successResponse(id, Map.of("tools", toolResults));
  }

  /**
   * 处理 tools/call 请求。
   *
   * <p>根据工具名称分发到对应执行器（chat、execute_agent、text2sql、rag_query），
   * 返回 MCP 标准格式的执行结果。
   *
   * @param id 请求 ID
   * @param params 请求参数（含 name + arguments）
   * @return tools/call 响应
   */
  private Map<String, Object> handleToolsCall(Object id, Map<String, Object> params) {
    String toolName = params.get("name") != null ? params.get("name").toString() : null;
    if (toolName == null || toolName.isBlank()) {
      return errorResponse(id, McpJsonRpcMessage.ERROR_INVALID_PARAMS, "缺少工具名称(name)");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> map
        ? (Map<String, Object>) map : new HashMap<>(0);

    log.info("[MCP-Server] 执行工具: toolName={}, argsSize={}", toolName, arguments.size());

    try {
      String resultText = dispatchToolExecution(toolName, arguments);
      return successResponse(id, buildToolResult(resultText, false));
    } catch (Exception e) {
      log.error("[MCP-Server] 工具执行失败: toolName={}, err={}", toolName, e.getMessage(), e);
      return successResponse(id, buildToolResult("工具执行失败: " + e.getMessage(), true));
    }
  }

  /**
   * 根据工具名称分发到具体执行逻辑。
   *
   * @param toolName 工具名称
   * @param arguments 工具参数
   * @return 执行结果文本
   */
  private String dispatchToolExecution(String toolName, Map<String, Object> arguments) {
    return switch (toolName) {
      case "chat" -> executeChat(arguments);
      case "execute_agent" -> executeAgent(arguments);
      case "text2sql" -> executeText2Sql(arguments);
      case "rag_query" -> executeRagQuery(arguments);
      default -> throw new IllegalArgumentException("不支持的工具: " + toolName);
    };
  }

  /**
   * 执行 chat 工具 — 简单对话。
   *
   * @param arguments 参数（必填 message，可选 conversationId、systemPrompt）
   * @return LLM 响应文本
   */
  private String executeChat(Map<String, Object> arguments) {
    String message = arguments.get("message") != null ? arguments.get("message").toString() : "";
    String conversationId = arguments.get("conversationId") != null
        ? arguments.get("conversationId").toString() : null;
    String systemPrompt = arguments.get("systemPrompt") != null
        ? arguments.get("systemPrompt").toString() : null;

    ChatResponse response = agentFacade.chat(conversationId, message, systemPrompt);
    return generateResponseContent(response);
  }

  /**
   * 执行 execute_agent 工具 — 多轮 Agent 执行。
   *
   * @param arguments 参数（必填 userInput，可选 conversationId、systemPrompt、maxIterations、enabledTools）
   * @return Agent 最终响应文本
   */
  private String executeAgent(Map<String, Object> arguments) {
    String userInput = arguments.get("userInput") != null
        ? arguments.get("userInput").toString() : "";
    String conversationId = arguments.get("conversationId") != null
        ? arguments.get("conversationId").toString() : null;
    String systemPrompt = arguments.get("systemPrompt") != null
        ? arguments.get("systemPrompt").toString() : null;
    Integer maxIterations = arguments.get("maxIterations") instanceof Number n
        ? n.intValue() : 10;

    AgentExecutionRequest request = AgentExecutionRequest.builder()
        .conversationId(conversationId)
        .userInput(userInput)
        .systemPrompt(systemPrompt)
        .maxIterations(maxIterations)
        .build();

    ChatResponse response = agentFacade.execute(request);
    return generateResponseContent(response);
  }

  /**
   * 执行 text2sql 工具 — 自然语言转 SQL 查询。
   *
   * <p>当前通过 Agent 执行链路实现（复用 AgentFactory 中的 Text2SQL Agent），
   * 未来可优化为直接调用底层 Text2SQL 服务。
   *
   * @param arguments 参数（必填 question，可选 tenantCode）
   * @return 查询结果描述
   */
  private String executeText2Sql(Map<String, Object> arguments) {
    String question = arguments.get("question") != null
        ? arguments.get("question").toString() : "";

    AgentExecutionRequest request = AgentExecutionRequest.builder()
        .userInput(question)
        .agentCode("text2sql")
        .maxIterations(5)
        .build();

    ChatResponse response = agentFacade.execute(request);
    return generateResponseContent(response);
  }

  /**
   * 执行 rag_query 工具 — 知识库向量检索。
   *
   * <p>通过 Agent 执行链路实现（复用 AgentFactory 中的 RAG Agent），
   * 返回检索到的文档片段列表。
   *
   * @param arguments 参数（必填 query，可选 topK、minScore）
   * @return 检索结果描述
   */
  private String executeRagQuery(Map<String, Object> arguments) {
    String query = arguments.get("query") != null ? arguments.get("query").toString() : "";

    AgentExecutionRequest request = AgentExecutionRequest.builder()
        .userInput(query)
        .agentCode("rag")
        .maxIterations(3)
        .build();

    ChatResponse response = agentFacade.execute(request);
    return generateResponseContent(response);
  }

  // ==========================================================================
  // JSON-RPC 响应辅助方法
  // ==========================================================================

  /**
   * 构建 JSON-RPC 成功响应。
   *
   * @param id 请求 ID
   * @param result 响应结果
   * @return 响应 Map（可直接序列化为 JSON）
   */
  private Map<String, Object> successResponse(Object id, Object result) {
    Map<String, Object> response = new HashMap<>(COLLECTION_CAPACITY);
    response.put("jsonrpc", JSONRPC_VERSION);
    response.put("id", id);
    response.put("result", result);
    return response;
  }

  /**
   * 构建 JSON-RPC 错误响应。
   *
   * @param id 请求 ID
   * @param code 错误码
   * @param message 错误消息
   * @return 错误响应 Map
   */
  private Map<String, Object> errorResponse(Object id, int code, String message) {
    Map<String, Object> response = new HashMap<>(COLLECTION_CAPACITY);
    response.put("jsonrpc", JSONRPC_VERSION);
    response.put("id", id);
    response.put("error", Map.of("code", code, "message", message));
    return response;
  }

  /**
   * 构建 MCP 工具调用结果 content 数组。
   *
   * <p>格式：{@code [{"type":"text","text":"..."}]}
   *
   * @param text 结果文本
   * @param isError 是否为错误结果
   * @return MCP 标准结果 Map
   */
  private Map<String, Object> buildToolResult(String text, boolean isError) {
    Map<String, Object> result = new HashMap<>(COLLECTION_CAPACITY);
    result.put("content", List.of(Map.of("type", "text", "text", text)));
    result.put("isError", isError);
    return result;
  }

  /**
   * 从 ChatResponse 提取响应内容文本。
   *
   * @param response ChatResponse
   * @return 响应内容
   */
  private String generateResponseContent(ChatResponse response) {
    if (response == null) {
      return "";
    }
    if (response.getContent() != null) {
      return response.getContent();
    }
    return "";
  }
}
