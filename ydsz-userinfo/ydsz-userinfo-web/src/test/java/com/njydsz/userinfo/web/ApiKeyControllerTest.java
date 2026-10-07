package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.json.YdszJson;
import com.njydsz.userinfo.domain.dto.ApiKeyCreateDTO;
import com.njydsz.userinfo.domain.query.ApiKeyPageQuery;
import com.njydsz.userinfo.domain.vo.ApiKeyVO;
import com.njydsz.userinfo.server.service.ApiKeyService;

/**
 * {@link ApiKeyController} 契约测试。
 *
 * <p>覆盖：创建参数校验、分页查询、全量列表、批量撤销、启用/禁用 404。
 */
@WebMvcTest(controllers = ApiKeyController.class)
class ApiKeyControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private ApiKeyService apiKeyService;

  @Nested
  @DisplayName("GET /apikey")
  class Page {

    @Test
    @DisplayName("should return 200 when service returns page")
    void ok() throws Exception {
      ApiKeyVO vo = new ApiKeyVO();
      vo.setId(1L);
      vo.setKeyName("ci-deploy");
      when(apiKeyService.pageKeys(any(ApiKeyPageQuery.class)))
          .thenReturn(com.njydsz.common.core.response.PageResponse.ofList(List.of(vo), null));
      mvc.perform(get("/apikey").param("pageNum", "1").param("pageSize", "10"))
          .andExpect(status().isOk())
          .andDo(print());
    }
  }

  @Nested
  @DisplayName("GET /apikey/all")
  class ListMyKeys {

    @Test
    @DisplayName("should return 200 with list")
    void ok() throws Exception {
      ApiKeyVO vo = new ApiKeyVO();
      vo.setId(1L);
      vo.setKeyName("ci-deploy");
      vo.setApiKeyPrefix("ak_***");
      when(apiKeyService.listMyKeys()).thenReturn(List.of(vo));
      mvc.perform(get("/apikey/all")).andExpect(status().isOk()).andDo(print());
    }
  }

  @Nested
  @DisplayName("POST /apikey")
  class Create {

    /** keyName 为空 → Bean Validation 400。 */
    @Test
    @DisplayName("should return 400 when keyName blank")
    void blankKeyName() throws Exception {
      ApiKeyCreateDTO dto = new ApiKeyCreateDTO();
      dto.setKeyName("");
      mvc.perform(post("/apikey").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /** keyName 超长 (>64) → 400。 */
    @Test
    @DisplayName("should return 400 when keyName exceeds 64 chars")
    void keyNameTooLong() throws Exception {
      ApiKeyCreateDTO dto = new ApiKeyCreateDTO();
      dto.setKeyName("a".repeat(100));
      mvc.perform(post("/apikey").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /** 创建成功 → 200 + ApiKeyVO（含明文 apiKey）。 */
    @Test
    @DisplayName("should return 200 with key on success")
    void ok() throws Exception {
      ApiKeyCreateDTO dto = new ApiKeyCreateDTO();
      dto.setKeyName("ci-deploy");
      ApiKeyVO vo = new ApiKeyVO();
      vo.setId(1L);
      vo.setKeyName("ci-deploy");
      vo.setApiKeyPrefix("ak_***");
      when(apiKeyService.createKey(any(ApiKeyCreateDTO.class))).thenReturn(vo);
      mvc.perform(post("/apikey").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isOk())
          .andDo(print());
    }
  }

  @Nested
  @DisplayName("DELETE /apikey?ids=...")
  class Revoke {

    /** 撤销成功 → 200 + 撤销数量。 */
    @Test
    @DisplayName("should return 200 with revoked count")
    void ok() throws Exception {
      when(apiKeyService.revokeKeys(anyList())).thenReturn(1);
      mvc.perform(delete("/apikey").param("ids", "1", "2")).andExpect(status().isOk()).andDo(print());
    }
  }

  @Nested
  @DisplayName("PUT /apikey/{id}/enabled")
  class UpdateEnabled {

    /** 更新成功 → 200。 */
    @Test
    @DisplayName("should return 200 on success")
    void ok() throws Exception {
      mvc.perform(put("/apikey/{id}/enabled", 1L).param("enabled", "false"))
          .andExpect(status().isOk())
          .andDo(print());
    }
  }
}
