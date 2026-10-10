package com.njydsz.userinfo.web.controller;

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
import com.njydsz.userinfo.domain.query.RolePageQuery;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.server.service.RoleService;

/**
 * {@link RoleController} Smoke Test。
 *
 * <p>验证角色管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RoleControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RoleService roleService;

  @org.mockito.InjectMocks
  private RoleController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /role/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      RoleVO vo = new RoleVO();
      vo.setId("role-001");
      vo.setRoleCode("ADMIN");
      vo.setRoleName("管理员");
      PageResponse<List<RoleVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(roleService.page(any(RolePageQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/role/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /role/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      RoleVO vo = new RoleVO();
      vo.setId("role-001");
      vo.setRoleCode("ADMIN");
      vo.setRoleName("管理员");
      when(roleService.getById("role-001")).thenReturn(vo);

      mockMvc.perform(get("/role/{id}", "role-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /role/list")
  class ListAll {

    @Test
    @DisplayName("should return 200 when list all succeeds")
    void should_return_200_when_list_all_succeeds() throws Exception {
      when(roleService.list()).thenReturn(java.util.List.of());

      mockMvc.perform(get("/role/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
