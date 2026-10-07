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
import com.njydsz.nextwiki.domain.vo.SearchResultVO;
import com.njydsz.nextwiki.server.service.SearchApplicationService;

/**
 * {@link SearchController} 单元测试。
 *
 * <p>覆盖综合搜索与自动补全端点。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

  private MockMvc mockMvc;

  @Mock
  private SearchApplicationService searchApplicationService;

  @InjectMocks
  private SearchController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 综合搜索返回 200。
   */
  @Test
  @DisplayName("POST /nextwiki/search 综合搜索返回结果")
  void searchReturnsOk() throws Exception {
    SearchResultVO result = SearchResultVO.builder().total(5L).build();

    when(searchApplicationService.searchWithFilters(any(), any())).thenReturn(result);

    String body = "{\"keyword\":\"合同\",\"scope\":\"all\"}";
    mockMvc.perform(post("/nextwiki/search")
            .header(AuthHeaderConstants.X_USER_ID, "user-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Happy-path: 搜索自动补全返回候选词列表。
   */
  @Test
  @DisplayName("GET /nextwiki/search/suggest 自动补全返回候选词")
  void suggestReturnsOk() throws Exception {
    when(searchApplicationService.autocomplete("合同")).thenReturn(List.of("合同模板", "合同法"));

    mockMvc.perform(get("/nextwiki/search/suggest")
            .param("prefix", "合同"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }
}
