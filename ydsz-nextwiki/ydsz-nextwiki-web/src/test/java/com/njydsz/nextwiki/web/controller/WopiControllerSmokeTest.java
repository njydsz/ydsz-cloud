package com.njydsz.nextwiki.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

  /** WOPI 回调访问令牌（与 X-WOPI-Authorization 请求头比对）。 */
  private static final String WOPI_ACCESS_TOKEN = "wopi-token-001";

  private MockMvc mockMvc;

  @Mock
  private WopiFileService wopiFileService;

  @Mock
  private NextwikiProperties properties;

  @InjectMocks
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
      FileNodeVO node = FileNodeVO.builder()
          .name("test.docx")
          .nodeType(FileNodeVO.TYPE_FILE)
          .size(1024L)
          .updatedAt(LocalDateTime.now())
          .build();
      // validateWopiToken 读取 properties.getWopi().getAccessToken()，未 stub 时返回 null 触发 NPE
      NextwikiProperties.WopiConfig wopiConfig = new NextwikiProperties.WopiConfig();
      wopiConfig.setAccessToken(WOPI_ACCESS_TOKEN);
      when(properties.getWopi()).thenReturn(wopiConfig);
      when(wopiFileService.findFileOptional("file-001")).thenReturn(Optional.of(node));

      mockMvc.perform(get("/nextwiki/wopi/files/file-001")
              .header("X-WOPI-Authorization", WOPI_ACCESS_TOKEN))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.error").value(false))
          .andExpect(jsonPath("$.baseFileName").value("test.docx"));
    }
  }
}
