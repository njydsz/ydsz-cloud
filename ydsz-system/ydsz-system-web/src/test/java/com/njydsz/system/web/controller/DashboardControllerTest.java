package com.njydsz.system.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.system.domain.vo.DashboardOverviewItemVO;
import com.njydsz.system.domain.vo.DashboardWorkspaceVO;
import com.njydsz.system.server.service.DashboardService;

/**
 * DashboardController 纯单元测试 — 直接实例化 Controller + Mock Service（无 Spring Context）。
 *
 * <p>验证：
 * <ul>
 *   <li>overview() 委托 dashboardService.overview() 并包装为 YdszResponse.success</li>
 *   <li>workspace() 委托 dashboardService.workspace() 并包装为 YdszResponse.success</li>
 *   <li>空数据场景返回 success 空集合</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.05
 */
@DisplayName("DashboardController - 工作台聚合 Controller 单元测试")
class DashboardControllerTest {

  private DashboardService dashboardService;
  private DashboardController controller;

  @BeforeEach
  void setUp() {
    dashboardService = mock(DashboardService.class);
    controller = new DashboardController(dashboardService);
  }

  @SuppressWarnings("unchecked")
  @Nested
  @DisplayName("overview()")
  class OverviewMethod {

    @Test
    @DisplayName("应将 Service 返回数据包装为 YdszResponse.success")
    void overview_shouldWrapServiceDataWithSuccessResponse() {
      DashboardOverviewItemVO item = new DashboardOverviewItemVO();
      item.setTitle("租户");
      item.setValue(10L);
      when(dashboardService.overview()).thenReturn(List.of(item));

      YdszResponse<List<DashboardOverviewItemVO>> response = controller.overview();

      assertThat(response).isNotNull();
      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getData()).hasSize(1);
      assertThat(response.getData().get(0).getTitle()).isEqualTo("租户");
      assertThat(response.getData().get(0).getValue()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Service 返回空集合时应包装为空 success 响应")
    void overview_withEmptyData_shouldReturnEmptySuccessResponse() {
      when(dashboardService.overview()).thenReturn(List.of());

      YdszResponse<List<DashboardOverviewItemVO>> response = controller.overview();

      assertThat(response).isNotNull();
      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getData()).isEmpty();
    }
  }

  @SuppressWarnings("unchecked")
  @Nested
  @DisplayName("workspace()")
  class WorkspaceMethod {

    @Test
    @DisplayName("应将 Service 返回数据包装为 YdszResponse.success")
    void workspace_shouldWrapServiceDataWithSuccessResponse() {
      DashboardWorkspaceVO vo = new DashboardWorkspaceVO();
      when(dashboardService.workspace()).thenReturn(vo);

      YdszResponse<DashboardWorkspaceVO> response = controller.workspace();

      assertThat(response).isNotNull();
      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getData()).isSameAs(vo);
    }
  }
}
