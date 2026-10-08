package com.njydsz.system.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.server.service.DictService;
import com.njydsz.system.web.controller.DictController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@link DictController} MockMvc 集成测试（standalone 模式）。
 *
 * <p>覆盖场景：分页查询接口存在性、参数校验失败（400）、业务异常错误码包装。
 *
 * @author ydsz
 * @since 26.10.06
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DictController - 字典类型 MockMvc 测试")
class DictControllerTest {

  private MockMvc mockMvc;

  @Mock
  private DictService dictService;

  @InjectMocks
  private DictController controller;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new TestExceptionHandler())
        .build();
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("GET /dict/type/page 应返回 200 且包含 code 字段")
  void page_shouldReturnOkWithCode() throws Exception {
    DictTypeVO vo = new DictTypeVO();
    vo.setTypeCode("order_status");
    PageResponse<List<DictTypeVO>> pageResponse = PageResponse.success(1L, 1L, 10L, List.of(vo));

    when(dictService.page(any(DictPageQuery.class))).thenReturn(pageResponse);

    mockMvc
        .perform(get("/dict/type/page").param("pageNum", "1").param("pageSize", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  @Test
  @DisplayName("POST /dict/type 缺少必填字段 typeCode 应返回 400 + 参数校验错误码")
  void save_withBlankTypeCode_shouldReturn400WithErrorBody() throws Exception {
    mockMvc
        .perform(
            post("/dict/type")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeName\":\"test\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(CoreExceptionCode.PARAM_ERROR.getCode()))
        .andExpect(jsonPath("$.msg").exists());
  }

  @Test
  @DisplayName("GET /dict/type/{id} 业务异常应返回 YdszResponse 错误码包装")
  void getById_whenBusinessException_shouldReturnErrorWrapped() throws Exception {
    when(dictService.getById("not-exist-id"))
        .thenThrow(new BusinessException(CoreExceptionCode.DATA_NOT_FOUND));

    mockMvc
        .perform(get("/dict/type/not-exist-id"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(CoreExceptionCode.DATA_NOT_FOUND.getCode()));
  }

  /**
   * 轻量级异常处理器（standalone 模式专用），提供与生产环境一致的错误响应格式。
   */
  @RestControllerAdvice
  static class TestExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public YdszResponse<Void> handleBusinessException(BusinessException e) {
      return YdszResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public YdszResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
      String message = e.getBindingResult().getFieldErrors().stream()
          .findFirst()
          .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
          .orElse("参数校验失败");
      return YdszResponse.error(CoreExceptionCode.PARAM_ERROR.getCode(), message);
    }
  }
}
