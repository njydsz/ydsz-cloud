package com.njydsz.userinfo.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.server.auth.LdapOrgSyncService;

/**
 * {@link LdapSyncController} Smoke Test。
 *
 * <p>验证 LDAP 同步状态查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class LdapSyncControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    LdapOrgSyncService ldapOrgSyncService = org.mockito.Mockito.mock(LdapOrgSyncService.class);
    LdapSyncController controller = new LdapSyncController(ldapOrgSyncService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /admin/ldap/sync/status")
  class GetStatus {

    @Test
    @DisplayName("should return 200 when status query succeeds")
    void should_return_200_when_status_query_succeeds() throws Exception {
      mockMvc.perform(get("/admin/ldap/sync/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
