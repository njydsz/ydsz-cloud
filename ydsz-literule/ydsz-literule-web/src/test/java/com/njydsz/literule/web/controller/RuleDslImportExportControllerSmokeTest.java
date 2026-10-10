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
 * {@link RuleDslImportExportController} Smoke Test。
 *
 * <p>验证规则 DSL 导入导出端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleDslImportExportControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RuleAdminService ruleAdminService;

  @org.mockito.InjectMocks
  private RuleDslImportExportController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/dsl/export")
  class ExportAllRuleDsl {

    @Test
    @DisplayName("should return 200 when export all rule DSL succeeds")
    void should_return_200_when_export_all_rule_dsl_succeeds() throws Exception {
      when(ruleAdminService.listAll()).thenReturn(List.of(new RuleDefinitionDTO()));

      mockMvc.perform(get("/literule/dsl/export"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
