package com.njydsz.nextwiki.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.nextwiki.domain.vo.ShareLinkVO;
import com.njydsz.nextwiki.server.service.ShareApplicationService;

/**
 * {@link ShareController} 单元测试。
 *
 * <p>覆盖分享创建与我的分享查询路径。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class ShareControllerTest {

  private MockMvc mockMvc;

  @Mock
  private ShareApplicationService shareApplicationService;

  @InjectMocks
  private ShareController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 创建分享链接返回 200。
   */
  @Test
  @DisplayName("POST /nextwiki/shares 创建分享链接返回 200")
  void createShareReturnsOk() throws Exception {
    ShareLinkVO link = ShareLinkVO.builder()
        .id("share-001")
        .shareCode("abc123")
        .fileNodeId("node-001")
        .build();

    when(shareApplicationService.createShare(
        any(), any(), any(), any(), any(), any()))
        .thenReturn(link);

    String body = "{\"fileNodeId\":\"node-001\",\"shareType\":\"PUBLIC\",\"maxAccessCount\":10}";
    mockMvc.perform(post("/nextwiki/shares")
            .header(AuthHeaderConstants.X_USER_ID, "user-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Happy-path: 查询我的分享列表返回 200。
   */
  @Test
  @DisplayName("GET /nextwiki/shares/my 查询我的分享返回 200")
  void mySharesReturnsOk() throws Exception {
    when(shareApplicationService.findByUserId("user-001")).thenReturn(List.of());

    mockMvc.perform(get("/nextwiki/shares/my")
            .header(AuthHeaderConstants.X_USER_ID, "user-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }
}
