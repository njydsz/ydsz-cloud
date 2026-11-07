package com.njydsz.cronjob.web.controller.dag;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.server.service.dag.JobDagInstanceService;
import com.njydsz.cronjob.server.vo.DagInstanceVisualizationVO;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link JobDagInstanceController} Smoke Test。
 *
 * <p>覆盖 DAG 实例查询/控制端点 {@code /cronjob/dag/instance/**}：
 *
 * <ul>
 *   <li>GET {@code /{instanceId}} — 实例详情
 *   <li>GET {@code /dag/{dagId}} — 按 DAG 查实例（limit 默认 20）
 *   <li>GET {@code /status/{status}} — 按状态查实例
 *   <li>GET {@code /{instanceId}/nodes} — 节点列表
 *   <li>GET {@code /{instanceId}/visualization} — 可视化数据
 *   <li>GET {@code /{instanceId}/mermaid} — Mermaid 文本
 *   <li>PUT {@code /{instanceId}/pause|resume|cancel|context} — 运行时控制
 * </ul>
 *
 * <p>验证路径变量/请求参数绑定、{@code limit} 默认值、成功信封字段、 以及 Service 抛 {@link SysException} 时的错误传播（无 advice 时异常冒泡）。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class JobDagInstanceControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private JobDagInstanceService jobDagInstanceService;

  @org.mockito.InjectMocks
  private JobDagInstanceController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  private JobDagInstanceVO instanceVo() {
    JobDagInstanceVO vo = new JobDagInstanceVO();
    vo.setId("inst-001");
    vo.setDagId("dag-001");
    vo.setDagKey("demo-dag");
    vo.setInstanceStatus("RUNNING");
    vo.setTriggerType("MANUAL");
    vo.setTriggerBy("user-001");
    vo.setTotalNodes(3);
    vo.setSuccessNodes(1);
    vo.setFailedNodes(0);
    return vo;
  }

  private JobDagNodeInstanceVO nodeVo() {
    JobDagNodeInstanceVO vo = new JobDagNodeInstanceVO();
    vo.setId("node-inst-001");
    vo.setDagInstanceId("inst-001");
    vo.setJobKey("job-key-A");
    vo.setNodeStatus("SUCCESS");
    return vo;
  }

  @Nested
  @DisplayName("GET /cronjob/dag/instance/{instanceId}")
  class GetInstance {

    @Test
    @DisplayName("should return instance detail in success envelope")
    void should_return_instance_detail() throws Exception {
      when(jobDagInstanceService.getInstanceById("inst-001")).thenReturn(instanceVo());

      mockMvc.perform(get("/cronjob/dag/instance/{instanceId}", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("inst-001"))
          .andExpect(jsonPath("$.data.instanceStatus").value("RUNNING"));
    }

    @Test
    @DisplayName("should propagate SysException when instance not found (no advice)")
    void should_propagate_not_found() throws Exception {
      when(jobDagInstanceService.getInstanceById("inst-404"))
          .thenThrow(new SysException("instance not found"));

      mockMvc.perform(get("/cronjob/dag/instance/{instanceId}", "inst-404"))
          .andExpect(status().is5xxServerError());
    }
  }

  @Nested
  @DisplayName("GET /cronjob/dag/instance/dag/{dagId}")
  class ListByDag {

    @Test
    @DisplayName("should default limit to 20 when param absent")
    void should_default_limit() throws Exception {
      when(jobDagInstanceService.listByDagId("dag-001", 20)).thenReturn(List.of(instanceVo()));

      mockMvc.perform(get("/cronjob/dag/instance/dag/{dagId}", "dag-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].id").value("inst-001"));

      verify(jobDagInstanceService).listByDagId("dag-001", 20);
    }

    @Test
    @DisplayName("should bind explicit limit param")
    void should_bind_limit_param() throws Exception {
      when(jobDagInstanceService.listByDagId("dag-001", 5)).thenReturn(List.of());

      mockMvc.perform(get("/cronjob/dag/instance/dag/{dagId}", "dag-001").param("limit", "5"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").isEmpty());

      verify(jobDagInstanceService).listByDagId("dag-001", 5);
    }
  }

  @Nested
  @DisplayName("GET /cronjob/dag/instance/status/{status}")
  class ListByStatus {

    @Test
    @DisplayName("should return instances filtered by status")
    void should_list_by_status() throws Exception {
      when(jobDagInstanceService.listByStatus("FAILED")).thenReturn(List.of(instanceVo()));

      mockMvc.perform(get("/cronjob/dag/instance/status/{status}", "FAILED"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].instanceStatus").value("RUNNING"));

      verify(jobDagInstanceService).listByStatus("FAILED");
    }
  }

  @Nested
  @DisplayName("GET /cronjob/dag/instance/{instanceId}/nodes")
  class ListNodes {

    @Test
    @DisplayName("should return node list for instance")
    void should_return_nodes() throws Exception {
      when(jobDagInstanceService.listNodes("inst-001")).thenReturn(List.of(nodeVo()));

      mockMvc.perform(get("/cronjob/dag/instance/{instanceId}/nodes", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].jobKey").value("job-key-A"))
          .andExpect(jsonPath("$.data[0].nodeStatus").value("SUCCESS"));
    }
  }

  @Nested
  @DisplayName("GET /cronjob/dag/instance/{instanceId}/visualization|mermaid")
  class Visualization {

    @Test
    @DisplayName("should return visualization VO in success envelope")
    void should_return_visualization() throws Exception {
      DagInstanceVisualizationVO vo = new DagInstanceVisualizationVO();
      vo.setInstance(instanceVo());
      vo.setNodeInstances(List.of(nodeVo()));
      when(jobDagInstanceService.getVisualization("inst-001")).thenReturn(vo);

      mockMvc.perform(get("/cronjob/dag/instance/{instanceId}/visualization", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.instance.id").value("inst-001"))
          .andExpect(jsonPath("$.data.nodeInstances[0].jobKey").value("job-key-A"));
    }

    @Test
    @DisplayName("should return mermaid text")
    void should_return_mermaid() throws Exception {
      when(jobDagInstanceService.getMermaidDiagram("inst-001"))
          .thenReturn("```mermaid\ngraph TD\n  A-->B\n```");

      mockMvc.perform(get("/cronjob/dag/instance/{instanceId}/mermaid", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("```mermaid\ngraph TD\n  A-->B\n```"));
    }
  }

  @Nested
  @DisplayName("PUT /cronjob/dag/instance/{instanceId}/pause|resume|cancel")
  class Control {

    @Test
    @DisplayName("should pause instance and return success(null) envelope")
    void should_pause() throws Exception {
      mockMvc.perform(put("/cronjob/dag/instance/{instanceId}/pause", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(jobDagInstanceService).pauseInstance("inst-001");
    }

    @Test
    @DisplayName("should resume instance")
    void should_resume() throws Exception {
      mockMvc.perform(put("/cronjob/dag/instance/{instanceId}/resume", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(jobDagInstanceService).resumeInstance("inst-001");
    }

    @Test
    @DisplayName("should cancel instance")
    void should_cancel() throws Exception {
      mockMvc.perform(put("/cronjob/dag/instance/{instanceId}/cancel", "inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(jobDagInstanceService).cancelInstance("inst-001");
    }

    @Test
    @DisplayName("should propagate SysException from pauseInstance (no advice)")
    void should_propagate_pause_exception() throws Exception {
      doThrow(new SysException("instance not RUNNING")).when(jobDagInstanceService).pauseInstance("inst-001");

      mockMvc.perform(put("/cronjob/dag/instance/{instanceId}/pause", "inst-001"))
          .andExpect(status().is5xxServerError());
    }
  }

  @Nested
  @DisplayName("PUT /cronjob/dag/instance/{instanceId}/context")
  class UpdateContext {

    @Test
    @DisplayName("should bind raw JSON string body and delegate to service")
    void should_update_context() throws Exception {
      mockMvc.perform(
              put("/cronjob/dag/instance/{instanceId}/context", "inst-001")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"orderId\":\"o-1\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(jobDagInstanceService).updateContext(eq("inst-001"), eq("{\"orderId\":\"o-1\"}"));
    }

    @Test
    @DisplayName("should propagate SysException when instance missing")
    void should_propagate_update_exception() throws Exception {
      doThrow(new SysException("instance not found"))
          .when(jobDagInstanceService)
          .updateContext(any(), any());

      mockMvc.perform(
              put("/cronjob/dag/instance/{instanceId}/context", "inst-404")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().is5xxServerError());
    }
  }
}
