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
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.query.TenantPageQuery;
import com.njydsz.system.domain.vo.TenantVO;
import com.njydsz.system.server.service.TenantService;

/**
 * {@link TenantController} Smoke Test。
 *
 * <p>验证租户管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class TenantControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private TenantService tenantService;

  @org.mockito.InjectMocks
  private TenantController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /tenant/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      TenantVO vo = new TenantVO();
      vo.setId("tenant-001");
      vo.setTenantName("测试租户");
      PageResponse<List<TenantVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(tenantService.page(any(TenantPageQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/tenant/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /tenant/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      TenantVO vo = new TenantVO();
      vo.setId("tenant-001");
      vo.setTenantName("测试租户");
      when(tenantService.getById("tenant-001")).thenReturn(vo);

      mockMvc.perform(get("/tenant/{id}", "tenant-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /tenant/accessible")
  class ListAccessible {

    @Test
    @DisplayName("should return 200 when list accessible tenants succeeds")
    void should_return_200_when_list_accessible_succeeds() throws Exception {
      when(tenantService.listAccessibleTenants()).thenReturn(List.of());

      mockMvc.perform(get("/tenant/accessible"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
