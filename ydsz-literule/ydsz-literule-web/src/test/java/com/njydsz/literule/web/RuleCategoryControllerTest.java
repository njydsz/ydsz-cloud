package com.njydsz.literule.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.literule.domain.vo.CategoryNodeVO;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
import com.njydsz.literule.server.spi.CategoryTreeNode;
import com.njydsz.literule.server.spi.RuleCategoryProvider;

/**
 * {@link RuleCategoryController} 单元测试。
 *
 * <p>覆盖类别树查询与按 Owner 查询路径。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class RuleCategoryControllerTest {

  private MockMvc mockMvc;

  @Mock
  private RuleCategoryProvider ruleCategoryProvider;

  @Mock
  private RuleAdminService ruleAdminService;

  @Mock
  private LiteruleWebConverter literuleWebConverter;

  @InjectMocks
  private RuleCategoryController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 获取类别树返回 200。
   */
  @Test
  @DisplayName("GET /literule/rules/category-tree 返回类别树")
  void categoryTreeReturnsOk() throws Exception {
    CategoryTreeNode treeNode = new CategoryTreeNode();
    CategoryNodeVO vo = new CategoryNodeVO();
    vo.setName("ROOT");
    vo.setPath("/");

    when(ruleCategoryProvider.buildTree()).thenReturn(treeNode);
    when(literuleWebConverter.entityToVO(treeNode)).thenReturn(vo);

    mockMvc.perform(get("/literule/rules/category-tree"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Happy-path: 按 Owner 查询规则列表（无结果）。
   */
  @Test
  @DisplayName("GET /literule/rules/by-owner 按责任人查询返回空列表")
  void listByOwnerReturnsEmptyOk() throws Exception {
    when(ruleCategoryProvider.listDefinitionsByOwner("testuser")).thenReturn(Collections.emptyList());

    mockMvc.perform(get("/literule/rules/by-owner").param("owner", "testuser"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }
}
