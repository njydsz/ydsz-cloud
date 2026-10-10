package com.njydsz.nextwiki.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.metrics.NextwikiMetrics;
import com.njydsz.nextwiki.server.service.FileApplicationService;

/**
 * {@link FileController} Smoke Test。
 *
 * <p>验证网盘文件管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class FileControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FileApplicationService fileApplicationService;

  @org.mockito.Mock
  private NextwikiMetrics nextwikiMetrics;

  @org.mockito.InjectMocks
  private FileController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/files/list")
  class ListFiles {

    @Test
    @DisplayName("should return 200 when list files succeeds")
    void should_return_200_when_list_files_succeeds() throws Exception {
      PageResponse<List<FileNodeVO>> pageResult = PageResponse.success(0L, 1L, 50L, List.of());
      when(fileApplicationService.listFiles(any(), any(), any(), any(), any(), anyInt(), anyInt()))
          .thenReturn(YdszResponse.success(pageResult));

      mockMvc.perform(get("/nextwiki/files/list")
              .param("page", "1")
              .param("pageSize", "50")
              .header("X-User-Id", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
