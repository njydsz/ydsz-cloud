package com.njydsz.system.web.controller;

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
import com.njydsz.system.domain.query.EntityVersionPageQuery;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.system.server.service.EntityVersionService;

/**
 * {@link DictVersionController} Smoke Test。
 *
 * <p>验证字典版本查询端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DictVersionControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private EntityVersionService entityVersionService;

  @org.mockito.Mock
  private DictItemService dictItemService;

  @org.mockito.InjectMocks
  private DictVersionController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(entityVersionService.listByResourceTypeAndKey(
        EntityVersionService.RESOURCE_TYPE_DICT, "order_status"))
        .thenReturn(List.of());
    when(entityVersionService.pageByResourceTypeAndKey(any(EntityVersionPageQuery.class)))
        .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));
  }

  @Nested
  @DisplayName("GET /dict/version/{typeCode}")
  class ListByTypeCode {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      mockMvc.perform(get("/dict/version/{typeCode}", "order_status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /dict/version/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      mockMvc.perform(get("/dict/version/page")
              .param("resourceType", "DICT")
              .param("resourceKey", "order_status")
              .param("pageNum", "1")
              .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T any(Class<T> clazz) {
    return org.mockito.ArgumentMatchers.any(clazz);
  }
}
