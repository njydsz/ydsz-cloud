package com.njydsz.workflow.server.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowTaskTransferService;
import com.njydsz.workflow.server.service.FlowTemplateService;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskOperateService;

/**
 * 流程任务转交服务实现。
 *
 * <p>当用户禁用、组织架构变更等事件发生时，由跨模块事件监听器调用本服务，
 * 将该用户名下的待办任务转交给代理人或上级，确保审批流程不中断。
 *
 * <p>核心场景：
 *
 * <ul>
 *   <li>用户禁用 → 转交该用户所有待办任务
 *   <li>组织架构变更 → 批量调整涉及部门下的审批人
 *   <li>项目立项创建 → 自动创建审批流程实例
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskTransferServiceImpl implements FlowTaskTransferService {

  /** 运行时任务仓储，查询待办任务 */
  private final FlowRunTaskRepository taskRepository;

  /** 任务操作服务，执行转办操作 */
  private final FlowTaskOperateService taskOperateService;

  /** 流程实例服务，启动审批流程 */
  private final FlowInstanceService instanceService;

  /** 流程模板服务，按类型匹配模板 */
  private final FlowTemplateService templateService;

  /** 单次转交最大任务数（防止批量操作过载） */
  private static final int MAX_TRANSFER_BATCH = 200;

  /**
   * 用户禁用时转交待办任务。
   *
   * <p>将该用户名下所有 PENDING/CLAIMED 状态的待办任务转交给指定代理人。
   * 如果代理人为空，则记录警告并跳过（不自动查找上级，避免误转）。
   *
   * @param disabledUserId 被禁用的用户 ID
   * @param transferToUserId 转交目标用户 ID（可为空，空时跳过并记录警告）
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void transferTasksByUserDisable(String disabledUserId, String transferToUserId) {
    log.info(
        "[FlowTaskTransfer] 用户禁用任务转交: disabledUserId={}, transferToUserId={}",
        disabledUserId,
        transferToUserId);

    if (!StringUtils.hasText(disabledUserId)) {
      log.warn("[FlowTaskTransfer] disabledUserId 为空，跳过转交");
      return;
    }

    if (!StringUtils.hasText(transferToUserId)) {
        log.warn("[FlowTaskTransfer] transferToUserId 为空，跳过转交（需显式指定代理人）");
        return;
    }

    // 查询该用户的所有待办任务
    List<FlowRunTaskVO> pendingTasks = taskRepository.findPendingTasksByAssignee(disabledUserId);
    if (pendingTasks == null || pendingTasks.isEmpty()) {
      log.info("[FlowTaskTransfer] 用户 {} 无待办任务，无需转交", disabledUserId);
      return;
    }

    // 限制批量大小
    List<FlowRunTaskVO> tasksToTransfer = pendingTasks.size() > MAX_TRANSFER_BATCH
        ? pendingTasks.subList(0, MAX_TRANSFER_BATCH)
        : pendingTasks;

    if (pendingTasks.size() > MAX_TRANSFER_BATCH) {
      log.warn(
          "[FlowTaskTransfer] 用户 {} 待办任务数 {} 超过上限 {}，仅转交前 {} 条",
          disabledUserId, pendingTasks.size(), MAX_TRANSFER_BATCH, MAX_TRANSFER_BATCH);
    }

    int successCount = 0;
    int failCount = 0;

    for (FlowRunTaskVO task : tasksToTransfer) {
      try {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(task.getId());
        dto.setUserId(disabledUserId);
        dto.setUserName(task.getAssigneeName());
        dto.setAction("TRANSFER");
        dto.setTargetUserId(transferToUserId);
        dto.setComment("用户禁用自动转交");
        dto.setTenantId(task.getTenantId());

        taskOperateService.transfer(dto);
        successCount++;
      } catch (Exception e) {
        failCount++;
        log.warn(
            "[FlowTaskTransfer] 转交任务失败: taskId={}, instanceId={}, err={}",
            task.getId(),
            task.getInstanceId(),
            e.getMessage());
      }
    }

    log.info(
        "[FlowTaskTransfer] 用户禁用任务转交完成: disabledUserId={}, total={}, success={}, fail={}",
        disabledUserId, tasksToTransfer.size(), successCount, failCount);
  }

  /**
   * 组织架构变更时批量调整审批人。
   *
   * <p>当部门合并/拆分/撤销时，批量调整该部门下所有在途流程的审批人配置。
   * 当前实现为骨架版本：记录变更事件并输出待处理的任务列表，
   * 后续根据业务需求扩展为自动更新审批人配置。
   *
   * @param deptId 发生变更的部门 ID
   * @param changeType 变更类型（MERGE/SPLIT/DISBAND/RENAME）
   */
  @Override
  public void adjustApproversByOrgChange(String deptId, String changeType) {
    log.info("[FlowTaskTransfer] 组织架构变更审批人调整: deptId={}, changeType={}", deptId, changeType);

    if (!StringUtils.hasText(deptId)) {
      log.warn("[FlowTaskTransfer] deptId 为空，跳过调整");
      return;
    }

    // TODO P2: 查询涉及 deptId 的所有在途流程，批量更新审批人配置
    // 当前实现：记录变更事件，后续根据组织架构服务获取部门关联用户后批量调整
    log.info(
        "[FlowTaskTransfer] 组织架构变更事件已记录，待后续实现自动调整: deptId={}, changeType={}",
        deptId,
        changeType);
  }

  /**
   * 项目立项创建时自动创建审批流程实例。
   *
   * <p>根据项目类型匹配对应的流程模板，自动发起审批流程。
   * 使用固定的业务类型 "project_initiation" 匹配模板。
   *
   * @param projectId 项目编号
   * @param projectName 项目名称
   * @param managerId 项目经理 ID
   */
  @Override
  public void createInitiationApprovalFlow(String projectId, String projectName, String managerId) {
    log.info(
        "[FlowTaskTransfer] 项目立项自动创建审批流程: projectId={}, projectName={}, managerId={}",
        projectId,
        projectName,
        managerId);

    if (!StringUtils.hasText(projectId)) {
      log.warn("[FlowTaskTransfer] projectId 为空，跳过创建审批流程");
      return;
    }

    // 根据项目类型匹配流程模板
    // 使用 PROJECT 分类查找第一个可用的流程模板
    List<Map<String, Object>> templates = templateService.listTemplates("PROJECT");
    if (templates == null || templates.isEmpty()) {
      log.warn(
          "[FlowTaskTransfer] 未找到项目立项流程模板: projectId={}, category=PROJECT",
          projectId);
      return;
    }

    // 取第一个模板的 templateCode
    String flowCode = (String) templates.get(0).get("templateCode");
    if (!StringUtils.hasText(flowCode)) {
      log.warn("[FlowTaskTransfer] 流程模板编码为空: projectId={}", projectId);
      return;
    }

    try {
      FlowStartProcessDTO dto = new FlowStartProcessDTO();
      dto.setFlowCode(flowCode);
      dto.setBusinessType("project_initiation");
      dto.setBusinessId(projectId);
      dto.setBusinessNo(projectId);
      dto.setTitle("项目立项审批: " + (StringUtils.hasText(projectName) ? projectName : projectId));
      dto.setInitiatorId(managerId);

      // 设置流程变量
      Map<String, Object> variables = new HashMap<>(16);
      variables.put("projectId", projectId);
      variables.put("projectName", projectName);
      variables.put("managerId", managerId);
      dto.setVariables(variables);

      String instanceId = instanceService.start(dto);
      log.info(
          "[FlowTaskTransfer] 项目立项审批流程创建成功: projectId={}, instanceId={}, flowCode={}",
          projectId,
          instanceId,
          flowCode);
    } catch (Exception e) {
      log.error(
          "[FlowTaskTransfer] 项目立项审批流程创建失败: projectId={}, err={}",
          projectId,
          e.getMessage(),
          e);
    }
  }
}
