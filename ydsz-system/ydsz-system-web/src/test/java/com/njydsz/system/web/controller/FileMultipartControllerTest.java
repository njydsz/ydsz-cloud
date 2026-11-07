package com.njydsz.system.web.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.test.BaseControllerMockTest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@link FileMultipartController} multipart upload contract tests. */
class FileMultipartControllerTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock private IFileStorageProvider fileStorageProvider;

  @InjectMocks private FileMultipartController controller;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
  }

  @Test
  @DisplayName("POST /file/multipart/init binds key/contentType/header and returns upload metadata")
  void should_initiate_multipart_upload() throws Exception {
    when(fileStorageProvider.initiateMultipartUpload(
            isNull(), eq("docs/report.bin"), eq("application/octet-stream")))
        .thenReturn("upload-1");

    mockMvc.perform(
            post("/file/multipart/init")
                .param("key", "docs/report.bin")
                .header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data.uploadId").value("upload-1"))
        .andExpect(jsonPath("$.data.recommendedPartSize").value(5 * 1024 * 1024));

    verify(fileStorageProvider)
        .initiateMultipartUpload(
            null, "docs/report.bin", "application/octet-stream");
  }

  @Test
  @DisplayName("POST /file/multipart/{uploadId}/part/{partNumber} forwards binary body")
  void should_upload_part_after_init() throws Exception {
    when(fileStorageProvider.initiateMultipartUpload(isNull(), eq("docs/report.bin"), eq("text/plain")))
        .thenReturn("upload-1");
    mockMvc.perform(
            post("/file/multipart/init")
                .param("key", "docs/report.bin")
                .param("contentType", "text/plain")
                .header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isOk());

    byte[] part = "part-data".getBytes();
    mockMvc.perform(
            post("/file/multipart/{uploadId}/part/{partNumber}", "upload-1", 1)
                .content(part)
                .contentType("application/octet-stream")
                .header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data").doesNotExist());

    verify(fileStorageProvider)
        .uploadPart(null, "docs/report.bin", "upload-1", 1, part);
  }

  @Test
  @DisplayName("POST /file/multipart/{uploadId}/complete completes with the initialized key")
  void should_complete_multipart_upload() throws Exception {
    when(fileStorageProvider.initiateMultipartUpload(isNull(), eq("docs/report.bin"), eq("text/plain")))
        .thenReturn("upload-1");
    mockMvc.perform(
            post("/file/multipart/init")
                .param("key", "docs/report.bin")
                .param("contentType", "text/plain")
                .header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isOk());

    mockMvc.perform(
            post("/file/multipart/{uploadId}/complete", "upload-1")
                .header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data.uploadId").value("upload-1"));

    verify(fileStorageProvider).completeMultipartUpload(null, "docs/report.bin", "upload-1");
  }

  @Test
  @DisplayName("multipart request variant accepts a file part without changing the init contract")
  void should_accept_multipart_request_for_init() throws Exception {
    when(fileStorageProvider.initiateMultipartUpload(
            isNull(), eq("docs/report.bin"), eq("application/octet-stream")))
        .thenReturn("upload-2");
    MockMultipartFile file =
        new MockMultipartFile("file", "report.bin", "application/octet-stream",
            "file-content".getBytes());

    mockMvc.perform(
            multipart("/file/multipart/init")
                .file(file)
                .param("key", "docs/report.bin")
                .header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data.uploadId").value("upload-2"));

    verify(fileStorageProvider)
        .initiateMultipartUpload(null, "docs/report.bin", "application/octet-stream");
  }

  @Test
  @DisplayName("multipart part upload forwards raw file content")
  void should_upload_multipart_part() throws Exception {
    when(fileStorageProvider.initiateMultipartUpload(
            isNull(), eq("docs/report.bin"), eq("application/octet-stream")))
        .thenReturn("upload-2");
    MockMultipartFile initFile =
        new MockMultipartFile("file", "report.bin", "application/octet-stream",
            "init-content".getBytes());
    mockMvc.perform(
            multipart("/file/multipart/init")
                .file(initFile)
                .param("key", "docs/report.bin")
                .header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isOk());

    byte[] part = "multipart-part".getBytes();
    MockMultipartFile partFile =
        new MockMultipartFile("file", "part-1.bin", "application/octet-stream", part);
    mockMvc.perform(
            multipart("/file/multipart/{uploadId}/part/{partNumber}", "upload-2", 2)
                .file(partFile)
                .header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

    verify(fileStorageProvider)
        .uploadPart(null, "docs/report.bin", "upload-2", 2, part);
  }

  @Test
  @DisplayName("DELETE /file/multipart/{uploadId} returns a success envelope")
  void should_abort_upload() throws Exception {
    mockMvc.perform(delete("/file/multipart/{uploadId}", "upload-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  @DisplayName("missing required key returns a validation error and skips storage")
  void should_reject_missing_key() throws Exception {
    mockMvc
        .perform(
            post("/file/multipart/init").header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("part number below one is rejected by bean validation")
  void should_reject_non_positive_part_number() throws Exception {
    when(fileStorageProvider.initiateMultipartUpload(isNull(), eq("docs/report.bin"), eq("text/plain")))
        .thenReturn("upload-1");
    mockMvc.perform(
            post("/file/multipart/init")
                .param("key", "docs/report.bin")
                .param("contentType", "text/plain")
                .header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/file/multipart/{uploadId}/part/{partNumber}", "upload-1", 0)
                .content(new byte[] {1})
                .header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("missing X-User-Id is rejected before storage access")
  void should_reject_missing_user_header() throws Exception {
    mockMvc
        .perform(post("/file/multipart/init").param("key", "docs/report.bin"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("unknown upload id is forwarded without a service call")
  void should_reject_unknown_upload_id_before_storage() throws Exception {
    mockMvc
        .perform(
            post("/file/multipart/{uploadId}/part/{partNumber}", "missing", 1)
                .content(new byte[] {1})
                .header(AuthHeaderConstants.X_USER_ID, "user-1"))
        .andExpect(status().isInternalServerError());
  }
}
