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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.service.DatasourceService;
import com.njydsz.generator.service.TableMetadataService;

/**
 * {@link TableMetaController} 单元测试。
 *
 * <p>覆盖表元数据查询与刷新路径。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class TableMetaControllerTest {

  private MockMvc mockMvc;

  @Mock
  private DatasourceService datasourceService;

  @Mock
  private TableMetadataService tableMetadataService;

  @InjectMocks
  private TableMetaController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 查询数据源下表元数据返回 200。
   */
  @Test
  @DisplayName("GET /generator/tables 查询表列表返回 200")
  void listTablesReturnsOk() throws Exception {
    GenTableMeta meta = new GenTableMeta();
    meta.setId(1L);
    meta.setTableName("ydsz_user");

    when(tableMetadataService.listCachedTables(any(Long.class))).thenReturn(List.of(meta));

    mockMvc.perform(get("/generator/tables").param("datasourceId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Error-path: 刷新元数据时数据源不存在抛出异常。
   */
  @Test
  @DisplayName("POST /generator/tables/refresh 数据源不存在抛出异常")
  void refreshTablesWithNonexistentDatasourceThrowsException() throws Exception {
    when(datasourceService.getById(any(Long.class))).thenReturn(null);

    mockMvc.perform(post("/generator/tables/refresh").param("datasourceId", "999"))
        .andExpect(status().isInternalServerError());
  }
}
