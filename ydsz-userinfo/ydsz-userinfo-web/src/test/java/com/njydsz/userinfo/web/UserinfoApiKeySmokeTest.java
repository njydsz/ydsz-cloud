package com.njydsz.userinfo.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.query.ApiKeyPageQuery;
import com.njydsz.userinfo.domain.vo.ApiKeyVO;
import com.njydsz.userinfo.server.service.ApiKeyService;
import com.njydsz.userinfo.web.controller.ApiKeyController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link ApiKeyController} Smoke Test（轻量级 MockMvc 独立容器模式）。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>正常分页列表接口 HTTP 200 + 响应体 code='A00000'
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.10.01
 */
class UserinfoApiKeySmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private ApiKeyService apiKeyService;

  @InjectMocks
  private ApiKeyController apiKeyController;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(apiKeyController);
  }

  @Test
  @DisplayName("GET /apikey 应返回 HTTP 200 + code=A00000")
  void pageKeys_shouldReturn200() throws Exception {
    // Given
    PageResponse<List<ApiKeyVO>> pageResponse =
        PageResponse.success(1L, 1L, 10L, List.of(new ApiKeyVO()));

    when(apiKeyService.pageKeys(any(ApiKeyPageQuery.class))).thenReturn(pageResponse);

    // When
    mockMvc.perform(get("/apikey")
            .param("pageNum", "1")
            .param("pageSize", "10"))
        // Then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
  }
}
