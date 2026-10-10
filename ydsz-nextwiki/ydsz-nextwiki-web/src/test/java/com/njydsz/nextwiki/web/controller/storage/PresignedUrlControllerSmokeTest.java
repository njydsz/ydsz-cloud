package com.njydsz.nextwiki.web.controller.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link PresignedUrlController} Smoke Test。
 *
 * <p>验证存储直传预签名 URL 生成端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class PresignedUrlControllerSmokeTest extends BaseControllerMockTest {

  private static final String OBJECT_KEY = "uploads/test-file.txt";

  private MockMvc mockMvc;

  private IFileStorage fileStorage;

  private PresignedUrlController controller;

  @BeforeEach
  void setUp() {
    fileStorage = mock(IFileStorage.class);
    controller = new PresignedUrlController(fileStorage);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /nextwiki/storage/presigned-upload")
  class GenerateUploadUrl {

    @Test
    @DisplayName("应返回 200 当生成上传预签名 URL 成功时")
    void should_return_200_when_generate_upload_url_succeeds() throws Exception {
      String presignedUrl = "https://storage.example.com/bucket/" + OBJECT_KEY + "?token=xxx";

      when(fileStorage.generatePresignedUploadUrl(any(), any(), any())).thenReturn(presignedUrl);

      String requestBody = "{\"objectKey\":\"" + OBJECT_KEY + "\",\"expireSeconds\":3600}";

      mockMvc.perform(post("/nextwiki/storage/presigned-upload")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody)
              .header(AuthHeaderConstants.X_USER_ID, "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /nextwiki/storage/presigned-download")
  class GenerateDownloadUrl {

    @Test
    @DisplayName("应返回 200 当生成下载预签名 URL 成功时")
    void should_return_200_when_generate_download_url_succeeds() throws Exception {
      String presignedUrl = "https://storage.example.com/bucket/" + OBJECT_KEY + "?token=yyy";

      when(fileStorage.generatePresignedUrl(any(), any())).thenReturn(presignedUrl);

      String requestBody = "{\"objectKey\":\"" + OBJECT_KEY + "\",\"expireSeconds\":3600}";

      mockMvc.perform(post("/nextwiki/storage/presigned-download")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody)
              .header(AuthHeaderConstants.X_USER_ID, "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
