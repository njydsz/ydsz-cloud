package com.njydsz.nextwiki.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.server.service.ChunkUploadApplicationService;

/**
 * {@link FileChunkController} Smoke Test。
 *
 * <p>验证分片上传端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class FileChunkControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private ChunkUploadApplicationService chunkUploadService;

  @org.mockito.InjectMocks
  private FileChunkController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/files/chunk/{uploadId}/uploaded-chunks")
  class ListUploadedChunks {

    @Test
    @DisplayName("should return 200 when list uploaded chunks succeeds")
    void should_return_200_when_list_uploaded_chunks_succeeds() throws Exception {
      when(chunkUploadService.getUploadedChunks("upload-001")).thenReturn(Set.of());

      mockMvc.perform(get("/nextwiki/files/chunk/upload-001/uploaded-chunks"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
