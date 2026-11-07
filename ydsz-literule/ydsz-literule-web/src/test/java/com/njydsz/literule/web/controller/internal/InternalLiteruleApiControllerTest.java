package com.njydsz.literule.web.controller.internal;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
import com.njydsz.literule.web.handler.LiteruleExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link InternalLiteruleApiController} HTTP contract test.
 */
class InternalLiteruleApiControllerTest extends BaseControllerMockTest {

  private static final String CONTENT_TYPE = "application/json";

  private MockMvc mockMvc;
  private RuleAdminService ruleAdminService;
  private RuleEngine ruleEngine;
  private LiteruleWebConverter literuleWebConverter;

  @BeforeEach
  void setUp() {
    ruleAdminService = org.mockito.Mockito.mock(RuleAdminService.class);
    ruleEngine = org.mockito.Mockito.mock(RuleEngine.class);
    literuleWebConverter = org.mockito.Mockito.mock(LiteruleWebConverter.class);
    InternalLiteruleApiController controller =
        new InternalLiteruleApiController(ruleAdminService, ruleEngine);
    ReflectionTestUtils.setField(controller, "literuleWebConverter", literuleWebConverter);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new LiteruleExceptionHandler(new MockEnvironment()))
        .build();
  }

  @Nested
  @DisplayName("POST /internal/literule/rules/dry-run")
  class DryRun {

    @Test
    @DisplayName("binds optional ruleCode and facts, maps every result")
    void should_bind_and_map_results() throws Exception {
      RuleResultVO result = RuleResultVO.builder().ruleCode("R001").build();
      RuleResultVO mapped = RuleResultVO.builder().ruleCode("R001").build();
      when(ruleAdminService.dryRun(eq("R001"), any())).thenReturn(List.of(result));
      when(literuleWebConverter.entityToVO(result)).thenReturn(mapped);

      mockMvc.perform(post("/internal/literule/rules/dry-run")
              .param("ruleCode", "R001")
              .contentType(CONTENT_TYPE)
              .content("{\"amount\":1200}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].ruleCode").value("R001"));

      verify(ruleAdminService).dryRun(eq("R001"), eq(Map.of("amount", 1200)));
      verify(literuleWebConverter).entityToVO(result);
    }

    @Test
    @DisplayName("passes null ruleCode through without touching the engine")
    void should_support_all_rules() throws Exception {
      when(ruleAdminService.dryRun(eq(null), any())).thenReturn(List.of());

      mockMvc.perform(post("/internal/literule/rules/dry-run")
              .contentType(CONTENT_TYPE)
              .content("{\"amount\":1}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").isEmpty());

      verify(ruleAdminService).dryRun(eq(null), eq(Map.of("amount", 1)));
      verify(ruleEngine, never()).evaluate(any());
    }
  }

  @Nested
  @DisplayName("POST /internal/literule/rules/evaluate")
  class Evaluate {

    @Test
    @DisplayName("uses DEFAULT scenario and filters by requested ruleCode")
    void should_filter_results() throws Exception {
      RuleResultVO matched = RuleResultVO.builder().ruleCode("R001").build();
      RuleResultVO other = RuleResultVO.builder().ruleCode("R002").build();
      RuleResultVO mapped = RuleResultVO.builder().ruleCode("R001").build();
      when(ruleEngine.evaluate(any(RuleContextVO.class))).thenReturn(List.of(matched, other));
      when(literuleWebConverter.entityToVO(matched)).thenReturn(mapped);

      mockMvc.perform(post("/internal/literule/rules/evaluate")
              .param("ruleCode", "R001")
              .contentType(CONTENT_TYPE)
              .content("{\"amount\":1200}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.length()").value(1))
          .andExpect(jsonPath("$.data[0].ruleCode").value("R001"));

      org.mockito.ArgumentCaptor<RuleContextVO> contextCaptor =
          org.mockito.ArgumentCaptor.forClass(RuleContextVO.class);
      verify(ruleEngine).evaluate(contextCaptor.capture());
      org.assertj.core.api.Assertions.assertThat(contextCaptor.getValue().getFacts())
          .containsEntry("amount", 1200);
      org.assertj.core.api.Assertions.assertThat(contextCaptor.getValue().getScenario())
          .isEqualTo("DEFAULT");
      org.assertj.core.api.Assertions.assertThat(contextCaptor.getValue().getSource())
          .isEqualTo("feign-internal");
      verify(literuleWebConverter).entityToVO(matched);
      verify(literuleWebConverter, never()).entityToVO(other);
    }

    @Test
    @DisplayName("returns every evaluated result when ruleCode is absent")
    void should_return_all_results() throws Exception {
      RuleResultVO first = RuleResultVO.builder().ruleCode("R001").build();
      RuleResultVO second = RuleResultVO.builder().ruleCode("R002").build();
      when(ruleEngine.evaluate(any(RuleContextVO.class))).thenReturn(List.of(first, second));
      when(literuleWebConverter.entityToVO(first)).thenReturn(first);
      when(literuleWebConverter.entityToVO(second)).thenReturn(second);

      mockMvc.perform(post("/internal/literule/rules/evaluate")
              .param("scenario", "BUDGET_CHECK")
              .contentType(CONTENT_TYPE)
              .content("{\"amount\":1200}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.length()").value(2));

      org.mockito.ArgumentCaptor<RuleContextVO> contextCaptor =
          org.mockito.ArgumentCaptor.forClass(RuleContextVO.class);
      verify(ruleEngine).evaluate(contextCaptor.capture());
      org.assertj.core.api.Assertions.assertThat(contextCaptor.getValue().getScenario())
          .isEqualTo("BUDGET_CHECK");
    }

    @Test
    @DisplayName("rejects a request without the required JSON body")
    void should_reject_missing_body() throws Exception {
      mockMvc.perform(post("/internal/literule/rules/evaluate")
              .param("ruleCode", "R001")
              .contentType(CONTENT_TYPE))
          .andExpect(status().isBadRequest());

      verify(ruleEngine, never()).evaluate(any());
      verify(literuleWebConverter, never()).entityToVO(any());
    }

    @Test
    @DisplayName("rejects malformed facts instead of accepting a null body")
    void should_reject_malformed_facts() throws Exception {
      mockMvc.perform(post("/internal/literule/rules/evaluate")
              .contentType(CONTENT_TYPE)
              .content("{\"amount\":"))
          .andExpect(status().isBadRequest());

      verify(ruleEngine, never()).evaluate(any());
    }
  }
}
