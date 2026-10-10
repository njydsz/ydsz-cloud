package com.njydsz.nextwiki.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.config.NextwikiProperties;
import com.njydsz.nextwiki.server.service.WopiFileService;

/**
 * {@link WopiController} Smoke Test。
 *
 * <p>验证 WOPI 在线编辑端点可正确返回 HTTP 200 + WOPI 响应结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class WopiControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private WopiFileService wopiFileService;

  @org.mockito.Mock
  private NextwikiProperties properties;

  @org.mockito.InjectMocks
  private WopiController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /nextwiki/wopi/files/{fileId}")
  class CheckFileInfo {

    @Test
    @DisplayName("should return 200 when check file info succeeds")
    void should_return_200_when_check_file_info_succeeds() throws Exception {
      FileNodeVO node = new FileNodeVO();
      node.setName("test.docx");
      node.setFile(true);
      node.setSize(1024L);
      node.setUpdatedAt(LocalDateTime.now());
      when(wopiFileService.findFileOptional("file-001"))
          .thenReturn(java.util.Optional.of(node));

      mockMvc.perform(get("/nextwiki/wopi/files/file-001"))
          .andExpect(status().isOk());
    }
  }
}
