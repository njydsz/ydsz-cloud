package com.njydsz.workflow.web.controller;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.domain.dto.FlowDeployProcessDTO;
import com.njydsz.workflow.domain.vo.FlowDefinitionDetailVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.server.service.FlowConditionExprService;
import com.njydsz.workflow.server.service.FlowCustomButtonService;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.workflow.server.service.FlowSlaService;
import com.njydsz.workflow.server.service.FlowTaskService;
import com.njydsz.workflow.web.controller.definition.FlowDefinitionController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FlowDefinitionController} HTTP 契约测试。
 *
 * <p>覆盖流程定义统一 Controller 的核心端点：部署（单条/zip 批量）、发布/废弃、按编码查询、分页查询、详情/预览、版本切换/启停/版本列表/差异对比/一键回滚、
 * 节点坐标更新、草稿编辑、导出/导入、变更影响分析、消息关联/错误抛出、事件订阅查询、SLA 扫描与单条处理、条件表达式（构建/解析/校验/预览/操作符/值类型/变量/模板）、
 * 自定义按钮（列出/保存/执行）。
 *
 * <p>每个场景验证真实 HTTP 路径与方法、参数或请求体绑定、响应字段、服务调用 verify / ArgumentCaptor，以及参数校验或未登录等负例。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowDefinitionControllerSmokeTest extends BaseControllerMockTest {

  private static final String DEF_ID = "def-001";
  private static final String USER_ID = "user-001";
  private static final String TENANT_ID = "1";

  private MockMvc mockMvc;

  @Mock
  private FlowDefinitionService definitionService;

  @Mock
  private FlowEventSubscriptionService eventSubscriptionService;

  @Mock
  private FlowSlaService slaService;

  @Mock
  private FlowConditionExprService conditionExprService;

  @Mock
  private FlowCustomButtonService customButtonService;

  @Mock
  private FlowTaskService taskService;

  @InjectMocks
  private FlowDefinitionController controller;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    LoginUser loginUser = new LoginUser();
    loginUser.setUserId(USER_ID);
    loginUser.setUsername("定义测试员");
    loginUser.setTenantId(TENANT_ID);
    AuthContextUtils.setCurrent(loginUser);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/deploy")
  class Deploy {

    @Test
    @DisplayName("部署流程定义：绑定请求体并返回定义 ID")
    void should_deploy() throws Exception {
      String body = "{\"flowCode\":\"LEAVE_APPLY\",\"flowName\":\"请假流程\"}";
      when(definitionService.deploy(any(FlowDeployProcessDTO.class))).thenReturn(DEF_ID);

      mockMvc.perform(post("/workflow/engine/definition/deploy")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(DEF_ID));

      ArgumentCaptor<FlowDeployProcessDTO> captor = ArgumentCaptor.forClass(FlowDeployProcessDTO.class);
      verify(definitionService).deploy(captor.capture());
      assertThat(captor.getValue().getFlowCode()).isEqualTo("LEAVE_APPLY");
      assertThat(captor.getValue().getFlowName()).isEqualTo("请假流程");
    }

    @Test
    @DisplayName("请求体缺少 flowCode 触发校验负例（400）")
    void should_reject_body_missing_flow_code() throws Exception {
      String body = "{\"flowName\":\"请假流程\"}";

      mockMvc.perform(post("/workflow/engine/definition/deploy")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());

      verify(definitionService, never()).deploy(any(FlowDeployProcessDTO.class));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/batchDeployZip")
  class BatchDeployZip {

    @Test
    @DisplayName("zip 批量部署：非空文件调用服务并返回统计")
    void should_batch_deploy_zip() throws Exception {
      MockMultipartFile file =
          new MockMultipartFile("file", "flows.zip", "application/zip", new byte[] {1, 2, 3});
      when(definitionService.batchDeployFromZip(any(), isNull())).thenReturn(null);

      mockMvc.perform(multipart("/workflow/engine/definition/batchDeployZip").file(file))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).batchDeployFromZip(any(), isNull());
    }

    @Test
    @DisplayName("空 zip 文件直接返回参数校验失败，不触达服务")
    void should_reject_empty_zip() throws Exception {
      MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.zip", "application/zip", new byte[0]);

      mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
              .multipart("/workflow/engine/definition/batchDeployZip")
              .file(emptyFile))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A10002"));

      verify(definitionService, never()).batchDeployFromZip(any(), any());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/{id}/publish")
  class Publish {

    @Test
    @DisplayName("发布：force 缺省 false，透传服务")
    void should_publish_with_default_force() throws Exception {
      mockMvc.perform(post("/workflow/engine/definition/{id}/publish", DEF_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).publish(DEF_ID, false);
    }

    @Test
    @DisplayName("强制发布：force=true 透传服务")
    void should_force_publish() throws Exception {
      mockMvc.perform(post("/workflow/engine/definition/{id}/publish", DEF_ID).param("force", "true"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).publish(DEF_ID, true);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/{id}/deprecate")
  class Deprecate {

    @Test
    @DisplayName("废弃定义：路径变量透传服务")
    void should_deprecate() throws Exception {
      mockMvc.perform(post("/workflow/engine/definition/{id}/deprecate", DEF_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).deprecate(DEF_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/code/{code}")
  class GetByCode {

    @Test
    @DisplayName("按编码查询已发布定义：version/tenantId 可选")
    void should_return_published_definition() throws Exception {
      FlowDefinitionVO vo = new FlowDefinitionVO();
      vo.setId(DEF_ID);
      vo.setFlowCode("LEAVE_APPLY");
      when(definitionService.getPublished("LEAVE_APPLY", null, null)).thenReturn(vo);

      mockMvc.perform(get("/workflow/engine/definition/code/{code}", "LEAVE_APPLY"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value(DEF_ID));

      verify(definitionService).getPublished("LEAVE_APPLY", null, null);
    }

    @Test
    @DisplayName("显式 version 与 tenantId 透传服务")
    void should_pass_version_and_tenant() throws Exception {
      when(definitionService.getPublished("LEAVE_APPLY", "3", "tenant-9")).thenReturn(null);

      mockMvc.perform(get("/workflow/engine/definition/code/{code}", "LEAVE_APPLY")
              .param("version", "3")
              .param("tenantId", "tenant-9"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).getPublished("LEAVE_APPLY", "3", "tenant-9");
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/page")
  class Page {

    @Test
    @DisplayName("分页查询：绑定 pageNo/pageSize 与可选过滤参数")
    void should_bind_page_filters() throws Exception {
      FlowDefinitionVO vo = new FlowDefinitionVO();
      vo.setId(DEF_ID);
      vo.setFlowCode("LEAVE_APPLY");
      PageResponse<List<FlowDefinitionVO>> pageResult = PageResponse.success(1L, 2L, 10L, List.of(vo));
      when(definitionService.page(2, 10, "HR", "LEAVE_APPLY")).thenReturn(pageResult);

      mockMvc.perform(get("/workflow/engine/definition/page")
              .param("pageNo", "2")
              .param("pageSize", "10")
              .param("category", "HR")
              .param("flowCode", "LEAVE_APPLY"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.pageNum").value(2))
          .andExpect(jsonPath("$.data.pageSize").value(10))
          .andExpect(jsonPath("$.data.data[0].id").value(DEF_ID));

      verify(definitionService).page(2, 10, "HR", "LEAVE_APPLY");
    }

    @Test
    @DisplayName("过滤参数缺省时以 null 透传")
    void should_pass_null_when_filters_absent() throws Exception {
      when(definitionService.page(anyInt(), anyInt(), isNull(), isNull()))
          .thenReturn(PageResponse.success(0L, 1L, 20L, List.of()));

      mockMvc.perform(get("/workflow/engine/definition/page"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).page(1, 20, null, null);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/{id}")
  class GetById {

    @Test
    @DisplayName("详情查询：返回 definition/nodes/skips")
    void should_return_detail() throws Exception {
      FlowDefinitionDetailVO detail = new FlowDefinitionDetailVO();
      detail.setNodes(List.of());
      detail.setSkips(List.of());
      when(definitionService.getDetail(DEF_ID)).thenReturn(detail);

      mockMvc.perform(get("/workflow/engine/definition/{id}", DEF_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.nodes").isArray())
          .andExpect(jsonPath("$.data.skips").isArray());

      verify(definitionService).getDetail(DEF_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/{id}/preview")
  class Preview {

    @Test
    @DisplayName("预览：返回详情并标记 isReadonly=true")
    void should_return_readonly_detail() throws Exception {
      FlowDefinitionDetailVO detail = new FlowDefinitionDetailVO();
      detail.setNodes(List.of());
      when(definitionService.getDetail(DEF_ID)).thenReturn(detail);

      mockMvc.perform(get("/workflow/engine/definition/{id}/preview", DEF_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.isReadonly").value(true));

      verify(definitionService).getDetail(DEF_ID);
    }

    @Test
    @DisplayName("定义为 null 时直接返回 null 详情，不抛 NPE")
    void should_handle_null_detail() throws Exception {
      when(definitionService.getDetail(DEF_ID)).thenReturn(null);

      mockMvc.perform(get("/workflow/engine/definition/{id}/preview", DEF_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/{code}/switchVersion")
  class SwitchVersion {

    @Test
    @DisplayName("切换激活版本：绑定 definitionId 与可选 tenantId")
    void should_switch_version() throws Exception {
      mockMvc.perform(post("/workflow/engine/definition/{code}/switchVersion", "LEAVE_APPLY")
              .param("definitionId", DEF_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).switchActiveVersion("LEAVE_APPLY", DEF_ID, null);
    }

    @Test
    @DisplayName("缺少 definitionId 触发负例（400）")
    void should_reject_missing_definition_id() throws Exception {
      mockMvc.perform(post("/workflow/engine/definition/{code}/switchVersion", "LEAVE_APPLY"))
          .andExpect(status().isBadRequest());

      verify(definitionService, never()).switchActiveVersion(anyString(), anyString(), any());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/{id}/enable")
  class Enable {

    @Test
    @DisplayName("启用定义：路径变量透传服务")
    void should_enable() throws Exception {
      mockMvc.perform(post("/workflow/engine/definition/{id}/enable", DEF_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).enable(DEF_ID);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/{id}/disable")
  class Disable {

    @Test
    @DisplayName("停用定义：路径变量透传服务")
    void should_disable() throws Exception {
      mockMvc.perform(post("/workflow/engine/definition/{id}/disable", DEF_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).disable(DEF_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/{id}/versions")
  class ListVersions {

    @Test
    @DisplayName("版本列表：返回版本信息列表")
    void should_return_versions() throws Exception {
      when(definitionService.listVersions(DEF_ID)).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/definition/{id}/versions", DEF_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).listVersions(DEF_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/{id}/diff")
  class DiffVersions {

    @Test
    @DisplayName("版本差异对比：绑定 v1/v2 返回差异 VO")
    void should_return_diff() throws Exception {
      when(definitionService.diffVersions(DEF_ID, 1, 2)).thenReturn(null);

      mockMvc.perform(get("/workflow/engine/definition/{id}/diff", DEF_ID)
              .param("v1", "1")
              .param("v2", "2"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).diffVersions(DEF_ID, 1, 2);
    }

    @Test
    @DisplayName("缺少 v1 触发负例（400）")
    void should_reject_missing_v1() throws Exception {
      mockMvc.perform(get("/workflow/engine/definition/{id}/diff", DEF_ID).param("v2", "2"))
          .andExpect(status().isBadRequest());

      verify(definitionService, never()).diffVersions(anyString(), any(), any());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/rollback")
  class RollbackDefinition {

    @Test
    @DisplayName("一键回滚：绑定 flowCode 并透传登录态租户")
    void should_rollback_definition() throws Exception {
      String body = "{\"flowCode\":\"LEAVE_APPLY\"}";
      when(definitionService.rollbackDefinition("LEAVE_APPLY", TENANT_ID)).thenReturn(null);

      mockMvc.perform(post("/workflow/engine/definition/rollback")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).rollbackDefinition("LEAVE_APPLY", TENANT_ID);
    }

    @Test
    @DisplayName("请求体缺少 flowCode 触发校验负例（400）")
    void should_reject_body_missing_flow_code() throws Exception {
      String body = "{}";

      mockMvc.perform(post("/workflow/engine/definition/rollback")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());

      verify(definitionService, never()).rollbackDefinition(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/{id}/node/{nodeCode}/coordinate")
  class UpdateNodeCoordinate {

    @Test
    @DisplayName("更新节点坐标：绑定坐标 JSON 字符串")
    void should_update_node_coordinate() throws Exception {
      String body = "{\"x\":100,\"y\":200}";

      mockMvc.perform(post("/workflow/engine/definition/{id}/node/{nodeCode}/coordinate", DEF_ID, "NODE_A")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).updateNodeCoordinate(DEF_ID, "NODE_A", body);
    }
  }

  @Nested
  @DisplayName("PUT /workflow/engine/definition/{id}")
  class UpdateDefinition {

    @Test
    @DisplayName("编辑草稿：绑定请求体并透传服务")
    void should_update_definition() throws Exception {
      String body = "{\"flowCode\":\"LEAVE_APPLY\",\"flowName\":\"请假流程-改\"}";

      mockMvc.perform(put("/workflow/engine/definition/{id}", DEF_ID)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).updateDefinition(eq(DEF_ID), any(FlowDeployProcessDTO.class));
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/{id}/export")
  class ExportDefinition {

    @Test
    @DisplayName("导出定义：返回 JSON 字符串")
    void should_export_definition() throws Exception {
      when(definitionService.exportDefinition(DEF_ID)).thenReturn("{\"flowCode\":\"LEAVE_APPLY\"}");

      mockMvc.perform(get("/workflow/engine/definition/{id}/export", DEF_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("{\"flowCode\":\"LEAVE_APPLY\"}"));

      verify(definitionService).exportDefinition(DEF_ID);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/import")
  class ImportDefinition {

    @Test
    @DisplayName("导入定义：tenantId 缺省取登录态租户")
    void should_import_definition_with_default_tenant() throws Exception {
      String body = "{\"flowCode\":\"LEAVE_APPLY\"}";
      when(definitionService.importDefinition(body, TENANT_ID)).thenReturn("def-imported-001");

      mockMvc.perform(post("/workflow/engine/definition/import")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("def-imported-001"));

      verify(definitionService).importDefinition(body, TENANT_ID);
    }

    @Test
    @DisplayName("显式 tenantId 覆盖登录态租户")
    void should_import_definition_with_explicit_tenant() throws Exception {
      String body = "{\"flowCode\":\"LEAVE_APPLY\"}";
      when(definitionService.importDefinition(body, "tenant-9")).thenReturn("def-imported-002");

      mockMvc.perform(post("/workflow/engine/definition/import")
              .param("tenantId", "tenant-9")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value("def-imported-002"));

      verify(definitionService).importDefinition(body, "tenant-9");
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/migrationImpact")
  class MigrationImpact {

    @Test
    @DisplayName("变更影响分析：绑定新旧定义 ID 返回报告")
    void should_return_migration_impact() throws Exception {
      when(definitionService.analyzeMigrationImpact("def-old", "def-new")).thenReturn(null);

      mockMvc.perform(get("/workflow/engine/definition/migrationImpact")
              .param("oldDefinitionId", "def-old")
              .param("newDefinitionId", "def-new"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(definitionService).analyzeMigrationImpact("def-old", "def-new");
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/info")
  class Info {

    @Test
    @DisplayName("引擎信息：返回固定 engineType/available")
    void should_return_engine_info() throws Exception {
      mockMvc.perform(get("/workflow/engine/info"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.engineType").value("YDSZ-Flow"))
          .andExpect(jsonPath("$.data.available").value(true));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/event/correlateMessage")
  class CorrelateMessage {

    @Test
    @DisplayName("消息关联：绑定参数并透传租户，返回触发数量")
    void should_correlate_message() throws Exception {
      when(eventSubscriptionService.correlateMessage(TENANT_ID, "ORDER_CREATED", "biz-001", "{\"a\":1}"))
          .thenReturn(2);

      mockMvc.perform(post("/workflow/engine/event/correlateMessage")
              .param("messageName", "ORDER_CREATED")
              .param("correlationKey", "biz-001")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"a\":1}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(2));

      verify(eventSubscriptionService).correlateMessage(TENANT_ID, "ORDER_CREATED", "biz-001", "{\"a\":1}");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/event/throwError")
  class ThrowError {

    @Test
    @DisplayName("抛出错误：绑定 errorCode 与可选 instanceId/payload")
    void should_throw_error() throws Exception {
      when(eventSubscriptionService.throwError(TENANT_ID, "inst-evt-001", "ERR_TIMEOUT", "{}"))
          .thenReturn(1);

      mockMvc.perform(post("/workflow/engine/event/throwError")
              .param("errorCode", "ERR_TIMEOUT")
              .param("instanceId", "inst-evt-001")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(1));

      verify(eventSubscriptionService).throwError(TENANT_ID, "inst-evt-001", "ERR_TIMEOUT", "{}");
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/instance/{instanceId}/eventSubscriptions")
  class ListEventSubscriptions {

    @Test
    @DisplayName("事件订阅列表：返回订阅 VO 列表")
    void should_return_event_subscriptions() throws Exception {
      when(eventSubscriptionService.listByInstanceVO("inst-evt-001")).thenReturn(List.of());

      mockMvc.perform(get("/workflow/engine/instance/{instanceId}/eventSubscriptions", "inst-evt-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(eventSubscriptionService).listByInstanceVO("inst-evt-001");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/sla/scan")
  class SlaScan {

    @Test
    @DisplayName("手动 SLA 扫描：返回处理任务数")
    void should_scan_sla() throws Exception {
      when(slaService.scanAndProcess()).thenReturn(12);

      mockMvc.perform(post("/workflow/engine/sla/scan"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(12));

      verify(slaService).scanAndProcess();
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/sla/process/{taskId}")
  class SlaProcess {

    @Test
    @DisplayName("单条 SLA 处理：返回处理成功布尔值")
    void should_process_sla() throws Exception {
      when(taskService.slaProcessByTaskId("task-001")).thenReturn(true);

      mockMvc.perform(post("/workflow/engine/sla/process/{taskId}", "task-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(taskService).slaProcessByTaskId("task-001");
    }

    @Test
    @DisplayName("任务不存在时返回 404 错误信封")
    void should_return_not_found_when_task_missing() throws Exception {
      when(taskService.slaProcessByTaskId("task-missing")).thenReturn(null);

      mockMvc.perform(post("/workflow/engine/sla/process/{taskId}", "task-missing"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A10101"));

      verify(taskService).slaProcessByTaskId("task-missing");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/conditionExpr/build")
  class BuildExpression {

    @Test
    @DisplayName("结构化条件转表达式：engine 缺省 AVIATOR")
    void should_build_expression() throws Exception {
      String body = "{\"conditionJson\":\"{\\\"field\\\":\\\"amount\\\"}\"}";
      when(conditionExprService.buildExpression(anyString(), eq("AVIATOR"))).thenReturn("amount > 1000");

      mockMvc.perform(post("/workflow/engine/definition/conditionExpr/build")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("amount > 1000"));

      verify(conditionExprService).buildExpression("{\"field\":\"amount\"}", "AVIATOR");
    }

    @Test
    @DisplayName("显式 engine 透传服务")
    void should_build_expression_with_engine() throws Exception {
      String body = "{\"conditionJson\":\"{}\",\"engine\":\"SPEL\"}";
      when(conditionExprService.buildExpression(anyString(), eq("SPEL"))).thenReturn("true");

      mockMvc.perform(post("/workflow/engine/definition/conditionExpr/build")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value("true"));

      verify(conditionExprService).buildExpression("{}", "SPEL");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/conditionExpr/parse")
  class ParseExpression {

    @Test
    @DisplayName("表达式转结构化条件：绑定 expression 与 engine")
    void should_parse_expression() throws Exception {
      String body = "{\"expression\":\"amount > 1000\",\"engine\":\"AVIATOR\"}";
      when(conditionExprService.parseExpression("amount > 1000", "AVIATOR")).thenReturn("{\"field\":\"amount\"}");

      mockMvc.perform(post("/workflow/engine/definition/conditionExpr/parse")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("{\"field\":\"amount\"}"));

      verify(conditionExprService).parseExpression("amount > 1000", "AVIATOR");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/conditionExpr/validate")
  class ValidateExpression {

    @Test
    @DisplayName("表达式校验：返回 valid/errors 等字段")
    void should_validate_expression() throws Exception {
      String body = "{\"expression\":\"amount > 1000\"}";
      when(conditionExprService.validateExpression("amount > 1000", "AVIATOR"))
          .thenReturn(Map.of("valid", true, "errors", List.of()));

      mockMvc.perform(post("/workflow/engine/definition/conditionExpr/validate")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.valid").value(true));

      verify(conditionExprService).validateExpression("amount > 1000", "AVIATOR");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/conditionExpr/preview")
  class PreviewExpression {

    @Test
    @DisplayName("表达式预览：绑定变量 Map 并返回执行结果")
    void should_preview_expression() throws Exception {
      String body = "{\"expression\":\"amount > 1000\",\"variables\":{\"amount\":2000},\"engine\":\"AVIATOR\"}";
      when(conditionExprService.previewExpression(eq("amount > 1000"), any(), eq("AVIATOR")))
          .thenReturn(Map.of("success", true, "result", true));

      mockMvc.perform(post("/workflow/engine/definition/conditionExpr/preview")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.success").value(true))
          .andExpect(jsonPath("$.data.result").value(true));

      verify(conditionExprService).previewExpression("amount > 1000", Map.of("amount", 2000), "AVIATOR");
    }

    @Test
    @DisplayName("variables 非 Map 时降级为空 Map，不抛异常")
    void should_handle_non_map_variables() throws Exception {
      String body = "{\"expression\":\"amount > 1000\",\"variables\":\"not-a-map\"}";
      when(conditionExprService.previewExpression(eq("amount > 1000"), any(), eq("AVIATOR")))
          .thenReturn(Map.of("success", false));

      mockMvc.perform(post("/workflow/engine/definition/conditionExpr/preview")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.success").value(false));
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/conditionExpr/operators")
  class Operators {

    @Test
    @DisplayName("操作符列表：返回 code/symbol/desc 列表")
    void should_return_operators() throws Exception {
      when(conditionExprService.getOperators()).thenReturn(List.of(Map.of("code", "GT", "symbol", ">")));

      mockMvc.perform(get("/workflow/engine/definition/conditionExpr/operators"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].code").value("GT"))
          .andExpect(jsonPath("$.data[0].symbol").value(">"));

      verify(conditionExprService).getOperators();
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/conditionExpr/valueTypes")
  class ValueTypes {

    @Test
    @DisplayName("值类型列表：返回 code/desc 列表")
    void should_return_value_types() throws Exception {
      when(conditionExprService.getValueTypes()).thenReturn(List.of(Map.of("code", "NUMBER", "desc", "数值")));

      mockMvc.perform(get("/workflow/engine/definition/conditionExpr/valueTypes"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].code").value("NUMBER"));

      verify(conditionExprService).getValueTypes();
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/conditionExpr/variables/{id}")
  class Variables {

    @Test
    @DisplayName("定义可用变量列表：路径变量透传服务")
    void should_return_variables() throws Exception {
      when(conditionExprService.getVariablesByDefinition(DEF_ID))
          .thenReturn(List.of(Map.of("name", "amount", "type", "NUMBER")));

      mockMvc.perform(get("/workflow/engine/definition/conditionExpr/variables/{id}", DEF_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].name").value("amount"));

      verify(conditionExprService).getVariablesByDefinition(DEF_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/conditionExpr/templates")
  class ConditionTemplates {

    @Test
    @DisplayName("条件模板列表：返回模板列表")
    void should_return_templates() throws Exception {
      when(conditionExprService.getConditionTemplates())
          .thenReturn(List.of(Map.of("templateName", "金额大于", "expression", "amount > 1000")));

      mockMvc.perform(get("/workflow/engine/definition/conditionExpr/templates"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].templateName").value("金额大于"));

      verify(conditionExprService).getConditionTemplates();
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/definition/customButtons")
  class ListCustomButtons {

    @Test
    @DisplayName("自定义按钮列表：绑定 definitionId 与 nodeCode")
    void should_return_custom_buttons() throws Exception {
      when(customButtonService.getCustomButtons(DEF_ID, "NODE_A"))
          .thenReturn(List.of(Map.of("buttonCode", "BTN_APPROVE")));

      mockMvc.perform(get("/workflow/engine/definition/customButtons")
              .param("definitionId", DEF_ID)
              .param("nodeCode", "NODE_A"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].buttonCode").value("BTN_APPROVE"));

      verify(customButtonService).getCustomButtons(DEF_ID, "NODE_A");
    }

    @Test
    @DisplayName("缺少 nodeCode 触发负例（400）")
    void should_reject_missing_node_code() throws Exception {
      mockMvc.perform(get("/workflow/engine/definition/customButtons").param("definitionId", DEF_ID))
          .andExpect(status().isBadRequest());

      verify(customButtonService, never()).getCustomButtons(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/customButtons")
  class SaveCustomButtons {

    @Test
    @DisplayName("保存自定义按钮：绑定请求体列表并透传服务")
    void should_save_custom_buttons() throws Exception {
      String body = "[{\"buttonCode\":\"BTN_APPROVE\",\"buttonName\":\"同意\"}]";

      mockMvc.perform(post("/workflow/engine/definition/customButtons")
              .param("definitionId", DEF_ID)
              .param("nodeCode", "NODE_A")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
      verify(customButtonService).saveCustomButtons(eq(DEF_ID), eq("NODE_A"), captor.capture());
      assertThat(captor.getValue()).hasSize(1);
      assertThat(captor.getValue().get(0)).containsEntry("buttonCode", "BTN_APPROVE");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/customButtons/execute")
  class ExecuteCustomButton {

    @Test
    @DisplayName("执行自定义按钮：绑定 taskId/buttonCode 与可选变量请求体")
    void should_execute_custom_button() throws Exception {
      String body = "{\"result\":\"ok\"}";
      when(customButtonService.executeButton("task-001", "BTN_APPROVE", USER_ID, "同意", Map.of("result", "ok")))
          .thenReturn(Map.of("success", true));

      mockMvc.perform(post("/workflow/engine/definition/customButtons/execute")
              .param("taskId", "task-001")
              .param("buttonCode", "BTN_APPROVE")
              .param("comment", "同意")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.success").value(true));

      verify(customButtonService).executeButton("task-001", "BTN_APPROVE", USER_ID, "同意", Map.of("result", "ok"));
    }

    @Test
    @DisplayName("无请求体时 variables 为 null")
    void should_execute_without_variables() throws Exception {
      when(customButtonService.executeButton("task-001", "BTN_APPROVE", USER_ID, null, null))
          .thenReturn(Map.of("success", true));

      mockMvc.perform(post("/workflow/engine/definition/customButtons/execute")
              .param("taskId", "task-001")
              .param("buttonCode", "BTN_APPROVE"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.success").value(true));

      verify(customButtonService).executeButton("task-001", "BTN_APPROVE", USER_ID, null, null);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/definition/deploy 请求体负例")
  class DeployNegative {

    @Test
    @DisplayName("请求体非 JSON 时触发解析负例（400）")
    void should_reject_malformed_json() throws Exception {
      mockMvc.perform(post("/workflow/engine/definition/deploy")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{not-json"))
          .andExpect(status().isBadRequest());

      verify(definitionService, never()).deploy(any(FlowDeployProcessDTO.class));
    }
  }

}
