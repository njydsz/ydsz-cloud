package com.njydsz.system.web.controller;

import static org.mockito.ArgumentMatchers.any;
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

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.query.DictItemPageQuery;
import com.njydsz.system.server.service.DictItemBatchService;
import com.njydsz.system.server.service.DictItemImportService;
import com.njydsz.system.server.service.DictItemService;

/**
 * {@link DictItemController} Smoke Test。
 *
 * <p>验证字典项查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DictItemControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private DictItemService dictItemService;

  @org.mockito.Mock
  private DictItemBatchService dictItemBatchService;

  @org.mockito.Mock
  private DictItemImportService dictItemImportService;

  @org.mockito.InjectMocks
  private DictItemController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(dictItemService.page(any(DictItemPageQuery.class)))
        .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));
    when(dictItemService.getById("item-001")).thenReturn(null);
    when(dictItemService.listEnabledByTypeCode("order_status")).thenReturn(List.of());
    when(dictItemService.listChildren("0")).thenReturn(List.of());
    when(dictItemService.buildTree("region")).thenReturn(List.of());
  }

  @Nested
  @DisplayName("GET /dict/item/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      mockMvc.perform(get("/dict/item/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /dict/item/type/{typeCode}")
  class ListByType {

    @Test
    @DisplayName("should return 200 when list by type succeeds")
    void should_return_200_when_list_by_type_succeeds() throws Exception {
      mockMvc.perform(get("/dict/item/type/{typeCode}", "order_status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /dict/item/lookup")
  class Lookup {

    @Test
    @DisplayName("should return 200 when lookup succeeds")
    void should_return_200_when_lookup_succeeds() throws Exception {
      when(dictItemService.getByTypeAndCode("order_status", "PAID")).thenReturn(null);

      mockMvc.perform(get("/dict/item/lookup")
              .param("typeCode", "order_status")
              .param("itemCode", "PAID"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /dict/item/tree/{typeCode}")
  class BuildTree {

    @Test
    @DisplayName("should return 200 when build tree succeeds")
    void should_return_200_when_build_tree_succeeds() throws Exception {
      mockMvc.perform(get("/dict/item/tree/{typeCode}", "region"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
