package com.njydsz.userinfo.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.server.auth.ScimPatchHandler;
import com.njydsz.userinfo.server.config.ScimProperties;
import com.njydsz.userinfo.server.service.UserAccountService;

/**
 * {@link ScimController} Smoke Test。
 *
 * <p>验证 SCIM 标准协议查询端点可正确返回 HTTP 200。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class ScimControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    UserAccountService userAccountService = org.mockito.Mockito.mock(UserAccountService.class);
    ScimProperties scimProperties = new ScimProperties();
    scimProperties.setAllowCreate(true);
    ScimPatchHandler scimPatchHandler = org.mockito.Mockito.mock(ScimPatchHandler.class);
    when(userAccountService.list()).thenReturn(List.of());
    ScimController controller = new ScimController(userAccountService, scimProperties, scimPatchHandler);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /scim/v2/Users")
  class ListUsers {

    @Test
    @DisplayName("should return 200 when list users succeeds")
    void should_return_200_when_list_users_succeeds() throws Exception {
      mockMvc.perform(get("/scim/v2/Users").param("startIndex", "1").param("count", "20"))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("GET /scim/v2/ServiceProviderConfig")
  class GetServiceProviderConfig {

    @Test
    @DisplayName("should return 200 when service provider config query succeeds")
    void should_return_200_when_service_provider_config_query_succeeds() throws Exception {
      mockMvc.perform(get("/scim/v2/ServiceProviderConfig"))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("GET /scim/v2/ResourceTypes")
  class GetResourceTypes {

    @Test
    @DisplayName("should return 200 when resource types query succeeds")
    void should_return_200_when_resource_types_query_succeeds() throws Exception {
      mockMvc.perform(get("/scim/v2/ResourceTypes"))
          .andExpect(status().isOk());
    }
  }
}
