package com.njydsz.generator.web.controller;


import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.service.DatasourceService;
import com.njydsz.generator.service.TableMetadataService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link TableMetaController} Smoke Test。
 *
 * <p>验证表元数据端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class TableMetaControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private DatasourceService datasourceService;

  private TableMetadataService tableMetadataService;

  @BeforeEach
  void setUp() {
    datasourceService = mock(DatasourceService.class);
    tableMetadataService = mock(TableMetadataService.class);
    TableMetaController controller = new TableMetaController(datasourceService, tableMetadataService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /generator/tables")
  class ListTables {

    @Test
    @DisplayName("should return 200 when list tables succeeds")
    void should_return_200_when_list_tables_succeeds() throws Exception {
      GenTableMeta meta = new GenTableMeta();
      meta.setId("table-001");
      meta.setTableName("ydsz_user");
      when(tableMetadataService.listCachedTables("ds-001")).thenReturn(List.of(meta));

      mockMvc.perform(get("/generator/tables").param("datasourceId", "ds-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /generator/tables/refresh")
  class RefreshTables {

    @Test
    @DisplayName("should return 200 when refresh tables succeeds")
    void should_return_200_when_refresh_tables_succeeds() throws Exception {
      GenDatasource ds = new GenDatasource();
      ds.setId("ds-001");
      when(datasourceService.getById("ds-001")).thenReturn(ds);

      GenTableMeta meta = new GenTableMeta();
      meta.setId("table-001");
      when(tableMetadataService.refreshTables(ds)).thenReturn(List.of(meta));

      mockMvc.perform(post("/generator/tables/refresh").param("datasourceId", "ds-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /generator/tables/columns")
  class GetColumns {

    @Test
    @DisplayName("should return 200 when get columns succeeds")
    void should_return_200_when_get_columns_succeeds() throws Exception {
      GenColumnMeta column = new GenColumnMeta();
      column.setId("col-001");
      column.setColumnName("id");
      when(tableMetadataService.listColumns("table-001")).thenReturn(List.of(column));

      mockMvc.perform(get("/generator/tables/columns").param("tableMetaId", "table-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /generator/tables/columns/refresh")
  class RefreshColumns {

    @Test
    @DisplayName("should return 200 when refresh columns succeeds")
    void should_return_200_when_refresh_columns_succeeds() throws Exception {
      GenDatasource ds = new GenDatasource();
      ds.setId("ds-001");
      when(datasourceService.getById("ds-001")).thenReturn(ds);

      GenTableMeta tableMeta = new GenTableMeta();
      tableMeta.setId("table-001");
      when(tableMetadataService.getOrRefresh(ds, "ydsz_user")).thenReturn(tableMeta);

      GenColumnMeta column = new GenColumnMeta();
      column.setId("col-001");
      when(tableMetadataService.refreshColumns(ds, tableMeta)).thenReturn(List.of(column));

      mockMvc.perform(
              post("/generator/tables/columns/refresh")
                  .param("datasourceId", "ds-001")
                  .param("tableName", "ydsz_user"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
