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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.service.FlowTaskService;
import com.njydsz.workflow.server.service.FlowTodoCountPushService;
import com.njydsz.workflow.web.controller.instance.FlowTaskController;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FlowTaskController} HTTP 契约测试。
 *
 * <p>覆盖任务统一 Controller 的核心端点：任务详情、办理动作（签收/通过/驳回/转办/委派/加签/跳转）、批量操作（批量通过/驳回/转办/催办/一键通过）、
 * 查询统计（待办/已办/超期/节点耗时）、辅助操作（减签/已阅/沟通/暂存/追加/取回/挂起/激活）、待办推送、加签历史分页。
 *
 * <p>每个场景验证真实 HTTP 路径与方法、参数或请求体绑定、响应字段、服务调用 verify / ArgumentCaptor，以及参数校验或未登录等负例。
 *
 * <p>待办端点通过 {@code AuthContextUtils.getUserId()} 读取登录态，未写入登录上下文时抛 401 {@code SysException}，因此该用例需先装配当前登录用户。
 *
 * @author ydsz-smoke-test
 * @since 26.10.10
 */
class FlowTaskControllerSmokeTest extends BaseControllerMockTest {

  private static final String TASK_ID = "task-001";
  private static final String USER_ID = "user-001";
  private static final String USER_NAME = "张三";
  private static final String TENANT_ID = "1";

  private MockMvc mockMvc;

  @Mock
  private FlowTaskService taskService;

  @Mock
  private WorkflowFacade workflowFacade;

  @Mock
  private FlowTodoCountPushService todoCountPushService;

  @InjectMocks
  private FlowTaskController controller;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    LoginUser loginUser = new LoginUser();
    loginUser.setUserId(USER_ID);
    loginUser.setUsername(USER_NAME);
    loginUser.setTenantId(TENANT_ID);
    AuthContextUtils.setCurrent(loginUser);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Nested
  @DisplayName("GET /workflow/engine/task/todo")
  class Todo {

    @Test
    @DisplayName("绑定分页与过滤参数，返回待办分页列表")
    void should_bind_page_and_filters() throws Exception {
      FlowRunTaskVO vo = new FlowRunTaskVO();
      vo.setId("task-001");
      vo.setFlowCode("LEAVE_APPLY");
      vo.setTitle("请假申请");
      PageResponse<List<FlowRunTaskVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(taskService.pageTodoVO(anyString(), anyString(), any(), any(), any(), any(), anyInt(), anyInt()))
          .thenReturn(pageResult);

      mockMvc.perform(get("/workflow/engine/task/todo")
              .param("page", "2")
              .param("size", "10")
              .param("flowCode", "LEAVE_APPLY")
              .param("businessType", "LEAVE"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.data[0].id").value("task-001"))
          .andExpect(jsonPath("$.data.data[0].flowCode").value("LEAVE_APPLY"))
          .andExpect(jsonPath("$.data.data[0].title").value("请假申请"));

      verify(taskService).pageTodoVO(USER_ID, TENANT_ID, "LEAVE_APPLY", "LEAVE", null, null, 2, 10);
    }

    @Test
    @DisplayName("未登录时抛 401，不触达服务")
    void should_reject_when_not_logged_in() throws Exception {
      RequestContext.clear();

      mockMvc.perform(get("/workflow/engine/task/todo"))
          .andExpect(status().isUnauthorized());

      verify(taskService, never()).pageTodoVO(anyString(), anyString(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("size 超过 100 触发参数校验负例（400）")
    void should_reject_size_over_max() throws Exception {
      mockMvc.perform(get("/workflow/engine/task/todo").param("size", "101"))
          .andExpect(status().isBadRequest());

      verify(taskService, never()).pageTodoVO(anyString(), anyString(), any(), any(), any(), any(), anyInt(), anyInt());
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/task/todo/cursor")
  class TodoCursor {

    @Test
    @DisplayName("绑定游标参数，返回下一页待办列表")
    void should_bind_cursor_params() throws Exception {
      FlowRunTaskVO vo = new FlowRunTaskVO();
      vo.setId("task-cursor-002");
      when(taskService.listTodoByAssigneeCursor(USER_ID, TENANT_ID, 5, null, "task-cursor-001", 20))
          .thenReturn(List.of(vo));

      mockMvc.perform(get("/workflow/engine/task/todo/cursor")
              .param("limit", "20")
              .param("lastPriority", "5")
              .param("lastId", "task-cursor-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].id").value("task-cursor-002"));

      verify(taskService).listTodoByAssigneeCursor(USER_ID, TENANT_ID, 5, null, "task-cursor-001", 20);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/task/{taskId}")
  class TaskDetail {

    @Test
    @DisplayName("按路径变量查询任务详情并透传服务")
    void should_return_task_detail() throws Exception {
      when(workflowFacade.getTaskDetail(TASK_ID)).thenReturn(Map.of("taskId", TASK_ID));

      mockMvc.perform(get("/workflow/engine/task/{taskId}", TASK_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.taskId").value(TASK_ID));

      verify(workflowFacade).getTaskDetail(TASK_ID);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/claim")
  class Claim {

    @Test
    @DisplayName("签收任务：taskId 作为查询参数绑定，操作人取登录态")
    void should_claim_task_with_login_user() throws Exception {
      mockMvc.perform(post("/workflow/engine/task/claim").param("taskId", TASK_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).claimTask(TASK_ID, USER_ID);
    }

    @Test
    @DisplayName("缺少 taskId 参数触发负例（400）")
    void should_reject_missing_task_id() throws Exception {
      mockMvc.perform(post("/workflow/engine/task/claim"))
          .andExpect(status().isBadRequest());

      verify(workflowFacade, never()).claimTask(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/pass")
  class Pass {

    @Test
    @DisplayName("通过任务：绑定请求体并回填登录态 userId/userName 后调用门面")
    void should_pass_task_with_bound_body() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"comment\":\"同意\",\"action\":\"PASS\"}";

      mockMvc.perform(post("/workflow/engine/task/pass")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<FlowTaskOperateDTO> captor = ArgumentCaptor.forClass(FlowTaskOperateDTO.class);
      verify(workflowFacade).completeTask(captor.capture());
      FlowTaskOperateDTO dto = captor.getValue();
      assertThat(dto.getTaskId()).isEqualTo(TASK_ID);
      assertThat(dto.getComment()).isEqualTo("同意");
      assertThat(dto.getAction()).isEqualTo("PASS");
      assertThat(dto.getUserId()).isEqualTo(USER_ID);
      assertThat(dto.getUserName()).isEqualTo(USER_NAME);
    }

    @Test
    @DisplayName("请求体缺少 taskId 触发 @Valid 校验负例（400）")
    void should_reject_body_without_task_id() throws Exception {
      String body = "{\"comment\":\"缺 taskId\"}";

      mockMvc.perform(post("/workflow/engine/task/pass")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());

      verify(workflowFacade, never()).completeTask(any(FlowTaskOperateDTO.class));
    }

    @Test
    @DisplayName("请求体非 JSON 时触发解析负例（400）")
    void should_reject_malformed_json() throws Exception {
      mockMvc.perform(post("/workflow/engine/task/pass")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{not-json"))
          .andExpect(status().isBadRequest());

      verify(workflowFacade, never()).completeTask(any(FlowTaskOperateDTO.class));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/reject")
  class Reject {

    @Test
    @DisplayName("驳回任务：绑定请求体含 targetNodeCode，回填登录态后调用 rejectTask")
    void should_reject_task_with_target_node() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"comment\":\"材料不全\",\"targetNodeCode\":\"NODE_APPLY\"}";

      mockMvc.perform(post("/workflow/engine/task/reject")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<FlowTaskOperateDTO> captor = ArgumentCaptor.forClass(FlowTaskOperateDTO.class);
      verify(workflowFacade).rejectTask(captor.capture());
      FlowTaskOperateDTO dto = captor.getValue();
      assertThat(dto.getTaskId()).isEqualTo(TASK_ID);
      assertThat(dto.getTargetNodeCode()).isEqualTo("NODE_APPLY");
      assertThat(dto.getUserId()).isEqualTo(USER_ID);
      assertThat(dto.getUserName()).isEqualTo(USER_NAME);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/transfer")
  class Transfer {

    @Test
    @DisplayName("转办任务：绑定目标人字段后调用 transferTask")
    void should_transfer_task() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"targetUserId\":\"user-002\",\"targetUserName\":\"李四\"}";

      mockMvc.perform(post("/workflow/engine/task/transfer")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<FlowTaskOperateDTO> captor = ArgumentCaptor.forClass(FlowTaskOperateDTO.class);
      verify(workflowFacade).transferTask(captor.capture());
      FlowTaskOperateDTO dto = captor.getValue();
      assertThat(dto.getTargetUserId()).isEqualTo("user-002");
      assertThat(dto.getTargetUserName()).isEqualTo("李四");
      assertThat(dto.getUserId()).isEqualTo(USER_ID);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/delegate")
  class Delegate {

    @Test
    @DisplayName("委派任务：绑定请求体后调用 delegateTask")
    void should_delegate_task() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"targetUserId\":\"user-003\"}";

      mockMvc.perform(post("/workflow/engine/task/delegate")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).delegateTask(any(FlowTaskOperateDTO.class));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/countersignBefore")
  class CountersignBefore {

    @Test
    @DisplayName("前加签：绑定请求体并回填登录态后调用门面")
    void should_countersign_before() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"targetUserId\":\"user-009\",\"targetUserName\":\"王五\"}";

      mockMvc.perform(post("/workflow/engine/task/countersignBefore")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<FlowTaskOperateDTO> captor = ArgumentCaptor.forClass(FlowTaskOperateDTO.class);
      verify(workflowFacade).countersignBeforeTask(captor.capture());
      assertThat(captor.getValue().getTargetUserId()).isEqualTo("user-009");
      assertThat(captor.getValue().getUserName()).isEqualTo(USER_NAME);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/countersignAfter")
  class CountersignAfter {

    @Test
    @DisplayName("后加签：绑定请求体后调用门面")
    void should_countersign_after() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"targetUserId\":\"user-010\"}";

      mockMvc.perform(post("/workflow/engine/task/countersignAfter")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).countersignAfterTask(any(FlowTaskOperateDTO.class));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/countersignParallel")
  class CountersignParallel {

    @Test
    @DisplayName("并加签：绑定请求体后调用门面")
    void should_countersign_parallel() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"targetUserId\":\"user-011\"}";

      mockMvc.perform(post("/workflow/engine/task/countersignParallel")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).countersignParallelTask(any(FlowTaskOperateDTO.class));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/jump")
  class Jump {

    @Test
    @DisplayName("管理员强制跳转：绑定目标节点后调用 jumpTask")
    void should_jump_task() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"targetNodeCode\":\"NODE_FINAL\"}";

      mockMvc.perform(post("/workflow/engine/task/jump")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<FlowTaskOperateDTO> captor = ArgumentCaptor.forClass(FlowTaskOperateDTO.class);
      verify(workflowFacade).jumpTask(captor.capture());
      assertThat(captor.getValue().getTargetNodeCode()).isEqualTo("NODE_FINAL");
      assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/freeJump")
  class FreeJump {

    @Test
    @DisplayName("自由流跳转：强制写入 action=JUMP 后调用 jumpTask")
    void should_force_jump_action() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"targetNodeCode\":\"NODE_X\",\"action\":\"\"}";

      mockMvc.perform(post("/workflow/engine/task/freeJump")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<FlowTaskOperateDTO> captor = ArgumentCaptor.forClass(FlowTaskOperateDTO.class);
      verify(workflowFacade).jumpTask(captor.capture());
      assertThat(captor.getValue().getAction()).isEqualTo("JUMP");
      assertThat(captor.getValue().getTargetNodeCode()).isEqualTo("NODE_X");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/batchPass")
  class BatchPass {

    @Test
    @DisplayName("批量通过：绑定 ID 列表，操作人取登录态，comment 传 null")
    void should_batch_pass() throws Exception {
      String body = "[\"task-001\",\"task-002\"]";

      mockMvc.perform(post("/workflow/engine/task/batchPass")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).batchPassTasks(List.of("task-001", "task-002"), USER_ID, null);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/batchReject")
  class BatchReject {

    @Test
    @DisplayName("批量驳回：聚合 taskId 列表与首条 comment/targetNodeCode")
    void should_batch_reject() throws Exception {
      String body = "[{\"taskId\":\"task-001\",\"comment\":\"驳回1\",\"targetNodeCode\":\"NODE_A\"},"
          + "{\"taskId\":\"task-002\",\"comment\":\"驳回2\"}]";

      mockMvc.perform(post("/workflow/engine/task/batchReject")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
      ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> nodeCaptor = ArgumentCaptor.forClass(String.class);
      verify(taskService).batchReject(idsCaptor.capture(), eq(USER_ID), commentCaptor.capture(), nodeCaptor.capture());
      assertThat(idsCaptor.getValue()).containsExactly("task-001", "task-002");
      assertThat(commentCaptor.getValue()).isEqualTo("驳回1");
      assertThat(nodeCaptor.getValue()).isEqualTo("NODE_A");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/batchTransfer")
  class BatchTransfer {

    @Test
    @DisplayName("批量转办：聚合 taskId 与首个目标人信息")
    void should_batch_transfer() throws Exception {
      String body = "[{\"taskId\":\"task-001\",\"targetUserId\":\"user-020\",\"targetUserName\":\"赵六\"},"
          + "{\"taskId\":\"task-002\"}]";

      mockMvc.perform(post("/workflow/engine/task/batchTransfer")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
      ArgumentCaptor<String> targetUserCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> targetNameCaptor = ArgumentCaptor.forClass(String.class);
      verify(taskService).batchTransfer(idsCaptor.capture(), eq(USER_ID), isNull(),
          targetUserCaptor.capture(), targetNameCaptor.capture());
      assertThat(idsCaptor.getValue()).containsExactly("task-001", "task-002");
      assertThat(targetUserCaptor.getValue()).isEqualTo("user-020");
      assertThat(targetNameCaptor.getValue()).isEqualTo("赵六");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/instance/batchUrge")
  class BatchUrge {

    @Test
    @DisplayName("批量催办：计算 total/success/failed 并返回结果 VO")
    void should_return_batch_urge_result() throws Exception {
      String body = "[\"inst-001\",\"inst-002\",\"inst-003\"]";
      when(taskService.batchUrge(List.of("inst-001", "inst-002", "inst-003"), USER_ID, "请尽快处理")).thenReturn(2);

      mockMvc.perform(post("/workflow/engine/instance/batchUrge")
              .contentType(MediaType.APPLICATION_JSON)
              .param("comment", "请尽快处理")
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.totalCount").value(3))
          .andExpect(jsonPath("$.data.successCount").value(2))
          .andExpect(jsonPath("$.data.failedCount").value(1));

      verify(taskService).batchUrge(List.of("inst-001", "inst-002", "inst-003"), USER_ID, "请尽快处理");
    }

    @Test
    @DisplayName("instanceIds 为 null 时 totalCount 记为 0，不抛 NPE")
    void should_handle_null_instance_ids() throws Exception {
      when(taskService.batchUrge(isNull(), eq(USER_ID), isNull())).thenReturn(0);

      mockMvc.perform(post("/workflow/engine/instance/batchUrge")
              .contentType(MediaType.APPLICATION_JSON)
              .content("null"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.totalCount").value(0))
          .andExpect(jsonPath("$.data.failedCount").value(0));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/passAll")
  class PassAll {

    @Test
    @DisplayName("一键通过：返回通过数量并透传登录态")
    void should_pass_all() throws Exception {
      when(taskService.passAll(USER_ID, USER_NAME)).thenReturn(7);

      mockMvc.perform(post("/workflow/engine/task/passAll"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(7));

      verify(taskService).passAll(USER_ID, USER_NAME);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/task/done")
  class Done {

    @Test
    @DisplayName("已办查询：绑定分页参数并返回列表")
    void should_return_done_page() throws Exception {
      FlowRunTaskVO vo = new FlowRunTaskVO();
      vo.setId("task-done-001");
      PageResponse<List<FlowRunTaskVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(taskService.pageDoneVO(anyString(), anyString(), any(), any(), any(), any(), anyInt(), anyInt()))
          .thenReturn(pageResult);

      mockMvc.perform(get("/workflow/engine/task/done").param("page", "1").param("size", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.data[0].id").value("task-done-001"));

      verify(taskService).pageDoneVO(USER_ID, TENANT_ID, null, null, null, null, 1, 20);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/task/done/search")
  class DoneSearch {

    @Test
    @DisplayName("已办多维筛选：绑定 keyword 等参数")
    void should_bind_keyword() throws Exception {
      FlowRunTaskVO vo = new FlowRunTaskVO();
      vo.setId("task-search-001");
      PageResponse<List<FlowRunTaskVO>> pageResult = PageResponse.success(1L, 1L, 20L, List.of(vo));
      when(taskService.pageDoneSearchVO(anyString(), anyString(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
          .thenReturn(pageResult);

      mockMvc.perform(get("/workflow/engine/task/done/search").param("keyword", "请假"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.data[0].id").value("task-search-001"));

      verify(taskService).pageDoneSearchVO(USER_ID, TENANT_ID, null, null, null, null, "请假", 1, 20);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/task/overdue")
  class Overdue {

    @Test
    @DisplayName("超期任务：绑定 limit 并返回列表")
    void should_return_overdue_list() throws Exception {
      FlowRunTaskVO vo = new FlowRunTaskVO();
      vo.setId("task-overdue-001");
      when(taskService.listOverdueVO(USER_ID, TENANT_ID, 200)).thenReturn(List.of(vo));

      mockMvc.perform(get("/workflow/engine/task/overdue"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].id").value("task-overdue-001"));

      verify(taskService).listOverdueVO(USER_ID, TENANT_ID, 200);
    }

    @Test
    @DisplayName("limit 超过 500 触发参数校验负例（400）")
    void should_reject_limit_over_max() throws Exception {
      mockMvc.perform(get("/workflow/engine/task/overdue").param("limit", "501"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/stats/nodeDuration")
  class NodeDurationStats {

    @Test
    @DisplayName("节点耗时统计：flowCode 可选，返回 Map 列表")
    void should_return_node_duration_stats() throws Exception {
      when(taskService.nodeDurationStats("LEAVE_APPLY", TENANT_ID))
          .thenReturn(List.of(Map.of("nodeCode", "NODE_A", "avgDurationMs", 1000)));

      mockMvc.perform(get("/workflow/engine/stats/nodeDuration").param("flowCode", "LEAVE_APPLY"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].nodeCode").value("NODE_A"));

      verify(taskService).nodeDurationStats("LEAVE_APPLY", TENANT_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/stats/overdue")
  class OverdueStats {

    @Test
    @DisplayName("超期统计：绑定 flowCode 与时间区间")
    void should_return_overdue_stats() throws Exception {
      when(taskService.overdueStats("LEAVE_APPLY", null, null))
          .thenReturn(List.of(Map.of("flowCode", "LEAVE_APPLY", "overdueCount", 3)));

      mockMvc.perform(get("/workflow/engine/stats/overdue").param("flowCode", "LEAVE_APPLY"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].overdueCount").value(3));

      verify(taskService).overdueStats("LEAVE_APPLY", null, null);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/countersignRemove")
  class CountersignRemove {

    @Test
    @DisplayName("减签：绑定请求体并回填登录态后调用服务")
    void should_countersign_remove() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"targetUserId\":\"user-009\"}";

      mockMvc.perform(post("/workflow/engine/task/countersignRemove")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      ArgumentCaptor<FlowTaskOperateDTO> captor = ArgumentCaptor.forClass(FlowTaskOperateDTO.class);
      verify(taskService).countersignRemove(captor.capture());
      assertThat(captor.getValue().getTargetUserId()).isEqualTo("user-009");
      assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/{taskId}/read")
  class MarkRead {

    @Test
    @DisplayName("已阅：路径变量 taskId + 登录态 userId 调用服务")
    void should_mark_read() throws Exception {
      mockMvc.perform(post("/workflow/engine/task/{taskId}/read", TASK_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(taskService).markRead(TASK_ID, USER_ID);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/communicate")
  class Communicate {

    @Test
    @DisplayName("沟通：绑定请求体后调用服务")
    void should_communicate() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"comment\":\"请补充说明\",\"targetUserId\":\"user-009\"}";

      mockMvc.perform(post("/workflow/engine/task/communicate")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(taskService).communicate(any(FlowTaskOperateDTO.class));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/saveDraft")
  class SaveDraft {

    @Test
    @DisplayName("暂存待审：绑定请求体后调用门面 saveDraft")
    void should_save_draft() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"comment\":\"草稿意见\"}";

      mockMvc.perform(post("/workflow/engine/task/saveDraft")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).saveDraft(any(FlowTaskOperateDTO.class));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/addApprover")
  class AddApprover {

    @Test
    @DisplayName("追加处理人：绑定请求体后调用门面 addApprover")
    void should_add_approver() throws Exception {
      String body = "{\"taskId\":\"" + TASK_ID + "\",\"targetUserId\":\"user-030\",\"targetUserName\":\"钱七\"}";

      mockMvc.perform(post("/workflow/engine/task/addApprover")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).addApprover(any(FlowTaskOperateDTO.class));
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/{taskId}/retract")
  class Retract {

    @Test
    @DisplayName("取回审批：返回新任务 ID 并透传 comment")
    void should_retract() throws Exception {
      when(taskService.retract(TASK_ID, USER_ID, "取回重新填写")).thenReturn("task-new-001");

      mockMvc.perform(post("/workflow/engine/task/{taskId}/retract", TASK_ID)
              .param("comment", "取回重新填写"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("task-new-001"));

      verify(taskService).retract(TASK_ID, USER_ID, "取回重新填写");
    }

    @Test
    @DisplayName("comment 缺省时为 null")
    void should_retract_without_comment() throws Exception {
      when(taskService.retract(TASK_ID, USER_ID, null)).thenReturn("task-new-002");

      mockMvc.perform(post("/workflow/engine/task/{taskId}/retract", TASK_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value("task-new-002"));

      verify(taskService).retract(TASK_ID, USER_ID, null);
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/{taskId}/suspend")
  class SuspendTask {

    @Test
    @DisplayName("任务挂起：透传 reason 给门面")
    void should_suspend_task() throws Exception {
      mockMvc.perform(post("/workflow/engine/task/{taskId}/suspend", TASK_ID).param("reason", "等待材料"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).suspendTask(TASK_ID, USER_ID, "等待材料");
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/task/{taskId}/activate")
  class ActivateTask {

    @Test
    @DisplayName("任务激活：调用门面 activateTask")
    void should_activate_task() throws Exception {
      mockMvc.perform(post("/workflow/engine/task/{taskId}/activate", TASK_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(workflowFacade).activateTask(TASK_ID, USER_ID);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/todo/count")
  class TodoCount {

    @Test
    @DisplayName("返回待办数与登录用户信息")
    void should_return_todo_count() throws Exception {
      FlowRunTaskVO vo = new FlowRunTaskVO();
      vo.setId("task-001");
      when(taskService.listTodoByUser(USER_ID, null, null, TENANT_ID)).thenReturn(List.of(vo));

      mockMvc.perform(get("/workflow/engine/todo/count"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.todoCount").value(1))
          .andExpect(jsonPath("$.data.userId").value(USER_ID));

      verify(taskService).listTodoByUser(USER_ID, null, null, TENANT_ID);
    }

    @Test
    @DisplayName("未登录时返回 0 计数兜底，不触达服务")
    void should_return_zero_when_not_logged_in() throws Exception {
      RequestContext.clear();

      mockMvc.perform(get("/workflow/engine/todo/count"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.todoCount").value(0));

      verify(taskService, never()).listTodoByUser(anyString(), any(), any(), anyString());
    }
  }

  @Nested
  @DisplayName("POST /workflow/engine/todo/pushMine")
  class PushMyTodoCount {

    @Test
    @DisplayName("手动推送待办数：返回 true 并调用推送服务")
    void should_push_todo_count() throws Exception {
      mockMvc.perform(post("/workflow/engine/todo/pushMine"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));

      verify(todoCountPushService).pushTodoCount(USER_ID);
    }

    @Test
    @DisplayName("未登录时返回 false 且不调用推送服务")
    void should_return_false_when_not_logged_in() throws Exception {
      RequestContext.clear();

      mockMvc.perform(post("/workflow/engine/todo/pushMine"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value(false));

      verify(todoCountPushService, never()).pushTodoCount(anyString());
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/task/{taskId}/rejectableNodes")
  class RejectableNodes {

    @Test
    @DisplayName("实例 ID 为空时返回空列表，不调用 listPassedNodes")
    void should_return_empty_when_instance_id_null() throws Exception {
      when(taskService.getTaskInstanceId(TASK_ID)).thenReturn(null);

      mockMvc.perform(get("/workflow/engine/task/{taskId}/rejectableNodes", TASK_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").isEmpty());

      verify(taskService, never()).listPassedNodes(anyString());
    }

    @Test
    @DisplayName("实例 ID 非空时返回历史节点列表")
    void should_return_passed_nodes() throws Exception {
      when(taskService.getTaskInstanceId(TASK_ID)).thenReturn("inst-001");
      when(taskService.listPassedNodes("inst-001"))
          .thenReturn(List.of(Map.of("nodeCode", "NODE_A", "nodeName", "申请")));

      mockMvc.perform(get("/workflow/engine/task/{taskId}/rejectableNodes", TASK_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].nodeCode").value("NODE_A"));

      verify(taskService).listPassedNodes("inst-001");
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/countersign/instance/{instanceId}")
  class CountersignByInstance {

    @Test
    @DisplayName("按实例分页查询加签历史，返回 PageResponse 信封")
    void should_return_countersign_page() throws Exception {
      PageResponse<List<Map<String, Object>>> pageResult =
          PageResponse.success(1L, 1L, 20L, List.of(Map.of("action", "COUNTERSIGN_BEFORE")));
      when(taskService.pageCountersignByInstance("inst-001", 1, 20)).thenReturn(pageResult);

      mockMvc.perform(get("/workflow/engine/countersign/instance/{instanceId}", "inst-001")
              .param("pageNo", "1")
              .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.data[0].action").value("COUNTERSIGN_BEFORE"));

      verify(taskService).pageCountersignByInstance("inst-001", 1, 20);
    }
  }

  @Nested
  @DisplayName("GET /workflow/engine/countersign/task/{taskId}")
  class CountersignByTask {

    @Test
    @DisplayName("按任务查询加签历史，内存分页后返回 PageResponse 信封")
    void should_return_countersign_list_paged() throws Exception {
      when(taskService.listCountersignByTask(TASK_ID))
          .thenReturn(List.of(Map.of("action", "COUNTERSIGN_AFTER"), Map.of("action", "COUNTERSIGN_REMOVE")));

      mockMvc.perform(get("/workflow/engine/countersign/task/{taskId}", TASK_ID)
              .param("pageNo", "1")
              .param("pageSize", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.total").value(2))
          .andExpect(jsonPath("$.data.pageSize").value(1))
          .andExpect(jsonPath("$.data.data.length()").value(1));

      verify(taskService).listCountersignByTask(TASK_ID);
    }
  }
}
