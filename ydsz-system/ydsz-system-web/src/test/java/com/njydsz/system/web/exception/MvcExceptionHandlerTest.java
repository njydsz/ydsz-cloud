package com.njydsz.system.web.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MvcExceptionHandler 集成测试 — 验证全局异常处理器返回正确的 HTTP 状态码和错误码结构。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>BusinessException → 业务错误码 + HTTP 200（YdszResponse.error）</li>
 *   <li>MissingServletRequestParameterException → 400 Bad Request</li>
 *   <li>未知异常 → 500 Internal Server Error</li>
 *   <li>@Valid 校验失败 → 400 参数错误</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.05
 */
@SpringBootTest(
    classes = {SystemApplication.class, MvcExceptionHandlerTest.ExceptionThrowingControllerTestConfig.class},
    properties = {"spring.main.allow-bean-definition-overriding=true"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DisplayName("MvcExceptionHandler - 异常处理集成测试")
class MvcExceptionHandlerTest {

  /** 测试请求 DTO（含 @NotBlank 校验注解）。 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class TestRequestDto {
    @jakarta.validation.constraints.NotBlank(message = "{validation.name_not_blank}")
    private String name;
  }

  /** 测试控制器配置（仅在测试 Context 中注册的 Bean）。 */
  @TestConfiguration
  static class ExceptionThrowingControllerTestConfig {

    @Bean
    @Primary
    ExceptionThrowingController exceptionThrowingController() {
      return new ExceptionThrowingController();
    }
  }

  /** 测试专用控制器 — 通过不同端点触发不同异常。 */
  @RestController
  @RequestMapping("/test/exception")
  static class ExceptionThrowingController {

    @GetMapping("/business")
    public String throwBusinessException() {
      throw new BusinessException(CoreExceptionCode.SYSTEM_ERROR, "测试业务异常");
    }

    @GetMapping("/missing-param")
    public String throwMissingParam(@RequestParam(required = true) String requiredParam) {
      return requiredParam;
    }

    @GetMapping("/unknown")
    public String throwUnknownException() {
      throw new IllegalStateException("模拟未知系统异常");
    }

    @PostMapping("/validation")
    public String throwValidation(@RequestBody @jakarta.validation.Valid TestRequestDto dto) {
      return dto.getName();
    }
  }

  @Autowired private MockMvc mockMvc;

  @Nested
  @DisplayName("业务异常场景")
  class BusinessExceptionScenario {

    @Test
    @DisplayName("BusinessException 应返回 YdszResponse.error 结构，HTTP 200，错误码正确")
    void businessException_shouldReturnYdszResponseErrorStructure() throws Exception {
      mockMvc
          .perform(get("/test/exception/business"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.code").value("B99999"))
          .andExpect(jsonPath("$.msg").exists());
    }
  }

  @Nested
  @DisplayName("参数异常场景")
  class ParameterExceptionScenario {

    @Test
    @DisplayName("缺少必填参数应返回 400 Bad Request")
    void missingParam_shouldReturn400() throws Exception {
      mockMvc
          .perform(get("/test/exception/missing-param"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("@Valid 校验失败应返回 400 参数错误")
    void validationFail_shouldReturn400() throws Exception {
      // 传空 JSON，触发 @NotBlank 校验
      mockMvc
          .perform(
              post("/test/exception/validation")
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("未知异常场景")
  class UnknownExceptionScenario {

    @Test
    @DisplayName("未捕获异常应返回 500 Internal Server Error")
    void unknownException_shouldReturn500() throws Exception {
      mockMvc
          .perform(get("/test/exception/unknown"))
          .andExpect(status().isInternalServerError());
    }
  }

  /** 静态断言 — 验证 YdszResponse 错误码枚举存在。 */
  @Test
  @DisplayName("CoreExceptionCode.SYSTEM_ERROR 枚举应存在且错误码非空")
  void systemErrorCode_shouldExistAndBeNonEmpty() {
    assertThat(CoreExceptionCode.SYSTEM_ERROR.getCode()).isNotEmpty();
    assertThat(CoreExceptionCode.SYSTEM_ERROR.getCode()).isEqualTo("B99999");
  }
}
