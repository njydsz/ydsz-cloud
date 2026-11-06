package com.njydsz.generator.web.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.generator.service.TemplateImportExportService;
import com.njydsz.generator.vo.TemplateZipVO;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ImportExportController} Smoke Test。
 *
 * <p>验证模板导入导出端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class ImportExportControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private TemplateImportExportService importExportService;

  @BeforeEach
  void setUp() {
    importExportService = mock(TemplateImportExportService.class);
    ImportExportController controller = new ImportExportController(importExportService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /generator/import-export/export")
  class Export {

    @Test
    @DisplayName("should return 200 when export succeeds")
    void should_return_200_when_export_succeeds() throws Exception {
      TemplateZipVO zip = new TemplateZipVO();
      zip.setFileName("templates.zip");
      zip.setData(new byte[] {1, 2, 3});
      when(importExportService.exportZip("group-001")).thenReturn(zip);

      mockMvc.perform(get("/generator/import-export/export").param("groupId", "group-001"))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("POST /generator/import-export/import")
  class Import {

    @Test
    @DisplayName("should return 200 when import succeeds")
    void should_return_200_when_import_succeeds() throws Exception {
      when(importExportService.importZip("group-001", new byte[] {1, 2, 3}, false)).thenReturn(3);

      MockMultipartFile file =
          new MockMultipartFile("file", "templates.zip", "application/zip", new byte[] {1, 2, 3});

      mockMvc.perform(
              multipart("/generator/import-export/import")
                  .file(file)
                  .param("groupId", "group-001")
                  .param("overwrite", "false"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
