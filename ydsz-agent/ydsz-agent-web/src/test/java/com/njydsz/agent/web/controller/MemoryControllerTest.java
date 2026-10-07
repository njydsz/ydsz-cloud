package com.njydsz.agent.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.dto.ConsolidateMemoryRequest;
import com.njydsz.agent.domain.dto.SaveMemoryRequest;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageRole;
import com.njydsz.agent.server.memory.ConversationMemoryConsolidationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.njydsz.common.json.YdszJson;

/**
 * MemoryController 集成测试。
 *
 * <p>使用 MockMvcBuilders.standaloneSetup + MockitoExtension 测试对话记忆管理接口。
 */
@ExtendWith(MockitoExtension.class)
class MemoryControllerTest {

  private MockMvc mockMvc;

  /**
   * 简单异常解析器：将所有未捕获异常转为 HTTP 500。
   *
   * <p>standaloneSetup 不包含全局异常处理器，测试异常场景时需要手动注册。
   */
  private static final HandlerExceptionResolver EXCEPTION_RESOLVER =
      (request, response, handler, ex) -> {
        try {
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (java.io.IOException e) {
          // ignore
        }
        return new ModelAndView();
      };

  @Mock
  private ConversationMemory conversationMemory;

  @Mock
  private ConversationMemoryConsolidationService consolidationService;

  @InjectMocks
  private MemoryController memoryController;

  @BeforeEach
  void setUp() {
    this.mockMvc =
        MockMvcBuilders.standaloneSetup(memoryController)
            .setHandlerExceptionResolvers(EXCEPTION_RESOLVER)
            .build();
  }

  @Nested
  @DisplayName("GET /agent/memory/{conversationId}")
  class LoadMemory {

    @Test
    @DisplayName("加载对话记忆 — 返回消息列表")
    void shouldReturnMemoryList() throws Exception {
      ChatMessage msg = ChatMessage.user("你好", "conv-001");
      when(conversationMemory.load("conv-001", 50)).thenReturn(List.of(msg));

      mockMvc.perform(get("/agent/memory/{conversationId}", "conv-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data[0].content").value("你好"))
          .andExpect(jsonPath("$.data[0].role").value("user"));

      verify(conversationMemory, times(1)).load("conv-001", 50);
    }

    @Test
    @DisplayName("加载空对话记忆 — 返回空列表")
    void shouldReturnEmptyList() throws Exception {
      when(conversationMemory.load("conv-empty", 50)).thenReturn(List.of());

      mockMvc.perform(get("/agent/memory/{conversationId}", "conv-empty"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").isEmpty());

      verify(conversationMemory).load("conv-empty", 50);
    }
  }

  @Nested
  @DisplayName("POST /agent/memory/{conversationId}")
  class SaveMemory {

    @Test
    @DisplayName("写入 USER 角色记忆 — 成功返回")
    void shouldSaveUserMemory() throws Exception {
      SaveMemoryRequest request = new SaveMemoryRequest();
      request.setRole("user");
      request.setContent("测试消息");
      doNothing().when(conversationMemory).save(anyString(), any(ChatMessage.class));

      String json = YdszJson.toJson(request);
      mockMvc.perform(post("/agent/memory/{conversationId}", "conv-001")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(conversationMemory, times(1)).save(eq("conv-001"), any(ChatMessage.class));
    }

    @Test
    @DisplayName("写入 TOOL 角色记忆 — toolCallId 为空时抛异常返回 500")
    void shouldFailWhenToolCallIdMissing() throws Exception {
      SaveMemoryRequest request = new SaveMemoryRequest();
      request.setRole("tool");
      request.setContent("tool result");

      String json = YdszJson.toJson(request);
      // Objects.requireNonNull 在到达 mock 前抛 NPE，异常解析器将其映射为 HTTP 500
      mockMvc.perform(post("/agent/memory/{conversationId}", "conv-001")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isInternalServerError());
    }
  }

  @Nested
  @DisplayName("DELETE /agent/memory/{conversationId}")
  class ClearMemory {

    @Test
    @DisplayName("清除对话记忆 — 返回成功")
    void shouldClearMemory() throws Exception {
      doNothing().when(conversationMemory).clear("conv-001");

      mockMvc.perform(delete("/agent/memory/{conversationId}", "conv-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(conversationMemory, times(1)).clear("conv-001");
    }
  }

  @Nested
  @DisplayName("GET /agent/memory/{conversationId}/count")
  class CountMessages {

    @Test
    @DisplayName("获取消息数量 — 返回计数")
    void shouldReturnMessageCount() throws Exception {
      when(conversationMemory.count("conv-001")).thenReturn(5L);

      mockMvc.perform(get("/agent/memory/{conversationId}/count", "conv-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value(5));

      verify(conversationMemory, times(1)).count("conv-001");
    }
  }

  @Nested
  @DisplayName("POST /agent/memory/{conversationId}/consolidate")
  class Consolidate {

    @Test
    @DisplayName("触发记忆整合 — 成功返回")
    void shouldTriggerConsolidation() throws Exception {
      ConsolidateMemoryRequest request = new ConsolidateMemoryRequest();
      request.setTenantId("tenant-1");
      when(consolidationService.consolidateConversation(anyString(), anyString())).thenReturn(0);

      String json = YdszJson.toJson(request);
      mockMvc.perform(post("/agent/memory/{conversationId}/consolidate", "conv-001")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(consolidationService).consolidateConversation("conv-001", "tenant-1");
    }
  }
}
