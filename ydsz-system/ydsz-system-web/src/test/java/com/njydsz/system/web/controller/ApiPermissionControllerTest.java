package com.njydsz.system.web.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.query.ApiPermissionQuery;
import com.njydsz.system.domain.vo.ApiPermissionVO;
import com.njydsz.system.server.service.ApiPermissionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@link ApiPermissionController} HTTP contract tests. */
class ApiPermissionControllerTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock private ApiPermissionService apiPermissionService;

  @InjectMocks private ApiPermissionController controller;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
  }

  @Nested
  @DisplayName("GET /permission/api/page")
  class Page {

    @Test
    @DisplayName("binds query fields and returns serialized permission data")
    void should_bind_query_and_return_page() throws Exception {
      ApiPermissionVO vo = new ApiPermissionVO();
      vo.setId("perm-1");
      vo.setApiCode("sys:config:list");
      vo.setApiName("config-list");
      vo.setHttpMethod("GET");
      vo.setUrlPattern("/config/page");
      vo.setControllerClass("com.njydsz.system.web.controller.ConfigController");
      vo.setMethodName("page");
      vo.setDescription("query config");
      vo.setStatus("ENABLED");

      when(apiPermissionService.page(any(ApiPermissionQuery.class)))
          .thenReturn(PageResponse.success(1L, 2L, 10L, List.of(vo)));

      mockMvc.perform(
              get("/permission/api/page")
                  .param("apiCode", "config")
                  .param("apiName", "list")
                  .param("controllerClass", "ConfigController")
                  .param("status", "ENABLED")
                  .param("pageNum", "2")
                  .param("pageSize", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.pageNum").value(2))
          .andExpect(jsonPath("$.data.pageSize").value(10))
          .andExpect(jsonPath("$.data.data[0].id").value("perm-1"))
          .andExpect(jsonPath("$.data.data[0].apiCode").value("sys:config:list"))
          .andExpect(jsonPath("$.data.data[0].httpMethod").value("GET"))
          .andExpect(jsonPath("$.data.data[0].urlPattern").value("/config/page"));

      ArgumentCaptor<ApiPermissionQuery> queryCaptor =
          ArgumentCaptor.forClass(ApiPermissionQuery.class);
      verify(apiPermissionService).page(queryCaptor.capture());
      ApiPermissionQuery query = queryCaptor.getValue();
      assertThat(query.getApiCode()).isEqualTo("config");
      assertThat(query.getApiName()).isEqualTo("list");
      assertThat(query.getControllerClass()).isEqualTo("ConfigController");
      assertThat(query.getStatus()).isEqualTo("ENABLED");
      assertThat(query.getPageNum()).isEqualTo(2);
      assertThat(query.getPageSize()).isEqualTo(10);
    }
  }

  @Nested
  @DisplayName("GET /permission/api/{id}")
  class GetById {

    @Test
    @DisplayName("binds path variable and returns null-safe success envelope")
    void should_get_by_id() throws Exception {
      when(apiPermissionService.getById("perm-1")).thenReturn(null);

      mockMvc.perform(get("/permission/api/{id}", "perm-1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());
    }
  }

  @Nested
  @DisplayName("POST /permission/api/scan")
  class Scan {

    @Test
    @DisplayName("returns the number of registered permissions")
    void should_trigger_scan() throws Exception {
      when(apiPermissionService.scanAndRegister()).thenReturn(7);

      mockMvc.perform(post("/permission/api/scan"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(7));
    }
  }

  @Nested
  @DisplayName("POST /permission/api/{id}/enable and /disable")
  class EnableDisable {

    @Test
    @DisplayName("enable delegates path variable to service")
    void should_enable() throws Exception {
      when(apiPermissionService.enable("perm-1")).thenReturn(true);

      mockMvc.perform(post("/permission/api/{id}/enable", "perm-1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("disable delegates path variable to service")
    void should_disable() throws Exception {
      when(apiPermissionService.disable("perm-1")).thenReturn(false);

      mockMvc.perform(post("/permission/api/{id}/disable", "perm-1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(false));
    }
  }

  @Nested
  @DisplayName("DELETE /permission/api/{id}")
  class Remove {

    @Test
    @DisplayName("remove delegates path variable to service")
    void should_remove() throws Exception {
      when(apiPermissionService.removeById("perm-1")).thenReturn(true);

      mockMvc.perform(delete("/permission/api/{id}", "perm-1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));
    }
  }

  @Nested
  @DisplayName("negative HTTP cases")
  class NegativeCases {

    @Test
    @DisplayName("invalid page number is rejected by query validation")
    void should_reject_invalid_page_number() throws Exception {
      mockMvc.perform(get("/permission/api/page").param("pageNum", "0"))
          .andExpect(status().isBadRequest());

      verify(apiPermissionService, never()).page(any(ApiPermissionQuery.class));
    }

    @Test
    @DisplayName("service failure does not return a success envelope")
    void should_not_claim_success_on_service_failure() throws Exception {
      when(apiPermissionService.getById("perm-boom"))
          .thenThrow(new IllegalStateException("permission store unavailable"));

      mockMvc.perform(get("/permission/api/{id}", "perm-boom"))
          .andExpect(status().isInternalServerError());
    }
  }
}
