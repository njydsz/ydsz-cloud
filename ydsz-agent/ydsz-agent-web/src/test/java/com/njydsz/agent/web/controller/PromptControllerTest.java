package com.njydsz.agent.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import com.njydsz.agent.server.prompt.PromptEvaluationService;
import com.njydsz.agent.server.prompt.PromptEvaluationService.PromptComparisonResult;
import com.njydsz.agent.server.prompt.PromptEvaluationService.PromptEvaluationResult;
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

import com.njydsz.common.json.YdszJson;

/**
 * PromptController 集成测试。
 *
 * <p>使用 MockMvcBuilders.standaloneSetup + MockitoExtension 测试 Prompt 评估接口。
 */
@ExtendWith(MockitoExtension.class)
class PromptControllerTest {

  private MockMvc mockMvc;

  @Mock
  private PromptEvaluationService evaluationService;

  @InjectMocks
  private PromptController promptController;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(promptController).build();
  }

  @Nested
  @DisplayName("POST /agent/prompt/evaluate")
  class Evaluate {

    @Test
    @DisplayName("评估 Prompt 模板 — 返回评估指标")
    void shouldReturnEvaluationResult() throws Exception {
      PromptEvaluationResult result = new PromptEvaluationResult(
          "tpl-001", "你是一个助手", "gpt-4",
          150L, 100, 50, 150,
          new BigDecimal("0.001250"), 200,
          "这是评估响应内容", LocalDateTime.now());
      when(evaluationService.evaluate(anyString(), any(), anyString(), anyString()))
          .thenReturn(result);

      String body = "{\"templateCode\":\"tpl-001\",\"variables\":{\"key\":\"value\"},"
          + "\"userMessage\":\"测试\",\"model\":\"gpt-4\"}";

      mockMvc.perform(post("/agent/prompt/evaluate")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.templateCode").value("tpl-001"))
          .andExpect(jsonPath("$.data.model").value("gpt-4"))
          .andExpect(jsonPath("$.data.totalTokens").value(150));

      verify(evaluationService, times(1))
          .evaluate(eq("tpl-001"), any(), eq("测试"), eq("gpt-4"));
    }

    @Test
    @DisplayName("评估请求缺少必填字段 — 返回 400")
    void shouldReturn400WhenTemplateCodeBlank() throws Exception {
      String body = "{\"templateCode\":\"\",\"variables\":{},\"userMessage\":\"测试\",\"model\":\"gpt-4\"}";

      mockMvc.perform(post("/agent/prompt/evaluate")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("POST /agent/prompt/compare")
  class Compare {

    @Test
    @DisplayName("对比评估两个 Prompt — 返回对比结果")
    void shouldReturnComparisonResult() throws Exception {
      PromptEvaluationResult resultA = new PromptEvaluationResult(
          "tpl-A", "模板A", "gpt-4", 200L, 120, 80, 200,
          new BigDecimal("0.002000"), 300, "响应A", LocalDateTime.now());
      PromptEvaluationResult resultB = new PromptEvaluationResult(
          "tpl-B", "模板B", "gpt-4", 150L, 100, 50, 150,
          new BigDecimal("0.001250"), 250, "响应B", LocalDateTime.now());
      PromptComparisonResult comparison = new PromptComparisonResult(resultA, resultB);

      when(evaluationService.compare(anyString(), anyString(), any(), anyString(), any()))
          .thenReturn(comparison);

      String body = "{\"templateCodeA\":\"tpl-A\",\"templateCodeB\":\"tpl-B\","
          + "\"variables\":{\"key\":\"value\"},\"userMessage\":\"测试对比\",\"model\":\"gpt-4\"}";

      mockMvc.perform(post("/agent/prompt/compare")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.resultA.templateCode").value("tpl-A"))
          .andExpect(jsonPath("$.data.resultB.templateCode").value("tpl-B"))
          .andExpect(jsonPath("$.data.resultA.totalTokens").value(200))
          .andExpect(jsonPath("$.data.resultB.totalTokens").value(150));

      verify(evaluationService, times(1))
          .compare(eq("tpl-A"), eq("tpl-B"), any(), eq("测试对比"), eq("gpt-4"));
    }

    @Test
    @DisplayName("对比请求缺少必填字段 — 返回 400")
    void shouldReturn400WhenTemplateCodeBlank() throws Exception {
      String body = "{\"templateCodeA\":\"\",\"templateCodeB\":\"tpl-B\","
          + "\"variables\":{},\"userMessage\":\"测试\",\"model\":\"gpt-4\"}";

      mockMvc.perform(post("/agent/prompt/compare")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());
    }
  }
}
