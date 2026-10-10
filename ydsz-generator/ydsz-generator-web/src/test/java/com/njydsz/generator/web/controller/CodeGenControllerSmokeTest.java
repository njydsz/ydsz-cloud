package com.njydsz.generator.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.generator.service.CodeGenService;
import com.njydsz.generator.vo.CodePreviewVO;

/**
 * {@link CodeGenController} Smoke Test。
 *
 * <p>验证代码生成端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class CodeGenControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private CodeGenService codeGenService;

  @InjectMocks
  private CodeGenController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /generator/code/preview")
  class Preview {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void should_return_200_when_preview_succeeds() throws Exception {
      CodePreviewVO vo = CodePreviewVO.builder()
          .fileName("UserController.java")
          .filePath("controller/UserController.java")
          .build();
      when(codeGenService.preview(anyString(), anyString(), anyString())).thenReturn(List.of(vo));

      mockMvc.perform(get("/generator/code/preview")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("tableName", "ydsz_user"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
