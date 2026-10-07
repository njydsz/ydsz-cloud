package com.njydsz.agent.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.agent.domain.dto.DagWorkflowDTO;
import com.njydsz.agent.domain.service.DagWorkflowService;
import com.njydsz.agent.domain.vo.DagWorkflowVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.json.YdszJson;

/**
 * DagWorkflowController 集成测试。
 *
 * <p>使用 MockMvcBuilders.standaloneSetup + MockitoExtension 测试 DAG 工作流持久化接口。
 */
@ExtendWith(MockitoExtension.class)
class DagWorkflowControllerTest {

  private MockMvc mockMvc;

  @Mock
  private DagWorkflowService dagWorkflowService;

  @InjectMocks
  private DagWorkflowController dagWorkflowController;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(dagWorkflowController).build();
  }

  @Nested
  @DisplayName("POST /agent/dag-workflow/save")
  class Save {

    @Test
    @DisplayName("保存工作流 — 新建成功返回 ID")
    void shouldSaveNewWorkflow() throws Exception {
      DagWorkflowDTO dto = new DagWorkflowDTO();
      dto.setWorkflowName("测试工作流");
      dto.setDslContent("nodes: []");
      dto.setCategory("test");

      when(dagWorkflowService.save(any(DagWorkflowDTO.class))).thenReturn("dag-123456");

      String json = YdszJson.toJson(dto);
      mockMvc.perform(post("/agent/dag-workflow/save")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value("dag-123456"));

      verify(dagWorkflowService, times(1)).save(any(DagWorkflowDTO.class));
    }
  }

  @Nested
  @DisplayName("GET /agent/dag-workflow/{code}")
  class GetByCode {

    @Test
    @DisplayName("根据编码查询工作流 — 返回 VO")
    void shouldReturnWorkflowByCode() throws Exception {
      DagWorkflowVO vo = new DagWorkflowVO();
      vo.setId("1");
      vo.setWorkflowCode("dag-001");
      vo.setName("订单分析工作流");
      vo.setDescription("分析订单数据");
      vo.setCategory("order");
      vo.setCreatedBy("admin");
      vo.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));

      when(dagWorkflowService.getByCode("dag-001")).thenReturn(vo);

      mockMvc.perform(get("/agent/dag-workflow/{code}", "dag-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.workflowCode").value("dag-001"))
          .andExpect(jsonPath("$.data.name").value("订单分析工作流"))
          .andExpect(jsonPath("$.data.category").value("order"));

      verify(dagWorkflowService, times(1)).getByCode("dag-001");
    }

    @Test
    @DisplayName("查询不存在的工作流 — 返回 null data")
    void shouldReturnNullForMissingCode() throws Exception {
      when(dagWorkflowService.getByCode("dag-missing")).thenReturn(null);

      mockMvc.perform(get("/agent/dag-workflow/{code}", "dag-missing"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(dagWorkflowService).getByCode("dag-missing");
    }
  }

  @Nested
  @DisplayName("GET /agent/dag-workflow/list")
  class ListWorkflows {

    @Test
    @DisplayName("查询工作流列表 — 按分类筛选")
    void shouldReturnWorkflowList() throws Exception {
      DagWorkflowVO vo1 = new DagWorkflowVO();
      vo1.setWorkflowCode("dag-001");
      vo1.setName("工作流A");
      DagWorkflowVO vo2 = new DagWorkflowVO();
      vo2.setWorkflowCode("dag-002");
      vo2.setName("工作流B");

      when(dagWorkflowService.listByCategory("report")).thenReturn(java.util.List.of(vo1, vo2));

      mockMvc.perform(get("/agent/dag-workflow/list").param("category", "report"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").isArray())
          .andExpect(jsonPath("$.data.length()").value(2));

      verify(dagWorkflowService).listByCategory("report");
    }

    @Test
    @DisplayName("查询工作流列表 — 不传分类参数返回全量")
    void shouldReturnAllWhenNoCategory() throws Exception {
      when(dagWorkflowService.listByCategory(null)).thenReturn(java.util.List.of());

      mockMvc.perform(get("/agent/dag-workflow/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").isArray());

      verify(dagWorkflowService).listByCategory(null);
    }
  }

  @Nested
  @DisplayName("DELETE /agent/dag-workflow/{code}")
  class Delete {

    @Test
    @DisplayName("删除工作流 — 返回成功")
    void shouldDeleteWorkflow() throws Exception {
      when(dagWorkflowService.deleteByCode("dag-001")).thenReturn(true);

      mockMvc.perform(delete("/agent/dag-workflow/{code}", "dag-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value(true));

      verify(dagWorkflowService, times(1)).deleteByCode("dag-001");
    }
  }
}
