package com.njydsz.literule.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.domain.vo.CategoryNodeVO;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
import com.njydsz.literule.server.spi.CategoryTreeNode;
import com.njydsz.literule.server.spi.RuleCategoryProvider;

/**
 * {@link RuleCategoryController} Smoke Test。
 *
 * <p>验证规则目录树端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleCategoryControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RuleCategoryProvider ruleCategoryProvider;

  @org.mockito.Mock
  private RuleAdminService ruleAdminService;

  @org.mockito.Mock
  private LiteruleWebConverter literuleWebConverter;

  @org.mockito.InjectMocks
  private RuleCategoryController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/rules/category-tree")
  class CategoryTree {

    @Test
    @DisplayName("should return 200 when get category tree succeeds")
    void should_return_200_when_get_category_tree_succeeds() throws Exception {
      CategoryTreeNode rawTree = new CategoryTreeNode();
      org.mockito.Mockito.when(ruleCategoryProvider.buildTree()).thenReturn(rawTree);
      CategoryNodeVO vo = new CategoryNodeVO();
      org.mockito.Mockito.when(literuleWebConverter.entityToVO(rawTree)).thenReturn(vo);

      mockMvc.perform(get("/literule/rules/category-tree"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
