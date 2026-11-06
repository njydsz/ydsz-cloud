package com.njydsz.generator.web.controller;


import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.generator.service.EntityReverseService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ReverseController} Smoke Test。
 *
 * <p>验证逆向工程端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class ReverseControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private EntityReverseService reverseService;

  @BeforeEach
  void setUp() {
    reverseService = mock(EntityReverseService.class);
    ReverseController controller = new ReverseController(reverseService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /generator/reverse/analyze")
  class Analyze {

    @Test
    @DisplayName("should return 200 when analyze succeeds")
    void should_return_200_when_analyze_succeeds() throws Exception {
      when(reverseService.reverseGenerate("/tmp/src", "group-001", "/tmp/out"))
          .thenReturn("generated-code");

      mockMvc.perform(post("/generator/reverse/analyze")
              .param("sourceFilePath", "/tmp/src")
              .param("templateGroupId", "group-001")
              .param("outputDir", "/tmp/out"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /generator/reverse/analyze-batch")
  class AnalyzeBatch {

    @Test
    @DisplayName("should return 200 when batch analyze succeeds")
    void should_return_200_when_batch_analyze_succeeds() throws Exception {
      when(reverseService.reverseBatch("/tmp/src", "group-001", "/tmp/out"))
          .thenReturn(List.of("result-1", "result-2"));

      mockMvc.perform(post("/generator/reverse/analyze-batch")
              .param("sourceDirPath", "/tmp/src")
              .param("templateGroupId", "group-001")
              .param("outputDir", "/tmp/out"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
