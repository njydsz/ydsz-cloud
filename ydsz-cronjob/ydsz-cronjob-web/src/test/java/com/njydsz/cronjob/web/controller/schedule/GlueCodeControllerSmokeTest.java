package com.njydsz.cronjob.web.controller.schedule;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.exception.handler.ValidationExceptionHandler;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.domain.vo.GlueCodeVO;
import com.njydsz.cronjob.server.service.schedule.GlueCodeService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlueCodeController} Smoke Test。
 *
 * <p>覆盖 GLUE 在线编码端点 {@code /cronjob/glue/**}： 保存 / 最新版本 / 版本列表 / 回滚 / 在线测试 / 语法校验 / 模板 / 版本对比。
 *
 * <p>验证请求体绑定与 {@code @NotBlank} 校验负例（装配 {@link ValidationExceptionHandler} 后 {@code A01053}）、 {@code language} 默认值、以及 {@code validate} 端点对 {@code testCode} 结果的映射。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class GlueCodeControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;
  private MockMvc adviceMockMvc;

  @org.mockito.Mock
  private GlueCodeService glueCodeService;

  @org.mockito.InjectMocks
  private GlueCodeController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    adviceMockMvc =
        standaloneSetupWithAdvice(
            new Object[] {
              new MvcExceptionHandler(
                  new MockEnvironment(), null, absentProvider(), absentProvider(), absentProvider()),
              new ValidationExceptionHandler(
                  new MockEnvironment(), null, absentProvider(), absentProvider())
            },
            controller);
  }

  /**
   * 构造一个「容器里没有这个 bean」的 {@code ObjectProvider}，用于脱离 Spring 容器手工装配 advice。
   *
   * @param <T> Provider 承载的 bean 类型
   * @return 取不到任何 bean 的 provider
   */
  private static <T> ObjectProvider<T> absentProvider() {
    return new ObjectProvider<>() {
      @Override
      public T getIfAvailable() {
        return null;
      }

      @Override
      public T getIfUnique() {
        return null;
      }
    };
  }

  private GlueCodeVO glueCodeVo() {
    GlueCodeVO vo = new GlueCodeVO();
    vo.setId("glue-001");
    vo.setJobId("job-001");
    vo.setVersion(3);
    vo.setLanguage("GROOVY");
    vo.setSourceCode("return 1");
    return vo;
  }

  @Nested
  @DisplayName("POST /cronjob/glue/save")
  class Save {

    @Test
    @DisplayName("should bind save request and return new version VO")
    void should_save() throws Exception {
      when(glueCodeService.save("job-001", "return 1", "GROOVY", "first"))
          .thenReturn(glueCodeVo());

      mockMvc.perform(
              post("/cronjob/glue/save")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"jobId\":\"job-001\",\"sourceCode\":\"return 1\",\"language\":\"GROOVY\",\"remark\":\"first\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.version").value(3))
          .andExpect(jsonPath("$.data.language").value("GROOVY"));

      verify(glueCodeService).save("job-001", "return 1", "GROOVY", "first");
    }

    @Test
    @DisplayName("should return A01053 + HTTP 400 when sourceCode is blank")
    void should_reject_blank_source() throws Exception {
      adviceMockMvc
          .perform(
              post("/cronjob/glue/save")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"jobId\":\"job-001\",\"sourceCode\":\"\",\"language\":\"GROOVY\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("A01053"));

      verify(glueCodeService, never()).save(any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("GET /cronjob/glue/latest|versions")
  class Query {

    @Test
    @DisplayName("should return latest glue code by jobId")
    void should_return_latest() throws Exception {
      when(glueCodeService.getLatest("job-001")).thenReturn(glueCodeVo());

      mockMvc.perform(get("/cronjob/glue/latest").param("jobId", "job-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.version").value(3));
    }

    @Test
    @DisplayName("should return version list ordered by service")
    void should_return_versions() throws Exception {
      GlueCodeVO v1 = glueCodeVo();
      v1.setVersion(1);
      GlueCodeVO v2 = glueCodeVo();
      v2.setVersion(2);
      when(glueCodeService.listVersions("job-001")).thenReturn(List.of(v2, v1));

      mockMvc.perform(get("/cronjob/glue/versions").param("jobId", "job-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].version").value(2))
          .andExpect(jsonPath("$.data[1].version").value(1));
    }

    @Test
    @DisplayName("should return 400 when jobId param missing")
    void should_reject_missing_job_id() throws Exception {
      mockMvc.perform(get("/cronjob/glue/latest")).andExpect(status().is4xxClientError());
    }
  }

  @Nested
  @DisplayName("POST /cronjob/glue/rollback")
  class Rollback {

    @Test
    @DisplayName("should bind rollback request and return new version")
    void should_rollback() throws Exception {
      when(glueCodeService.rollback("job-001", 2)).thenReturn(glueCodeVo());

      mockMvc.perform(
              post("/cronjob/glue/rollback")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"jobId\":\"job-001\",\"version\":2}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.version").value(3));

      verify(glueCodeService).rollback("job-001", 2);
    }

    @Test
    @DisplayName("should return A01053 when version is null")
    void should_reject_null_version() throws Exception {
      adviceMockMvc
          .perform(
              post("/cronjob/glue/rollback")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"jobId\":\"job-001\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("A01053"));

      verify(glueCodeService, never()).rollback(any(), any());
    }
  }

  @Nested
  @DisplayName("POST /cronjob/glue/test|validate")
  class TestAndValidate {

    @Test
    @DisplayName("should return testCode result map")
    void should_test() throws Exception {
      when(glueCodeService.testCode("return 1", "GROOVY", "{}"))
          .thenReturn(Map.of("success", true, "result", "1", "durationMs", 12));

      mockMvc.perform(
              post("/cronjob/glue/test")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"sourceCode\":\"return 1\",\"language\":\"GROOVY\",\"paramsJson\":\"{}\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.success").value(true))
          .andExpect(jsonPath("$.data.result").value("1"));
    }

    @Test
    @DisplayName("should map testCode failure into valid=false payload")
    void should_validate_failure() throws Exception {
      when(glueCodeService.testCode(eq("bad code"), eq("GROOVY"), isNull()))
          .thenReturn(Map.of("success", false, "error", "groovy compile error"));

      mockMvc.perform(
              post("/cronjob/glue/validate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"sourceCode\":\"bad code\",\"language\":\"GROOVY\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.valid").value(false))
          .andExpect(jsonPath("$.data.errors").value("groovy compile error"));
    }

    @Test
    @DisplayName("should map testCode success into valid=true payload")
    void should_validate_success() throws Exception {
      when(glueCodeService.testCode(eq("return 1"), eq("GROOVY"), isNull()))
          .thenReturn(Map.of("success", true));

      mockMvc.perform(
              post("/cronjob/glue/validate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"sourceCode\":\"return 1\",\"language\":\"GROOVY\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.valid").value(true));
    }
  }

  @Nested
  @DisplayName("GET /cronjob/glue/template|diff")
  class TemplateAndDiff {

    @Test
    @DisplayName("should default language to GROOVY and return template map")
    void should_return_template_default_language() throws Exception {
      when(glueCodeService.getCodeTemplate("GROOVY"))
          .thenReturn(Map.of("template", "return 1", "language", "GROOVY"));

      mockMvc.perform(get("/cronjob/glue/template"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.template").value("return 1"));

      verify(glueCodeService).getCodeTemplate("GROOVY");
    }

    @Test
    @DisplayName("should bind versionA/versionB and return diff map")
    void should_return_diff() throws Exception {
      when(glueCodeService.diffVersions("job-001", 1, 2))
          .thenReturn(Map.of("versionA", 1, "versionB", 2, "diff", "line-diff"));

      mockMvc.perform(
              get("/cronjob/glue/diff")
                  .param("jobId", "job-001")
                  .param("versionA", "1")
                  .param("versionB", "2"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.diff").value("line-diff"));
    }
  }
}
