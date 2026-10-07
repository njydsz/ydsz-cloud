package com.njydsz.workflow.web.controller.definition;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.server.service.FlowConditionExprService;
import com.njydsz.workflow.server.service.FlowCustomButtonService;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.workflow.server.service.FlowSlaService;
import com.njydsz.workflow.server.service.FlowTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link FlowDefinitionController} Smoke Test（轻量级 MockMvc 独立容器模式）。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>正常分页列表接口 HTTP 200 + 响应体结构校验
 * </ul>
 *
 * <p>注意：FlowDefinitionController#page 直接返回 {@link PageResponse}（非 YdszResponse 包装），
 * 因此本测试不校验 {@code $.code} 字段，而校验分页结构字段是否存在。
 *
 * @author ydsz-smoke-test
 * @since 26.10.01
 */
class FlowDefinitionSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private FlowDefinitionService definitionService;

  @Mock
  private FlowEventSubscriptionService eventSubscriptionService;

  @Mock
  private FlowSlaService slaService;

  @Mock
  private FlowConditionExprService conditionExprService;

  @Mock
  private FlowCustomButtonService customButtonService;

  @Mock
  private FlowTaskService taskService;

  @InjectMocks
  private FlowDefinitionController flowDefinitionController;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(flowDefinitionController);
  }

  @Test
  @DisplayName("GET /workflow/engine/definition/page 应返回 HTTP 200 + 分页结构")
  void page_shouldReturn200() throws Exception {
    // Given
    PageResponse<List<FlowDefinitionVO>> pageResponse =
        PageResponse.success(1L, 1L, 20L, List.of(new FlowDefinitionVO()));

    when(definitionService.page(anyInt(), anyInt(), anyString(), anyString()))
        .thenReturn(pageResponse);

    // When
    mockMvc.perform(get("/workflow/engine/definition/page")
            .param("pageNo", "1")
            .param("pageSize", "20"))
        // Then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data").isArray());
  }
}
