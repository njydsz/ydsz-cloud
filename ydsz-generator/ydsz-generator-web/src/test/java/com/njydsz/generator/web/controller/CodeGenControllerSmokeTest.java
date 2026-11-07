package com.njydsz.generator.web.controller;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.generator.enums.ConflictStrategyEnum;
import com.njydsz.generator.query.GenCodeGenerateQuery;
import com.njydsz.generator.service.CodeGenService;
import com.njydsz.generator.vo.CodePreviewVO;
import com.njydsz.generator.vo.GenResultVO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link CodeGenController} Smoke Test。
 *
 * <p>验证代码生成端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * <p><b>补充覆盖（26.11.06）：</b>原文件只有一个 preview 用例，未覆盖下载、正式生成、
 * 全量生成与失败路径。本次补齐：
 *
 * <ul>
 *   <li>preview — 响应字段（fileName/filePath/content/isConflict）与空结果
 *   <li>preview/zip — 二进制下载、Content-Disposition、ZIP 条目内容、空内容条目跳过
 *   <li>generate — 响应字段（historyId/fileCount/successCount/skipCount/failCount）、
 *       冲突策略透传与非法值 400
 *   <li>generate/all — 枚举绑定（含非法枚举值 400）与默认值
 *   <li>负例 — 缺必填参数 400、Service 抛业务异常时错误码透传
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class CodeGenControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private CodeGenService codeGenService;

  private CodeGenController controller;

  @BeforeEach
  void setUp() {
    codeGenService = mock(CodeGenService.class);
    controller = new CodeGenController(codeGenService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /generator/code/preview")
  class Preview {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void should_return_200_when_preview_succeeds() throws Exception {
      CodePreviewVO vo = CodePreviewVO.builder()
          .fileName("UserController.java")
          .filePath("controller/UserController.java")
          .build();
      when(codeGenService.preview(anyString(), anyString(), anyString())).thenReturn(List.of(vo));

      mockMvc.perform(get("/generator/code/preview")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("tableName", "ydsz_user"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }

    @Test
    @DisplayName("预览条目应透出 fileName/filePath/content/isConflict 全部字段")
    void should_expose_all_preview_fields() throws Exception {
      CodePreviewVO vo = CodePreviewVO.builder()
          .fileName("User.java")
          .filePath("domain/entity/User.java")
          .content("public class User {}")
          .isConflict(true)
          .build();
      when(codeGenService.preview("ds-001", "grp-001", "ydsz_user"))
          .thenReturn(List.of(vo));

      mockMvc.perform(get("/generator/code/preview")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("tableName", "ydsz_user"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].fileName").value("User.java"))
          .andExpect(jsonPath("$.data[0].filePath").value("domain/entity/User.java"))
          .andExpect(jsonPath("$.data[0].content").value("public class User {}"))
          .andExpect(jsonPath("$.data[0].isConflict").value(true));
    }

    @Test
    @DisplayName("无预览结果时应返回空数组而非 null")
    void should_return_empty_array_when_no_preview() throws Exception {
      when(codeGenService.preview(anyString(), anyString(), anyString())).thenReturn(List.of());

      mockMvc.perform(get("/generator/code/preview")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("tableName", "ydsz_user"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").isArray())
          .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("缺少必填参数时应返回 400 且不调用 Service")
    void should_return_400_when_required_param_missing() throws Exception {
      mockMvc.perform(get("/generator/code/preview")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001"))
          .andExpect(status().isBadRequest());

      verify(codeGenService, never()).preview(anyString(), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("GET /generator/code/preview/zip")
  class DownloadPreviewZip {

    @Test
    @DisplayName("应返回 200 + application/octet-stream + attachment 头")
    void should_return_zip_binary_with_attachment_header() throws Exception {
      when(codeGenService.preview(anyString(), anyString(), anyString())).thenReturn(List.of(
          CodePreviewVO.builder()
              .fileName("UserController.java")
              .content("public class UserController {}")
              .build()));

      mockMvc.perform(get("/generator/code/preview/zip")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("tableName", "ydsz_user"))
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
              org.hamcrest.Matchers.containsString("attachment")))
          .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
              MediaType.APPLICATION_OCTET_STREAM_VALUE));
    }

    @Test
    @DisplayName("ZIP 内应包含预览条目文件名与内容")
    void should_pack_preview_entries_into_zip() throws Exception {
      when(codeGenService.preview(anyString(), anyString(), anyString())).thenReturn(List.of(
          CodePreviewVO.builder()
              .fileName("User.java")
              .content("public class User {}")
              .build(),
          CodePreviewVO.builder()
              .fileName("Order.java")
              .content("public class Order {}")
              .build()));

      MvcResult result = mockMvc.perform(get("/generator/code/preview/zip")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("tableName", "ydsz_user"))
          .andExpect(status().isOk())
          .andReturn();

      byte[] body = result.getResponse().getContentAsByteArray();
      assertThat(zipEntryNames(body)).contains("User.java", "Order.java");
      assertThat(zipEntryContent(body, "User.java")).isEqualTo("public class User {}");
    }

    @Test
    @DisplayName("content 为空或 null 的条目应被跳过，不写入 ZIP")
    void should_skip_entries_with_blank_content() throws Exception {
      when(codeGenService.preview(anyString(), anyString(), anyString())).thenReturn(List.of(
          CodePreviewVO.builder().fileName("Empty.java").content("").build(),
          CodePreviewVO.builder().fileName("Null.java").content(null).build(),
          CodePreviewVO.builder()
              .fileName("Keep.java")
              .content("public class Keep {}")
              .build()));

      MvcResult result = mockMvc.perform(get("/generator/code/preview/zip")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("tableName", "ydsz_user"))
          .andExpect(status().isOk())
          .andReturn();

      byte[] body = result.getResponse().getContentAsByteArray();
      assertThat(zipEntryNames(body)).containsExactly("Keep.java");
    }

    @Test
    @DisplayName("fileName 为 null 时应回落为 unknown 条目名")
    void should_fallback_to_unknown_entry_name() throws Exception {
      when(codeGenService.preview(anyString(), anyString(), anyString())).thenReturn(List.of(
          CodePreviewVO.builder().fileName(null).content("body").build()));

      MvcResult result = mockMvc.perform(get("/generator/code/preview/zip")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("tableName", "ydsz_user"))
          .andExpect(status().isOk())
          .andReturn();

      assertThat(zipEntryNames(result.getResponse().getContentAsByteArray()))
          .containsExactly("unknown");
    }
  }

  @Nested
  @DisplayName("POST /generator/code/generate")
  class Generate {

    @Test
    @DisplayName("应返回 200 且透出 historyId/各计数")
    void should_return_200_with_result_fields() throws Exception {
      when(codeGenService.generate(any(GenCodeGenerateQuery.class)))
          .thenReturn(GenResultVO.builder()
              .historyId("his-001")
              .fileCount(12)
              .successCount(10)
              .skipCount(1)
              .failCount(1)
              .build());

      mockMvc.perform(post("/generator/code/generate")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"datasourceId\":\"ds-001\",\"templateGroupId\":\"grp-001\","
                  + "\"tableName\":\"ydsz_user\",\"outputDir\":\"/tmp/gen\","
                  + "\"conflictStrategy\":\"OVERRIDE\",\"triggeredBy\":\"tester\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.historyId").value("his-001"))
          .andExpect(jsonPath("$.data.fileCount").value(12))
          .andExpect(jsonPath("$.data.successCount").value(10))
          .andExpect(jsonPath("$.data.skipCount").value(1))
          .andExpect(jsonPath("$.data.failCount").value(1));
    }

    @Test
    @DisplayName("conflictStrategy 应原样透传给 Service")
    void should_pass_conflict_strategy_through() throws Exception {
      when(codeGenService.generate(any(GenCodeGenerateQuery.class)))
          .thenReturn(GenResultVO.builder().build());

      mockMvc.perform(post("/generator/code/generate")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"datasourceId\":\"ds-001\",\"templateGroupId\":\"grp-001\","
                  + "\"tableName\":\"ydsz_user\",\"outputDir\":\"/tmp/gen\","
                  + "\"conflictStrategy\":\"MERGE\"}"))
          .andExpect(status().isOk());

      ArgumentCaptor<GenCodeGenerateQuery> captor =
          ArgumentCaptor.forClass(GenCodeGenerateQuery.class);
      verify(codeGenService).generate(captor.capture());
      assertThat(captor.getValue().getConflictStrategy()).isEqualTo("MERGE");
      assertThat(captor.getValue().getTriggeredBy()).isNull();
    }

    @Test
    @DisplayName("非法 conflictStrategy 应返回 400 且不调用 Service")
    void should_return_400_for_invalid_conflict_strategy() throws Exception {
      mockMvc.perform(post("/generator/code/generate")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"datasourceId\":\"ds-001\",\"templateGroupId\":\"grp-001\","
                  + "\"tableName\":\"ydsz_user\",\"outputDir\":\"/tmp/gen\","
                  + "\"conflictStrategy\":\"BOGUS\"}"))
          .andExpect(status().isBadRequest());

      verify(codeGenService, never()).generate(any(GenCodeGenerateQuery.class));
    }

    @Test
    @DisplayName("缺少 outputDir 应返回 400（@NotBlank 校验）")
    void should_return_400_when_output_dir_missing() throws Exception {
      mockMvc.perform(post("/generator/code/generate")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"datasourceId\":\"ds-001\",\"templateGroupId\":\"grp-001\","
                  + "\"tableName\":\"ydsz_user\"}"))
          .andExpect(status().isBadRequest());

      verify(codeGenService, never()).generate(any(GenCodeGenerateQuery.class));
    }
  }

  @Nested
  @DisplayName("POST /generator/code/generate/all")
  class GenerateAll {

    @Test
    @DisplayName("应返回 200 且按枚举透传 conflictStrategy")
    void should_return_200_and_pass_enum_strategy() throws Exception {
      when(codeGenService.generateAll(anyString(), anyString(), anyString(),
          any(ConflictStrategyEnum.class), anyString()))
          .thenReturn(GenResultVO.builder().historyId("his-all").build());

      mockMvc.perform(post("/generator/code/generate/all")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("outputDir", "/tmp/gen")
              .param("conflictStrategy", "APPEND")
              .param("triggeredBy", "tester"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.historyId").value("his-all"));

      verify(codeGenService).generateAll(eq("ds-001"), eq("grp-001"), eq("/tmp/gen"),
          eq(ConflictStrategyEnum.APPEND), eq("tester"));
    }

    @Test
    @DisplayName("未传 conflictStrategy / triggeredBy 时应使用默认值 SKIP / system")
    void should_use_default_strategy_and_triggered_by() throws Exception {
      when(codeGenService.generateAll(anyString(), anyString(), anyString(),
          any(ConflictStrategyEnum.class), anyString()))
          .thenReturn(GenResultVO.builder().build());

      mockMvc.perform(post("/generator/code/generate/all")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("outputDir", "/tmp/gen"))
          .andExpect(status().isOk());

      verify(codeGenService).generateAll(eq("ds-001"), eq("grp-001"), eq("/tmp/gen"),
          eq(ConflictStrategyEnum.SKIP), eq("system"));
    }

    @Test
    @DisplayName("非法枚举值应返回 400 且不调用 Service")
    void should_return_400_for_invalid_enum_value() throws Exception {
      mockMvc.perform(post("/generator/code/generate/all")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("outputDir", "/tmp/gen")
              .param("conflictStrategy", "NOPE"))
          .andExpect(status().isBadRequest());

      verify(codeGenService, never()).generateAll(anyString(), anyString(), anyString(),
          any(ConflictStrategyEnum.class), anyString());
    }

    @Test
    @DisplayName("缺少 outputDir 应返回 400")
    void should_return_400_when_output_dir_missing() throws Exception {
      mockMvc.perform(post("/generator/code/generate/all")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001"))
          .andExpect(status().isBadRequest());

      verify(codeGenService, never()).generateAll(anyString(), anyString(), anyString(),
          any(ConflictStrategyEnum.class), anyString());
    }
  }

  @Nested
  @DisplayName("异常信封（MvcExceptionHandler 已装配）")
  class ErrorEnvelope {

    private MockMvc adviceMockMvc;

    @BeforeEach
    void setUpAdvice() {
      adviceMockMvc =
          standaloneSetupWithAdvice(
              new MvcExceptionHandler(
                  new MockEnvironment(),
                  null,
                  absentProvider(),
                  absentProvider(),
                  absentProvider()),
              controller);
    }

    @Test
    @DisplayName("Service 抛 BusinessException 应透传错误码 + HTTP 状态")
    void should_expose_business_error_code() throws Exception {
      when(codeGenService.preview(anyString(), anyString(), anyString()))
          .thenThrow(BusinessException.of(CoreExceptionCode.PARAM_ERROR));

      adviceMockMvc.perform(get("/generator/code/preview")
              .param("datasourceId", "ds-001")
              .param("templateGroupId", "grp-001")
              .param("tableName", "ydsz_user"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("A01052"));
    }

    @Test
    @DisplayName("Service 抛系统异常应返回 500 + 错误码")
    void should_expose_system_error_code() throws Exception {
      when(codeGenService.generate(any(GenCodeGenerateQuery.class)))
          .thenThrow(BusinessException.of(CoreExceptionCode.SYSTEM_ERROR));

      adviceMockMvc.perform(post("/generator/code/generate")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"datasourceId\":\"ds-001\",\"templateGroupId\":\"grp-001\","
                  + "\"tableName\":\"ydsz_user\",\"outputDir\":\"/tmp/gen\"}"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }
  }

  /**
   * "容器里没有这个 bean" 的 ObjectProvider，用于脱离 Spring 容器手工装配 advice。
   *
   * @param <T> Provider 承载的 bean 类型
   * @return 取不到任何 bean 的 provider
   */
  private static <T> org.springframework.beans.factory.ObjectProvider<T> absentProvider() {
    return new org.springframework.beans.factory.ObjectProvider<>() {
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

  /** 解析 ZIP 字节流，返回全部条目名（按出现顺序）。 */
  private static List<String> zipEntryNames(byte[] zipBytes) throws Exception {
    List<String> names = new java.util.ArrayList<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes),
        StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        names.add(entry.getName());
      }
    }
    return names;
  }

  /** 解析 ZIP 字节流，返回指定条目名的 UTF-8 内容。 */
  private static String zipEntryContent(byte[] zipBytes, String entryName) throws Exception {
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes),
        StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entryName.equals(entry.getName())) {
          return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
        }
      }
    }
    return null;
  }
}
