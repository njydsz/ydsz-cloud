package com.njydsz.generator.web.controller;


import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.service.DatasourceService;
import com.njydsz.generator.vo.GenDatasourceRespVO;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link DatasourceController} Smoke Test。
 *
 * <p>验证数据源管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，
 * 防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class DatasourceControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private DatasourceService datasourceService;

  @BeforeEach
  void setUp() {
    datasourceService = mock(DatasourceService.class);
    DatasourceController controller = new DatasourceController(datasourceService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /generator/datasources")
  class ListDatasources {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      GenDatasourceRespVO vo = new GenDatasourceRespVO();
      vo.setId("ds-001");
      vo.setName("测试数据源");
      when(datasourceService.listAllVO()).thenReturn(List.of(vo));

      mockMvc.perform(get("/generator/datasources"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /generator/datasources/default")
  class GetDefault {

    @Test
    @DisplayName("should return 200 when get default succeeds")
    void should_return_200_when_get_default_succeeds() throws Exception {
      GenDatasourceRespVO vo = new GenDatasourceRespVO();
      vo.setId("ds-default");
      when(datasourceService.getDefaultVO()).thenReturn(vo);

      mockMvc.perform(get("/generator/datasources/default"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /generator/datasources/test")
  class TestConnection {

    @Test
    @DisplayName("should return 200 when test connection succeeds")
    void should_return_200_when_test_connection_succeeds() throws Exception {
      when(datasourceService.testConnection(any(GenDatasource.class))).thenReturn(true);

      mockMvc.perform(post("/generator/datasources/test")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"test\",\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\",\"username\":\"postgres\",\"password\":\"secret\",\"dialect\":\"postgresql\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /generator/datasources")
  class Create {

    @Test
    @DisplayName("should return 200 when create succeeds")
    void should_return_200_when_create_succeeds() throws Exception {
      GenDatasourceRespVO vo = new GenDatasourceRespVO();
      vo.setId("ds-new");
      when(datasourceService.createAndReturnVO(any(GenDatasource.class))).thenReturn(vo);

      mockMvc.perform(post("/generator/datasources")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"test\",\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\",\"username\":\"postgres\",\"password\":\"secret\",\"dialect\":\"postgresql\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("DELETE /generator/datasources/{id}")
  class Delete {

    @Test
    @DisplayName("should return 200 when delete succeeds")
    void should_return_200_when_delete_succeeds() throws Exception {
      mockMvc.perform(delete("/generator/datasources/{id}", "ds-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
