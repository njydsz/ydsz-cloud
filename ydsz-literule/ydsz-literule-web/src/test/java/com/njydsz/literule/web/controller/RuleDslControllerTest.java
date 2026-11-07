package com.njydsz.literule.web.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.web.handler.LiteruleExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RuleDslController} HTTP binding, DSL validation, parsing, and preview test.
 */
class RuleDslControllerTest extends BaseControllerMockTest {

  private static final String CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;

  private MockMvc mockMvc;
  private ExpressionEngine evaluator;

  @BeforeEach
  void setUp() {
    evaluator = mock(ExpressionEngine.class);
    RuleDslController controller = new RuleDslController(evaluator);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new LiteruleExceptionHandler(new MockEnvironment()))
        .build();
  }

  @Nested
  @DisplayName("POST /literule/dsl/validate")
  class Validate {

    @Test
    @DisplayName("binds YAML content, validates condition and severity expressions, and returns counts")
    void should_validate_yaml_dsl() throws Exception {
      when(evaluator.validate("amount > 100")).thenReturn(true);
      when(evaluator.validate("'YELLOW'")).thenReturn(true);

      mockMvc.perform(post("/literule/dsl/validate")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\"rules:\\n  - code: R001\\n    name: Demo rule\\n"
                  + "    type: expression\\n    condition: \\\"amount > 100\\\"\\n"
                  + "    severity_expression: \\\"'YELLOW'\\\"\\n\",\"format\":\"yaml\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.valid").value(true))
          .andExpect(jsonPath("$.data.ruleCount").value(1))
          .andExpect(jsonPath("$.data.chainCount").value(0))
          .andExpect(jsonPath("$.data.errors").isEmpty());

      verify(evaluator).validate("amount > 100");
      verify(evaluator).validate("'YELLOW'");
    }

    @Test
    @DisplayName("returns a successful validation envelope with errors for invalid expressions")
    void should_return_errors_for_invalid_expression() throws Exception {
      when(evaluator.validate("amount >")).thenReturn(false);

      mockMvc.perform(post("/literule/dsl/validate")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\"rules:\\n  - code: R001\\n    name: Demo rule\\n"
                  + "    condition: \\\"amount >\\\"\\n\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.valid").value(false))
          .andExpect(jsonPath("$.data.ruleCount").value(1))
          .andExpect(jsonPath("$.data.errors.length()").value(1));

      verify(evaluator).validate("amount >");
    }

    @Test
    @DisplayName("returns VALIDATION_FAILED for blank content without parsing")
    void should_reject_blank_content() throws Exception {
      mockMvc.perform(post("/literule/dsl/validate")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\" \",\"format\":\"yaml\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A10002"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(evaluator, never()).validate(any());
    }

    @Test
    @DisplayName("reports malformed YAML as an invalid format while keeping HTTP success semantics")
    void should_report_malformed_yaml() throws Exception {
      mockMvc.perform(post("/literule/dsl/validate")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\"rules: [unterminated\",\"format\":\"yaml\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.valid").value(false))
          .andExpect(jsonPath("$.data.ruleCount").value(0))
          .andExpect(jsonPath("$.data.errors[0]").value("格式不合法"));
    }
  }

  @Nested
  @DisplayName("POST /literule/dsl/parse")
  class Parse {

    @Test
    @DisplayName("parses YAML and returns rules, chains, and metadata fields")
    void should_parse_yaml() throws Exception {
      mockMvc.perform(post("/literule/dsl/parse")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\"rules:\\n  - code: R001\\n    name: Demo rule\\n"
                  + "    condition: \\\"true\\\"\\n"
                  + "chains: []\\nmeta:\\n  version: 1\\n\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.rules.length()").value(1))
          .andExpect(jsonPath("$.data.rules[0].code").value("R001"))
          .andExpect(jsonPath("$.data.chains").isArray())
          .andExpect(jsonPath("$.data.meta.version").value(1));
    }

    @Test
    @DisplayName("parses JSON when format is explicitly JSON")
    void should_parse_json() throws Exception {
      mockMvc.perform(post("/literule/dsl/parse")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\"{\\\"rules\\\":[{\\\"code\\\":\\\"R001\\\","
                  + "\\\"name\\\":\\\"Demo rule\\\",\\\"condition\\\":\\\"true\\\"}]}\","
                  + "\"format\":\"JSON\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.rules[0].code").value("R001"));
    }

    @Test
    @DisplayName("returns VALIDATION_FAILED for blank content")
    void should_reject_blank_content() throws Exception {
      mockMvc.perform(post("/literule/dsl/parse")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\"\",\"format\":\"yaml\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A10002"));

      verify(evaluator, never()).validate(any());
    }

    @Test
    @DisplayName("returns DSL_PARSE_ERROR for malformed content")
    void should_return_parse_error() throws Exception {
      mockMvc.perform(post("/literule/dsl/parse")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\"rules: [unterminated\",\"format\":\"yaml\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("B93302"))
          .andExpect(jsonPath("$.data").doesNotExist());
    }
  }

  @Nested
  @DisplayName("POST /literule/dsl/preview")
  class Preview {

    @Test
    @DisplayName("binds facts, evaluates the parsed rule, and maps triggered/severity fields")
    void should_preview_rule_result() throws Exception {
      when(evaluator.validate(any())).thenReturn(true);
      when(evaluator.evalBoolean(eq("amount > 100"), any())).thenReturn(true);
      when(evaluator.eval(eq("'YELLOW'"), any())).thenReturn("YELLOW");

      mockMvc.perform(post("/literule/dsl/preview")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\"rules:\\n  - code: R001\\n    name: Demo rule\\n"
                  + "    condition: \\\"amount > 100\\\"\\n"
                  + "    severity_expression: \\\"'YELLOW'\\\"\\n\","
                  + "\"format\":\"yaml\",\"facts\":{\"amount\":1200}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.length()").value(1))
          .andExpect(jsonPath("$.data[0].ruleCode").value("R001"))
          .andExpect(jsonPath("$.data[0].triggered").value(true))
          .andExpect(jsonPath("$.data[0].severity").value("YELLOW"));

      ArgumentCaptor<RuleContextVO> contextCaptor = ArgumentCaptor.forClass(RuleContextVO.class);
      verify(evaluator).evalBoolean(eq("amount > 100"), contextCaptor.capture());
      assertThat(contextCaptor.getValue().getFacts()).containsEntry("amount", 1200);
      assertThat(contextCaptor.getValue().getScenario()).isEqualTo("DSL_PREVIEW");
    }

    @Test
    @DisplayName("degrades a single failed rule to an untriggered result and keeps other results")
    void should_handle_rule_evaluation_failure() throws Exception {
      when(evaluator.validate(any())).thenReturn(true);
      org.mockito.Mockito.doThrow(new IllegalStateException("invalid fact"))
          .when(evaluator).evalBoolean(any(), any());

      mockMvc.perform(post("/literule/dsl/preview")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\"rules:\\n  - code: R001\\n    name: Demo rule\\n"
                  + "    condition: \\\"amount > 100\\\"\\n\","
                  + "\"facts\":{\"amount\":1200}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.length()").value(1))
          .andExpect(jsonPath("$.data[0].ruleCode").value("R001"))
          .andExpect(jsonPath("$.data[0].triggered").value(false))
          .andExpect(jsonPath("$.data[0].error").exists());
    }

    @Test
    @DisplayName("rejects blank content before parsing or evaluation")
    void should_reject_blank_content() throws Exception {
      mockMvc.perform(post("/literule/dsl/preview")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\" \",\"facts\":{\"amount\":1}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A10002"));

      verify(evaluator, never()).evalBoolean(any(), any());
    }

    @Test
    @DisplayName("returns DSL_PARSE_ERROR for malformed DSL")
    void should_return_parse_error() throws Exception {
      mockMvc.perform(post("/literule/dsl/preview")
              .contentType(CONTENT_TYPE)
              .content("{\"content\":\"rules: [unterminated\",\"facts\":{}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("B93302"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(evaluator, never()).evalBoolean(any(), any());
    }
  }
}
