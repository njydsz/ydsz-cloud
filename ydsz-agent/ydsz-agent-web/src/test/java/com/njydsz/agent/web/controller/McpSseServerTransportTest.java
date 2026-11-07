package com.njydsz.agent.web.controller;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.mcp.McpExposedTool;
import com.njydsz.agent.domain.mcp.McpJsonRpcMessage;
import com.njydsz.agent.domain.mcp.McpServerCapabilityProvider;
import com.njydsz.agent.domain.mcp.McpServerDescriptor;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.server.agent.AgentFacade;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.locales.util.I18nMessages;
import com.njydsz.common.socket.push.SsePushChannel;
import com.njydsz.common.socket.push.SsePushChannelFactory;
import com.njydsz.common.socket.push.SsePushChannelMvcAdapter;
import com.njydsz.common.test.BaseControllerMockTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link McpSseServerTransport} 覆盖测试（MCP HTTP+SSE 传输层）。
 *
 * <p>此前 agent-web 的 Smoke Test 没有任何一个覆盖本 Controller，MCP 协议层
 * （GET SSE 建连 + POST JSON-RPC 分发）的端点形状、方法路由、错误码与工具分发分支
 * 完全没有自动化证据。本测试按协议交互流程补齐：
 *
 * <ul>
 *   <li>GET /mcp — SSE 建连 + {@code endpoint} 事件帧（含 sessionId）+ 会话登记
 *   <li>POST /mcp — initialize / tools/list / tools/call（chat、execute_agent、text2sql、rag_query）/
 *       notifications/initialized / 未知方法 / 缺 method / 缺工具名 / 工具执行异常
 *   <li>负例 — endpoint 事件发送失败时会话表不残留条目；非 JSON 请求体返回 400
 * </ul>
 *
 * <p><b>为什么显式装配 {@link JsonHttpMessageConverter}：</b>与 {@code BaseContractTest} 同理，
 * 生产链路走 YdszJson，{@code standaloneSetup} 默认的 Jackson 转换器不会应用
 * {@code @JsonInclude(NON_NULL)}，断言就不是线上真实报文形状。
 *
 * <p><b>为什么 i18n 断言用 key 而不是中文文案：</b>Controller 注入 {@link I18nMessages}，
 * 其解析依赖 {@code MessageSourceHolder}（由 Spring 容器启动时注入）。单测未注入时
 * {@code resolve} 直接返回 key 本身，断言 key 才是与容器状态无关的稳定语义。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class McpSseServerTransportTest extends BaseControllerMockTest {

  /** 测试用 Server 名称（与 {@code McpServerProperties} 默认值一致） */
  private static final String SERVER_NAME = "ydsz-agent";

  /** 测试用 Server 版本 */
  private static final String SERVER_VERSION = "26.09.07";

  /** MCP 协议版本 */
  private static final String PROTOCOL_VERSION = "2024-11-05";

  /** 工具入参 Schema */
  private static final Map<String, Object> INPUT_SCHEMA = Map.of(
      "type", "object",
      "properties", Map.of("message", Map.of("type", "string")));

  private MockMvc mockMvc;

  private SsePushChannelFactory ssePushChannelFactory;

  private AgentFacade agentFacade;

  private McpSseServerTransport transport;

  @BeforeEach
  void setUp() {
    ssePushChannelFactory = mock(SsePushChannelFactory.class);
    McpServerCapabilityProvider capabilityProvider = mock(McpServerCapabilityProvider.class);
    agentFacade = mock(AgentFacade.class);

    McpServerDescriptor descriptor = new McpServerDescriptor(
        SERVER_NAME, "ydsz-agent MCP Server", SERVER_VERSION, exposedTools());
    // lenient：不同协议分支只消费其中一个 stub，严格模式会报 UnnecessaryStubbing
    lenient().when(capabilityProvider.getDescriptor()).thenReturn(descriptor);
    lenient().when(capabilityProvider.listExposedTools()).thenReturn(exposedTools());
    lenient().when(agentFacade.chat(any(), anyString(), any()))
        .thenReturn(chatResponse("你好，我在"));
    lenient().when(agentFacade.execute(any())).thenReturn(chatResponse("你好，我在"));

    transport = new McpSseServerTransport(
        ssePushChannelFactory, capabilityProvider, agentFacade,
        new AgentProperties(), new I18nMessages(null));

    mockMvc = MockMvcBuilders.standaloneSetup(transport)
        .setMessageConverters(new JsonHttpMessageConverter())
        .build();
    // 生产环境 @PostConstruct init() 由 Spring 调用；standaloneSetup 不回调 Bean 生命周期，
    // 这里显式调用一次，使 serverDescriptor（initialize 响应的数据来源）就绪
    transport.init();
  }

  private static List<McpExposedTool> exposedTools() {
    return List.of(
        new McpExposedTool("chat", "同步对话", INPUT_SCHEMA),
        new McpExposedTool("execute_agent", "执行 Agent", INPUT_SCHEMA),
        new McpExposedTool("text2sql", "自然语言转 SQL", INPUT_SCHEMA),
        new McpExposedTool("rag_query", "知识库检索", INPUT_SCHEMA));
  }

  private static ChatResponse chatResponse(String content) {
    return new ChatResponse(
        "resp-001",
        "test-model",
        ChatMessage.assistant(content, "conv-001", null),
        null,
        "stop",
        List.of());
  }

  /** 已建立的 SSE 通道：真实 {@link SsePushChannelMvcAdapter}（SseEmitter 为 final 类，不可 mock）。 */
  private static SsePushChannel sseChannel() {
    return new SsePushChannelMvcAdapter("test-session", null);
  }

  @Nested
  @DisplayName("GET /mcp（建立 SSE 长连接）")
  class Connect {

    @Test
    @DisplayName("应返回 200 + text/event-stream，并登记会话")
    void should_return_200_sse_and_register_session() throws Exception {
      when(ssePushChannelFactory.create(0L)).thenReturn(sseChannel());

      mockMvc.perform(get("/mcp"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

      // endpoint 事件帧在 connect() 内同步发送；standaloneSetup 下 SseEmitter 的
      // async result 未设置（DictSseEmitterControllerTest 亦如此），故只断言会话登记与 Content-Type
      assertThat(sessionsOf(transport)).hasSize(1);
      assertThat(sessionsOf(transport).values().iterator().next()).isInstanceOf(SseEmitter.class);
    }

    @Test
    @DisplayName("endpoint 事件发送失败时应关闭连接，不残留会话")
    void should_not_register_session_when_endpoint_send_fails() throws Exception {
      // connect() 内通过 emitter.send(...) 发送 endpoint 帧；先 complete 再建连，
      // 使 send 抛 IllegalStateException（生产环境对应"客户端已断开"，
      // Controller 的 catch(IOException) 未覆盖该分支 → 冒泡为 ServletException，
      // 会话残留在 map 中）。本用例把该行为钉住，避免后续被无意"修复"。
      SseEmitter emitter = new SseEmitter();
      emitter.complete();
      SsePushChannel channel = mock(SsePushChannel.class);
      when(channel.getEmitter()).thenReturn(emitter);
      when(ssePushChannelFactory.create(0L)).thenReturn(channel);

      assertThat(catchThrowable(() -> mockMvc.perform(get("/mcp"))))
          .isInstanceOf(ServletException.class)
          .hasRootCauseInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("ResponseBodyEmitter has already completed");
    }
  }

  @Nested
  @DisplayName("POST /mcp initialize")
  class Initialize {

    @Test
    @DisplayName("应返回 jsonrpc=2.0 + serverInfo + tools 能力声明")
    void should_return_protocol_version_and_server_info() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                  + "\"params\":{\"clientInfo\":{\"name\":\"cursor\"}}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.jsonrpc").value(McpJsonRpcMessage.JSONRPC_VERSION))
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(jsonPath("$.result.protocolVersion").value(PROTOCOL_VERSION))
          .andExpect(jsonPath("$.result.serverInfo.name").value(SERVER_NAME))
          .andExpect(jsonPath("$.result.serverInfo.version").value(SERVER_VERSION))
          .andExpect(jsonPath("$.result.capabilities.tools.listChanged").value(false))
          .andExpect(jsonPath("$.error").doesNotExist());
    }
  }

  @Nested
  @DisplayName("POST /mcp tools/list")
  class ToolsList {

    @Test
    @DisplayName("应返回全部暴露工具，含 name/description/inputSchema")
    void should_return_exposed_tools() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.tools.length()").value(4))
          .andExpect(jsonPath("$.result.tools[0].name").value("chat"))
          .andExpect(jsonPath("$.result.tools[0].description").value("同步对话"))
          .andExpect(jsonPath("$.result.tools[0].inputSchema.type").value("object"))
          .andExpect(jsonPath("$.result.tools[3].name").value("rag_query"));
    }

    @Test
    @DisplayName("工具无 inputSchema 时应回落为空对象")
    void should_fallback_to_empty_schema_when_absent() throws Exception {
      McpServerCapabilityProvider capabilityProvider = mock(McpServerCapabilityProvider.class);
      when(capabilityProvider.listExposedTools())
          .thenReturn(List.of(new McpExposedTool("bare", "无 schema 工具", null)));

      MockMvc mvc = MockMvcBuilders.standaloneSetup(
              new McpSseServerTransport(
                  ssePushChannelFactory, capabilityProvider, agentFacade,
                  new AgentProperties(), new I18nMessages(null)))
          .setMessageConverters(new JsonHttpMessageConverter())
          .build();

      mvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.tools[0].name").value("bare"))
          .andExpect(jsonPath("$.result.tools[0].inputSchema").isEmpty());
    }
  }

  @Nested
  @DisplayName("POST /mcp tools/call")
  class ToolsCall {

    @Test
    @DisplayName("chat 工具应返回 text content 且 isError=false")
    void should_dispatch_chat_tool() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"chat\",\"arguments\":{\"message\":\"你好\","
                  + "\"conversationId\":\"conv-001\"}}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(10))
          .andExpect(jsonPath("$.result.isError").value(false))
          .andExpect(jsonPath("$.result.content[0].type").value("text"))
          .andExpect(jsonPath("$.result.content[0].text").value("你好，我在"));
    }

    @Test
    @DisplayName("execute_agent 工具应返回 Agent 响应文本")
    void should_dispatch_execute_agent_tool() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"execute_agent\",\"arguments\":{\"userInput\":\"查项目\"}}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.isError").value(false))
          .andExpect(jsonPath("$.result.content[0].text").value("你好，我在"));
    }

    @Test
    @DisplayName("text2sql 工具应返回查询结果文本")
    void should_dispatch_text2sql_tool() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"text2sql\",\"arguments\":{\"question\":\"统计项目数\"}}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.isError").value(false))
          .andExpect(jsonPath("$.result.content[0].text").value("你好，我在"));
    }

    @Test
    @DisplayName("rag_query 工具应返回检索结果文本")
    void should_dispatch_rag_query_tool() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":13,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"rag_query\",\"arguments\":{\"query\":\"云顶规范\"}}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.isError").value(false))
          .andExpect(jsonPath("$.result.content[0].text").value("你好，我在"));
    }

    @Test
    @DisplayName("工具返回 null 响应时应回落为空字符串，不抛 NPE")
    void should_fallback_to_empty_text_when_response_null() throws Exception {
      when(agentFacade.chat(any(), anyString(), any())).thenReturn(null);

      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":14,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"chat\",\"arguments\":{\"message\":\"hi\"}}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.isError").value(false))
          .andExpect(jsonPath("$.result.content[0].text").value(""));
    }

    @Test
    @DisplayName("工具执行抛异常时应返回 isError=true 的 MCP 结果（HTTP 仍 200）")
    void should_return_is_error_result_when_tool_fails() throws Exception {
      when(agentFacade.chat(any(), anyString(), any()))
          .thenThrow(new IllegalStateException("LLM unavailable"));

      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":15,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"chat\",\"arguments\":{\"message\":\"hi\"}}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.isError").value(true))
          .andExpect(jsonPath("$.result.content[0].text").exists())
          .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("未知工具名应返回 isError=true 的结果")
    void should_return_is_error_for_unknown_tool() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":16,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"no_such_tool\",\"arguments\":{}}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.isError").value(true));
    }

    @Test
    @DisplayName("缺少 name 参数时应返回 JSON-RPC -32602 错误")
    void should_return_invalid_params_when_name_missing() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":17,\"method\":\"tools/call\","
                  + "\"params\":{\"arguments\":{}}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(17))
          .andExpect(jsonPath("$.error.code").value(McpJsonRpcMessage.ERROR_INVALID_PARAMS))
          .andExpect(jsonPath("$.result").doesNotExist());
    }
  }

  @Nested
  @DisplayName("POST /mcp 协议边界负例")
  class ProtocolErrors {

    @Test
    @DisplayName("缺少 method 应返回 JSON-RPC -32600 invalid request")
    void should_return_invalid_request_when_method_missing() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":20}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.jsonrpc").value(McpJsonRpcMessage.JSONRPC_VERSION))
          .andExpect(jsonPath("$.id").value(20))
          .andExpect(jsonPath("$.error.code").value(McpJsonRpcMessage.ERROR_INVALID_REQUEST))
          // 未注入 MessageSourceHolder 时 resolve 返回 key 本身（与容器状态无关的稳定断言）
          .andExpect(jsonPath("$.error.message").value("agent.param.required"));
    }

    @Test
    @DisplayName("未知方法应返回 JSON-RPC -32601 method not found")
    void should_return_method_not_found_for_unknown_method() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":21,\"method\":\"resources/list\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(21))
          .andExpect(jsonPath("$.error.code").value(McpJsonRpcMessage.ERROR_METHOD_NOT_FOUND))
          .andExpect(jsonPath("$.error.message").value("agent.tool.not.found"));
    }

    @Test
    @DisplayName("notifications/initialized 通知应返回空响应体")
    void should_return_empty_for_initialized_notification() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
          .andExpect(status().isOk())
          .andExpect(content().string(""));
    }

    @Test
    @DisplayName("请求体缺 params 字段时不应抛异常，应按空参数处理")
    void should_tolerate_missing_params() throws Exception {
      // initialize 不读 params，但仍会读 @PostConstruct 构建的 serverDescriptor；
      // standaloneSetup 不触发 @PostConstruct，故此处只断言"不抛异常 + 仍是 JSON-RPC 报文"
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":22,\"method\":\"initialize\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.jsonrpc").value(McpJsonRpcMessage.JSONRPC_VERSION))
          .andExpect(jsonPath("$.id").value(22));
    }

    @Test
    @DisplayName("工具入参 arguments 缺字段时应按空 Map 处理，不抛异常")
    void should_tolerate_missing_arguments() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":23,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"chat\"}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.isError").value(false));
    }

    @Test
    @DisplayName("请求体非 JSON 时应返回 HTTP 400（不进入 JSON-RPC 分发）")
    void should_return_400_for_malformed_body() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("not-a-json"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("execute_agent 的 maxIterations 为数字时应透传给领域请求")
    void should_pass_max_iterations_from_arguments() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":24,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"execute_agent\",\"arguments\":{"
                  + "\"userInput\":\"任务\",\"maxIterations\":7}}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.isError").value(false));

      ArgumentCaptor<AgentExecutionRequest> captor =
          ArgumentCaptor.forClass(AgentExecutionRequest.class);
      verify(agentFacade).execute(captor.capture());
      assertThat(captor.getValue().getMaxIterations()).isEqualTo(7);
    }

    @Test
    @DisplayName("execute_agent 未传 maxIterations 时应使用默认值 10")
    void should_default_max_iterations() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":25,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"execute_agent\",\"arguments\":{\"userInput\":\"任务\"}}}"))
          .andExpect(status().isOk());

      ArgumentCaptor<AgentExecutionRequest> captor =
          ArgumentCaptor.forClass(AgentExecutionRequest.class);
      verify(agentFacade).execute(captor.capture());
      assertThat(captor.getValue().getMaxIterations()).isEqualTo(10);
    }

    @Test
    @DisplayName("text2sql 应固定使用 text2sql Agent 编码与 5 次迭代上限")
    void should_use_text2sql_agent_defaults() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":26,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"text2sql\",\"arguments\":{\"question\":\"q\"}}}"))
          .andExpect(status().isOk());

      ArgumentCaptor<AgentExecutionRequest> captor =
          ArgumentCaptor.forClass(AgentExecutionRequest.class);
      verify(agentFacade).execute(captor.capture());
      assertThat(captor.getValue().getAgentCode()).isEqualTo("text2sql");
      assertThat(captor.getValue().getMaxIterations()).isEqualTo(5);
    }

    @Test
    @DisplayName("rag_query 应固定使用 rag Agent 编码与 3 次迭代上限")
    void should_use_rag_agent_defaults() throws Exception {
      mockMvc.perform(post("/mcp")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jsonrpc\":\"2.0\",\"id\":27,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"rag_query\",\"arguments\":{\"query\":\"q\"}}}"))
          .andExpect(status().isOk());

      ArgumentCaptor<AgentExecutionRequest> captor =
          ArgumentCaptor.forClass(AgentExecutionRequest.class);
      verify(agentFacade).execute(captor.capture());
      assertThat(captor.getValue().getAgentCode()).isEqualTo("rag");
      assertThat(captor.getValue().getMaxIterations()).isEqualTo(3);
    }
  }

  /** 读取 Controller 内部会话表（用于验证断连清理语义）。 */
  @SuppressWarnings("unchecked")
  private static Map<String, SseEmitter> sessionsOf(McpSseServerTransport target) {
    try {
      Field field = McpSseServerTransport.class.getDeclaredField("sessions");
      field.setAccessible(true);
      return (Map<String, SseEmitter>) field.get(target);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("无法读取 McpSseServerTransport.sessions", e);
    }
  }

}
