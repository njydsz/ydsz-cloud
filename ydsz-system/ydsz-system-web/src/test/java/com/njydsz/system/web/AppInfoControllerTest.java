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
import com.njydsz.system.domain.query.AppInfoPageQuery;
import com.njydsz.system.domain.vo.AppInfoVO;
import com.njydsz.system.server.service.AppInfoService;
import com.njydsz.system.web.controller.AppInfoController;
import com.njydsz.system.web.handler.SystemExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AppInfoController} MockMvc 集成测试。
 *
 * <p>覆盖场景：分页查询、参数校验失败、业务异常错误码包装。
 *
 * @author ydsz
 * @since 26.10.06
 */
@WebMvcTest(AppInfoController.class)
@Import(SystemExceptionHandler.class)
@DisplayName("AppInfoController - 应用注册 MockMvc 测试")
class AppInfoControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AppInfoService appInfoService;

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("GET /app/page 应返回 200 且包含 code 字段")
  void page_shouldReturnOkWithCode() throws Exception {
    AppInfoVO vo = new AppInfoVO();
    vo.setAppCode("demo_app");
    PageResponse<List<AppInfoVO>> pageResponse = new PageResponse<>();
    pageResponse.setRecords(List.of(vo));
    pageResponse.setTotal(1L);

    when(appInfoService.page(any(AppInfoPageQuery.class))).thenReturn(pageResponse);

    mockMvc
        .perform(get("/app/page").param("pageNum", "1").param("pageSize", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS))
        .andExpect(jsonPath("$.data.records[0].appCode").value("demo_app"));
  }

  @Test
  @DisplayName("POST /app 缺少必填字段 appCode 应返回 400 + 参数校验错误码")
  void save_withBlankAppCode_shouldReturn400WithErrorBody() throws Exception {
    mockMvc
        .perform(
            post("/app")
                .contentType("application/json")
                .content("{\"appName\":\"demo\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(CoreExceptionCode.PARAM_ERROR.getCode()))
        .andExpect(jsonPath("$.msg").exists());
  }

  @Test
  @DisplayName("GET /app/{id} 业务异常应返回 YdszResponse 错误码包装")
  void getById_whenBusinessException_shouldReturnErrorWrapped() throws Exception {
    when(appInfoService.getById("not-exist-id"))
        .thenThrow(new BusinessException(CoreExceptionCode.DATA_NOT_FOUND));

    mockMvc
        .perform(get("/app/not-exist-id"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(CoreExceptionCode.DATA_NOT_FOUND.getCode()));
  }
}
