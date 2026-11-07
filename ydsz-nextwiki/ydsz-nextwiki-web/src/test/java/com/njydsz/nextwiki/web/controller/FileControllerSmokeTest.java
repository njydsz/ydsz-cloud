package com.njydsz.nextwiki.web.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.metrics.NextwikiMetrics;
import com.njydsz.nextwiki.server.service.FileApplicationService;
import com.njydsz.nextwiki.web.handler.NextwikiExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FileController} core file-operation HTTP contract test.
 */
class FileControllerSmokeTest extends BaseControllerMockTest {

  private static final String CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;
  private static final String USER_ID = "user-001";

  private MockMvc mockMvc;
  private FileApplicationService fileApplicationService;
  private NextwikiMetrics nextwikiMetrics;

  @BeforeEach
  void setUp() {
    fileApplicationService = mock(FileApplicationService.class);
    nextwikiMetrics = mock(NextwikiMetrics.class);
    FileController controller = new FileController(fileApplicationService, nextwikiMetrics);
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setValidator(validator)
        .setControllerAdvice(new NextwikiExceptionHandler(new MockEnvironment()))
        .build();
  }

  @Nested
  @DisplayName("POST /nextwiki/files/upload")
  class Upload {

    @Test
    @DisplayName("binds multipart and optional parameters, returns node data, and records success metric")
    void should_upload_file() throws Exception {
      MockMultipartFile file = new MockMultipartFile(
          "file", "report.txt", "text/plain", "file-content".getBytes());
      FileNodeVO uploaded = fileNode("node-001", "report.txt", "file");
      when(fileApplicationService.upload(any(), eq("parent-001"), eq("final-report.txt"),
          eq("first version"), eq(USER_ID))).thenReturn(uploaded);

      mockMvc.perform(multipart("/nextwiki/files/upload")
              .file(file)
              .param("parentId", "parent-001")
              .param("rename", "final-report.txt")
              .param("versionRemark", "first version")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("node-001"))
          .andExpect(jsonPath("$.data.name").value("report.txt"))
          .andExpect(jsonPath("$.data.nodeType").value("file"));

      verify(fileApplicationService).upload(any(), eq("parent-001"), eq("final-report.txt"),
          eq("first version"), eq(USER_ID));
      verify(nextwikiMetrics).recordUpload();
    }

    @Test
    @DisplayName("passes null optional values when only file and user are supplied")
    void should_upload_without_optional_parameters() throws Exception {
      MockMultipartFile file = new MockMultipartFile(
          "file", "plain.txt", "text/plain", "content".getBytes());
      when(fileApplicationService.upload(any(), isNull(), isNull(), isNull(), eq(USER_ID)))
          .thenReturn(fileNode("node-002", "plain.txt", "file"));

      mockMvc.perform(multipart("/nextwiki/files/upload")
              .file(file)
              .header("X-User-Id", USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("node-002"));

      verify(fileApplicationService).upload(any(), isNull(), isNull(), isNull(), eq(USER_ID));
    }

    @Test
    @DisplayName("returns BAD REQUEST for a missing file part and never records upload success")
    void should_reject_missing_file() throws Exception {
      mockMvc.perform(multipart("/nextwiki/files/upload")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isBadRequest());

      verify(fileApplicationService, never()).upload(any(), any(), any(), any(), any());
      verify(nextwikiMetrics, never()).recordUpload();
    }

    @Test
    @DisplayName("returns SYSTEM_ERROR for upload failure and does not record success metric")
    void should_not_record_metric_on_failure() throws Exception {
      MockMultipartFile file = new MockMultipartFile(
          "file", "bad.txt", "text/plain", "bad".getBytes());
      when(fileApplicationService.upload(any(), any(), any(), any(), eq(USER_ID)))
          .thenThrow(new IllegalStateException("storage unavailable"));

      mockMvc.perform(multipart("/nextwiki/files/upload")
              .file(file)
              .header("X-User-Id", USER_ID))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));

      verify(nextwikiMetrics, never()).recordUpload();
    }
  }

  @Nested
  @DisplayName("GET /nextwiki/files/list")
  class ListFiles {

    @Test
    @DisplayName("binds list query parameters, user header, and page defaults")
    void should_list_files() throws Exception {
      PageResponse<List<FileNodeVO>> pageResult = PageResponse.success(0L, 1L, 50L, List.of());
      when(fileApplicationService.listFiles(any(), any(), any(), any(), any(), anyInt(), anyInt()))
          .thenReturn(YdszResponse.success(pageResult));

      mockMvc.perform(get("/nextwiki/files/list")
              .param("parentId", "parent-001")
              .param("sortBy", "name")
              .param("sortDir", "asc")
              .param("type", "file")
              .param("page", "1")
              .param("pageSize", "50")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.total").value(0))
          .andExpect(jsonPath("$.data.pageNum").value(1))
          .andExpect(jsonPath("$.data.pageSize").value(50));

      verify(fileApplicationService).listFiles(
          eq("parent-001"), eq(USER_ID), eq("name"), eq("asc"), eq("file"), eq(1), eq(50));
    }

    @Test
    @DisplayName("returns SYSTEM_ERROR when list lookup fails")
    void should_return_error_when_list_fails() throws Exception {
      when(fileApplicationService.listFiles(any(), any(), any(), any(), any(), anyInt(), anyInt()))
          .thenThrow(new IllegalStateException("database unavailable"));

      mockMvc.perform(get("/nextwiki/files/list")
              .param("page", "1")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }
  }

  @Nested
  @DisplayName("File mutation endpoints")
  class Mutations {

    @Test
    @DisplayName("POST /folders binds JSON body and returns created folder fields")
    void should_create_folder() throws Exception {
      when(fileApplicationService.createFolder("parent-001", "Reports", USER_ID))
          .thenReturn(fileNode("folder-001", "Reports", "folder"));

      mockMvc.perform(post("/nextwiki/files/folders")
              .header("X-User-Id", USER_ID)
              .contentType(CONTENT_TYPE)
              .content("{\"parentId\":\"parent-001\",\"name\":\"Reports\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("folder-001"))
          .andExpect(jsonPath("$.data.nodeType").value("folder"));

      verify(fileApplicationService).createFolder("parent-001", "Reports", USER_ID);
    }

    @Test
    @DisplayName("PUT /{nodeId}/move binds path and request body")
    void should_move_file() throws Exception {
      when(fileApplicationService.move("node-001", "target-folder", USER_ID))
          .thenReturn(fileNode("node-001", "report.txt", "file"));

      mockMvc.perform(put("/nextwiki/files/node-001/move")
              .header("X-User-Id", USER_ID)
              .contentType(CONTENT_TYPE)
              .content("{\"targetParentId\":\"target-folder\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("node-001"));

      verify(fileApplicationService).move("node-001", "target-folder", USER_ID);
    }

    @Test
    @DisplayName("PUT /{nodeId}/rename binds the new name and returns updated node")
    void should_rename_file() throws Exception {
      when(fileApplicationService.rename("node-001", "new-name.txt", USER_ID))
          .thenReturn(fileNode("node-001", "new-name.txt", "file"));

      mockMvc.perform(put("/nextwiki/files/node-001/rename")
              .header("X-User-Id", USER_ID)
              .contentType(CONTENT_TYPE)
              .content("{\"newName\":\"new-name.txt\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.name").value("new-name.txt"));

      verify(fileApplicationService).rename("node-001", "new-name.txt", USER_ID);
    }

    @Test
    @DisplayName("DELETE /{nodeId} delegates delete and records delete metric")
    void should_delete_file() throws Exception {
      mockMvc.perform(delete("/nextwiki/files/node-001")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(fileApplicationService).delete("node-001", USER_ID);
      verify(nextwikiMetrics).recordDelete();
    }

    @Test
    @DisplayName("POST /{nodeId}/copy routes folders to recursive folder copy")
    void should_copy_folder() throws Exception {
      when(fileApplicationService.getFileInfo("folder-001"))
          .thenReturn(fileNode("folder-001", "Reports", "folder"));
      when(fileApplicationService.copyFolder("folder-001", "target-folder", USER_ID))
          .thenReturn(fileNode("folder-copy-001", "Reports", "folder"));

      mockMvc.perform(post("/nextwiki/files/folder-001/copy")
              .param("targetParentId", "target-folder")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("folder-copy-001"))
          .andExpect(jsonPath("$.data.nodeType").value("folder"));

      verify(fileApplicationService).copyFolder("folder-001", "target-folder", USER_ID);
      verify(fileApplicationService, never()).copy(any(), any(), any());
    }

    @Test
    @DisplayName("POST /{nodeId}/copy routes files directly to file copy")
    void should_copy_file() throws Exception {
      when(fileApplicationService.getFileInfo("node-001"))
          .thenReturn(fileNode("node-001", "report.txt", "file"));
      when(fileApplicationService.copy("node-001", "target-folder", USER_ID))
          .thenReturn(fileNode("node-copy-001", "report.txt", "file"));

      mockMvc.perform(post("/nextwiki/files/node-001/copy")
              .param("targetParentId", "target-folder")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("node-copy-001"));

      verify(fileApplicationService).copy("node-001", "target-folder", USER_ID);
      verify(fileApplicationService, never()).copyFolder(any(), any(), any());
    }

    @Test
    @DisplayName("PUT /sort binds nested sort items and returns the updated count")
    void should_batch_sort() throws Exception {
      when(fileApplicationService.batchSort(eq("parent-001"), any(), eq(USER_ID))).thenReturn(2);

      mockMvc.perform(put("/nextwiki/files/sort")
              .header("X-User-Id", USER_ID)
              .contentType(CONTENT_TYPE)
              .content("{\"parentId\":\"parent-001\",\"items\":["
                  + "{\"nodeId\":\"node-1\",\"sort\":0},"
                  + "{\"nodeId\":\"node-2\",\"sort\":1}]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(2));

      verify(fileApplicationService).batchSort(eq("parent-001"), any(), eq(USER_ID));
    }

    @Test
    @DisplayName("rejects malformed mutation JSON without invoking the service")
    void should_reject_malformed_json() throws Exception {
      mockMvc.perform(put("/nextwiki/files/node-001/rename")
              .header("X-User-Id", USER_ID)
              .contentType(CONTENT_TYPE)
              .content("{\"newName\":"))
          .andExpect(status().isBadRequest());

      verify(fileApplicationService, never()).rename(any(), any(), any());
    }

    @Test
    @DisplayName("returns SYSTEM_ERROR on delete failure and does not report a successful delete")
    void should_not_record_metric_on_delete_failure() throws Exception {
      doThrow(new IllegalStateException("delete failed"))
          .when(fileApplicationService).delete("node-001", USER_ID);

      mockMvc.perform(delete("/nextwiki/files/node-001")
              .header("X-User-Id", USER_ID))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));

      verify(nextwikiMetrics, never()).recordDelete();
    }
  }

  private FileNodeVO fileNode(String id, String name, String type) {
    return FileNodeVO.builder()
        .id(id)
        .name(name)
        .nodeType(type)
        .size(1024L)
        .currentVersion(1)
        .build();
  }
}
