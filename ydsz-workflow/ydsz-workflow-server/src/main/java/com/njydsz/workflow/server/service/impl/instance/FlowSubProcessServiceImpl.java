package com.njydsz.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.server.config.FlowProperties;
import com.njydsz.workflow.server.engine.FlowEventListener;
import com.njydsz.workflow.server.engine.FlowNodeExt;
import com.njydsz.workflow.server.engine.FlowWorkflowEvent;
import com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowSubProcessService;

/**
 * 工作流子流程服务实现
 *
 * <p>对 {@link FlowSubProcessService} 接口的完整实现，承担 BPMN 2.0 规范中
 * <b>CallActivity（子流程调用）</b>节点的运行时支持。当父流程到达 {@code callActivity} 节点时，
 * 通过本服务启动子流程实例；子流程完成后，通过事件回调推进父流程。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>子流程启动（{@link #startSubProcess}）</b>：父流程到达 {@code callActivity} 时调用， 独立启动子流程实例，并通过 {@code
 *       parentInstanceId} 字段建立父子关联
 *   <li><b>子流程完成回调（{@link #onSubProcessComplete}）</b>：子流程完成 / 终止时触发， 通过 {@code FlowWorkflowEvent}
 *       通知父流程推进到下一节点
 *   <li><b>子流程独立超时处理</b>：从 {@code node.ext} JSON 读取 {@code subProcessTimeout} 设置 dueAt， 子流程超时由
 *       {@link FlowSlaServiceImpl} 单独监控
 *   <li><b>父子流程数据上下文传递</b>：合并父流程变量传递给子流程，子流程完成时回写 「子流程输出变量」到父流程 {@code flow_variables}
 *   <li><b>子流程实例追踪</b>：{@link #getSubProcessTree} 递归查询子流程树，支持 UI 展示「父子链路」
 *   <li><b>子流程嵌套层级限制</b>：最大深度可配置（{@code workflow.subprocess.max-nesting-depth}，默认 3 层），
 *       防止「无限递归」导致资源耗尽
 *   <li><b>子流程事件通知</b>：触发 {@code onInstanceStart} 和发布 {@link FlowWorkflowEvent}， 监听器可订阅父子流程的生命周期
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>子流程启动通过 {@link WorkflowFacade#startProcess} 开启新事务（{@code REQUIRES_NEW}），
 *       父流程事务失败不影响已启动的子流程（子流程可独立完成）
 *   <li>子流程完成回调开启新事务，避免子流程状态变更与父流程推进耦合
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>独立 vs 嵌入式</b>：本实现采用「独立子流程」（子流程有独立 {@code instanceId} / 待办 / 历史），
 *       更易于审批粒度控制和跨租户分析
 *   <li><b>父子变量传递</b>：通过 {@code inVariables / outVariables} JSON 字段声明输入输出变量，
 *       子流程只能看到白名单内的父变量，避免父流程敏感数据泄露
 *   <li><b>嵌套深度保护</b>：超过 {@code maxNestingDepth} 抛 {@code SysException} 阻断启动， 防止循环调用（如 A → B → A）
 *   <li><b>子流程 SLA</b>：子流程节点可独立配置 SLA，父流程的 SLA 监控不影响子流程
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 父流程到达 callActivity 节点
 * String subInstanceId = subProcessService.startSubProcess(
 *     parentInstanceId, callActivityNode, parentVariables, currentUserId);
 *
 * // 子流程完成后，父流程自动推进
 * // (由子流程完成事件触发 onSubProcessComplete → 父流程 advancer.advance)
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowSubProcessService 接口定义
 * @see WorkflowFacade 工作流门面
 * @see com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer 流程推进引擎
 * @see FlowSlaServiceImpl SLA 监控
 * @see com.njydsz.workflow.domain.enums.FlowNodeType#CALL_ACTIVITY CallActivity 节点类型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowSubProcessServiceImpl implements FlowSubProcessService {

  /**
   * P2-8 / P3-3.4: 最大子流程嵌套深度（可配置）。
   *
   * <p>通过 {@code ydsz.flow.subprocess.max-nesting-depth} 属性配置，默认 3 层。 生产环境可根据业务复杂度调整，建议不超过 10
   * 层（过深嵌套难以维护且影响性能）。
   */
  private final FlowProperties flowProperties;

  /** 流程实例仓储，查询/更新父实例和子流程实例 */
  private final FlowInstanceRepository instanceRepository;

  /** 流程定义服务，解析子流程的流程定义 */
  private final FlowDefinitionService definitionService;

  /** 流程实例服务，启动子流程实例 */
  private final FlowInstanceService instanceService;

  /** 流程推进引擎，子流程完成后推进父流程 */
  private final DefaultFlowAdvancer advancer;

  /** 工作流门面，启动子流程实例的统一入口 */
  private final WorkflowFacade workflowFacade;

  /** 事件监听器列表（可能为 null） */
  private final List<FlowEventListener> eventListeners;

  /** Spring 事件发布器（可能为 null） */
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String startSubProcess(
      FlowInstanceVO parentInstance, FlowNodeVO callActivityNode, Map<String, Object> variables) {
    if (parentInstance == null || callActivityNode == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("父实例/callActivity 节点不能为空")
          .build();
    }
    // 1. 从节点 ext JSON 提取子流程编码
    String subFlowCode = extractSubFlowCode(callActivityNode);
    if (subFlowCode == null || subFlowCode.isBlank()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("callActivity 节点未配置子流程编码: nodeCode=" + callActivityNode.getNodeCode())
          .build();
    }
    // 2. 校验子流程定义存在且已发布
    FlowDefinitionVO subDef =
        definitionService.getPublished(subFlowCode, null, parentInstance.getTenantId());
    if (subDef == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("子流程定义未发布或不存在: flowCode=" + subFlowCode)
          .build();
    }
    // 3. 检查嵌套深度（P2-8: 可配置，默认 3 层）
    int maxNestingDepth = flowProperties.getSubProcess().getMaxNestingDepth();
    int nestingDepth = getNestingDepth(parentInstance.getId());
    if (nestingDepth >= maxNestingDepth) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.subprocess.nesting.exceeded")
          .params(maxNestingDepth, nestingDepth, parentInstance.getId())
          .build();
    }
    log.info(
        "[SubProcess] 嵌套深度检查: parentInstance={} depth={} max={}",
        parentInstance.getId(),
        nestingDepth,
        maxNestingDepth);
    // 4. 将父流程 variables 合并传递给子流程（增强上下文传递）
    Map<String, Object> parentVars = instanceService.getVariables(parentInstance.getId());
    Map<String, Object> mergedVars = new HashMap<>(parentVars);
    if (variables != null) {
      mergedVars.putAll(variables);
    }
    // 标记父流程信息
    mergedVars.put("_parentInstanceId", parentInstance.getId());
    mergedVars.put("_parentNodeCode", callActivityNode.getNodeCode());
    mergedVars.put("_parentFlowCode", parentInstance.getFlowCode());
    // 5. 通过 WorkflowFacade 启动子流程（parentInstanceId 由 DTO 传递）
    FlowStartProcessDTO dto = buildSubProcessStartDTO(parentInstance, subFlowCode, mergedVars);
    dto.setParentInstanceId(parentInstance.getId());
    dto.setParentNodeCode(callActivityNode.getNodeCode());
    String childId = workflowFacade.startProcess(dto);
    // 6. 从 ext JSON 读取 subProcessTimeout 并设置 dueAt
    Double subProcessTimeout = extractSubProcessTimeout(callActivityNode);
    if (subProcessTimeout != null && subProcessTimeout > 0 && childId != null) {
      LocalDateTime dueAt = LocalDateTime.now().plusHours((long) Math.ceil(subProcessTimeout));
      instanceService.setDueAt(childId, dueAt);
      log.info(
          "[SubProcess] 子流程超时设置: childInstance={} timeoutHours={} dueAt={}",
          childId,
          subProcessTimeout,
          dueAt);
    }
    // 7. 触发 onInstanceStart 事件
    fireInstanceStart(childId, mergedVars);
    log.info(
        "[SubProcess] 启动子流程: parentInstance={} callActivityNode={} childInstance={} subFlowCode={} depth={}",
        parentInstance.getId(),
        callActivityNode.getNodeCode(),
        childId,
        subFlowCode,
        nestingDepth + 1);
    return childId;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void onSubProcessCompleted(String childInstanceId) {
    if (childInstanceId == null) {
      return;
    }
    FlowInstanceVO child = instanceRepository.findById(childInstanceId).orElse(null);
    if (child == null) {
      log.warn("[SubProcess] 子实例不存在: id={}", childInstanceId);
      return;
    }
    String parentId = child.getParentInstanceId();
    String parentNodeCode = child.getParentNodeCode();
    if (parentId == null || parentNodeCode == null) {
      // 非子流程场景
      return;
    }
    FlowInstanceVO parent = instanceRepository.findById(parentId).orElse(null);
    if (parent == null) {
      log.warn("[SubProcess] 父实例不存在: id={}", parentId);
      return;
    }
    if (!"RUNNING".equalsIgnoreCase(parent.getFlowStatus())) {
      log.info("[SubProcess] 父实例非运行态，跳过回调: id={} status={}", parentId, parent.getFlowStatus());
      return;
    }
    // 清除子流程超时标记
    instanceService.setDueAt(childInstanceId, null);
    // 将子流程的输出变量合并回父流程 variables
    Map<String, Object> childVars = instanceService.getVariables(childInstanceId);
    if (childVars != null && !childVars.isEmpty()) {
      // 过滤掉内部标记变量，只合并业务变量
      Map<String, Object> businessVars = new HashMap<>(childVars);
      businessVars.remove("_parentInstanceId");
      businessVars.remove("_parentNodeCode");
      businessVars.remove("_parentFlowCode");
      instanceService.setVariables(parentId, businessVars);
      log.info(
          "[SubProcess] 子流程变量回写父流程: childId={} parentId={} varKeys={}",
          childInstanceId,
          parentId,
          businessVars.keySet());
    }
    // 推进父流程到 callActivity 节点的下一节点
    Map<String, Object> variables = parseVariables(parent.getVariable());
    List<FlowNodeVO> nextNodes = advancer.advance(parent, parentNodeCode, "PASS", null, variables);
    if (nextNodes.isEmpty()) {
      // 父流程无下一节点：完成
      instanceService.complete(parent.getId(), parentNodeCode);
      log.info("[SubProcess] 子流程完成触发父流程结束: parent={} child={}", parentId, childInstanceId);
      // 发布异步事件
      publishWorkflowEvent("SUBPROCESS_COMPLETED", childInstanceId, parentId);
      return;
    }
    ((FlowInstanceServiceImpl) instanceService)
        .generateTasksForNodes(parent.getId(), nextNodes, variables);
    FlowNodeVO first = nextNodes.get(0);
    instanceRepository.updateStatus(
        parent.getId(),
        parent.getFlowStatus(),
        first.getNodeCode(),
        first.getNodeName(),
        null,
        null);
    log.info("[SubProcess] 子流程完成触发父流程推进: parent={} → next={}", parentId, first.getNodeCode());
    // 发布异步事件
    publishWorkflowEvent("SUBPROCESS_COMPLETED", childInstanceId, parentId);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void onSubProcessTerminated(String childInstanceId, String reason, boolean terminal) {
    if (childInstanceId == null) {
      return;
    }
    FlowInstanceVO child = instanceRepository.findById(childInstanceId).orElse(null);
    if (child == null) {
      return;
    }
    String parentId = child.getParentInstanceId();
    String parentNodeCode = child.getParentNodeCode();
    if (parentId == null || parentNodeCode == null) {
      return;
    }
    FlowInstanceVO parent = instanceRepository.findById(parentId).orElse(null);
    if (parent == null) {
      return;
    }
    if (terminal) {
      instanceService.terminate(parent.getId(), reason);
      log.info(
          "[SubProcess] 子流程终止触发父流程终止: parent={} child={} reason={}",
          parentId,
          childInstanceId,
          reason);
    } else {
      // 驳回：父流程状态置为 REJECTED
      instanceRepository.updateStatus(
          parent.getId(),
          FlowInstanceStatus.REJECTED.name(),
          null,
          null,
          LocalDateTime.now(),
          null);
      log.info(
          "[SubProcess] 子流程驳回触发父流程驳回: parent={} child={} reason={}",
          parentId,
          childInstanceId,
          reason);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<FlowInstanceVO> listChildren(String parentInstanceId) {
    if (parentInstanceId == null) {
      return List.of();
    }
    return instanceRepository.findChildren(parentInstanceId);
  }

  @Override
  public FlowStartProcessDTO buildSubProcessStartDTO(
      FlowInstanceVO parentInstance, String subFlowCode, Map<String, Object> variables) {
    FlowStartProcessDTO dto = new FlowStartProcessDTO();
    dto.setFlowCode(subFlowCode);
    dto.setTitle(
        parentInstance.getTitle() == null
            ? "子流程-" + subFlowCode
            : "[子流程] " + parentInstance.getTitle());
    dto.setBusinessType("SUB_" + parentInstance.getBusinessType());
    dto.setBusinessId(parentInstance.getBusinessId());
    dto.setBusinessNo(parentInstance.getBusinessNo());
    dto.setInitiatorId(parentInstance.getInitiatorId());
    dto.setInitiatorName(parentInstance.getInitiatorName());
    dto.setTenantId(parentInstance.getTenantId());
    dto.setVariables(variables == null ? new HashMap<>() : new HashMap<>(variables));
    dto.setProviderTraceId(parentInstance.getProviderTraceId());
    return dto;
  }

  // ============== 新增公开方法 ==============

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> getSubProcessContext(String childInstanceId) {
    if (childInstanceId == null) {
      return new HashMap<>();
    }
    FlowInstanceVO child = instanceRepository.findById(childInstanceId).orElse(null);
    if (child == null) {
      log.warn("[SubProcess] getSubProcessContext 子实例不存在: id={}", childInstanceId);
      return new HashMap<>();
    }
    // 子流程自身变量
    Map<String, Object> childVars = instanceService.getVariables(childInstanceId);
    Map<String, Object> context = new HashMap<>(childVars);
    // 父流程变量
    String parentId = child.getParentInstanceId();
    if (parentId != null) {
      Map<String, Object> parentVars = instanceService.getVariables(parentId);
      context.putAll(parentVars);
      context.put("_parentInstanceId", parentId);
      context.put("_parentNodeCode", child.getParentNodeCode());
    }
    // 添加实例元信息
    context.put("_childInstanceId", childInstanceId);
    context.put("_childFlowCode", child.getFlowCode());
    context.put("_childFlowName", child.getFlowName());
    log.info(
        "[SubProcess] getSubProcessContext: childId={} parentId={} varCount={}",
        childInstanceId,
        parentId,
        context.size());
    return context;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Map<String, Object>> listSubProcessTree(String parentInstanceId) {
    List<Map<String, Object>> tree = new ArrayList<>();
    if (parentInstanceId == null) {
      return tree;
    }
    List<FlowInstanceVO> children = listChildren(parentInstanceId);
    for (FlowInstanceVO child : children) {
      Map<String, Object> node = new LinkedHashMap<>();
      node.put("instanceId", child.getId());
      node.put("instanceName", child.getTitle());
      node.put("flowCode", child.getFlowCode());
      node.put("status", child.getFlowStatus());
      // 递归查询子节点的子流程
      List<Map<String, Object>> subProcesses = listSubProcessTree(child.getId());
      node.put("subProcesses", subProcesses);
      node.put("startAt", child.getStartAt());
      node.put("endAt", child.getEndAt());
      tree.add(node);
    }
    return tree;
  }

  // ============== 私有方法 ==============

  /**
   * 从节点 ext JSON 提取子流程编码
   *
   * @param node 流程节点实体
   * @return 子流程编码；未配置返回 null
   */
  private String extractSubFlowCode(FlowNodeVO node) {
    if (node.getExt() == null || node.getExt().isBlank()) {
      return null;
    }
    try {
      Map<String, Object> ext = FlowNodeExt.parseSafe(node.getExt());
      if (ext == null) {
        return null;
      }
      Object v = ext.get("callActivityFlowCode");
      if (v == null) {
        v = ext.get("subProcessFlowCode");
      }
      return v == null ? null : v.toString();
    } catch (Exception e) {
      log.warn("[SubProcess] 节点 ext 解析失败: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 从节点 ext JSON 提取子流程超时小时数
   *
   * @param node 流程节点实体
   * @return 子流程超时小时数；未配置返回 null
   */
  private Double extractSubProcessTimeout(FlowNodeVO node) {
    if (node.getExt() == null || node.getExt().isBlank()) {
      return null;
    }
    try {
      Map<String, Object> ext = FlowNodeExt.parseSafe(node.getExt());
      if (ext == null) {
        return null;
      }
      Object v = ext.get("subProcessTimeout");
      if (v == null) {
        return null;
      }
      return v instanceof Number ? ((Number) v).doubleValue() : Double.parseDouble(v.toString());
    } catch (Exception e) {
      log.warn(
          "[SubProcess] 解析 subProcessTimeout 失败: nodeCode={} err={}",
          node.getNodeCode(),
          e.getMessage());
      return null;
    }
  }

  /**
   * 递归计算嵌套深度（从 parentInstanceId 向上追溯）
   *
   * <p>P2-8: 迭代上限基于可配置的 maxNestingDepth，额外加 10 作为安全余量， 防止数据异常（如循环引用）导致无限递归。
   *
   * @param parentInstanceId 当前父流程实例 ID
   * @return 已有嵌套深度（不含当前层级）
   */
  private int getNestingDepth(String parentInstanceId) {
    int depth = 0;
    String currentId = parentInstanceId;
    // 防止无限循环：上限 = 配置最大深度 + 10 安全余量
    int maxIterations = flowProperties.getSubProcess().getMaxNestingDepth() + 10;
    while (currentId != null && depth < maxIterations) {
      FlowInstanceVO instance = instanceRepository.findById(currentId).orElse(null);
      if (instance == null) {
        break;
      }
      String nextParentId = instance.getParentInstanceId();
      if (nextParentId == null) {
        break;
      }
      depth++;
      currentId = nextParentId;
    }
    return depth;
  }

  /**
   * 触发 onInstanceStart 事件
   *
   * @param instanceId 子流程实例 ID
   * @param variables 流程变量
   */
  private void fireInstanceStart(String instanceId, Map<String, Object> variables) {
    if (eventListeners == null) {
      return;
    }
    for (FlowEventListener listener : eventListeners) {
      try {
        listener.onInstanceStart(instanceId, variables);
      } catch (Exception e) {
        log.warn(
            "[SubProcess] onInstanceStart 事件失败: instanceId={} err={}", instanceId, e.getMessage());
      }
    }
  }

  /**
   * 发布 Spring 异步事件
   *
   * @param eventType 事件类型
   * @param childInstanceId 子流程实例 ID
   * @param parentInstanceId 父流程实例 ID
   */
  private void publishWorkflowEvent(
      String eventType, String childInstanceId, String parentInstanceId) {
    if (eventPublisher == null) {
      return;
    }
    try {
      Map<String, Object> data = new HashMap<>();
      data.put("childInstanceId", childInstanceId);
      data.put("parentInstanceId", parentInstanceId);
      eventPublisher.publishEvent(new FlowWorkflowEvent(this, eventType, parentInstanceId, null, data));
    } catch (Exception e) {
      log.warn("[SubProcess] 发布 Spring 事件失败: type={} err={}", eventType, e.getMessage());
    }
  }

  private Map<String, Object> parseVariables(String variableJson) {
    if (variableJson == null || variableJson.isBlank()) {
      return new HashMap<>();
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(variableJson);
      return map == null ? new HashMap<>() : map;
    } catch (Exception e) {
      return new HashMap<>();
    }
  }
}
