package com.njydsz.literule.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.domain.vo.RulePackVO;
import com.njydsz.literule.server.benchmark.RuleStressTestService;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
import com.njydsz.literule.server.spi.RulePackProvider;

/**
 * {@link RulePackController} Smoke Test。
 *
 * <p>验证规则集市场端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RulePackControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RulePackProvider rulePackProvider;

  @org.mockito.Mock
  private ObjectProvider<RuleStressTestService> ruleStressTestServiceProvider;

  @org.mockito.Mock
  private LiteruleWebConverter literuleWebConverter;

  @org.mockito.InjectMocks
  private RulePackController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/rules/packs")
  class ListPacks {

    @Test
    @DisplayName("should return 200 when list packs succeeds")
    void should_return_200_when_list_packs_succeeds() throws Exception {
      when(rulePackProvider.listAll()).thenReturn(List.of());

      mockMvc.perform(get("/literule/rules/packs"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /literule/rules/packs/search")
  class SearchPacks {

    @Test
    @DisplayName("should return 200 when search packs succeeds")
    void should_return_200_when_search_packs_succeeds() throws Exception {
      when(rulePackProvider.search("test")).thenReturn(List.of());

      mockMvc.perform(get("/literule/rules/packs/search").param("keyword", "test"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
