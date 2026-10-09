package com.njydsz.system.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.server.service.DictService;

/**
 * {@link DictController} Smoke Test。
 *
 * <p>验证每个端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class DictControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;
  private DictService dictService;

  @BeforeEach
  void setUp() {
    dictService = org.mockito.Mockito.mock(DictService.class);
    DictController dictController = new DictController(dictService);
    mockMvc = standaloneSetup(dictController);
  }

  @Nested
  @DisplayName("GET /dict/type/page")
  class Page {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void page_returns200() throws Exception {
      DictTypeVO vo = new DictTypeVO();
      vo.setId("dict-001");
      vo.setTypeCode("ORDER_STATUS");
      PageResponse<List<DictTypeVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(dictService.page(any(DictPageQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/dict/type/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /dict/type/{id}")
  class GetById {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void getById_returns200() throws Exception {
      DictTypeVO vo = new DictTypeVO();
      vo.setId("dict-001");
      vo.setTypeCode("ORDER_STATUS");
      when(dictService.getById("dict-001")).thenReturn(vo);

      mockMvc.perform(get("/dict/type/{id}", "dict-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /dict/type")
  class Save {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void save_returns200() throws Exception {
      when(dictService.save(any(DictTypeDTO.class))).thenReturn("dict-new");

      String body = "{\"typeCode\":\"NEW_TYPE\",\"typeName\":\"新字典类型\",\"status\":\"ENABLED\"}";

      mockMvc.perform(post("/dict/type").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("PUT /dict/type")
  class Update {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void update_returns200() throws Exception {
      when(dictService.updateById(any(DictTypeDTO.class))).thenReturn(true);

      String body = "{\"id\":\"dict-001\",\"typeCode\":\"ORDER_STATUS\",\"typeName\":\"订单状态\",\"status\":\"ENABLED\"}";

      mockMvc.perform(put("/dict/type").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("DELETE /dict/type/{id}")
  class Remove {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void remove_returns200() throws Exception {
      when(dictService.removeById("dict-001")).thenReturn(true);

      mockMvc.perform(delete("/dict/type/{id}", "dict-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /dict/type/all")
  class ListAll {

    @Test
    @DisplayName("should return 200 with YdszResponse envelope")
    void listAll_returns200() throws Exception {
      when(dictService.listAll()).thenReturn(List.of());

      mockMvc.perform(get("/dict/type/all"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
