package com.njydsz.userinfo.web.controller;

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

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
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
 * <p>Mockito 严格桩（STRICT_STUBS）要求每个 stub 都被所属端点消费，因此桩定义在各
 * {@code @Test} 内部，{@code setUp()} 仅装配 MockMvc。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class InternalApiControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private UserAccountService userAccountService;

  @Mock
  private DepartmentService departmentService;

  @Mock
  private RoleService roleService;

  @Mock
  private PostService postService;

  @Mock
  private CompanyService companyService;

  @Mock
  private WorkflowApproverCacheService workflowCacheService;

  @InjectMocks
  private InternalApiController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /internal/user/info")
  class GetUserInfo {

    @Test
    @DisplayName("should return 200 when user info query succeeds")
    void should_return_200_when_user_info_query_succeeds() throws Exception {
      when(userAccountService.getById("user-001")).thenReturn(new UserAccountVO());

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
      when(departmentService.tree()).thenReturn(List.of());

      mockMvc.perform(get("/internal/dept/tree"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /internal/dept/list")
  class GetDeptList {

    @Test
    @DisplayName("should return 200 when dept list query succeeds")
    void should_return_200_when_dept_list_query_succeeds() throws Exception {
      DepartmentVO dept = new DepartmentVO();
      dept.setId("dept-001");
      dept.setDeptName("研发部");
      when(departmentService.list()).thenReturn(List.of(dept));

      mockMvc.perform(get("/internal/dept/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].deptName").value("研发部"));
    }
  }

  @Nested
  @DisplayName("GET /internal/user/role-codes")
  class ListRoleCodes {

    @Test
    @DisplayName("should return 200 when role codes query succeeds")
    void should_return_200_when_role_codes_query_succeeds() throws Exception {
      when(userAccountService.listRoleCodesByUserId("user-001")).thenReturn(List.of("PM"));

      mockMvc.perform(get("/internal/user/role-codes").param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0]").value("PM"));
    }
  }

  @Nested
  @DisplayName("GET /internal/user/list-by-role")
  class ListUserIdsByRole {

    @Test
    @DisplayName("should return 200 when user ids by role query succeeds")
    void should_return_200_when_user_ids_by_role_query_succeeds() throws Exception {
      when(workflowCacheService.listUserIdsByRoleCode("PM")).thenReturn(List.of("user-100"));

      mockMvc.perform(get("/internal/user/list-by-role").param("roleCode", "PM"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0]").value("user-100"));
    }
  }

  @Nested
  @DisplayName("GET /internal/user/dept-ids")
  class ListDeptIds {

    @Test
    @DisplayName("should return 200 when dept ids query succeeds")
    void should_return_200_when_dept_ids_query_succeeds() throws Exception {
      when(userAccountService.listDeptIdsByUserId("user-001")).thenReturn(List.of("dept-001"));

      mockMvc.perform(get("/internal/user/dept-ids").param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0]").value("dept-001"));
    }
  }

  @Nested
  @DisplayName("GET /internal/user/leader")
  class GetLeaderByUserId {

    @Test
    @DisplayName("should return 200 when leader query succeeds")
    void should_return_200_when_leader_query_succeeds() throws Exception {
      when(workflowCacheService.getLeaderByUserId("user-001")).thenReturn("user-002");

      mockMvc.perform(get("/internal/user/leader").param("userId", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("user-002"));
    }
  }

  @Nested
  @DisplayName("GET /internal/user/list-by-position")
  class ListUserIdsByPosition {

    @Test
    @DisplayName("should return 200 when user ids by position query succeeds")
    void should_return_200_when_user_ids_by_position_query_succeeds() throws Exception {
      when(workflowCacheService.listUserIdsByPositionCode("PM")).thenReturn(List.of("user-100"));

      mockMvc.perform(get("/internal/user/list-by-position").param("positionCode", "PM"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0]").value("user-100"));
    }
  }

  @Nested
  @DisplayName("GET /internal/dept/leader-by-id")
  class GetDeptLeaderById {

    @Test
    @DisplayName("should return 200 when dept leader by id query succeeds")
    void should_return_200_when_dept_leader_by_id_query_succeeds() throws Exception {
      when(workflowCacheService.getDeptLeaderByDeptId("dept-001")).thenReturn("user-009");

      mockMvc.perform(get("/internal/dept/leader-by-id").param("deptId", "dept-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("user-009"));
    }
  }

  @Nested
  @DisplayName("GET /internal/dept/leader-by-code")
  class GetDeptLeaderByCode {

    @Test
    @DisplayName("should return 200 when dept leader by code query succeeds")
    void should_return_200_when_dept_leader_by_code_query_succeeds() throws Exception {
      when(workflowCacheService.getDeptLeaderByDeptCode("TECH")).thenReturn("user-010");

      mockMvc.perform(get("/internal/dept/leader-by-code").param("deptCode", "TECH"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("user-010"));
    }
  }

  @Nested
  @DisplayName("GET /internal/user/verify-password")
  class VerifyPassword {

    @Test
    @DisplayName("should return 200 when verify password succeeds")
    void should_return_200_when_verify_password_succeeds() throws Exception {
      when(userAccountService.verifyPassword("user-001", "Secret123")).thenReturn(true);

      mockMvc.perform(
              get("/internal/user/verify-password")
                  .param("userId", "user-001")
                  .param("password", "Secret123"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));
    }
  }
}
