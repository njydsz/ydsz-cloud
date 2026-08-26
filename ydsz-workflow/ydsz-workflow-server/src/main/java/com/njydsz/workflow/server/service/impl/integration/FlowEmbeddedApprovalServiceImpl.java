package com.njydsz.workflow.server.service.impl.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.EmbeddedApprovalActionDTO;
import com.njydsz.workflow.domain.dto.EmbeddedApprovalViewDTO;
import com.njydsz.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowHisTaskVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.server.service.FlowEmbeddedApprovalService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * 嵌入式审批服务实现
 *
 * <p>对 {@link FlowEmbeddedApprovalService} 接口的完整实现，承担工作流引擎的<b>嵌入式审批</b>能力。 业务系统在自己的页面（PC
 * Web）内嵌审批面板时，<b>单次接口拉齐</b>「实例 + 流程图 + 待办 + 历史」， 并提供「快捷操作」免去业务方感知 {@code taskId} / {@code
 * instanceId}，是大厂 B 端工作流集成的标准形态。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>面板加载（{@link #loadPanel}）</b>：通过 {@code businessType + businessId} 唯一定位流程实例， 一次返回「实例元数据 +
 *       流程图 JSON + 当前待办 + 历史轨迹 + 操作权限位」
 *   <li><b>快捷通过（{@link #quickPass}）</b>：业务页面直接点「通过」，无需关心 taskId 解析
 *   <li><b>快捷驳回（{@link #quickReject}）</b>：业务页面直接点「驳回」，支持驳回到任意节点
 *   <li><b>操作人角色判断</b>：根据 userId 判定其是「发起人 / 当前审批人 / 观察者」，不同角色展示不同操作按钮
 *   <li><b>权限校验</b>：业务方仅能操作自己发起的流程或被指派的待办，避免越权
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 业务系统（采购 / OA / CRM）在自己的详情页内嵌审批面板
 * EmbeddedApprovalViewDTO view = embeddedApprovalService.loadPanel(
 *     "purchase_order", "PO-2026-0001", currentUserId);
 *
 * if (view.canPass()) {
 *     embeddedApprovalService.quickPass(
 *         "purchase_order", "PO-2026-0001", currentUserId, "同意");
 * }
 * }</pre>
 *
 * <p><b>嵌入式 vs 标准 API：</b>
 *
 * <table>
 *   <caption>接口形态对比</caption>
 *   <tr><th>维度</th><th>嵌入式（{@code EmbeddedApprovalService}）</th><th>标准 API（{@code FlowInstanceService}）</th></tr>
 *   <tr><td>入参</td><td>{@code businessType + businessId}（业务语义）</td><td>{@code instanceId}（工作流语义）</td></tr>
 *   <tr><td>出参</td><td>实例 + 图 + 待办 + 历史 + 操作权限</td><td>单一对象（按接口）</td></tr>
 *   <tr><td>调用次数</td><td>1 次拉齐（前端无 N+1）</td><td>4-5 次组合</td></tr>
 *   <tr><td>适用场景</td><td>业务系统集成工作流</td><td>工作流管理后台</td></tr>
 * </table>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>{@link #loadPanel} 启用 {@code @Transactional(readOnly = true)}，支持只读副本路由
 *   <li>{@link #quickPass} / {@link #quickReject} 启用 {@code @Transactional(rollbackFor =
 *       Exception.class)}， 委托给 {@link FlowTaskService} 在子事务中处理
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>单次拉齐</b>：避免前端发起 4-5 个 HTTP 请求，减少网络往返，提升首屏渲染速度
 *   <li><b>业务语义</b>：以 {@code businessType + businessId} 而非 {@code instanceId} 定位， 业务方无需感知工作流的存在
 *   <li><b>权限分离</b>：所有快捷操作均校验「当前用户对当前 instance 的操作权限」， 避免业务方绕过工作流权限控制
 *   <li><b>PC Web only</b>：根据项目硬约束，<b>工作流永远不适配移动端</b>， 本服务仅服务于 PC 浏览器内嵌场景
 * </ul>
 *
 * <p><b>合规约束：</b>本类不涉及电子签章（CA 证书 / 司法存证 / 合同防篡改等）能力， 嵌入式审批面板的「签署生效」诉求由独立「电子签章服务」承担，本服务仅作为审批流转的承接方。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowEmbeddedApprovalService 接口定义
 * @see com.njydsz.workflow.domain.dto.EmbeddedApprovalViewDTO 嵌入式审批面板视图
 * @see FlowInstanceService 流程实例服务
 * @see FlowTaskService 流程任务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowEmbeddedApprovalServiceImpl implements FlowEmbeddedApprovalService {

  /** 流程实例服务，启动/查询/终止嵌入式审批流程 */
  private final FlowInstanceService instanceService;

  /** 流程任务服务，执行通过/驳回等审批操作 */
  private final FlowTaskService taskService;

  /** P2-2: 历史任务仓储（嵌入式审批面板加载审批轨迹） */
  private final FlowHisTaskRepository hisTaskRepository;

  /** MapStruct 转换器（DO/VO/DTO 转换） */
  private final WorkflowConverter converter;

  /** 操作人角色：发起人 */
  private static final String ROLE_INITIATOR = "INITIATOR";

  /** 操作人角色：当前审批人 */
  private static final String ROLE_APPROVER = "APPROVER";

  /** 操作人角色：观察者（无操作权限） */
  private static final String ROLE_OBSERVER = "OBSERVER";

  @Override
  @Transactional(readOnly = true)
  public EmbeddedApprovalViewDTO loadPanel(String businessType, String businessId, String userId) {
    if (businessType == null
        || businessType.isBlank()
        || businessId == null
        || businessId.isBlank()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("businessType / businessId 不能为空")
          .build();
    }

    // 1. 查流程实例
    FlowInstanceVO instance = instanceService.getByBusiness(businessType, businessId);
    if (instance == null) {
      // 未发起流程，返回空面板（前端可点击"发起审批"按钮）
      return EmbeddedApprovalViewDTO.builder()
          .businessType(businessType)
          .businessId(businessId)
          .instance(null)
          .diagram(null)
          .currentTasks(Collections.emptyList())
          .history(Collections.emptyList())
          .myRole(ROLE_OBSERVER)
          .actions(List.of("SUBMIT"))
          .canRecall(false)
          .finished(false)
          .message("未发起流程")
          .build();
    }

    // 2. 查当前待办
    List<FlowRunTaskVO> pending = taskService.listPendingByInstance(instance.getId());

    // 3. 计算 myRole / mine / actions
    String myRole = computeMyRole(instance, pending, userId);
    List<EmbeddedApprovalViewDTO.CurrentTaskView> currentTaskViews =
        buildCurrentTaskViews(pending, userId);
    List<String> actions = computeActions(instance, pending, userId);
    boolean canRecall = canRecall(instance, pending, userId);
    boolean finished = FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished();

    // 4. 查历史轨迹（合并历史任务 + 审计日志）
    List<Map<String, Object>> history = loadHistory(instance.getId());

    // 5. 流程图（带高亮当前节点）
    Map<String, Object> diagram = loadDiagram(instance);

    // 6. 转 instance view
    List<FlowInstanceViewDTO.FlowTaskViewDTO> taskViews =
        currentTaskViews.stream()
            .map(
                t ->
                    FlowInstanceViewDTO.FlowTaskViewDTO.builder()
                        .id(t.getTaskId())
                        .nodeCode(t.getNodeCode())
                        .nodeName(t.getNodeName())
                        .nodeType(t.getNodeType())
                        .assigneeType(t.getAssigneeType())
                        .assigneeId(t.getAssigneeId())
                        .assigneeName(t.getAssigneeName())
                        .performType(t.getPerformType())
                        .taskStatus(t.getTaskStatus())
                        .createAt(t.getCreateAt())
                        .dueAt(t.getDueAt())
                        .build())
            .toList();
    FlowInstanceViewDTO instanceView = instanceService.toView(instance, taskViews);

    return EmbeddedApprovalViewDTO.builder()
        .businessType(businessType)
        .businessId(businessId)
        .instance(instanceView)
        .diagram(diagram)
        .currentTasks(currentTaskViews)
        .history(history)
        .myRole(myRole)
        .actions(actions)
        .canRecall(canRecall)
        .finished(finished)
        .message(finished ? "流程已结束" : "流程进行中")
        .build();
  }

  @Override
  public void quickAction(EmbeddedApprovalActionDTO dto) {
    if (dto == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_afb63fa5")
          .build();
    }
    String action = dto.getAction() == null ? "" : dto.getAction().toUpperCase();
    FlowInstanceVO instance =
        instanceService.getByBusiness(dto.getBusinessType(), dto.getBusinessId());
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("error.workflow.msg_b72e8598")
          .build();
    }
    if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_8243ec9a")
          .build();
    }

    switch (action) {
      case "PASS":
      case "REJECT":
      case "TRANSFER":
      case "DELEGATE":
        {
          FlowRunTaskVO mine = findMyTask(instance.getId(), dto.getUserId());
          if (mine == null) {
            throw SysException.builder()
                .resultCode(YdszResultCode.FORBIDDEN)
                .message("error.workflow.msg_1440b2f2")
                .build();
          }
          FlowTaskOperateDTO op = new FlowTaskOperateDTO();
          op.setTaskId(mine.getId());
          op.setUserId(dto.getUserId());
          op.setUserName(dto.getUserName());
          op.setComment(dto.getComment());
          op.setCommentType(dto.getCommentType());
          op.setTargetUserId(dto.getTargetUserId());
          op.setTargetUserName(dto.getTargetUserName());
          op.setVariables(dto.getVariables());
          op.setTenantId(dto.getTenantId());
          if ("PASS".equals(action)) {
            taskService.pass(op);
          } else if ("REJECT".equals(action)) {
            taskService.reject(op);
          } else if ("TRANSFER".equals(action)) {
            if (dto.getTargetUserId() == null) {
              throw SysException.builder()
                  .resultCode(YdszResultCode.BAD_REQUEST)
                  .message("error.workflow.msg_df306e2b")
                  .build();
            }
            taskService.transfer(op);
          } else { // DELEGATE
            if (dto.getTargetUserId() == null) {
              throw SysException.builder()
                  .resultCode(YdszResultCode.BAD_REQUEST)
                  .message("委派操作必须指定 targetUserId")
                  .build();
            }
            taskService.delegate(op);
          }
          break;
        }
      case "URGE":
        {
          List<String> urged =
              taskService.urge(instance.getId(), dto.getUserId(), dto.getComment());
          log.info(
              "[EmbeddedApproval] URGE instance={} operator={} count={}",
              instance.getId(),
              dto.getUserId(),
              urged.size());
          break;
        }
      case "WITHDRAW":
        {
          boolean ok = instanceService.recall(instance.getId(), dto.getUserId());
          if (!ok) {
            throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
                .message("error.workflow.msg_ad7c50c2")
                .build();
          }
          break;
        }
      default:
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.workflow.msg_3adf9016")
            .params(dto.getAction())
            .build();
    }
  }

  // ============ 私有方法 ============

  /**
   * 计算当前用户在流程中的角色
   *
   * @param instance 参数说明
   * @param pending 参数说明
   * @param userId 参数说明
   * @return 返回值说明
   */
  private String computeMyRole(FlowInstanceVO instance, List<FlowRunTaskVO> pending, String userId) {
    if (userId == null) {
      return ROLE_OBSERVER;
    }
    if (userId.equals(instance.getInitiatorId())) {
      return ROLE_INITIATOR;
    }
    if (pending != null) {
      for (FlowRunTaskVO t : pending) {
        if (isMine(t, userId) && !FlowTaskStatus.valueOf(t.getTaskStatus()).isFinished()) {
          return ROLE_APPROVER;
        }
      }
    }
    return ROLE_OBSERVER;
  }

  /**
   * 计算当前用户可执行的操作
   *
   * @param instance 参数说明
   * @param pending 参数说明
   * @param userId 参数说明
   * @return 返回值说明
   */
  private List<String> computeActions(
      FlowInstanceVO instance, List<FlowRunTaskVO> pending, String userId) {
    List<String> actions = new ArrayList<>();
    if (userId == null) {
      return actions;
    }
    boolean isInitiator = userId.equals(instance.getInitiatorId());
    boolean isFinished = FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished();
    boolean canActAsApprover = false;
    if (pending != null) {
      for (FlowRunTaskVO t : pending) {
        if (isMine(t, userId) && !FlowTaskStatus.valueOf(t.getTaskStatus()).isFinished()) {
          canActAsApprover = true;
          break;
        }
      }
    }

    if (isFinished) {
      // 流程已结束，只能查看
      return actions;
    }

    if (canActAsApprover) {
      actions.add("PASS");
      actions.add("REJECT");
      actions.add("TRANSFER");
      actions.add("DELEGATE");
      actions.add("URGE");
    }
    if (isInitiator) {
      // 发起人可催办
      if (!actions.contains("URGE")) {
        actions.add("URGE");
      }
      // 撤回（仅当下一节点未处理）
      if (canRecall(instance, pending, userId)) {
        actions.add("WITHDRAW");
      }
    }
    return actions;
  }

  /**
   * 当前用户是否可撤回（P0-4 修复：补全下游已处理判断）
   * 
   * <p>撤回条件：
   * 
   * <ol>
   * <li>操作人是发起人
   * <li>实例未结束（RUNNING）
   * <li>所有 PENDING 任务均未签收（CLAIMED）
   * <li>【P0-4 新增】无已完成的历史任务 — 如果有审批人已处理过任务，说明流程已推进到下游，不可撤回
   * </ol>
   *
   * @param instance 参数说明
   * @param pending 参数说明
   * @param userId 参数说明
   * @return 返回值说明
   */
  private boolean canRecall(FlowInstanceVO instance, List<FlowRunTaskVO> pending, String userId) {
    if (userId == null) {
      return false;
    }
    if (!userId.equals(instance.getInitiatorId())) {
      return false;
    }
    if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
      return false;
    }
    // 撤回前置条件：当前节点的 PENDING 任务全部属于发起人（没有真实审批人介入）
    // 简化判断：所有 PENDING 任务均未签收（CLAIMED）
    if (pending == null) {
      return false;
    }
    for (FlowRunTaskVO t : pending) {
      if (FlowTaskStatus.CLAIMED.name().equals(t.getTaskStatus())) {
        return false;
      }
    }
    // P0-4: 检查是否有已完成的历史任务（排除 START 节点）— 有则说明审批人已处理过，流程已推进，不可撤回
    List<FlowHisTaskVO> hisTasks = hisTaskRepository.findByInstanceId(instance.getId());
    if (hisTasks != null) {
      // 排除 START(0) 节点归档记录（发起人提交产生的），只检查是否有真实审批人处理过
      boolean hasApprovalHistory =
          hisTasks.stream().anyMatch(h -> h.getNodeType() != null && h.getNodeType() != 0);
      if (hasApprovalHistory) {
        log.debug("[EmbeddedApproval] 实例已有审批历史任务，不可撤回 instanceId={}", instance.getId());
        return false;
      }
    }
    return true;
  }

  /**
   * 判定 task 是否属于指定 userId（USER/ROLE/DEPT 等多种 assigneeType 均纳入判断）
   *
   * @param t 参数说明
   * @param userId 参数说明
   * @return 返回值说明
   */
  private boolean isMine(FlowRunTaskVO t, String userId) {
    if (t == null || userId == null) {
      return false;
    }
    String assigneeType = t.getAssigneeType();
    String assigneeId = t.getAssigneeId();
    String uid = String.valueOf(userId);
    if (assigneeType == null || "USER".equalsIgnoreCase(assigneeType)) {
      return uid.equals(assigneeId);
    }
    // ROLE / DEPT 场景：assigneeId 形如 "1,2,3"，简化判断：包含即可（实际由 assignee resolver 解析）
    if (assigneeId != null) {
      for (String s : assigneeId.split(",")) {
        if (uid.equals(s.trim())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * 找到当前用户 mine 的第一个未完成任务
   *
   * @param instanceId 参数说明
   * @param userId 参数说明
   * @return 返回值说明
   */
  private FlowRunTaskVO findMyTask(String instanceId, String userId) {
    if (userId == null) {
      return null;
    }
    List<FlowRunTaskVO> pending = taskService.listPendingByInstance(instanceId);
    for (FlowRunTaskVO t : pending) {
      if (isMine(t, userId) && !FlowTaskStatus.valueOf(t.getTaskStatus()).isFinished()) {
        return t;
      }
    }
    return null;
  }

  /**
   * 构造当前待办视图
   *
   * @param pending 参数说明
   * @param userId 参数说明
   * @return 返回值说明
   */
  private List<EmbeddedApprovalViewDTO.CurrentTaskView> buildCurrentTaskViews(
      List<FlowRunTaskVO> pending, String userId) {
    if (pending == null || pending.isEmpty()) {
      return Collections.emptyList();
    }
    List<EmbeddedApprovalViewDTO.CurrentTaskView> out = new ArrayList<>(pending.size());
    for (FlowRunTaskVO t : pending) {
      out.add(
          EmbeddedApprovalViewDTO.CurrentTaskView.builder()
              .taskId(t.getId())
              .nodeCode(t.getNodeCode())
              .nodeName(t.getNodeName())
              .nodeType(t.getNodeType())
              .assigneeType(t.getAssigneeType())
              .assigneeId(t.getAssigneeId())
              .assigneeName(t.getAssigneeName())
              .performType(t.getPerformType())
              .taskStatus(t.getTaskStatus())
              .createAt(t.getCreatedAt())
              .dueAt(t.getDueAt())
              .mine(isMine(t, userId))
              .build());
    }
    return out;
  }

  /** 加载审批轨迹（历史任务 + 审计日志） */
  private List<Map<String, Object>> loadHistory(String instanceId) {
    try {
      List<FlowHisTaskVO> his = hisTaskRepository.findByInstanceId(instanceId);
      if (his == null || his.isEmpty()) {
        return Collections.emptyList();
      }
      List<Map<String, Object>> out = new ArrayList<>(his.size());
      for (FlowHisTaskVO t : his) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "TASK");
        m.put("taskId", t.getId());
        m.put("nodeCode", t.getNodeCode());
        m.put("nodeName", t.getNodeName());
        m.put("assigneeId", t.getAssigneeId());
        m.put("assigneeName", t.getAssigneeName());
        m.put("action", t.getPerformType());
        m.put("comment", t.getComment());
        m.put("timestamp", t.getFinishAt());
        m.put("taskStatus", t.getTaskStatus());
        out.add(m);
      }
      return out;
    } catch (Exception e) {
      log.warn("[EmbeddedApproval] 加载历史轨迹失败: {}", e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * 加载流程图（含高亮当前节点）
   *
   * <p>嵌入式场景下流程图较大（包含 definition/nodes/skips），由前端按需通过 GET /workflow/engine/instance/{id}/diagram
   * 单独拉取，本接口不返回以保持轻量。 仅返回最简的节点信息用于高亮当前节点。
   */
  private Map<String, Object> loadDiagram(FlowInstanceVO instance) {
    Map<String, Object> light = new LinkedHashMap<>();
    light.put("currentNodeCode", instance.getCurrentNodeCode());
    light.put("currentNodeName", instance.getCurrentNodeName());
    light.put("flowCode", instance.getFlowCode());
    light.put("flowStatus", instance.getFlowStatus());
    return light;
  }
}
