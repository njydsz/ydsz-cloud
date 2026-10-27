package com.njydsz.system.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.query.VariablePageQuery;
import com.njydsz.system.server.service.VariableService;

/**
 * {@link VariableController} Smoke Test。
 *
 * <p>验证系统变量查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class VariableControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private VariableService variableService;

  @InjectMocks
  private VariableController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /variable/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      when(variableService.page(any(VariablePageQuery.class)))
          .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));

      mockMvc.perform(get("/variable/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /variable/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      when(variableService.getById("var-001")).thenReturn(null);

      mockMvc.perform(get("/variable/{id}", "var-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /variable/key/{variableKey}")
  class GetByKey {

    @Test
    @DisplayName("should return 200 when get by key succeeds")
    void should_return_200_when_get_by_key_succeeds() throws Exception {
      when(variableService.getVariableValue("finance.current_fiscal_year")).thenReturn(null);

      mockMvc.perform(get("/variable/key/{variableKey}", "finance.current_fiscal_year"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  /**
   * 异常信封反例：装配统一异常处理器后，Service 抛出的业务异常必须映射成
   * {@code YdszResponse{code,msg}} + 异常自带的 HTTP 状态码。
   *
   * <p>此前全仓 163 个 Smoke Test 一律用不带 advice 的 {@code standaloneSetup}，
   * 只证明"端点能返回 200"，错误码与状态码映射从未被执行过。
   */
  @Nested
  @DisplayName("异常信封（MvcExceptionHandler 已装配）")
  class ErrorEnvelope {

    private MockMvc adviceMockMvc;

    @BeforeEach
    void setUpAdvice() {
      adviceMockMvc =
          standaloneSetupWithAdvice(
              new MvcExceptionHandler(
                  new MockEnvironment(),
                  null,
                  ObjectProvider.empty(),
                  ObjectProvider.empty(),
                  ObjectProvider.empty()),
              controller);
    }

    @Test
    @DisplayName("业务异常 A01057 → HTTP 400 + $.code 原样透出")
    void businessError_shouldReturn400WithCode() throws Exception {
      when(variableService.getById("var-err"))
          .thenThrow(BusinessException.of(CoreExceptionCode.BUSINESS_ERROR));

      adviceMockMvc
          .perform(get("/variable/{id}", "var-err"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("A01057"));
    }

    @Test
    @DisplayName("系统异常 B01052 → HTTP 500（状态码取自异常本身，非处理器写死）")
    void systemError_shouldReturn500WithCode() throws Exception {
      when(variableService.getById("var-boom"))
          .thenThrow(BusinessException.of(CoreExceptionCode.SYSTEM_ERROR));

      adviceMockMvc
          .perform(get("/variable/{id}", "var-boom"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }
  }
}
