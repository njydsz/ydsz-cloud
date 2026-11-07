package com.njydsz.nextwiki.web.controller;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.service.ChunkUploadApplicationService;
import com.njydsz.nextwiki.web.handler.NextwikiExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FileChunkController} chunk upload lifecycle HTTP contract test.
 */
class FileChunkControllerSmokeTest extends BaseControllerMockTest {

  private static final String USER_ID = "user-001";

  private MockMvc mockMvc;
  private ChunkUploadApplicationService chunkUploadService;

  @BeforeEach
  void setUp() {
    chunkUploadService = mock(ChunkUploadApplicationService.class);
    FileChunkController controller = new FileChunkController(chunkUploadService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new NextwikiExceptionHandler(new MockEnvironment()))
        .build();
  }

  @Nested
  @DisplayName("POST /nextwiki/files/chunk/init")
  class InitChunkUpload {

    @Test
    @DisplayName("binds file metadata and optional parent, then returns session fields")
    void should_initialize_upload() throws Exception {
      ChunkUploadApplicationService.ChunkUploadInit init =
          ChunkUploadApplicationService.ChunkUploadInit.builder()
              .uploadId("upload-001")
              .totalChunks(3)
              .chunkSize(5_242_880L)
              .build();
      when(chunkUploadService.initChunkUpload("video.mp4", 12_582_912L, 3, "folder-001", USER_ID))
          .thenReturn(init);

      mockMvc.perform(post("/nextwiki/files/chunk/init")
              .param("fileName", "video.mp4")
              .param("fileSize", "12582912")
              .param("totalChunks", "3")
              .param("parentId", "folder-001")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.uploadId").value("upload-001"))
          .andExpect(jsonPath("$.data.totalChunks").value(3))
          .andExpect(jsonPath("$.data.chunkSize").value(5242880));

      verify(chunkUploadService).initChunkUpload(
          "video.mp4", 12_582_912L, 3, "folder-001", USER_ID);
    }

    @Test
    @DisplayName("passes null parentId for root initialization")
    void should_initialize_at_root() throws Exception {
      ChunkUploadApplicationService.ChunkUploadInit init =
          ChunkUploadApplicationService.ChunkUploadInit.builder()
              .uploadId("upload-root")
              .totalChunks(1)
              .chunkSize(1_024L)
              .build();
      when(chunkUploadService.initChunkUpload("tiny.txt", 1_024L, 1, null, USER_ID))
          .thenReturn(init);

      mockMvc.perform(post("/nextwiki/files/chunk/init")
              .param("fileName", "tiny.txt")
              .param("fileSize", "1024")
              .param("totalChunks", "1")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.uploadId").value("upload-root"));

      verify(chunkUploadService).initChunkUpload("tiny.txt", 1_024L, 1, null, USER_ID);
    }

    @Test
    @DisplayName("returns SYSTEM_ERROR when initialization fails")
    void should_return_error_when_init_fails() throws Exception {
      when(chunkUploadService.initChunkUpload(any(), eq(1L), eq(1), any(), eq(USER_ID)))
          .thenThrow(new IllegalStateException("redis unavailable"));

      mockMvc.perform(post("/nextwiki/files/chunk/init")
              .param("fileName", "tiny.txt")
              .param("fileSize", "1")
              .param("totalChunks", "1")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }
  }

  @Nested
  @DisplayName("POST /nextwiki/files/chunk/{uploadId}/{chunkNumber}")
  class UploadChunk {

    @Test
    @DisplayName("binds upload path, numeric chunk, multipart part, and owner header")
    void should_upload_chunk() throws Exception {
      MockMultipartFile chunk = new MockMultipartFile(
          "chunk", "chunk-2.bin", "application/octet-stream", "chunk-content".getBytes());

      mockMvc.perform(multipart("/nextwiki/files/chunk/upload-001/2")
              .file(chunk)
              .header("X-User-Id", USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(chunkUploadService).uploadChunk("upload-001", 2, chunk, USER_ID);
    }

    @Test
    @DisplayName("returns BAD REQUEST when the chunk part is absent")
    void should_reject_missing_chunk() throws Exception {
      mockMvc.perform(multipart("/nextwiki/files/chunk/upload-001/2")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isBadRequest());

      verify(chunkUploadService, never()).uploadChunk(any(), any(Integer.class), any(), any());
    }

    @Test
    @DisplayName("returns SYSTEM_ERROR when chunk persistence fails")
    void should_return_error_when_chunk_upload_fails() throws Exception {
      MockMultipartFile chunk = new MockMultipartFile(
          "chunk", "chunk-1.bin", "application/octet-stream", "content".getBytes());
      doThrow(new IllegalStateException("temporary disk full"))
          .when(chunkUploadService).uploadChunk("upload-001", 1, chunk, USER_ID);

      mockMvc.perform(multipart("/nextwiki/files/chunk/upload-001/1")
              .file(chunk)
              .header("X-User-Id", USER_ID))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }
  }

  @Nested
  @DisplayName("POST /nextwiki/files/chunk/{uploadId}/complete")
  class CompleteChunkUpload {

    @Test
    @DisplayName("binds uploadId and owner, then returns completed file node fields")
    void should_complete_upload() throws Exception {
      FileNodeVO completed = FileNodeVO.builder()
          .id("node-001")
          .name("video.mp4")
          .nodeType("file")
          .size(12_582_912L)
          .build();
      when(chunkUploadService.completeChunkUpload("upload-001", USER_ID)).thenReturn(completed);

      mockMvc.perform(post("/nextwiki/files/chunk/upload-001/complete")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("node-001"))
          .andExpect(jsonPath("$.data.name").value("video.mp4"))
          .andExpect(jsonPath("$.data.nodeType").value("file"))
          .andExpect(jsonPath("$.data.size").value(12582912));

      verify(chunkUploadService).completeChunkUpload("upload-001", USER_ID);
    }

    @Test
    @DisplayName("returns SYSTEM_ERROR when merge completion fails")
    void should_return_error_when_complete_fails() throws Exception {
      when(chunkUploadService.completeChunkUpload("upload-001", USER_ID))
          .thenThrow(new IllegalStateException("chunk 2 missing"));

      mockMvc.perform(post("/nextwiki/files/chunk/upload-001/complete")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }
  }

  @Nested
  @DisplayName("DELETE /nextwiki/files/chunk/{uploadId}")
  class AbortChunkUpload {

    @Test
    @DisplayName("binds uploadId and delegates cleanup")
    void should_abort_upload() throws Exception {
      mockMvc.perform(delete("/nextwiki/files/chunk/upload-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(chunkUploadService).abortChunkUpload("upload-001");
    }

    @Test
    @DisplayName("returns SYSTEM_ERROR when abort cleanup fails")
    void should_return_error_when_abort_fails() throws Exception {
      doThrow(new IllegalStateException("cleanup failed"))
          .when(chunkUploadService).abortChunkUpload("upload-001");

      mockMvc.perform(delete("/nextwiki/files/chunk/upload-001"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }
  }

  @Nested
  @DisplayName("GET /nextwiki/files/chunk/{uploadId}/uploaded-chunks")
  class UploadedChunks {

    @Test
    @DisplayName("binds uploadId and returns uploaded chunk numbers")
    void should_list_uploaded_chunks() throws Exception {
      when(chunkUploadService.getUploadedChunks("upload-001")).thenReturn(Set.of(1, 3));

      mockMvc.perform(get("/nextwiki/files/chunk/upload-001/uploaded-chunks"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.length()").value(2))
          .andExpect(jsonPath("$.data[0]").value(1))
          .andExpect(jsonPath("$.data[1]").value(3));

      verify(chunkUploadService).getUploadedChunks("upload-001");
    }

    @Test
    @DisplayName("returns an empty set for an unknown upload session")
    void should_return_empty_uploaded_chunks() throws Exception {
      when(chunkUploadService.getUploadedChunks("missing")).thenReturn(Set.of());

      mockMvc.perform(get("/nextwiki/files/chunk/missing/uploaded-chunks"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").isEmpty());
    }
  }

  @Nested
  @DisplayName("GET /nextwiki/files/chunk/{uploadId}/progress")
  class UploadProgress {

    @Test
    @DisplayName("binds uploadId and returns progress fields")
    void should_get_progress() throws Exception {
      ChunkUploadApplicationService.UploadProgress progress =
          ChunkUploadApplicationService.UploadProgress.builder()
              .uploadId("upload-001")
              .uploadedChunks(2)
              .totalChunks(4)
              .percent(50)
              .build();
      when(chunkUploadService.getUploadProgress("upload-001")).thenReturn(progress);

      mockMvc.perform(get("/nextwiki/files/chunk/upload-001/progress"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.uploadId").value("upload-001"))
          .andExpect(jsonPath("$.data.uploadedChunks").value(2))
          .andExpect(jsonPath("$.data.totalChunks").value(4))
          .andExpect(jsonPath("$.data.percent").value(50));

      verify(chunkUploadService).getUploadProgress("upload-001");
    }
  }
}
