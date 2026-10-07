package com.njydsz.nextwiki.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.metrics.NextwikiMetrics;
import com.njydsz.nextwiki.server.service.FileApplicationService;

/**
 * {@link FileController} 单元测试。
 *
 * <p>覆盖文件目录查询与删除端点。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class FileControllerTest {

  private MockMvc mockMvc;

  @Mock
  private FileApplicationService fileApplicationService;

  @Mock
  private NextwikiMetrics nextwikiMetrics;

  @InjectMocks
  private FileController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 列出目录内容返回分页数据 200。
   */
  @Test
  @DisplayName("GET /nextwiki/files/list 列出目录返回分页数据")
  void listFilesReturnsOkWithPageData() throws Exception {
    FileNodeVO node = FileNodeVO.builder().id("node-001").name("README.md").build();
    PageResponse<List<FileNodeVO>> page = PageResponse.success(1L, 1L, 50L, List.of(node));
    YdszResponse<PageResponse<List<FileNodeVO>>> response = YdszResponse.success(page);

    when(fileApplicationService.listFiles(any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(response);

    mockMvc.perform(get("/nextwiki/files/list")
            .header(AuthHeaderConstants.X_USER_ID, "user-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Happy-path: 删除文件返回 200。
   */
  @Test
  @DisplayName("DELETE /nextwiki/files/{nodeId} 删除文件返回 200")
  void deleteFileReturnsOk() throws Exception {
    doNothing().when(fileApplicationService).delete(any(), any());

    mockMvc.perform(delete("/nextwiki/files/node-001")
            .header(AuthHeaderConstants.X_USER_ID, "user-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }
}
