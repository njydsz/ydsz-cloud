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
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.query.TenantPlanPageQuery;
import com.njydsz.system.server.service.TenantPlanMenuService;
import com.njydsz.system.server.service.TenantPlanService;

/**
 * {@link TenantPlanController} Smoke Test。
 *
 * <p>验证租户套餐查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class TenantPlanControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private TenantPlanService planService;

  @Mock
  private TenantPlanMenuService planMenuService;

  @InjectMocks
  private TenantPlanController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /tenant-plan/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      when(planService.page(any(TenantPlanPageQuery.class)))
          .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));

      mockMvc.perform(get("/tenant-plan/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /tenant-plan/list")
  class ListAll {

    @Test
    @DisplayName("should return 200 when list all succeeds")
    void should_return_200_when_list_all_succeeds() throws Exception {
      when(planService.listAll()).thenReturn(List.of());

      mockMvc.perform(get("/tenant-plan/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /tenant-plan/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      when(planService.getById("plan-001")).thenReturn(null);

      mockMvc.perform(get("/tenant-plan/{id}", "plan-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /tenant-plan/{planId}/menus")
  class ListMenus {

    @Test
    @DisplayName("should return 200 when list menus succeeds")
    void should_return_200_when_list_menus_succeeds() throws Exception {
      when(planMenuService.listByPlanId("plan-001")).thenReturn(List.of());

      mockMvc.perform(get("/tenant-plan/{planId}/menus", "plan-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
