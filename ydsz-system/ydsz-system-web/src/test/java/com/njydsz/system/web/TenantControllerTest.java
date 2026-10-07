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
import com.njydsz.system.domain.query.TenantPageQuery;
import com.njydsz.system.domain.vo.TenantVO;
import com.njydsz.system.server.service.TenantService;
import com.njydsz.system.web.controller.TenantController;
import com.njydsz.system.web.handler.SystemExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link TenantController} MockMvc 集成测试。
 *
 * <p>覆盖场景：分页查询接口存在性、参数校验失败（400）、业务异常错误码包装。
 *
 * @author ydsz
 * @since 26.10.06
 */
@WebMvcTest(TenantController.class)
@Import(SystemExceptionHandler.class)
@DisplayName("TenantController - 租户管理 MockMvc 测试")
class TenantControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private TenantService tenantService;

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("GET /tenant/page 应返回 200 且包含 code 字段")
  void page_shouldReturnOkWithCode() throws Exception {
    TenantVO vo = new TenantVO();
    vo.setTenantCode("demo_tenant");
    PageResponse<List<TenantVO>> pageResponse = new PageResponse<>();
    pageResponse.setRecords(List.of(vo));
    pageResponse.setTotal(1L);

    when(tenantService.page(any(TenantPageQuery.class))).thenReturn(pageResponse);

    mockMvc
        .perform(get("/tenant/page").param("pageNum", "1").param("pageSize", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS))
        .andExpect(jsonPath("$.data.records[0].tenantCode").value("demo_tenant"));
  }

  @Test
  @DisplayName("POST /tenant 缺少必填字段 tenantCode 应返回 400 + 参数校验错误码")
  void save_withBlankTenantCode_shouldReturn400WithErrorBody() throws Exception {
    mockMvc
        .perform(
            post("/tenant")
                .contentType("application/json")
                .content("{\"tenantName\":\"demo\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(CoreExceptionCode.PARAM_ERROR.getCode()))
        .andExpect(jsonPath("$.msg").exists());
  }

  @Test
  @DisplayName("GET /tenant/accessible 业务异常应返回 YdszResponse 错误码包装")
  void listAccessible_whenBusinessException_shouldReturnErrorWrapped() throws Exception {
    when(tenantService.listAccessibleTenants())
        .thenThrow(new BusinessException(CoreExceptionCode.DATA_NOT_FOUND));

    mockMvc
        .perform(get("/tenant/accessible"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(CoreExceptionCode.DATA_NOT_FOUND.getCode()));
  }
}
