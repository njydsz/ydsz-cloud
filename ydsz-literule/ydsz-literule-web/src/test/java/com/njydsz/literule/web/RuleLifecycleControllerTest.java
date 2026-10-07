package com.njydsz.literule.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleStatus;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.server.approval.RuleApprovalService;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.converter.LiteruleWebConverter;

/**
 * {@link RuleLifecycleController} 单元测试。
 *
 * <p>覆盖审批通过 happy-path 与规则不存在 error-path。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class RuleLifecycleControllerTest {

  private MockMvc mockMvc;

  @Mock
  private RuleAdminService ruleAdminService;

  @Mock
  private ObjectProvider<RuleApprovalService> ruleApprovalServiceProvider;

  @Mock
  private LiteruleWebConverter literuleWebConverter;

  @Mock
  private RuleApprovalService ruleApprovalService;

  @InjectMocks
  private RuleLifecycleController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 审批通过 DRAFT 规则，返回 200。
   */
  @Test
  @DisplayName("POST /literule/rules/{ruleCode}/approve 审批通过返回 200")
  void approveDraftRuleReturnsOk() throws Exception {
    RuleDefinitionDTO def = new RuleDefinitionDTO();
    def.setCode("RULE_001");
    def.setStatus(RuleStatus.DRAFT.name());

    RuleDefinitionVO vo = new RuleDefinitionVO();
    vo.setRuleCode("RULE_001");
    vo.setStatus(RuleStatus.PUBLISHED.name());

    when(ruleAdminService.getByCode("RULE_001")).thenReturn(def);
    when(ruleAdminService.save(any(RuleDefinitionDTO.class), eq("ADMIN"), any(String.class)))
        .thenReturn(def);
    when(literuleWebConverter.entityToVO(any(RuleDefinitionDTO.class))).thenReturn(vo);

    String body = "{\"comment\":\"审批通过\"}";
    mockMvc.perform(post("/literule/rules/RULE_001/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Operator", "ADMIN")
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Error-path: 审批不存在的规则，返回业务错误码。
   */
  @Test
  @DisplayName("POST /literule/rules/{ruleCode}/approve 规则不存在返回错误码")
  void approveNonExistentRuleReturnsError() throws Exception {
    when(ruleAdminService.getByCode("NOT_EXIST")).thenReturn(null);

    String body = "{\"comment\":\"审批通过\"}";
    mockMvc.perform(post("/literule/rules/NOT_EXIST/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Operator", "ADMIN")
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(
            com.njydsz.literule.domain.enums.LiteruleExceptionCode.RULE_NOT_FOUND.getCode()));
  }
}
