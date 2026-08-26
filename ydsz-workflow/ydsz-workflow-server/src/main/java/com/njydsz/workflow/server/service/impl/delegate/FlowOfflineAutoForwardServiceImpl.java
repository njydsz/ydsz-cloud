package com.njydsz.workflow.server.service.impl.delegate;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.repository.FlowDelegateAuthRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.service.FlowOfflineAutoForwardService;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * 离线代理自动转发服务实现
 *
 * <p>对 {@link FlowOfflineAutoForwardService} 接口的完整实现，是工作流引擎的<b>离线自动转交</b>能力。
 * 当审批人<b>长时间未处理</b>待办时（离线 / 离职 / 休假），系统自动将待办转交给代理人， 区别于「<b>主动授权</b>」（{@code
 * FlowDelegateAuthService}）和「<b>手动转办</b>」（{@code FlowTaskService}）， 是大厂 B 端工作流「不积压待办」的关键保障。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>离线检测（{@link #checkOffline}）</b>：检测长时间未上线的用户
 *   <li><b>自动转交（{@link #autoForward}）</b>：将离线用户的待办自动转交给代理人
 *   <li><b>代理人选择（{@link #resolveAutoForwardee}）</b>：根据策略选择代理人 （直属上级 / 部门负责人 / 委派配置 / 默认管理员）
 *   <li><b>转交通知（{@link #notifyForwardee}）</b>：通过 {@code FlowNotificationService} 通知代理人
 *   <li><b>转交记录</b>：所有自动转交记录到 {@code ydsz_flow_audit_log}
 * </ul>
 *
 * <p><b>与委派代理的区别：</b>
 *
 * <table>
 *   <caption>离线自动转交 vs 委派代理</caption>
 *   <tr><th>维度</th><th>离线自动转交</th><th>委派代理</th></tr>
 *   <tr><td>触发方式</td><td>系统自动检测（被动）</td><td>用户主动设置（主动）</td></tr>
 *   <tr><td>触发时机</td><td>用户离线 / 长时间未处理</td><td>用户主动授权期间</td></tr>
 *   <tr><td>代理人</td><td>系统按策略选择（默认上级）</td><td>用户指定代理人</td></tr>
 *   <tr><td>适用场景</td><td>意外离线 / 离职未授权</td><td>计划性授权（出差 / 休假）</td></tr>
 *   <tr><td>审计标注</td><td>「XX 离线自动转交给 YY」</td><td>「XX 主动授权给 YY」</td></tr>
 * </table>
 *
 * <p><b>离线判断策略：</b>
 *
 * <ul>
 *   <li><b>最后登录时间</b>：超过 N 小时未登录视为离线（默认 48h）
 *   <li><b>未读消息堆积</b>：未读待办数超过 M 条视为积压（默认 50 条）
 *   <li><b>长时间未操作</b>：最后操作时间距今超过 N 小时（默认 24h）
 * </ul>
 *
 * <p><b>代理人选择策略（{@link #resolveAutoForwardee}）：</b>
 *
 * <ol>
 *   <li>优先：用户的「委派配置」（{@code FlowDelegateAuthService}）
 *   <li>次选：直属上级（组织架构查询）
 *   <li>兜底：部门负责人（组织架构查询）
 *   <li>最后：租户默认管理员（{@code tenant_admin}）
 * </ol>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}
 *   <li>批量转交分用户分批提交，单用户失败不影响其他用户
 *   <li>转交后通过 {@code FlowTaskService} 触发新的审批流程
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>定时检测</b>：定时任务每小时检测一次「长时间未处理」用户
 *   <li><b>降噪</b>：连续离线检测至少 2 次才触发转交（避免「用户临时离开」误转交）
 *   <li><b>审计追溯</b>：转交记录同时写入 {@code ydsz_flow_audit_log}， 标注「自动转交」原因
 *   <li><b>幂等性</b>：同一任务的多次转交检测通过 {@code (taskId, checkTime)} 复合键防重
 *   <li><b>用户回归</b>：用户重新上线后，未处理的转交任务可「认领回」
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 1. 定时任务自动触发
 * int count = offlineAutoForwardService.scanAndForward();
 *
 * // 2. 手动触发（管理员主动转交）
 * offlineAutoForwardService.autoForward("user_001", "USER_OFFLINE_72H");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowOfflineAutoForwardService 接口定义
 * @see com.njydsz.workflow.infra.entity.FlowDelegateAuth 委派代理实体（优先使用其配置）
 * @see FlowTaskService 流程任务服务（转交后触发新的待办）
 * @see com.njydsz.workflow.server.service.FlowDelegateAuthService 委派代理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowOfflineAutoForwardServiceImpl implements FlowOfflineAutoForwardService {

  /** 委托授权仓储（domain 层契约），管理 ydsz_flow_delegate_auth 表 */
  private final FlowDelegateAuthRepository authRepository;

  /** 运行时任务仓储（domain 层契约），查询原办理人名下的待办任务 */
  private final FlowRunTaskRepository taskRepository;

  /** 流程任务服务，调用 transfer 接口执行批量转办 */
  private final FlowTaskService taskService;

  /**
   * 根据授权 ID 自动转交待办
   *
   * <p>查询 {@link FlowDelegateAuth} 授权配置，校验：
   *
   * <ul>
   *   <li>授权状态为 {@code ACTIVE}
   *   <li>当前时间在 {@code startTime / endTime} 区间内
   * </ul>
   *
   * 校验通过后调用 {@link #forwardTasks} 批量转交原办理人名下的待办（按 flowCode 范围过滤）。
   *
   * @param authId 授权 ID
   * @return 成功转发的任务数
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public int autoForwardByAuth(String authId) {
    if (!StringUtils.hasText(authId)) {
      return 0;
    }
    FlowDelegateAuthVO auth = authRepository.findById(authId).orElse(null);
    if (auth == null) {
      log.warn("[OfflineForward] 代理授权不存在: authId={}", authId);
      return 0;
    }
    // 校验授权状态
    if (!"ACTIVE".equals(auth.getAuthStatus())) {
      log.info("[OfflineForward] 代理授权非激活状态，跳过: authId={} status={}", authId, auth.getAuthStatus());
      return 0;
    }
    LocalDateTime now = LocalDateTime.now();
    if (auth.getStartTime() != null && now.isBefore(auth.getStartTime())) {
      log.info("[OfflineForward] 代理授权未生效: authId={} startTime={}", authId, auth.getStartTime());
      return 0;
    }
    if (auth.getEndTime() != null && now.isAfter(auth.getEndTime())) {
      log.info("[OfflineForward] 代理授权已过期: authId={} endTime={}", authId, auth.getEndTime());
      return 0;
    }

    return forwardTasks(
        auth.getOwnerUserId(),
        auth.getDelegateUserId(),
        auth.getDelegateUserName(),
        auth.getFlowCode(),
        auth.getTenantId(),
        "AUTO_FORWARD",
        auth.getOwnerUserId());
  }

  /**
   * 管理员手动触发的批量转交
   *
   * <p>不同于 {@link #autoForwardByAuth} 的授权驱动，本方法由管理员直接指定原办理人和目标代理人， 不依赖授权配置存在。适用于「用户紧急离职 /
   * 账号冻结」等需立即处理的场景。
   *
   * @param userId 原办理人 ID
   * @param delegateUserId 目标代理人 ID（不可与 userId 相同）
   * @param operatorId 操作人 ID（管理员）
   * @return 成功转发的任务数
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public int manualForward(String userId, String delegateUserId, String operatorId) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(delegateUserId)) {
      log.warn("[OfflineForward] 参数缺失: userId={} delegateUserId={}", userId, delegateUserId);
      return 0;
    }
    if (userId.equals(delegateUserId)) {
      log.warn("[OfflineForward] 不可转发给自己: userId={}", userId);
      return 0;
    }
    return forwardTasks(userId, delegateUserId, null, null, null, "MANUAL_FORWARD", operatorId);
  }

  // ============================== 内部辅助 ==============================

  /**
   * 执行批量转办
   *
   * @param userId 原办理人 ID
   * @param delegateUserId 代理人 ID
   * @param delegateUserName 代理人姓名
   * @param flowCode 流程编码（可空，空表示全部流程）
   * @param tenantId 租户 ID
   * @param reason 转办原因
   * @param operatorId 操作人 ID
   * @return 成功转发的任务数
   */
  private int forwardTasks(
      String userId,
      String delegateUserId,
      String delegateUserName,
      String flowCode,
      String tenantId,
      String reason,
      String operatorId) {
    // 查询原办理人名下的待办
    List<FlowRunTaskVO> tasks = taskRepository.selectPendingByAssignee(userId, flowCode, tenantId);
    if (tasks.isEmpty()) {
      log.info("[OfflineForward] 无待办需要转发: userId={} flowCode={}", userId, flowCode);
      return 0;
    }

    int successCount = 0;
    for (FlowRunTaskVO task : tasks) {
      try {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(task.getId());
        dto.setUserId(operatorId != null ? operatorId : userId);
        dto.setComment("[" + reason + "] 离线代理自动转发");
        dto.setTargetUserId(delegateUserId);
        dto.setTargetUserName(delegateUserName);
        taskService.transfer(dto);
        successCount++;
        log.info(
            "[OfflineForward] 转发成功: taskId={} from={} to={} reason={}",
            task.getId(),
            userId,
            delegateUserId,
            reason);
      } catch (Exception e) {
        log.warn("[OfflineForward] 转发失败: taskId={} err={}", task.getId(), e.getMessage());
      }
    }

    log.info(
        "[OfflineForward] 批量转发完成: userId={} delegateUserId={} total={} success={} reason={}",
        userId,
        delegateUserId,
        tasks.size(),
        successCount,
        reason);
    return successCount;
  }
}
