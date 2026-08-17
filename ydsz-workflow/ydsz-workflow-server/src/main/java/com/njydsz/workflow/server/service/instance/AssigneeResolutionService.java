package com.njydsz.workflow.server.service.instance;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.domain.dto.FlowAssigneeDTO;
import com.njydsz.workflow.infra.entity.FlowInstanceDO;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.domain.enums.FlowAssigneeType;
import com.njydsz.workflow.server.engine.FlowVariableStrategy;

/**
 * 办理人解析服务
 *
 * <p>从 {@link com.njydsz.workflow.server.service.impl.instance.FlowTaskCreateService} 中抽出的办理人解析逻辑，
 * 承担运行时任务（{@link FlowRunTaskDO}）的办理人字段解析与填充职责。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>办理人解析</b>：根据节点 {@code permissionFlag} 解析实际办理人 ID， 支持显式指定、发起人兜底、变量策略解析三种路径
 *   <li><b>发起人识别</b>：从流程变量中提取发起人 ID（{@code initiatorId / _initiatorId}）
 * </ul>
 *
 * <p><b>设计意图：</b>FlowTaskCreateService 原承担任务创建 + 办理人解析 + 服务节点执行等多重职责，
 * 本次拆分将最独立的办理人解析逻辑先行抽出，后续将继续抽出委派改写、空办理人兜底等逻辑。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.impl.instance.FlowTaskCreateService 任务创建服务（调用方）
 * @see FlowVariableStrategy 变量表达式策略
 */
@Slf4j
@Service
public class AssigneeResolutionService {

  /** 变量策略，解析节点 permissionFlag 中的表达式 */
  private final FlowVariableStrategy variableStrategy;

  public AssigneeResolutionService(FlowVariableStrategy variableStrategy) {
    this.variableStrategy = variableStrategy;
  }

  /**
   * 解析办理人并写入任务实体
   *
   * <p>解析优先级：
   *
   * <ol>
   *   <li>{@code explicit} 非空 — 直接使用显式指定的办理人
   *   <li>{@code permissionFlag} 为空 — 回退为发起人（INITIATOR）
   *   <li>通过 {@link FlowVariableStrategy#resolveAssignee} 解析表达式
   * </ol>
   *
   * @param task 待填充的运行时任务（直接修改其 assignee 字段）
   * @param node 当前流程节点（含 permissionFlag 配置）
   * @param variables 流程变量上下文
   * @param explicit 显式指定的办理人，为 null 时走节点配置解析
   * @param instance 流程实例（用于获取发起人 ID 兜底）
   */
  public void resolveAssignee(
      FlowRunTaskDO task,
      FlowNodeDO node,
      Map<String, Object> variables,
      FlowAssigneeDTO explicit,
      FlowInstanceDO instance) {
    String perm = node.getPermissionFlag();
    if (explicit != null) {
      task.setAssigneeType(explicit.getUserType());
      task.setAssigneeId(explicit.getUserId());
      task.setAssigneeName(explicit.getUserName());
      return;
    }
    if (!StringUtils.hasText(perm)) {
      task.setAssigneeType(FlowAssigneeType.INITIATOR.name());
      task.setAssigneeId(
          instance != null && instance.getInitiatorId() != null
              ? String.valueOf(instance.getInitiatorId())
              : String.valueOf(task.getId()));
      task.setAssigneeName("INITIATOR");
      return;
    }
    String resolved = variableStrategy.resolveAssignee(perm, variables);
    if (resolved == null) {
      task.setAssigneeType(FlowAssigneeType.USER.name());
      task.setAssigneeId(perm);
      return;
    }
    // 多人取首段
    String firstResolved = resolved.split(",")[0].trim();
    task.setAssigneeType(FlowAssigneeType.USER.name());
    task.setAssigneeId(firstResolved);
    task.setAssigneeName("USER:" + firstResolved);
  }

  /**
   * 从流程变量中解析发起人 ID
   *
   * <p>依次尝试 {@code initiatorId} 和 {@code _initiatorId} 两个变量名。
   *
   * @param variables 流程变量上下文
   * @return 发起人 ID，未找到时返回 null
   */
  public String resolveInitiatorId(Map<String, Object> variables) {
    if (variables == null || variables.isEmpty()) {
      return null;
    }
    Object val = variables.get("initiatorId");
    if (val == null) {
      val = variables.get("_initiatorId");
    }
    if (val == null) {
      return null;
    }
    return String.valueOf(val);
  }
}
