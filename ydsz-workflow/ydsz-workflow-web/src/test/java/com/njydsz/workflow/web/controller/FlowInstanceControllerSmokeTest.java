package com.njydsz.workflow.web.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.query.FlowInstancePageQuery;
import com.njydsz.workflow.domain.vo.FlowAutoTriggerVO;
import com.njydsz.workflow.domain.vo.FlowBatchStartResultVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.server.service.DraftInstanceService;
import com.njydsz.workflow.server.service.FlowAutoTriggerService;
import com.njydsz.workflow.server.service.FlowInstanceMigrationService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.web.controller.instance.FlowInstanceController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FlowInstanceController} HTTP 契约测试。
 *
 * <p>覆盖流程实例统一 Controller 的核心端点：启动（单条/批量）、草稿（保存/提交/取消）、按业务查询、生命周期控制（终止/挂起/激活/撤回/回滚/重审）、
 * 审计与时间线（auditTrail/timeline/diagram/replay）、分页查询（page/my/all）、变量读写、催办（实例级/节点级）、表单渲染、实例迁移（执行/预览/自动映射）、
 * 自动触发规则（列出/创建/删除/启停）。
 *
 * <p>每个场景验证真实 HTTP 路径与方法、参数或请求体绑定、响应字段、服务调用 verify / ArgumentCaptor，以及参数校验负例。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class FlowInstanceControllerSmokeTest extends BaseControllerMockTest {

  private static final String INST_ID = "inst-001";
  private static final String USER_ID = "user-001";
  private static final String TENANT_ID = "1";

  private MockMvc mockMvc;

  @Mock
  private FlowInstanceService instanceService;

  @Mock
  private WorkflowFacade workflowFacade;

  @Mock
  private FlowInstanceMigrationService instanceMigrationService;

  @Mock
  private FlowAutoTriggerService autoTriggerService;

  @Mock
  private DraftInstanceService draftInstanceService;

  @InjectMocks
  private FlowInstanceController controller;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    LoginUser loginUser = new LoginUser();
    loginUser.setUserId(USER_ID);
    loginUser.setUsername("实例测试员");
    loginUser.setTenantId(TENANT_ID);
    AuthContextUtils.setCurrent(loginUser);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/start")
  class StartProcess {

    @Test
    @DisplayName("启动流程：绑定请求体并返回实例 ID")
    void should_start_process() throws Exception {
      String body = "{\"flowCode\":\"LEAVE_APPLY\",\"businessType\":\"LEAVE\",\"businessId\":\"biz-001\",\"title\":\"请假\"}";
      when(workflowFacade.startProcess(any(FlowStartProcessDTO.class))).thenReturn(INST_ID);

      mockMvc.perform(post("/workflow/engine/instance/start")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(INST_ID));

      ArgumentCaptor<FlowStartProcessDTO> captor = ArgumentCaptor.forClass(FlowStartProcessDTO.class);
      verify(workflowFacade).startProcess(captor.capture());
      assertThat(captor.getValue().getFlowCode()).isEqualTo("LEAVE_APPLY");
      assertThat(captor.getValue().getBusinessId()).isEqualTo("biz-001");
    }

    @Test
    @DisplayName("请求体缺少必填 businessType 触发校验负例（400）")
    void should_reject_body_missing_business_type() throws Exception {
      String body = "{\"flowCode\":\"LEAVE_APPLY\",\"businessId\":\"biz-001\"}";

      mockMvc.perform(post("/workflow/engine/instance/start")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());

      verify(workflowFacade, never()).startProcess(any(FlowStartProcessDTO.class));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/batchStart")
  class BatchStart {

    @Test
    @DisplayName("批量启动：绑定 DTO 列表并返回成功/失败统计")
    void should_batch_start() throws Exception {
      String body = "[{\"flowCode\":\"LEAVE_APPLY\",\"businessType\":\"LEAVE\",\"businessId\":\"biz-001\"},"
          + "{\"flowCode\":\"LEAVE_APPLY\",\"businessType\":\"LEAVE\",\"businessId\":\"biz-002\"}]";
      FlowBatchStartResultVO result = new FlowBatchStartResultVO();
      result.setSuccessCount(2);
      result.setFailedCount(0);
      result.setInstanceIds(List.of("inst-001", "inst-002"));
      when(instanceService.batchStartInstances(anyList())).thenReturn(result);

      mockMvc.perform(post("/workflow/engine/instance/batchStart")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.successCount").value(2))
          .andExpect(jsonPath("$.data.failedCount").value(0))
          .andExpect(jsonPath("$.data.instanceIds[0]").value("inst-001"));

      ArgumentCaptor<List<FlowStartProcessDTO>> captor = ArgumentCaptor.forClass(List.class);
      verify(instanceService).batchStartInstances(captor.capture());
      assertThat(captor.getValue()).hasSize(2);
      assertThat(captor.getValue().get(1).getBusinessId()).isEqualTo("biz-002");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/draft/save")
  class SaveDraft {

    @Test
    @DisplayName("保存草稿：绑定请求体并返回草稿实例 ID")
    void should_save_draft() throws Exception {
      String body = "{\"flowCode\":\"LEAVE_APPLY\",\"businessType\":\"LEAVE\",\"businessId\":\"biz-001\","
          + "\"draftData\":{\"dayCount\":3}}";
      when(draftInstanceService.saveDraft(any())).thenReturn("draft-inst-001");

      mockMvc.perform(post("/workflow/engine/instance/draft/save")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("draft-inst-001"));

      verify(draftInstanceService).saveDraft(any());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/draft/submit")
  class SubmitDraft {

    @Test
    @DisplayName("提交草稿：绑定 instanceId/draftData/operatorId 并透传服务")
    void should_submit_draft() throws Exception {
      String body = "{\"instanceId\":\"draft-inst-001\",\"operatorId\":\"user-001\",\"draftData\":{\"dayCount\":3}}";
      when(draftInstanceService.submitDraft("draft-inst-001", Map.of("dayCount", 3), "user-001"))
          .thenReturn("inst-submitted-001");

      mockMvc.perform(post("/workflow/engine/instance/draft/submit")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("inst-submitted-001"));

      verify(draftInstanceService).submitDraft("draft-inst-001", Map.of("dayCount", 3), "user-001");
    }

    @Test
    @DisplayName("请求体缺少 instanceId 触发校验负例（400）")
    void should_reject_body_missing_instance_id() throws Exception {
      String body = "{\"operatorId\":\"user-001\"}";

      mockMvc.perform(post("/workflow/engine/instance/draft/submit")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());

      verify(draftInstanceService, never()).submitDraft(anyString(), any(), anyString());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/{id}/draft/cancel")
  class CancelDraft {

    @Test
    @DisplayName("取消草稿：路径变量透传服务")
    void should_cancel_draft() throws Exception {
      mockMvc.perform(post("/workflow/engine/instance/{id}/draft/cancel", "draft-inst-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(draftInstanceService).cancelDraft("draft-inst-001");
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/byBusiness")
  class GetByBusiness {

    @Test
    @DisplayName("按业务类型与业务 ID 查询实例视图")
    void should_return_instance_view() throws Exception {
      when(workflowFacade.getByBusiness("LEAVE", "biz-001")).thenReturn(null);

      mockMvc.perform(get("/workflow/engine/instance/byBusiness")
              .param("businessType", "LEAVE")
              .param("businessId", "biz-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).getByBusiness("LEAVE", "biz-001");
    }

    @Test
    @DisplayName("缺少必填 businessId 触发负例（400）")
    void should_reject_missing_business_id() throws Exception {
      mockMvc.perform(get("/workflow/engine/instance/byBusiness").param("businessType", "LEAVE"))
          .andExpect(status().isBadRequest());

      verify(workflowFacade, never()).getByBusiness(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/{id}/terminate")
  class Terminate {

    @Test
    @DisplayName("终止流程：透传 reason 给门面")
    void should_terminate() throws Exception {
      mockMvc.perform(post("/workflow/engine/instance/{id}/terminate", INST_ID).param("reason", "发起人撤销"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).terminateProcess(INST_ID, "发起人撤销");
    }

    @Test
    @DisplayName("reason 缺省时为 null")
    void should_terminate_without_reason() throws Exception {
      mockMvc.perform(post("/workflow/engine/instance/{id}/terminate", INST_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).terminateProcess(INST_ID, null);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/{id}/suspend")
  class Suspend {

    @Test
    @DisplayName("挂起流程：调用门面 suspendProcess")
    void should_suspend() throws Exception {
      mockMvc.perform(post("/workflow/engine/instance/{id}/suspend", INST_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).suspendProcess(INST_ID);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/{id}/activate")
  class Activate {

    @Test
    @DisplayName("激活流程：调用门面 activateProcess")
    void should_activate() throws Exception {
      mockMvc.perform(post("/workflow/engine/instance/{id}/activate", INST_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).activateProcess(INST_ID);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/{id}/recall")
  class Recall {

    @Test
    @DisplayName("撤回流程：绑定可选 targetNodeCode 并返回布尔结果")
    void should_recall() throws Exception {
      when(instanceService.recall(INST_ID, USER_ID, "NODE_APPLY")).thenReturn(true);

      mockMvc.perform(post("/workflow/engine/instance/{id}/recall", INST_ID).param("targetNodeCode", "NODE_APPLY"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(instanceService).recall(INST_ID, USER_ID, "NODE_APPLY");
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/{id}/recallableNodes")
  class RecallableNodes {

    @Test
    @DisplayName("查询可撤回节点列表并透传发起人")
    void should_return_recallable_nodes() throws Exception {
      when(instanceService.listRecallableNodes(INST_ID, USER_ID))
          .thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/instance/{id}/recallableNodes", INST_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(instanceService).listRecallableNodes(INST_ID, USER_ID);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/{id}/rollback")
  class Rollback {

    @Test
    @DisplayName("回滚实例：绑定 reason 与 maxRollbackDays 默认值 7")
    void should_rollback_with_default_days() throws Exception {
      when(instanceService.rollback(INST_ID, USER_ID, "误操作", 7)).thenReturn(true);

      mockMvc.perform(post("/workflow/engine/instance/{id}/rollback", INST_ID).param("reason", "误操作"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(instanceService).rollback(INST_ID, USER_ID, "误操作", 7);
    }

    @Test
    @DisplayName("显式指定 maxRollbackDays 覆盖默认值")
    void should_rollback_with_custom_days() throws Exception {
      when(instanceService.rollback(INST_ID, USER_ID, "误操作", 30)).thenReturn(true);

      mockMvc.perform(post("/workflow/engine/instance/{id}/rollback", INST_ID)
              .param("reason", "误操作")
              .param("maxRollbackDays", "30"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(true));

      verify(instanceService).rollback(INST_ID, USER_ID, "误操作", 30);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/{id}/resubmit")
  class Resubmit {

    @Test
    @DisplayName("驳回后重审：绑定变量请求体与 redoMode 默认 RESTART")
    void should_resubmit_with_variables() throws Exception {
      String body = "{\"dayCount\":5}";
      when(workflowFacade.resubmitProcess(INST_ID, USER_ID, Map.of("dayCount", 5), "重新提交", "RESTART"))
          .thenReturn("inst-reborn-001");

      mockMvc.perform(post("/workflow/engine/instance/{id}/resubmit", INST_ID)
              .param("comment", "重新提交")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("inst-reborn-001"));

      verify(workflowFacade).resubmitProcess(INST_ID, USER_ID, Map.of("dayCount", 5), "重新提交", "RESTART");
    }

    @Test
    @DisplayName("无请求体时 variables 为 null，redoMode 显式 NEW_INSTANCE")
    void should_resubmit_without_body() throws Exception {
      when(workflowFacade.resubmitProcess(INST_ID, USER_ID, null, null, "NEW_INSTANCE"))
          .thenReturn("inst-reborn-002");

      mockMvc.perform(post("/workflow/engine/instance/{id}/resubmit", INST_ID).param("redoMode", "NEW_INSTANCE"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value("inst-reborn-002"));

      verify(workflowFacade).resubmitProcess(INST_ID, USER_ID, null, null, "NEW_INSTANCE");
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/{id}/auditTrail")
  class AuditTrail {

    @Test
    @DisplayName("审计轨迹查询：返回列表")
    void should_return_audit_trail() throws Exception {
      when(workflowFacade.listAuditTrail(INST_ID)).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/instance/{id}/auditTrail", INST_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).listAuditTrail(INST_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/{id}/timeline")
  class Timeline {

    @Test
    @DisplayName("时间线查询：返回列表")
    void should_return_timeline() throws Exception {
      when(workflowFacade.getTimeline(INST_ID)).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/instance/{id}/timeline", INST_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).getTimeline(INST_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/{id}/diagram")
  class Diagram {

    @Test
    @DisplayName("流程图查询：返回 definition/nodes/skips")
    void should_return_diagram() throws Exception {
      when(workflowFacade.getDiagram(INST_ID)).thenReturn(null);

      mockMvc.perform(get("/workflow/engine/instance/{id}/diagram", INST_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).getDiagram(INST_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/{id}/replay")
  class Replay {

    @Test
    @DisplayName("流程回放步骤序列：返回列表")
    void should_return_replay_steps() throws Exception {
      when(workflowFacade.getReplaySteps(INST_ID)).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/instance/{id}/replay", INST_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).getReplaySteps(INST_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/page")
  class InstancePage {

    @Test
    @DisplayName("多维分页：绑定全部过滤参数并装配 FlowInstancePageQuery")
    void should_bind_all_filters() throws Exception {
      FlowInstanceVO vo = new FlowInstanceVO();
      vo.setId(INST_ID);
      vo.setFlowCode("LEAVE_APPLY");
      PageResponse<List<FlowInstanceVO>> pageResult = PageResponse.success(1L, 2L, 10L, List.of(vo));
      when(instanceService.page(any(FlowInstancePageQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/workflow/engine/instance/page")
              .param("pageNo", "2")
              .param("pageSize", "10")
              .param("businessType", "LEAVE")
              .param("initiatorId", USER_ID)
              .param("flowStatus", "RUNNING")
              .param("startTime", "2026-01-01T00:00:00")
              .param("endTime", "2026-01-31T23:59:59"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.pageNum").value(2))
          .andExpect(jsonPath("$.data.pageSize").value(10))
          .andExpect(jsonPath("$.data.data[0].id").value(INST_ID));

      ArgumentCaptor<FlowInstancePageQuery> captor = ArgumentCaptor.forClass(FlowInstancePageQuery.class);
      verify(instanceService).page(captor.capture());
      FlowInstancePageQuery query = captor.getValue();
      assertThat(query.getPageNum()).isEqualTo(2);
      assertThat(query.getPageSize()).isEqualTo(10);
      assertThat(query.getBusinessType()).isEqualTo("LEAVE");
      assertThat(query.getInitiatorId()).isEqualTo(USER_ID);
      assertThat(query.getFlowStatus()).isEqualTo("RUNNING");
      assertThat(query.getStartTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0, 0));
      assertThat(query.getEndTime()).isEqualTo(LocalDateTime.of(2026, 1, 31, 23, 59, 59));
      assertThat(query.getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("startTime 非 ISO-8601 格式时绑定失败负例（400）")
    void should_reject_malformed_start_time() throws Exception {
      mockMvc.perform(get("/workflow/engine/instance/page").param("startTime", "not-a-date"))
          .andExpect(status().isBadRequest());

      verify(instanceService, never()).page(any(FlowInstancePageQuery.class));
    }

    @Test
    @DisplayName("pageSize 超过 100 触发参数校验负例（400）")
    void should_reject_page_size_over_max() throws Exception {
      mockMvc.perform(get("/workflow/engine/instance/page").param("pageSize", "101"))
          .andExpect(status().isBadRequest());

      verify(instanceService, never()).page(any(FlowInstancePageQuery.class));
    }

    @Test
    @DisplayName("显式 tenantId 覆盖登录态租户")
    void should_prefer_explicit_tenant_id() throws Exception {
      when(instanceService.page(any(FlowInstancePageQuery.class)))
          .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));

      mockMvc.perform(get("/workflow/engine/instance/page").param("tenantId", "tenant-9"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<FlowInstancePageQuery> captor = ArgumentCaptor.forClass(FlowInstancePageQuery.class);
      verify(instanceService).page(captor.capture());
      assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-9");
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/my")
  class InstanceMy {

    @Test
    @DisplayName("我发起的实例：initiatorId 取登录态，忽略 flowCode/flowName")
    void should_bind_login_user_as_initiator() throws Exception {
      PageResponse<List<FlowInstanceVO>> pageResult = PageResponse.success(0L, 1L, 20L, List.of());
      when(instanceService.page(any(FlowInstancePageQuery.class))).thenReturn(pageResult);

      mockMvc.perform(get("/workflow/engine/instance/my")
              .param("flowCode", "LEAVE_APPLY")
              .param("status", "RUNNING"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<FlowInstancePageQuery> captor = ArgumentCaptor.forClass(FlowInstancePageQuery.class);
      verify(instanceService).page(captor.capture());
      FlowInstancePageQuery query = captor.getValue();
      assertThat(query.getInitiatorId()).isEqualTo(USER_ID);
      assertThat(query.getFlowStatus()).isEqualTo("RUNNING");
      assertThat(query.getTenantId()).isEqualTo(TENANT_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/all")
  class InstanceAll {

    @Test
    @DisplayName("全部实例查询：返回 Map 分页结构")
    void should_return_all_instances() throws Exception {
      PageResponse<List<Map<String, Object>>> pageResult =
          PageResponse.success(1L, 1L, 20L, List.of(Map.of("id", INST_ID)));
      when(workflowFacade.listAllInstances(null, "RUNNING", null, null, 1, 20)).thenReturn(pageResult);

      mockMvc.perform(get("/workflow/engine/instance/all").param("flowStatus", "RUNNING"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.data[0].id").value(INST_ID));

      verify(workflowFacade).listAllInstances(null, "RUNNING", null, null, 1, 20);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/{id}/variables")
  class GetVariables {

    @Test
    @DisplayName("读取流程变量：返回变量 Map")
    void should_return_variables() throws Exception {
      when(instanceService.getVariables(INST_ID)).thenReturn(Map.of("dayCount", 3));

      mockMvc.perform(get("/workflow/engine/instance/{id}/variables", INST_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.dayCount").value(3));

      verify(instanceService).getVariables(INST_ID);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/{id}/variables")
  class SetVariables {

    @Test
    @DisplayName("写入流程变量：绑定 DTO 后透传 variables")
    void should_set_variables() throws Exception {
      String body = "{\"variables\":{\"dayCount\":5,\"level\":\"P2\"}}";

      mockMvc.perform(post("/workflow/engine/instance/{id}/variables", INST_ID)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
      verify(instanceService).setVariables(eq(INST_ID), captor.capture());
      assertThat(captor.getValue()).containsEntry("dayCount", 5).containsEntry("level", "P2");
    }

    @Test
    @DisplayName("variables 为 null 触发 @Valid 校验负例（400）")
    void should_reject_null_variables() throws Exception {
      String body = "{}";

      mockMvc.perform(post("/workflow/engine/instance/{id}/variables", INST_ID)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());

      verify(instanceService, never()).setVariables(anyString(), any());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/{id}/urge")
  class Urge {

    @Test
    @DisplayName("催办：绑定 comment 并返回被催办人列表")
    void should_urge() throws Exception {
      when(workflowFacade.urgeTask(INST_ID, USER_ID, "请尽快处理")).thenReturn(List.of("user-1001", "user-1002"));

      mockMvc.perform(post("/workflow/engine/instance/{id}/urge", INST_ID).param("comment", "请尽快处理"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0]").value("user-1001"))
          .andExpect(jsonPath("$.data[1]").value("user-1002"));

      verify(workflowFacade).urgeTask(INST_ID, USER_ID, "请尽快处理");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/{id}/urge/node")
  class UrgeByNode {

    @Test
    @DisplayName("节点级催办：绑定 nodeCode 与 comment")
    void should_urge_by_node() throws Exception {
      when(workflowFacade.urgeNodeTask(INST_ID, "NODE_DEPT_AUDIT", USER_ID, "催办"))
          .thenReturn(List.of("user-1001"));

      mockMvc.perform(post("/workflow/engine/instance/{id}/urge/node", INST_ID)
              .param("nodeCode", "NODE_DEPT_AUDIT")
              .param("comment", "催办"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0]").value("user-1001"));

      verify(workflowFacade).urgeNodeTask(INST_ID, "NODE_DEPT_AUDIT", USER_ID, "催办");
    }

    @Test
    @DisplayName("nodeCode 缺省时以 null 透传，退化为实例级催办")
    void should_urge_by_node_without_node_code() throws Exception {
      when(workflowFacade.urgeNodeTask(INST_ID, null, USER_ID, null)).thenReturn(List.of());

      mockMvc.perform(post("/workflow/engine/instance/{id}/urge/node", INST_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).urgeNodeTask(INST_ID, null, USER_ID, null);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/{id}/formRender")
  class FormRender {

    @Test
    @DisplayName("表单渲染数据：taskId 可选，返回 Map")
    void should_return_form_render_data() throws Exception {
      when(instanceService.getFormRenderData(INST_ID, "task-001"))
          .thenReturn(Map.of("nodeCode", "NODE_DEPT_AUDIT"));

      mockMvc.perform(get("/workflow/engine/instance/{id}/formRender", INST_ID).param("taskId", "task-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.nodeCode").value("NODE_DEPT_AUDIT"));

      verify(instanceService).getFormRenderData(INST_ID, "task-001");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/migrate")
  class MigrateInstances {

    @Test
    @DisplayName("执行实例迁移：绑定请求体并返回迁移报告")
    void should_migrate_instances() throws Exception {
      String body = "{\"sourceDefinitionId\":\"def-001\",\"targetDefinitionId\":\"def-002\","
          + "\"nodeMapping\":{\"NODE_A\":\"NODE_B\"},\"dryRun\":false}";
      when(instanceMigrationService.migrate(any())).thenReturn(null);

      mockMvc.perform(post("/workflow/engine/instance/migrate")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(instanceMigrationService).migrate(any());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/migrate/preview")
  class PreviewMigration {

    @Test
    @DisplayName("预览迁移：返回 dry-run 报告")
    void should_preview_migration() throws Exception {
      String body = "{\"sourceDefinitionId\":\"def-001\",\"targetDefinitionId\":\"def-002\",\"dryRun\":true}";
      when(instanceMigrationService.previewMigration(any())).thenReturn(null);

      mockMvc.perform(post("/workflow/engine/instance/migrate/preview")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(instanceMigrationService).previewMigration(any());
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/migrate/autoMap")
  class AutoMapNodes {

    @Test
    @DisplayName("自动映射节点编码：绑定两个 Long 型定义 ID")
    void should_auto_map_nodes() throws Exception {
      when(instanceMigrationService.autoMapNodes(100L, 200L)).thenReturn(Map.of("NODE_A", "NODE_B"));

      mockMvc.perform(get("/workflow/engine/instance/migrate/autoMap")
              .param("sourceDefinitionId", "100")
              .param("targetDefinitionId", "200"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.NODE_A").value("NODE_B"));

      verify(instanceMigrationService).autoMapNodes(100L, 200L);
    }

    @Test
    @DisplayName("定义 ID 非数字触发类型不匹配负例（400）")
    void should_reject_non_numeric_definition_id() throws Exception {
      mockMvc.perform(get("/workflow/engine/instance/migrate/autoMap")
              .param("sourceDefinitionId", "abc")
              .param("targetDefinitionId", "200"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/trigger/list")
  class ListTriggers {

    @Test
    @DisplayName("列出触发规则：返回规则列表")
    void should_list_triggers() throws Exception {
      FlowAutoTriggerVO vo = new FlowAutoTriggerVO();
      vo.setId("trigger-001");
      vo.setSourceFlowCode("LEAVE_APPLY");
      vo.setTargetFlowCode("NOTIFY_APPLY");
      vo.setIsEnabled(true);
      when(autoTriggerService.listAll()).thenReturn(List.of(vo));

      mockMvc.perform(get("/workflow/engine/instance/trigger/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].id").value("trigger-001"))
          .andExpect(jsonPath("$.data[0].isEnabled").value(true));

      verify(autoTriggerService).listAll();
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/trigger")
  class CreateTrigger {

    @Test
    @DisplayName("创建触发规则：绑定请求体后调用 registerTrigger")
    void should_create_trigger() throws Exception {
      String body = "{\"sourceFlowCode\":\"LEAVE_APPLY\",\"targetFlowCode\":\"NOTIFY_APPLY\","
          + "\"conditionExpression\":\"amount > 1000\"}";

      mockMvc.perform(post("/workflow/engine/instance/trigger")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(autoTriggerService).registerTrigger("LEAVE_APPLY", "NOTIFY_APPLY", "amount > 1000");
    }

    @Test
    @DisplayName("请求体缺少 sourceFlowCode 触发校验负例（400）")
    void should_reject_body_missing_source_flow_code() throws Exception {
      String body = "{\"targetFlowCode\":\"NOTIFY_APPLY\"}";

      mockMvc.perform(post("/workflow/engine/instance/trigger")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());

      verify(autoTriggerService, never()).registerTrigger(anyString(), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("DELETE /workflow/engine/instance/trigger/{id}")
  class DeleteTrigger {

    @Test
    @DisplayName("删除触发规则：路径变量透传服务")
    void should_delete_trigger() throws Exception {
      mockMvc.perform(delete("/workflow/engine/instance/trigger/{id}", "trigger-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(autoTriggerService).deleteById("trigger-001");
    }
  }

  @Nested
  @DisplayName("PUT /workflow/engine/instance/trigger/{id}/toggle")
  class ToggleTrigger {

    @Test
    @DisplayName("启停触发规则：返回 id 与切换后 enabled 状态")
    void should_toggle_trigger() throws Exception {
      when(autoTriggerService.toggleEnabled("trigger-001")).thenReturn(false);

      mockMvc.perform(put("/workflow/engine/instance/trigger/{id}/toggle", "trigger-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("trigger-001"))
          .andExpect(jsonPath("$.data.enabled").value(false));

      verify(autoTriggerService).toggleEnabled("trigger-001");
    }
  }
}
