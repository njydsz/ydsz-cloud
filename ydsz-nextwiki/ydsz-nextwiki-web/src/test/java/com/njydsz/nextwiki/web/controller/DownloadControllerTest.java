package com.njydsz.nextwiki.web.controller;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.metrics.NextwikiMetrics;
import com.njydsz.nextwiki.server.service.DownloadApplicationService;
import com.njydsz.nextwiki.web.handler.NextwikiExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link DownloadController} full and range download HTTP contract test.
 */
class DownloadControllerTest extends BaseControllerMockTest {

  private static final String USER_ID = "user-001";

  private MockMvc mockMvc;
  private DownloadApplicationService downloadApplicationService;
  private NextwikiMetrics nextwikiMetrics;
  private IFileStorage fileStorage;

  @BeforeEach
  void setUp() {
    downloadApplicationService = mock(DownloadApplicationService.class);
    nextwikiMetrics = mock(NextwikiMetrics.class);
    fileStorage = mock(IFileStorage.class);
    DownloadController controller =
        new DownloadController(downloadApplicationService, nextwikiMetrics);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new NextwikiExceptionHandler(new MockEnvironment()))
        .build();
  }

  @Nested
  @DisplayName("POST /nextwiki/download/{nodeId}")
  class DownloadFile {

    @Test
    @DisplayName("prepares download, writes full content, sets headers, and records success")
    void should_download_full_file() throws Exception {
      FileNodeVO node = fileNode("node-001", "report.txt", "text/plain", 15L);
      when(downloadApplicationService.prepareDownload("node-001", USER_ID, "127.0.0.1"))
          .thenReturn(DownloadApplicationService.DownloadContext.builder()
              .node(node)
              .storage(fileStorage)
              .build());
      doAnswer(invocation -> {
        HttpServletResponse response = invocation.getArgument(2);
        response.getOutputStream().write("hello download".getBytes(StandardCharsets.UTF_8));
        return null;
      }).when(fileStorage).download(eq("bucket-001"), eq("key-001"), any(HttpServletResponse.class));

      mockMvc.perform(post("/nextwiki/download/node-001")
              .header("X-User-Id", USER_ID)
              .with(request -> {
                request.setRemoteAddr("127.0.0.1");
                return request;
              }))
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "15"))
          .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
              .string("hello download"));

      ArgumentCaptor<HttpServletResponse> responseCaptor =
          ArgumentCaptor.forClass(HttpServletResponse.class);
      verify(fileStorage).download(
          eq("bucket-001"), eq("key-001"), responseCaptor.capture());
      assertThat(responseCaptor.getValue().getStatus()).isEqualTo(200);
      verify(nextwikiMetrics).recordDownload();
    }

    @Test
    @DisplayName("handles Range bytes=5-9 and writes only the requested segment")
    void should_download_range() throws Exception {
      FileNodeVO node = fileNode("node-001", "report.txt", "text/plain", 20L);
      when(downloadApplicationService.prepareDownload("node-001", USER_ID, "127.0.0.1"))
          .thenReturn(DownloadApplicationService.DownloadContext.builder()
              .node(node)
              .storage(fileStorage)
              .build());
      when(fileStorage.downloadAsStream("bucket-001", "key-001"))
          .thenReturn(new java.io.ByteArrayInputStream(
              "0123456789abcdefghij".getBytes(StandardCharsets.UTF_8)));

      mockMvc.perform(post("/nextwiki/download/node-001")
              .header("X-User-Id", USER_ID)
              .header("Range", "bytes=5-9")
              .with(request -> {
                request.setRemoteAddr("127.0.0.1");
                return request;
              }))
          .andExpect(status().isPartialContent())
          .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 5-9/20"))
          .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "5"))
          .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
              .string("56789"));

      verify(nextwikiMetrics).recordDownload();
    }

    @Test
    @DisplayName("returns 416 when the requested range starts beyond the file")
    void should_reject_unsatisfiable_range() throws Exception {
      FileNodeVO node = fileNode("node-001", "report.txt", "text/plain", 20L);
      when(downloadApplicationService.prepareDownload("node-001", USER_ID, "127.0.0.1"))
          .thenReturn(DownloadApplicationService.DownloadContext.builder()
              .node(node)
              .storage(fileStorage)
              .build());

      mockMvc.perform(post("/nextwiki/download/node-001")
              .header("X-User-Id", USER_ID)
              .header("Range", "bytes=25-30")
              .with(request -> {
                request.setRemoteAddr("127.0.0.1");
                return request;
              }))
          .andExpect(status().isRequestedRangeNotSatisfiable())
          .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */20"));

      verify(fileStorage, never()).downloadAsStream(any(), any());
      verify(nextwikiMetrics, never()).recordDownload();
    }

    @Test
    @DisplayName("returns FILE_STORAGE_NOT_CONFIGURED and does not record success")
    void should_reject_missing_storage() throws Exception {
      FileNodeVO node = fileNode("node-001", "report.txt", "text/plain", 15L);
      when(downloadApplicationService.prepareDownload("node-001", USER_ID, "127.0.0.1"))
          .thenReturn(DownloadApplicationService.DownloadContext.builder()
              .node(node)
              .storage(null)
              .build());

      mockMvc.perform(post("/nextwiki/download/node-001")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("W01012"));

      verify(fileStorage, never()).download(any(), any(), any());
      verify(nextwikiMetrics, never()).recordDownload();
    }

    @Test
    @DisplayName("returns SYSTEM_ERROR for storage stream failure and does not record success")
    void should_not_record_metric_on_storage_failure() throws Exception {
      FileNodeVO node = fileNode("node-001", "report.txt", "text/plain", 15L);
      when(downloadApplicationService.prepareDownload("node-001", USER_ID, "127.0.0.1"))
          .thenReturn(DownloadApplicationService.DownloadContext.builder()
              .node(node)
              .storage(fileStorage)
              .build());
      doThrow(new IllegalStateException("storage stream failed"))
          .when(fileStorage).download(eq("bucket-001"), eq("key-001"), any());

      mockMvc.perform(post("/nextwiki/download/node-001")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));

      verify(nextwikiMetrics, never()).recordDownload();
    }

    @Test
    @DisplayName("converts range stream failure to FILE_DOWNLOAD_FAILED and records no success")
    void should_not_record_metric_on_range_failure() throws Exception {
      FileNodeVO node = fileNode("node-001", "report.txt", "text/plain", 20L);
      when(downloadApplicationService.prepareDownload("node-001", USER_ID, "127.0.0.1"))
          .thenReturn(DownloadApplicationService.DownloadContext.builder()
              .node(node)
              .storage(fileStorage)
              .build());
      when(fileStorage.downloadAsStream("bucket-001", "key-001"))
          .thenThrow(new IllegalStateException("range stream failed"));

      mockMvc.perform(post("/nextwiki/download/node-001")
              .header("X-User-Id", USER_ID)
              .header("Range", "bytes=0-5"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("W01013"));

      verify(nextwikiMetrics, never()).recordDownload();
    }
  }

  private FileNodeVO fileNode(String id, String name, String mimeType, long size) {
    return FileNodeVO.builder()
        .id(id)
        .name(name)
        .nodeType("file")
        .mimeType(mimeType)
        .size(size)
        .bucketName("bucket-001")
        .storageKey("key-001")
        .build();
  }
}
