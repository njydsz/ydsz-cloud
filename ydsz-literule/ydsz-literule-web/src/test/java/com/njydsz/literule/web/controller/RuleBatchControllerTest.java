package com.njydsz.literule.web.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.spi.RuleChainGraphProvider;
import com.njydsz.literule.web.handler.LiteruleExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RuleBatchController} HTTP contract and batch outcome test.
 */
class RuleBatchControllerTest extends BaseControllerMockTest {

  private static final String CONTENT_TYPE = "application/json";
  private static final String OPERATOR = "operator-001";

  private MockMvc mockMvc;
  private RuleAdminService ruleAdminService;
  private RuleChainGraphProvider ruleChainGraphProvider;

  @BeforeEach
  void setUp() {
    ruleAdminService = org.mockito.Mockito.mock(RuleAdminService.class);
    ruleChainGraphProvider = org.mockito.Mockito.mock(RuleChainGraphProvider.class);
    RuleBatchController controller =
        new RuleBatchController(ruleAdminService, ruleChainGraphProvider);
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setValidator(validator)
        .setControllerAdvice(new LiteruleExceptionHandler(new MockEnvironment()))
        .build();
  }

  @Nested
  @DisplayName("DELETE /literule/rules/{ruleCode}")
  class DeleteRule {

    @Test
    @DisplayName("archives a publishable rule, disables it, saves, and clears its graph")
    void should_archive_rule_and_clear_graph() throws Exception {
      RuleDefinitionDTO definition = publishedRule("R001");
      when(ruleAdminService.getByCode("R001")).thenReturn(definition);

      mockMvc.perform(delete("/literule/rules/R001")
              .header("X-Operator", OPERATOR))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(ruleAdminService).save(definition, OPERATOR, "[删除] 软删除规则 status=ARCHIVED");
      org.assertj.core.api.Assertions.assertThat(definition.getStatus()).isEqualTo("ARCHIVED");
      org.assertj.core.api.Assertions.assertThat(definition.isEnabled()).isFalse();
      verify(ruleChainGraphProvider).delete("R001");
    }

    @Test
    @DisplayName("returns RULE_NOT_FOUND and does not touch save or graph cleanup")
    void should_return_error_for_missing_rule() throws Exception {
      when(ruleAdminService.getByCode("missing")).thenReturn(null);

      mockMvc.perform(delete("/literule/rules/missing")
              .header("X-Operator", OPERATOR))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("B93001"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(ruleAdminService, never()).save(any(), any(), any());
      verify(ruleChainGraphProvider, never()).delete(any());
    }

    @Test
    @DisplayName("rejects an unparsable status instead of archiving it")
    void should_return_error_for_invalid_status() throws Exception {
      RuleDefinitionDTO definition = publishedRule("R001");
      definition.setStatus("NOT_A_STATUS");
      when(ruleAdminService.getByCode("R001")).thenReturn(definition);

      mockMvc.perform(delete("/literule/rules/R001")
              .header("X-Operator", OPERATOR))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("B93004"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(ruleAdminService, never()).save(any(), any(), any());
      verify(ruleChainGraphProvider, never()).delete(any());
    }

    @Test
    @DisplayName("rejects an archived rule because ARCHIVED cannot transition again")
    void should_reject_archived_rule() throws Exception {
      RuleDefinitionDTO definition = publishedRule("R001");
      definition.setStatus("ARCHIVED");
      when(ruleAdminService.getByCode("R001")).thenReturn(definition);

      mockMvc.perform(delete("/literule/rules/R001")
              .header("X-Operator", OPERATOR))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("B93004"));

      verify(ruleAdminService, never()).save(any(), any(), any());
      verify(ruleChainGraphProvider, never()).delete(any());
    }
  }

  @Nested
  @DisplayName("POST /literule/rules/batch-toggle")
  class BatchToggle {

    @Test
    @DisplayName("binds DTO, defaults operator, and saves only published enabled rules")
    void should_toggle_published_rules() throws Exception {
      RuleDefinitionDTO published = publishedRule("R001");
      RuleDefinitionDTO draft = publishedRule("R002");
      draft.setStatus("DRAFT");
      when(ruleAdminService.getByCode("R001")).thenReturn(published);
      when(ruleAdminService.getByCode("R002")).thenReturn(draft);

      mockMvc.perform(post("/literule/rules/batch-toggle")
              .contentType(CONTENT_TYPE)
              .content("{\"ruleCodes\":[\"R001\",\"R002\"],\"isEnabled\":true}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.success").value(1))
          .andExpect(jsonPath("$.data.failed.length()").value(1))
          .andExpect(jsonPath("$.data.failed[0]").value("R002: 未发布的规则不能启用"));

      verify(ruleAdminService).save(published, "SYSTEM", "[批量] 启用");
      verify(ruleAdminService, never()).save(eq(draft), any(), any());
      org.assertj.core.api.Assertions.assertThat(published.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("collects missing rules and service failures without failing the whole request")
    void should_collect_partial_failures() throws Exception {
      RuleDefinitionDTO first = publishedRule("R001");
      RuleDefinitionDTO failing = publishedRule("R002");
      when(ruleAdminService.getByCode("R001")).thenReturn(first);
      when(ruleAdminService.getByCode("missing")).thenReturn(null);
      when(ruleAdminService.getByCode("R002")).thenReturn(failing);
      doThrow(new IllegalStateException("storage unavailable"))
          .when(ruleAdminService).save(eq(failing), any(), any());

      mockMvc.perform(post("/literule/rules/batch-toggle")
              .header("X-Operator", OPERATOR)
              .contentType(CONTENT_TYPE)
              .content("{\"ruleCodes\":[\"R001\",\"missing\",\"R002\"],\"isEnabled\":false}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.success").value(1))
          .andExpect(jsonPath("$.data.failed.length()").value(2))
          .andExpect(jsonPath("$.data.failed[0]").value("missing: 不存在"))
          .andExpect(jsonPath("$.data.failed[1]").value("R002: storage unavailable"));

      verify(ruleAdminService).save(first, OPERATOR, "[批量] 停用");
      org.assertj.core.api.Assertions.assertThat(first.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("rejects blank ruleCodes and null isEnabled through @Valid")
    void should_reject_invalid_body() throws Exception {
      mockMvc.perform(post("/literule/rules/batch-toggle")
              .contentType(CONTENT_TYPE)
              .content("{\"ruleCodes\":[],\"isEnabled\":null}"))
          .andExpect(status().isBadRequest());

      verify(ruleAdminService, never()).getByCode(any());
      verify(ruleAdminService, never()).save(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("POST /literule/rules/batch-priority")
  class BatchPriority {

    @Test
    @DisplayName("adjusts priority by delta and clamps it to 100")
    void should_adjust_and_clamp_priority() throws Exception {
      RuleDefinitionDTO definition = publishedRule("R001");
      definition.setPriority(95);
      when(ruleAdminService.getByCode("R001")).thenReturn(definition);

      mockMvc.perform(post("/literule/rules/batch-priority")
              .header("X-Operator", OPERATOR)
              .contentType(CONTENT_TYPE)
              .content("{\"ruleCodes\":[\"R001\"],\"delta\":10}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.success").value(1))
          .andExpect(jsonPath("$.data.failed").isEmpty());

      verify(ruleAdminService).save(definition, OPERATOR, "[批量] 优先级调整 +10");
      org.assertj.core.api.Assertions.assertThat(definition.getPriority()).isEqualTo(100);
    }

    @Test
    @DisplayName("returns validation failure when delta is zero without reading rules")
    void should_reject_zero_delta() throws Exception {
      mockMvc.perform(post("/literule/rules/batch-priority")
              .header("X-Operator", OPERATOR)
              .contentType(CONTENT_TYPE)
              .content("{\"ruleCodes\":[\"R001\"],\"delta\":0}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A10002"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(ruleAdminService, never()).getByCode(any());
      verify(ruleAdminService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("rejects a blank ruleCodes array through @Valid")
    void should_reject_empty_rules() throws Exception {
      mockMvc.perform(post("/literule/rules/batch-priority")
              .contentType(CONTENT_TYPE)
              .content("{\"ruleCodes\":[],\"delta\":1}"))
          .andExpect(status().isBadRequest());

      verify(ruleAdminService, never()).getByCode(any());
    }
  }

  @Nested
  @DisplayName("POST /literule/rules/batch-category")
  class BatchCategory {

    @Test
    @DisplayName("binds category and updates every existing rule")
    void should_update_category() throws Exception {
      RuleDefinitionDTO first = publishedRule("R001");
      RuleDefinitionDTO second = publishedRule("R002");
      when(ruleAdminService.getByCode("R001")).thenReturn(first);
      when(ruleAdminService.getByCode("R002")).thenReturn(second);

      mockMvc.perform(post("/literule/rules/batch-category")
              .header("X-Operator", OPERATOR)
              .contentType(CONTENT_TYPE)
              .content("{\"ruleCodes\":[\"R001\",\"R002\"],\"category\":\"finance/credit\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.success").value(2))
          .andExpect(jsonPath("$.data.failed").isEmpty());

      verify(ruleAdminService).save(first, OPERATOR, "[批量] 分类调整为 finance/credit");
      verify(ruleAdminService).save(second, OPERATOR, "[批量] 分类调整为 finance/credit");
      org.assertj.core.api.Assertions.assertThat(first.getCategory()).isEqualTo("finance/credit");
      org.assertj.core.api.Assertions.assertThat(second.getCategory()).isEqualTo("finance/credit");
    }

    @Test
    @DisplayName("reports missing rules while retaining successful updates")
    void should_report_missing_rule() throws Exception {
      RuleDefinitionDTO definition = publishedRule("R001");
      when(ruleAdminService.getByCode("R001")).thenReturn(definition);
      when(ruleAdminService.getByCode("missing")).thenReturn(null);

      mockMvc.perform(post("/literule/rules/batch-category")
              .header("X-Operator", OPERATOR)
              .contentType(CONTENT_TYPE)
              .content("{\"ruleCodes\":[\"missing\",\"R001\"],\"category\":\"audit\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.success").value(1))
          .andExpect(jsonPath("$.data.failed[0]").value("missing: 不存在"));

      verify(ruleAdminService).save(definition, OPERATOR, "[批量] 分类调整为 audit");
    }

    @Test
    @DisplayName("rejects a blank category through @Valid")
    void should_reject_blank_category() throws Exception {
      mockMvc.perform(post("/literule/rules/batch-category")
              .contentType(CONTENT_TYPE)
              .content("{\"ruleCodes\":[\"R001\"],\"category\":\" \"}"))
          .andExpect(status().isBadRequest());

      verify(ruleAdminService, never()).save(any(), any(), any());
    }
  }

  private RuleDefinitionDTO publishedRule(String code) {
    RuleDefinitionDTO definition = new RuleDefinitionDTO();
    definition.setCode(code);
    definition.setName("Rule " + code);
    definition.setStatus("PUBLISHED");
    definition.setPriority(50);
    definition.setEnabled(true);
    return definition;
  }
}
