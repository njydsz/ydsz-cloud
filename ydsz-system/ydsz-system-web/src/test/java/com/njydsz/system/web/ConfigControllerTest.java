package com.njydsz.system.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.excel.spring.ExcelWebSupport;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.service.ConfigBatchService;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.web.controller.ConfigController;
import com.njydsz.system.web.handler.SystemExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link ConfigController} MockMvc 集成测试。
 *
 * <p>覆盖场景：分页查询接口、参数校验失败（400）、业务异常错误码包装。
 *
 * <p>使用 {@code @WebMvcTest(ConfigController.class)} 仅加载 Controller 层，
 * 所有 Service 依赖通过 {@code @MockBean} 模拟，{@link SystemExceptionHandler}
 * 通过 {@code @Import} 纳入 MVC 异常处理链路。
 *
 * @author ydsz
 * @since 26.10.06
 */
@WebMvcTest(ConfigController.class)
@Import(SystemExceptionHandler.class)
@DisplayName("ConfigController - 系统配置 MockMvc 测试")
class ConfigControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ConfigService configService;

  @MockBean
  private ConfigBatchService configBatchService;

  @MockBean
  private ExcelWebSupport excelWebSupport;

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("GET /config/page 应返回 200 且响应体包含 code 字段")
  void page_shouldReturnOkWithCode() throws Exception {
    ConfigVO vo = new ConfigVO();
    vo.setConfigKey("test.key");
    PageResponse<List<ConfigVO>> pageResponse = new PageResponse<>();
    pageResponse.setRecords(List.of(vo));
    pageResponse.setTotal(1L);

    when(configService.page(any(ConfigPageQuery.class))).thenReturn(pageResponse);

    mockMvc
        .perform(get("/config/page").param("pageNum", "1").param("pageSize", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS))
        .andExpect(jsonPath("$.data.records[0].configKey").value("test.key"));
  }

  @Test
  @DisplayName("POST /config 缺少必填字段 configKey 应返回 400 + 参数校验错误码")
  void save_withBlankKey_shouldReturn400WithErrorBody() throws Exception {
    mockMvc
        .perform(
            post("/config")
                .contentType("application/json")
                .content("{\"configGroup\":\"test\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(CoreExceptionCode.PARAM_ERROR.getCode()))
        .andExpect(jsonPath("$.msg").exists());
  }

  @Test
  @DisplayName("GET /config/key/{key} 业务异常应返回 YdszResponse 错误码包装")
  void getByKey_whenBusinessException_shouldReturnErrorWrapped() throws Exception {
    when(configService.getConfigValue("not.exist.key"))
        .thenThrow(new BusinessException(CoreExceptionCode.DATA_NOT_FOUND));

    mockMvc
        .perform(get("/config/key/not.exist.key"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(CoreExceptionCode.DATA_NOT_FOUND.getCode()))
        .andExpect(jsonPath("$.msg").exists());
  }
}
