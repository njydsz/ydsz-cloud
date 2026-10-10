package com.njydsz.message.web.controller;

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
import com.njydsz.message.domain.vo.ErrorCodeVO;
import com.njydsz.message.server.service.error.MessageExceptionCodeRegistry;

/**
 * {@link MessageErrorCodesController} Smoke Test。
 *
 * <p>验证错误码元信息端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class MessageErrorCodesControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private MessageExceptionCodeRegistry errorCodeRegistry;

  @org.mockito.InjectMocks
  private MessageErrorCodesController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/error-codes")
  class ListAllErrorCodes {

    @Test
    @DisplayName("should return 200 when list error codes succeeds")
    void should_return_200_when_list_error_codes_succeeds() throws Exception {
      ErrorCodeVO vo = new ErrorCodeVO();
      vo.setCode("E00001");
      when(errorCodeRegistry.listAll()).thenReturn(List.of(vo));

      mockMvc.perform(get("/message/error-codes"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
