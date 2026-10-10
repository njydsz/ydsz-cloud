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
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.literule.domain.vo.RuleConflictInfoVO;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
import com.njydsz.literule.server.spi.RuleConflictDetectorProvider;

/**
 * {@link RuleConflictController} Smoke Test。
 *
 * <p>验证规则冲突检测端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class RuleConflictControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private RuleConflictDetectorProvider ruleConflictDetectorProvider;

  @org.mockito.Mock
  private LiteruleWebConverter literuleWebConverter;

  @org.mockito.InjectMocks
  private RuleConflictController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /literule/rules/conflicts")
  class DetectConflicts {

    @Test
    @DisplayName("should return 200 when detect conflicts succeeds")
    void should_return_200_when_detect_conflicts_succeeds() throws Exception {
      when(ruleConflictDetectorProvider.detectConflicts()).thenReturn(List.of());
      when(literuleWebConverter.entityToVO(org.mockito.ArgumentMatchers.any()))
          .thenReturn(new RuleConflictInfoVO());

      mockMvc.perform(get("/literule/rules/conflicts"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
