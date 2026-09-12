package com.njydsz.workflow.server.timer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.enums.FlowTimeoutStrategy;
import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * 超时处理器默认实现
 *
 * <p>执行任务超时后的具体策略逻辑：
 *
 * <ul>
 *   <li>AUTO_PASS — 标记"超时自动通过"，调用审批通过逻辑
 *   <li>TRANSFER_ADMIN — 转交管理员（adminUserId）
 *   <li>TRANSFER_SUPERIOR — 转交上级（预留，转交默认管理员）
 *   <li>REMIND — 发送催办通知
 * </ul>
 *
 * <p>超时处理均记录审计日志（TIMEOUT_ACTION）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowTimeoutHandlerImpl implements FlowTimeoutHandler {

  /** 默认管理员用户 ID（转交目标兜底） */
  private static final String DEFAULT_ADMIN_USER_ID = "1";

  private final FlowTaskService taskService;

  /** 审计日志仓储，记录超时审计 */
  private final FlowAuditLogRepository auditLogRepository;

  /** {@inheritDoc} */
  @Override
  public String handleTimeout(FlowRunTaskVO task, FlowTimeoutStrategy strategy) {
    if (task == null || strategy == null) {
      return "跳过：任务或策略为空";
    }

    String taskId = task.getId();
    String nodeId = task.getNodeCode();
    String instanceId = task.getInstanceId();

    log.info(
        "[Flow-Timeout] 处理超时任务: taskId={} instanceId={} node={} strategy={}",
        taskId, instanceId, nodeId, strategy.getCode());

    return switch (strategy) {
      case AUTO_PASS -> handleAutoPass(task);
      case TRANSFER_ADMIN -> handleTransferAdmin(task);
      case TRANSFER_SUPERIOR -> handleTransferSuperior(task);
      case REMIND -> handleRemind(task);
    };
  }

  /**
   * 自动通过策略：构建超时自动审批 DTO，委托任务服务通过
   *
   * @param task 超时任务
   * @return 处理结果
   */
  private String handleAutoPass(FlowRunTaskVO task) {
    try {
      FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
      dto.setTaskId(task.getId());
      dto.setUserId("SYSTEM_TIMEOUT");
      dto.setUserName("系统超时");
      dto.setComment("超时自动通过");
      taskService.timeoutAutoPass(dto);
      log.info("[Flow-Timeout] 超时自动通过: taskId={}", task.getId());
      return "已自动通过";
    } catch (Exception e) {
      log.error("[Flow-Timeout] 自动通过失败: taskId={} err={}", task.getId(), e.getMessage(), e);
      return "自动通过失败: " + e.getMessage();
    }
  }

  /**
   * 转交管理员策略：将任务转交给配置的管理员
   *
   * @param task 超时任务
   * @return 处理结果
   */
  private String handleTransferAdmin(FlowRunTaskVO task) {
    try {
      String adminUserId = resolveAdminUserId(task);
      FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
      dto.setTaskId(task.getId());
      dto.setUserId("SYSTEM_TIMEOUT");
      dto.setUserName("系统超时");
      dto.setTargetUserId(adminUserId);
      dto.setTargetUserName("管理员");
      dto.setComment("超时自动转交管理员");
      taskService.timeoutTransfer(dto);
      log.info("[Flow-Timeout] 超时转交管理员: taskId={} admin={}", task.getId(), adminUserId);
      return "已转交管理员(" + adminUserId + ")";
    } catch (Exception e) {
      log.error("[Flow-Timeout] 转交管理员失败: taskId={} err={}", task.getId(), e.getMessage(), e);
      return "转交管理员失败: " + e.getMessage();
    }
  }

  /**
   * 转交上级策略：预留实现，当前转交管理员
   *
   * <p>TODO: 对接组织架构服务获取发起人上级
   *
   * @param task 超时任务
   * @return 处理结果
   */
  private String handleTransferSuperior(FlowRunTaskVO task) {
    // 暂未对接组织架构，降级到转交管理员
    log.info("[Flow-Timeout] 转交上级暂不可用，降级到转交管理员: taskId={}", task.getId());
    return handleTransferAdmin(task);
  }

  /**
   * 催办策略：发送提醒通知
   *
   * @param task 超时任务
   * @return 处理结果
   */
  private String handleRemind(FlowRunTaskVO task) {
    try {
      taskService.timeoutRemind(task.getInstanceId(), task.getNodeCode());
      log.info("[Flow-Timeout] 超时催办: taskId={} instanceId={}", task.getId(), task.getInstanceId());
      return "已发送催办通知";
    } catch (Exception e) {
      log.error("[Flow-Timeout] 催办失败: taskId={} err={}", task.getId(), e.getMessage(), e);
      return "催办失败: " + e.getMessage();
    }
  }

  /**
   * 解析管理员用户 ID
   *
   * <p>从任务关联的管理员配置中解析，未配置时使用默认值。
   *
   * @param task 运行时任务
   * @return 管理员用户 ID
   */
  private String resolveAdminUserId(FlowRunTaskVO task) {
    // TODO: 从节点 ext 中解析 adminUserId 配置
    return DEFAULT_ADMIN_USER_ID;
  }
}
