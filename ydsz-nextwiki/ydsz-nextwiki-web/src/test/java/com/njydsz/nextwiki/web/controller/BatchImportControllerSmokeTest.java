package com.njydsz.nextwiki.web.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.server.service.BatchImportApplicationService;

/**
 * {@link BatchImportController} Smoke Test。
 *
 * <p>验证批量导入端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class BatchImportControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private BatchImportApplicationService batchImportService;

  private BatchImportController controller;

  @BeforeEach
  void setUp() {
    batchImportService = mock(BatchImportApplicationService.class);
    controller = new BatchImportController(batchImportService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /nextwiki/import/batch-upload")
  class BatchUpload {

    @Test
    @DisplayName("应返回 200 当批量上传成功时")
    void should_return_200_when_batch_upload_succeeds() throws Exception {
      MockMultipartFile file = new MockMultipartFile("files", "test.txt",
          "text/plain", "hello world".getBytes());

      when(batchImportService.batchUpload(null, null, "user-001"))
          .thenReturn(BatchImportApplicationService.BatchImportResult.empty());

      mockMvc.perform(multipart("/nextwiki/import/batch-upload")
              .file(file)
              .header(AuthHeaderConstants.X_USER_ID, "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /nextwiki/import/zip")
  class ImportZip {

    @Test
    @DisplayName("应返回 200 当 ZIP 导入成功时")
    void should_return_200_when_import_zip_succeeds() throws Exception {
      MockMultipartFile zipFile = new MockMultipartFile("file", "archive.zip",
          "application/zip", "zip-content".getBytes());

      when(batchImportService.importFromZip(null, null, "user-001"))
          .thenReturn(BatchImportApplicationService.BatchImportResult.empty());

      mockMvc.perform(multipart("/nextwiki/import/zip")
              .file(zipFile)
              .header(AuthHeaderConstants.X_USER_ID, "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
