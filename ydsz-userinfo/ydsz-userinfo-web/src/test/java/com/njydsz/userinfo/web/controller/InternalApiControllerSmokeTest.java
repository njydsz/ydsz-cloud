package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.service.CompanyService;
import com.njydsz.userinfo.server.service.DepartmentService;
import com.njydsz.userinfo.server.service.PostService;
import com.njydsz.userinfo.server.service.RoleService;
import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.server.service.WorkflowApproverCacheService;

/**
 * {@link InternalApiController} Smoke Test。
 *
 * <p>验证内部 API 查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class InternalApiControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private UserAccountService userAccountService;

  @org.mockito.Mock
  private DepartmentService departmentService;

  @org.mockito.Mock
  private RoleService roleService;

  @org.mockito.Mock
  private PostService postService;

  @org.mockito.Mock
  private CompanyService companyService;

  @org.mockito.Mock
  private WorkflowApproverCacheService workflowCacheService;

  @org.mockito.InjectMocks
  private InternalApiController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(userAccountService.getById(anyString())).thenReturn(new UserAccountVO());
    when(departmentService.tree()).thenReturn(List.of());
    when(departmentService.list()).thenReturn(List.of());
    when(userAccountService.listRoleCodesByUserId(anyString())).thenReturn(List.of());
    when(userAccountService.listDeptIdsByUserId(anyString())).thenReturn(List.of());
    when(workflowCacheService.getLeaderByUserId(anyString())).thenReturn(null);
    when(workflowCacheService.listUserIdsByRoleCode(anyString())).thenReturn(List.of());
    when(workflowCacheService.listUserIdsByPositionCode(anyString())).thenReturn(List.of());
    when(workflowCacheService.getDeptLeaderByDeptId(anyString())).thenReturn(null);
    when(workflowCacheService.getDeptLeaderByDeptCode(anyString())).thenReturn(null);
    when(userAccountService.verifyPassword(anyString(), anyString())).thenReturn(false);
  }

  @Nested
  @DisplayName("GET /internal/user/info")
  class GetUserInfo {

    @Test
    @DisplayName("should return 200 when user info query succeeds")
    void should_return_200_when_user_info_query_succeeds() throws Exception {
      mockMvc.perform(get("/internal/user/info").param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /internal/dept/tree")
  class GetDeptTree {

    @Test
    @DisplayName("should return 200 when dept tree query succeeds")
    void should_return_200_when_dept_tree_query_succeeds() throws Exception {
      mockMvc.perform(get("/internal/dept/tree"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /internal/user/role-codes")
  class ListRoleCodes {

    @Test
    @DisplayName("should return 200 when role codes query succeeds")
    void should_return_200_when_role_codes_query_succeeds() throws Exception {
      mockMvc.perform(get("/internal/user/role-codes").param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
