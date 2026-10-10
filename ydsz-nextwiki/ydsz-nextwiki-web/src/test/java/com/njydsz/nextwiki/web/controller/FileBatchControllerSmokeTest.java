package com.njydsz.nextwiki.web.controller;

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

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;
import com.njydsz.nextwiki.server.service.BatchTaskService;
import com.njydsz.nextwiki.server.service.FileApplicationService;
import com.njydsz.nextwiki.server.service.VersionDiffApplicationService;

/**
 * {@link FileBatchController} Smoke Test。
 *
 * <p>验证文件版本管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class FileBatchControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private FileApplicationService fileApplicationService;

  @org.mockito.Mock
  private BatchTaskService batchTaskService;

  @org.mockito.Mock
  private VersionDiffApplicationService versionDiffApplicationService;

  @org.mockito.InjectMocks
  private FileBatchController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/files/{nodeId}/versions")
  class ListVersions {

    @Test
    @DisplayName("should return 200 when list file versions succeeds")
    void should_return_200_when_list_file_versions_succeeds() throws Exception {
      when(fileApplicationService.getVersionHistory("node-001")).thenReturn(List.of(new FileVersionVO()));

      mockMvc.perform(get("/nextwiki/files/node-001/versions"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
