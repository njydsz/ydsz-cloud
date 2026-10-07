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
import com.njydsz.system.domain.query.DictItemPageQuery;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.server.service.DictItemBatchService;
import com.njydsz.system.server.service.DictItemImportService;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.system.web.controller.DictItemController;
import com.njydsz.system.web.handler.SystemExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link DictItemController} MockMvc 集成测试。
 *
 * <p>覆盖场景：分页查询接口存在性、参数校验失败（400）、业务异常错误码包装。
 *
 * @author ydsz
 * @since 26.10.06
 */
@WebMvcTest(DictItemController.class)
@Import(SystemExceptionHandler.class)
@DisplayName("DictItemController - 字典项 MockMvc 测试")
class DictItemControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private DictItemService dictItemService;

  @MockitoBean
  private DictItemBatchService dictItemBatchService;

  @MockitoBean
  private DictItemImportService dictItemImportService;

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("GET /dict/item/page 应返回 200 且包含 code 字段")
  void page_shouldReturnOkWithCode() throws Exception {
    DictItemVO vo = new DictItemVO();
    vo.setItemCode("PAID");
    PageResponse<List<DictItemVO>> pageResponse = new PageResponse<>();
    pageResponse.setRecords(List.of(vo));
    pageResponse.setTotal(1L);

    when(dictItemService.page(any(DictItemPageQuery.class))).thenReturn(pageResponse);

    mockMvc
        .perform(get("/dict/item/page").param("pageNum", "1").param("pageSize", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS))
        .andExpect(jsonPath("$.data.records[0].itemCode").value("PAID"));
  }

  @Test
  @DisplayName("POST /dict/item 缺少必填字段 typeCode 应返回 400 + 参数校验错误码")
  void save_withBlankTypeCode_shouldReturn400WithErrorBody() throws Exception {
    mockMvc
        .perform(
            post("/dict/item")
                .contentType("application/json")
                .content("{\"itemCode\":\"test\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(CoreExceptionCode.PARAM_ERROR.getCode()))
        .andExpect(jsonPath("$.msg").exists());
  }

  @Test
  @DisplayName("GET /dict/item/type/{typeCode} 业务异常应返回 YdszResponse 错误码包装")
  void listByType_whenBusinessException_shouldReturnErrorWrapped() throws Exception {
    when(dictItemService.listEnabledByTypeCode("not-exist-type"))
        .thenThrow(new BusinessException(CoreExceptionCode.DATA_NOT_FOUND));

    mockMvc
        .perform(get("/dict/item/type/not-exist-type"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(CoreExceptionCode.DATA_NOT_FOUND.getCode()));
  }
}
