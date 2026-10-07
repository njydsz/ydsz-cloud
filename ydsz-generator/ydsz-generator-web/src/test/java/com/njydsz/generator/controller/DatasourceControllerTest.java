package com.njydsz.generator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.service.DatasourceService;
import com.njydsz.generator.vo.GenDatasourceRespVO;

/**
 * {@link DatasourceController} 单元测试。
 *
 * <p>覆盖数据源列表查询与创建路径。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class DatasourceControllerTest {

  private MockMvc mockMvc;

  @Mock
  private DatasourceService datasourceService;

  @InjectMocks
  private DatasourceController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 查询全部数据源返回 200。
   */
  @Test
  @DisplayName("GET /generator/datasources 查询数据源列表返回 200")
  void listDatasourcesReturnsOk() throws Exception {
    when(datasourceService.listAllVO()).thenReturn(List.of());

    mockMvc.perform(get("/generator/datasources"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Happy-path: 创建新数据源返回 200。
   */
  @Test
  @DisplayName("POST /generator/datasources 创建数据源返回 200")
  void createDatasourceReturnsOk() throws Exception {
    GenDatasourceRespVO vo = new GenDatasourceRespVO();
    vo.setId(1L);
    vo.setName("测试数据源");

    when(datasourceService.createAndReturnVO(any(GenDatasource.class))).thenReturn(vo);

    String body = "{\"name\":\"测试数据源\","
        + "\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\","
        + "\"username\":\"postgres\",\"password\":\"secret\",\"dialect\":\"postgresql\"}";
    mockMvc.perform(post("/generator/datasources")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }
}
