package com.njydsz.literule.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.server.config.ABTestService;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
import com.njydsz.literule.server.expression.ExpressionValidationService;

import java.util.List;

/**
 * {@link RuleAdminController} 单元测试。
 *
 * <p>使用 MockMvc standaloneSetup + Mockito 模拟所有依赖，覆盖规则查询核心路径。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class RuleAdminControllerTest {

  private MockMvc mockMvc;

  @Mock
  private RuleAdminService ruleAdminService;

  @Mock
  private ABTestService abTestService;

  @Mock
  private com.njydsz.literule.domain.RuleEngine ruleEngine;

  @Mock
  private ExpressionValidationService expressionValidationService;

  @Mock
  private com.njydsz.literule.server.version.RuleVersionDiffService ruleVersionDiffService;

  @Mock
  private LiteruleWebConverter literuleWebConverter;

  @InjectMocks
  private RuleAdminController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 分页查询规则列表返回 200 + 数据体。
   */
  @Test
  @DisplayName("GET /literule/rules 分页查询返回规则列表")
  void listRulesReturnsOkWithData() throws Exception {
    RuleDefinitionVO vo = new RuleDefinitionVO();
    vo.setRuleCode("RULE_001");
    vo.setRuleName("测试规则");

    PageResponse<List<RuleDefinitionDTO>> page = PageResponse.success(1L, 1L, 20L, List.of());
    when(ruleAdminService.pageRuleDefinitions(any(PageQuery.class))).thenReturn(page);
    when(literuleWebConverter.entityToVO(any(RuleDefinitionDTO.class))).thenReturn(vo);

    mockMvc.perform(get("/literule/rules")
            .param("pageNum", "1")
            .param("pageSize", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Happy-path: 按编码查询规则详情返回 200。
   */
  @Test
  @DisplayName("GET /literule/rules/{ruleCode} 按编码查询规则详情")
  void getRuleByCodeReturnsOk() throws Exception {
    RuleDefinitionDTO dto = new RuleDefinitionDTO();
    dto.setCode("RULE_001");

    RuleDefinitionVO vo = new RuleDefinitionVO();
    vo.setRuleCode("RULE_001");
    vo.setRuleName("测试规则");

    when(ruleAdminService.getByCode("RULE_001")).thenReturn(dto);
    when(literuleWebConverter.entityToVO(dto)).thenReturn(vo);

    mockMvc.perform(get("/literule/rules/RULE_001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }
}
