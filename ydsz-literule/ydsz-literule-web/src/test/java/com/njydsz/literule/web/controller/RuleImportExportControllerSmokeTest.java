package com.njydsz.literule.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.server.config.RuleAdminService;

/**
 * {@link RuleImportExportController} Smoke Test。
 *
 * <p>验证规则导入导出端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleImportExportControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RuleAdminService ruleAdminService;

  @org.mockito.InjectMocks
  private RuleImportExportController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/rules/export")
  class ExportRules {

    @Test
    @DisplayName("should return 200 when export rules succeeds")
    void should_return_200_when_export_rules_succeeds() throws Exception {
      when(ruleAdminService.listAll()).thenReturn(List.of(new RuleDefinitionDTO()));

      mockMvc.perform(get("/literule/rules/export"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
