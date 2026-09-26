package com.njydsz.system.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.excel.spring.ExcelWebSupport;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.service.ConfigBatchService;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.web._support.WebMvcTestApplication;
import tools.jackson.databind.ObjectMapper;

/**
 * ConfigController MockMvc integration test.
 */
@SpringBootTest(classes = WebMvcTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfigControllerMockMvcTest {

  @MockBean private ConfigService configService;
  @MockBean private ConfigBatchService configBatchService;
  @MockBean private ExcelWebSupport excelWebSupport;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private static final String TENANT_ID = "1";

  private MockHttpServletRequestBuilder getJson(String url) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Tenant-Id", TENANT_ID);
  }

  @Nested
  @DisplayName("GET /config/public")
  class GetPublicConfigs {

    @Test
    @DisplayName("should return 200 with public config list")
    void shouldReturnPublicConfigsWith200() throws Exception {
      ConfigVO vo = new ConfigVO();
      vo.setId("pub-1");
      vo.setConfigKey("theme.color");
      vo.setConfigValue("#FFFFFF");
      vo.setConfigGroup("ui");
      when(configService.listPublicConfigs()).thenReturn(List.of(vo));

      MvcResult result = mockMvc.perform(getJson("/config/public")).andReturn();

      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      YdszResponse<?> resp = objectMapper.readValue(result.getResponse().getContentAsString(), YdszResponse.class);
      assertThat(resp.getCode()).isEqualTo("A00000");
      verify(configService).listPublicConfigs();
    }

    @Test
    @DisplayName("should return empty list when no public configs")
    void shouldReturnEmptyList() throws Exception {
      when(configService.listPublicConfigs()).thenReturn(List.of());

      MvcResult result = mockMvc.perform(getJson("/config/public")).andReturn();

      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      YdszResponse<?> resp = objectMapper.readValue(result.getResponse().getContentAsString(), YdszResponse.class);
      assertThat(resp.getCode()).isEqualTo("A00000");
    }
  }

  @Nested
  @DisplayName("GET /config/page")
  class GetPage {

    @ParameterizedTest(name = "pageNum={0}")
    @ValueSource(strings = {"-1", "0"})
    @DisplayName("should handle invalid pageNum without 500")
    void shouldHandleInvalidPageNumWithout500(String invalidPageNum) throws Exception {
      when(configService.page(any(ConfigPageQuery.class))).thenReturn(PageResponse.empty(0L, 20L));

      MvcResult result = mockMvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/config/page")
                  .param("pageNum", invalidPageNum)
                  .param("pageSize", "20")
                  .accept(MediaType.APPLICATION_JSON)
                  .header("X-Tenant-Id", TENANT_ID))
          .andReturn();

      assertThat(result.getResponse().getStatusCode()).isNotEqualTo(500);
    }

    @Test
    @DisplayName("should delegate to Service.page")
    void shouldDelegateToService() throws Exception {
      when(configService.page(any(ConfigPageQuery.class))).thenReturn(PageResponse.empty(0L, 20L));

      MvcResult result = mockMvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/config/page")
                  .param("pageNum", "1")
                  .param("pageSize", "20")
                  .accept(MediaType.APPLICATION_JSON)
                  .header("X-Tenant-Id", TENANT_ID))
          .andReturn();

      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      verify(configService).page(any(ConfigPageQuery.class));
    }
  }

  @Nested
  @DisplayName("GET /config/key/{configKey}")
  class GetByKey {

    @Test
    @DisplayName("should return 200 with config value")
    void shouldReturnConfigValueWith200() throws Exception {
      when(configService.getConfigValue("theme.color")).thenReturn("#FFFFFF");

      MvcResult result = mockMvc.perform(getJson("/config/key/theme.color")).andReturn();

      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      YdszResponse<?> resp = objectMapper.readValue(result.getResponse().getContentAsString(), YdszResponse.class);
      assertThat(resp.getCode()).isEqualTo("A00000");
      verify(configService).getConfigValue("theme.color");
    }

    @Test
    @DisplayName("should return 200 with null when key not found")
    void shouldReturn200WhenKeyNotFound() throws Exception {
      when(configService.getConfigValue(anyString())).thenReturn(null);

      MvcResult result = mockMvc.perform(getJson("/config/key/nonexistent.key")).andReturn();

      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      YdszResponse<?> resp = objectMapper.readValue(result.getResponse().getContentAsString(), YdszResponse.class);
      assertThat(resp.getCode()).isEqualTo("A00000");
    }
  }

  @Nested
  @DisplayName("POST /config")
  class CreateConfig {

    @Test
    @DisplayName("should return 200 for valid request")
    void shouldReturn200ForValidRequest() throws Exception {
      when(configService.save(any(ConfigDTO.class))).thenReturn("new-config-id-001");
      String body = "{\"configKey\":\"test.timeout\",\"configValue\":\"30\",\"configGroup\":\"timeouts\",\"valueType\":\"NUMBER\",\"isPublic\":false,\"sort\":0}";

      MvcResult result = mockMvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/config")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .header("X-Tenant-Id", TENANT_ID))
          .andReturn();

      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      verify(configService).save(any(ConfigDTO.class));
    }

    @Test
    @DisplayName("should return 400 when missing required field")
    void shouldReturn400WhenMissingRequiredField() throws Exception {
      String body = "{\"configValue\":\"30\",\"configGroup\":\"timeouts\",\"valueType\":\"NUMBER\"}";

      MvcResult result = mockMvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/config")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .header("X-Tenant-Id", TENANT_ID))
          .andReturn();

      assertThat(result.getResponse().getStatusCode()).isEqualTo(400);
    }
  }
}