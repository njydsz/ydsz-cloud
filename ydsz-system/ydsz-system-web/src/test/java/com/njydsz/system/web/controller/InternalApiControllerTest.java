package com.njydsz.system.web.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.RequestMethod;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.server.service.AppInfoService;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.server.service.DictItemService;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@link InternalApiController} service-to-service HTTP contract tests. */
class InternalApiControllerTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock private ConfigService configService;
  @Mock private DictItemService dictItemService;
  @Mock private AppInfoService appInfoService;

  @InjectMocks private InternalApiController controller;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
  }

  @Test
  @DisplayName("POST /internal/config/get binds body and returns config value")
  void should_get_config() throws Exception {
    when(configService.getConfigValue("ydsz.workflow.sla")).thenReturn("48");

    mockMvc.perform(
            post("/internal/config/get")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"ydsz.workflow.sla\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data").value("48"));

    verify(configService).getConfigValue("ydsz.workflow.sla");
  }

  @Test
  @DisplayName("POST /internal/dict/item returns value and null-safe data")
  void should_get_dict_item() throws Exception {
    DictItemVO item = new DictItemVO();
    item.setItemValue("启用");
    when(dictItemService.getByTypeAndCode("status", "enabled")).thenReturn(item);

    mockMvc.perform(
            post("/internal/dict/item")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeCode\":\"status\",\"itemCode\":\"enabled\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data").value("启用"));

    when(dictItemService.getByTypeAndCode("status", "missing")).thenReturn(null);
    mockMvc.perform(
            post("/internal/dict/item")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeCode\":\"status\",\"itemCode\":\"missing\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  @DisplayName("POST /internal/dict/list maps item values and preserves empty result")
  void should_list_dict_items() throws Exception {
    DictItemVO first = new DictItemVO();
    first.setItemValue("启用");
    DictItemVO second = new DictItemVO();
    second.setItemValue("停用");
    when(dictItemService.listEnabledByTypeCode("status")).thenReturn(List.of(first, second));

    mockMvc.perform(
            post("/internal/dict/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeCode\":\"status\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data[0]").value("启用"))
        .andExpect(jsonPath("$.data[1]").value("停用"));

    when(dictItemService.listEnabledByTypeCode("empty")).thenReturn(List.of());
    mockMvc.perform(
            post("/internal/dict/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeCode\":\"empty\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  @DisplayName("POST /internal/app/validate delegates credentials to service")
  void should_validate_client() throws Exception {
    when(appInfoService.validateClient("client-a", "secret-a")).thenReturn(true);

    mockMvc.perform(
            post("/internal/app/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appKey\":\"client-a\",\"appSecret\":\"secret-a\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data").value(true));
  }

  @Test
  @DisplayName("invalid config body returns validation envelope and skips service")
  void should_reject_invalid_config_body() throws Exception {
    mockMvc
        .perform(
            post("/internal/config/get")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"\"}"))
        .andExpect(status().isBadRequest());

    verify(configService, never()).getConfigValue("");
  }

  @Test
  @DisplayName("malformed JSON returns bad request")
  void should_reject_malformed_json() throws Exception {
    mockMvc
        .perform(
            post("/internal/app/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("missing request body returns bad request")
  void should_reject_missing_body() throws Exception {
    mockMvc
        .perform(post("/internal/dict/list").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("unsupported method returns method-not-allowed")
  void should_reject_unsupported_method() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/internal/config/get")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  @DisplayName("service failure skips a successful response")
  void should_not_claim_success_on_service_failure() throws Exception {
    when(configService.getConfigValue("broken-key"))
        .thenThrow(new IllegalStateException("config store unavailable"));

    mockMvc
        .perform(
            post("/internal/config/get")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"broken-key\"}"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  @DisplayName("unsupported request method is rejected before service invocation")
  void should_reject_request_method_without_service_call() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                    RequestMethod.PATCH, "/internal/dict/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeCode\":\"status\"}"))
        .andExpect(status().isMethodNotAllowed());

    verify(dictItemService, never()).listEnabledByTypeCode("status");
  }
}
