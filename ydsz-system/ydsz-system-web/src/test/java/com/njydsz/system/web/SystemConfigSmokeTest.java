package com.njydsz.system.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.excel.spring.ExcelWebSupport;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.service.ConfigBatchService;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.web.controller.ConfigController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link ConfigController} Smoke Test（轻量级 MockMvc 独立容器模式）。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>正常分页列表接口 HTTP 200 + 响应体 code='A00000'
 * </ul>
 *
 * <p>使用 {@code standaloneSetup} 不启动真实端口，依赖全部通过 {@code @Mock} 模拟。
 *
 * @author ydsz-smoke-test
 * @since 26.10.01
 */
class SystemConfigSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock
  private ConfigService configService;

  @Mock
  private ConfigBatchService configBatchService;

  @Mock
  private ExcelWebSupport excelWebSupport;

  @InjectMocks
  private ConfigController configController;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(configController);
  }

  @Test
  @DisplayName("GET /config/page 应返回 HTTP 200 + code=A00000")
  void page_shouldReturn200() throws Exception {
    // Given
    ConfigVO vo = new ConfigVO();
    vo.setConfigKey("test.key");
    PageResponse<List<ConfigVO>> pageResponse =
        PageResponse.success(1L, 1L, 10L, List.of(vo));

    when(configService.page(any(ConfigPageQuery.class))).thenReturn(pageResponse);

    // When
    mockMvc.perform(get("/config/page")
            .param("pageNum", "1")
            .param("pageSize", "10"))
        // Then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
  }
}
