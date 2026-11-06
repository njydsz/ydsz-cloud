package com.njydsz.generator.web.controller;


import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.generator.entity.GenHistory;
import com.njydsz.generator.entity.GenHistoryFile;
import com.njydsz.generator.service.GenHistoryService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link HistoryController} Smoke Test。
 *
 * <p>验证代码生成历史端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class HistoryControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private GenHistoryService historyService;

  @BeforeEach
  void setUp() {
    historyService = mock(GenHistoryService.class);
    HistoryController controller = new HistoryController(historyService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /generator/history")
  class ListRecent {

    @Test
    @DisplayName("should return 200 when list recent succeeds")
    void should_return_200_when_list_recent_succeeds() throws Exception {
      GenHistory history = new GenHistory();
      history.setId("his-001");
      when(historyService.listRecent(10)).thenReturn(List.of(history));

      mockMvc.perform(get("/generator/history").param("limit", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /generator/history/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 when get by id succeeds")
    void should_return_200_when_get_by_id_succeeds() throws Exception {
      GenHistory history = new GenHistory();
      history.setId("his-001");
      when(historyService.getById("his-001")).thenReturn(history);

      mockMvc.perform(get("/generator/history/{id}", "his-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /generator/history/{id}/files")
  class ListFiles {

    @Test
    @DisplayName("should return 200 when list files succeeds")
    void should_return_200_when_list_files_succeeds() throws Exception {
      GenHistoryFile file = new GenHistoryFile();
      file.setId("file-001");
      when(historyService.listFiles("his-001")).thenReturn(List.of(file));

      mockMvc.perform(get("/generator/history/{id}/files", "his-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /generator/history/{id}/rollback")
  class Rollback {

    @Test
    @DisplayName("should return 200 when rollback succeeds")
    void should_return_200_when_rollback_succeeds() throws Exception {
      mockMvc.perform(post("/generator/history/{id}/rollback", "his-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("DELETE /generator/history/{id}")
  class Delete {

    @Test
    @DisplayName("should return 200 when delete succeeds")
    void should_return_200_when_delete_succeeds() throws Exception {
      mockMvc.perform(delete("/generator/history/{id}", "his-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
