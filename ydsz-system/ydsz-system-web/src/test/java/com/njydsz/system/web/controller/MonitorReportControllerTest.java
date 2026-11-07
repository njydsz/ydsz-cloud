package com.njydsz.system.web.controller;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.dto.MonitorErrorBatchDTO;
import com.njydsz.system.domain.dto.MonitorErrorDTO;
import com.njydsz.system.domain.dto.MonitorWebVitalBatchDTO;
import com.njydsz.system.domain.dto.MonitorWebVitalDTO;
import com.njydsz.system.server.service.MonitorReportService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@link MonitorReportController} frontend telemetry contract tests. */
class MonitorReportControllerTest extends BaseControllerMockTest {

  private static final int MAX_SOURCEMAP_BYTES = 20 * 1024 * 1024;

  private MockMvc mockMvc;

  @Mock private MonitorReportService monitorReportService;

  @InjectMocks private MonitorReportController controller;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
  }

  @Test
  @DisplayName("POST /monitor/error binds batch body and records errors")
  void should_report_errors() throws Exception {
    MonitorErrorBatchDTO batch = new MonitorErrorBatchDTO();
    MonitorErrorDTO error = new MonitorErrorDTO();
    error.setType("window");
    error.setMessage("boom");
    batch.setErrors(List.of(error));

    mockMvc.perform(
            post("/monitor/error")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().toJson(batch)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data").doesNotExist());

    ArgumentCaptor<MonitorErrorBatchDTO> captor =
        ArgumentCaptor.forClass(MonitorErrorBatchDTO.class);
    verify(monitorReportService).recordErrors(captor.capture());
    assertThat(captor.getValue().getErrors()).hasSize(1);
    assertThat(captor.getValue().getErrors().get(0).getType()).isEqualTo("window");
  }

  @Test
  @DisplayName("POST /monitor/web-vitals binds alert parameter and records vitals")
  void should_report_web_vitals() throws Exception {
    MonitorWebVitalBatchDTO batch = new MonitorWebVitalBatchDTO();
    MonitorWebVitalDTO vital = new MonitorWebVitalDTO();
    vital.setName("LCP");
    vital.setValue(1800D);
    vital.setRating("good");
    batch.setVitals(List.of(vital));

    mockMvc.perform(
            post("/monitor/web-vitals")
                .param("alert", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().toJson(batch)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

    verify(monitorReportService).recordWebVitals(batch, true);
  }

  @Test
  @DisplayName("POST /v1/monitor/sourcemaps uploads raw bytes with query parameters")
  void should_upload_sourcemap() throws Exception {
    byte[] content = "{\"version\":3}".getBytes();
    when(monitorReportService.storeSourcemap("release-1", "app.js.map", content))
        .thenReturn("https://files.example.com/sourcemaps/release-1/app.js.map");

    mockMvc.perform(
            post("/v1/monitor/sourcemaps")
                .param("release", "release-1")
                .param("file", "dist/app.js.map")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(content))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data").value(
            "https://files.example.com/sourcemaps/release-1/app.js.map"));

    verify(monitorReportService).storeSourcemap("release-1", "dist/app.js.map", content);
  }

  @Test
  @DisplayName("POST /monitor/sourcemaps supports the versionless compatibility path")
  void should_upload_sourcemap_without_version_segment() throws Exception {
    byte[] content = "{\"version\":3}".getBytes();
    when(monitorReportService.storeSourcemap("release-2", "app.js.map", content))
        .thenReturn("https://files.example.com/sourcemaps/release-2/app.js.map");

    mockMvc.perform(
            post("/monitor/sourcemaps")
                .param("release", "release-2")
                .param("file", "app.js.map")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(content))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
  }

  @Test
  @DisplayName("oversized sourcemap returns BAD_REQUEST without storage access")
  void should_reject_oversized_sourcemap() throws Exception {
    byte[] content = new byte[MAX_SOURCEMAP_BYTES + 1];

    mockMvc
        .perform(
            post("/monitor/sourcemaps")
                .param("release", "release-1")
                .param("file", "app.js.map")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(content))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("A10001"));

    verify(monitorReportService, never())
        .storeSourcemap(any(String.class), any(String.class), any(byte[].class));
  }

  @Test
  @DisplayName("empty error batch is rejected by validation")
  void should_reject_empty_error_batch() throws Exception {
    MonitorErrorBatchDTO batch = new MonitorErrorBatchDTO();
    batch.setErrors(List.of());

    mockMvc
        .perform(
            post("/monitor/error")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().toJson(batch)))
        .andExpect(status().isBadRequest());

    verify(monitorReportService, never()).recordErrors(any(MonitorErrorBatchDTO.class));
  }

  @Test
  @DisplayName("more than 50 errors is rejected by validation")
  void should_reject_too_many_errors() throws Exception {
    MonitorErrorBatchDTO batch = new MonitorErrorBatchDTO();
    List<MonitorErrorDTO> errors = new ArrayList<>();
    for (int index = 0; index < 51; index++) {
      errors.add(new MonitorErrorDTO());
    }
    batch.setErrors(errors);

    mockMvc
        .perform(
            post("/monitor/error")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().toJson(batch)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("missing alert parameter defaults to false")
  void should_default_alert_to_false() throws Exception {
    MonitorWebVitalBatchDTO batch = new MonitorWebVitalBatchDTO();
    MonitorWebVitalDTO vital = new MonitorWebVitalDTO();
    vital.setName("CLS");
    batch.setVitals(List.of(vital));

    mockMvc.perform(
            post("/monitor/web-vitals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().toJson(batch)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

    verify(monitorReportService).recordWebVitals(batch, false);
  }

  @Test
  @DisplayName("missing sourcemap query parameter is rejected")
  void should_reject_missing_sourcemap_parameter() throws Exception {
    mockMvc
        .perform(
            post("/monitor/sourcemaps")
                .param("file", "app.js.map")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content("{}".getBytes()))
        .andExpect(status().isBadRequest());

    verify(monitorReportService, never())
        .storeSourcemap(any(String.class), any(String.class), any(byte[].class));
  }
}
