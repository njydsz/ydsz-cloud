package com.njydsz.literule.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.excel.spring.ExcelWebSupport;
import com.njydsz.literule.domain.spi.DecisionTableEvalProvider;
import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.server.config.DecisionTableAdminService;
import com.njydsz.literule.server.config.DecisionTableQueryService;
import com.njydsz.literule.server.converter.LiteruleWebConverter;

/**
 * {@link RuleDecisionTableController} 单元测试。
 *
 * <p>覆盖决策表查询与评估两个核心端点。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class RuleDecisionTableControllerTest {

  private MockMvc mockMvc;

  @Mock
  private DecisionTableQueryService decisionTableQueryService;

  @Mock
  private ObjectProvider<DecisionTableAdminService> decisionTableAdminServiceProvider;

  @Mock
  private DecisionTableEvalProvider decisionTableEvalProvider;

  @Mock
  private ExcelWebSupport excelWebSupport;

  @Mock
  private LiteruleWebConverter literuleWebConverter;

  @InjectMocks
  private RuleDecisionTableController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 查询决策表列表返回 200。
   */
  @Test
  @DisplayName("GET /literule/rules/decision-tables 返回决策表列表")
  void listDecisionTablesReturnsOk() throws Exception {
    DecisionTableVO vo = new DecisionTableVO();
    vo.setTableCode("DT_001");
    vo.setTableName("测试决策表");

    when(decisionTableQueryService.findAll()).thenReturn(List.of(vo));

    mockMvc.perform(get("/literule/rules/decision-tables"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Happy-path: 评估决策表返回命中结果（无命中返回空列表）。
   */
  @Test
  @DisplayName("POST /literule/rules/decision-tables/{tableCode}/evaluate 评估返回结果")
  void evaluateDecisionTableReturnsOk() throws Exception {
    Map<String, Object> facts = new HashMap<>();
    facts.put("amount", 100);

    when(decisionTableEvalProvider.evaluate("DT_001", facts)).thenReturn(Collections.emptyList());

    String body = "{\"amount\":100}";
    mockMvc.perform(post("/literule/rules/decision-tables/DT_001/evaluate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }
}
