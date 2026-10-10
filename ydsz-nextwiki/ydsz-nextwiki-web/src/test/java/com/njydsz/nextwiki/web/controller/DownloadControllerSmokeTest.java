package com.njydsz.nextwiki.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.nextwiki.server.metrics.NextwikiMetrics;
import com.njydsz.nextwiki.server.service.DownloadApplicationService;
import com.njydsz.nextwiki.server.service.DownloadApplicationService.SignedDownloadContext;

/**
 * {@link DownloadController} Smoke Test。
 *
 * <p>验证签名 URL 下载端点可正确装配并返回 HTTP 200（二进制流输出），防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DownloadControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private DownloadApplicationService downloadApplicationService;

  @org.mockito.Mock
  private NextwikiMetrics nextwikiMetrics;

  @org.mockito.Mock
  private IFileStorage fileStorage;

  @org.mockito.InjectMocks
  private DownloadController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/download/signed/{sign}")
  class DownloadBySignedUrl {

    @Test
    @DisplayName("should return 200 when download by signed URL succeeds")
    void should_return_200_when_download_by_signed_url_succeeds() throws Exception {
      SignedDownloadContext context = SignedDownloadContext.builder()
          .storage(fileStorage)
          .storageKey("file/test.txt")
          .build();
      when(downloadApplicationService.resolveSignedDownload("test-sign", 1234567890L))
          .thenReturn(context);
      when(fileStorage.downloadAsStream(null, "file/test.txt"))
          .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

      mockMvc.perform(get("/nextwiki/download/signed/test-sign")
              .param("expires", "1234567890"))
          .andExpect(status().isOk());
    }
  }
}
