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
import com.njydsz.userinfo.domain.query.UserAccountPageQuery;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.service.LoginHistoryService;
import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.server.service.UserExcelService;
import com.njydsz.userinfo.server.service.UserLifecycleService;
import com.njydsz.userinfo.server.auth.SensitiveVerifyService;

/**
 * {@link UserAccountController} Smoke Test。
 *
 * <p>验证用户管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class UserAccountControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private UserAccountService userAccountService;

  @org.mockito.Mock
  private UserExcelService userExcelService;

  @org.mockito.Mock
  private LoginHistoryService loginHistoryService;

  @org.mockito.Mock
  private SensitiveVerifyService sensitiveVerifyService;

  @org.mockito.Mock
  private UserLifecycleService lifecycleService;

  @org.mockito.InjectMocks
  private UserAccountController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /user/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      UserAccountVO vo = new UserAccountVO();
      vo.setId("user-001");
      vo.setUsername("admin");
      PageResponse<List<UserAccountVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(userAccountService.page(any(UserAccountPageQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/user/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /user/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      UserAccountVO vo = new UserAccountVO();
      vo.setId("user-001");
      vo.setUsername("admin");
      when(userAccountService.getById("user-001")).thenReturn(vo);

      mockMvc.perform(get("/user/{id}", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /user/list")
  class ListAll {

    @Test
    @DisplayName("should return 200 when list all succeeds")
    void should_return_200_when_list_all_succeeds() throws Exception {
      when(userAccountService.list()).thenReturn(java.util.List.of());

      mockMvc.perform(get("/user/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
