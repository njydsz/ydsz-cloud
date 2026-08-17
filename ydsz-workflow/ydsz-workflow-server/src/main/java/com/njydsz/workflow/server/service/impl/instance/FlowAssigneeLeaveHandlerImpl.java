package com.njydsz.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.infra.entity.FlowDelegateAuthDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowDelegateAuthMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.FlowAssigneeLeaveHandler;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * 审批人离职/调岗自动处理服务实现（P1-1）
 *
 * <p>对 {@link FlowAssigneeLeaveHandler} 接口的完整实现，是工作流引擎的<b>人事变更联动</b>能力。 当审批人发生<b>离职 /
 * 调岗</b>等人事变更加，自动将其名下的待办任务转交给替代人， 避免「待办无人处理」导致的流程卡死。 是大厂 B 端工作流「业务连续性」的关键保障。
 *
 * <p><b>替代人解析优先级：</b>
 *
 * <ol>
 *   <li><b>显式指定</b>：调用方传入的 {@code replacementUserId} 参数（最高优先级）
 *   <li><b>长期授权委派</b>：从 {@code ydsz_flow_delegate_auth} 表查询用户当前生效的委派
 *   <li><b>直属上级</b>：通过 {@code FlowAssigneeResolver} 查询（避免循环依赖，当前<b>未启用</b>）
 *   <li><b>管理员兜底</b>：{@code ADMIN_FALLBACK_USER_ID}（默认 {@code 1}，可通过配置覆盖）
 * </ol>
 *
 * <p><b>支持的离职类型：</b>
 *
 * <ul>
 *   <li>{@code RESIGN} — <b>离职</b>：所有待办（PENDING / CLAIMED）转交给替代人
 *   <li>{@code TRANSFER} — <b>调岗</b>：当前实现与 RESIGN 一致（仅转交当前部门相关流程）
 * </ul>
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>替代人解析</b>：{@link #resolveReplacement} — 按优先级解析替代人
 *   <li><b>授权委派查询</b>：{@link #findActiveDelegate} — 查询用户当前有效的委派记录
 *   <li><b>批量转交</b>：{@link #handleLeave} — 遍历用户名下所有待办任务，调用 {@link FlowTaskService#transfer} 执行转交
 * </ul>
 *
 * <p><b>事务边界：</b>{@code handleLeave} 开启 {@code @Transactional(rollbackFor = Exception.class)}，
 * 「替代人解析 + 任务查询 + 批量转交」原子性。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>去重防护</b>：替代人与原审批人相同时直接跳过（避免自转交）
 *   <li><b>单条容错</b>：单条任务转交失败仅记录 warn 日志，<b>不影响其他任务</b>
 *   <li><b>审计完整</b>：转交通知中带「[RESIGN] / [TRANSFER]」前缀，便于审计追溯
 *   <li><b>去重周期</b>：仅处理 {@code PENDING} / {@code CLAIMED} 状态任务，已完成任务不再处理
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 场景：张三离职，李四接管其所有审批待办
 * int count = leaveHandler.handleLeave(
 *     "1001",  // 张三
 *     "RESIGN", // 离职类型
 *     "1002",   // 李四（显式指定替代人）
 *     "admin"   // 操作人（系统）
 * );
 * }</pre>
 *
 * <p><b>调用入口：</b>
 *
 * <ul>
 *   <li>{@code ydsz-system} 用户管理模块在「用户离职 / 调岗」时通过 Feign / Event 触发本服务
 *   <li>支持<b>手动触发</b>（管理员主动调整）和<b>系统触发</b>（HR 系统推送）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowAssigneeLeaveHandler 接口定义
 * @see FlowRunTaskDO 运行时任务实体
 * @see FlowDelegateAuthDO 长期授权委派实体
 * @see FlowTaskService 流程任务服务（转交通道）
 * @see FlowDelegateAuthService 委派代理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAssigneeLeaveHandlerImpl implements FlowAssigneeLeaveHandler {

  // ============================== 依赖注入 ==============================

  /** 运行时任务 Mapper，负责 {@code ydsz_flow_run_task} 表的查询（待办任务来源） */
  private final FlowRunTaskMapper taskMapper;

  /** 委派授权 Mapper，负责 {@code ydsz_flow_delegate_auth} 表的查询（长期授权委派来源） */
  private final FlowDelegateAuthMapper delegateAuthMapper;

  /** 流程任务服务，调用 {@code transfer} 接口执行任务转交（注入 {@code FlowTaskService} 门面） */
  private final FlowTaskService taskService;

  // ============================== 常量 ==============================

  /**
   * 管理员兜底用户 ID
   *
   * <p>当所有替代人解析均失败时使用。可通过外部配置覆盖（{@code workflow.leave.admin-fallback-user-id}）， 当前实现为静态常量，默认 {@code
   * "1"}（系统超级管理员）。
   */
  private static final String ADMIN_FALLBACK_USER_ID = "1";

  // ============================== 公共方法 ==============================

  /**
   * 处理审批人离职 / 调岗 — 批量转交名下待办
   *
   * <p><b>处理流程：</b>
   *
   * <ol>
   *   <li>解析替代人（按优先级）
   *   <li>查询用户名下所有 {@code PENDING} / {@code CLAIMED} 状态任务
   *   <li>遍历调用 {@link FlowTaskService#transfer} 逐个转交
   *   <li>统计成功数并返回
   * </ol>
   *
   * <p><b>事务边界：</b>开启 {@code @Transactional(rollbackFor = Exception.class)}， 整个方法作为一个事务。单条转交失败仅记录
   * warn 日志<b>不回滚整体事务</b>。
   *
   * @param userId 离职 / 调岗人 ID
   * @param leaveType 离职类型（{@code RESIGN} / {@code TRANSFER}，可空默认 {@code RESIGN}）
   * @param replacementUserId 显式指定的替代人 ID（可空，将走委派 / 兜底逻辑）
   * @param operatorId 操作人 ID（可空；为空时使用 {@code userId} 作为操作人）
   * @return 成功转交的任务数（失败的任务不计入）
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public int handleLeave(
      String userId, String leaveType, String replacementUserId, String operatorId) {
    if (!StringUtils.hasText(userId)) {
      log.warn("[LeaveHandler] userId 为空，跳过");
      return 0;
    }
    if (!StringUtils.hasText(leaveType)) {
      leaveType = "RESIGN";
    }
    log.info(
        "[LeaveHandler] 开始处理审批人离岗: userId={} type={} replacement={} operator={}",
        userId,
        leaveType,
        replacementUserId,
        operatorId);

    // 1. 解析替代人
    String resolvedReplacement = resolveReplacement(userId, replacementUserId);
    if (!StringUtils.hasText(resolvedReplacement)) {
      log.error("[LeaveHandler] 无法解析替代人: userId={} 使用管理员兜底", userId);
      resolvedReplacement = ADMIN_FALLBACK_USER_ID;
    }
    if (userId.equals(resolvedReplacement)) {
      log.warn("[LeaveHandler] 替代人与原审批人相同，跳过: userId={}", userId);
      return 0;
    }

    // 2. 查询待办任务
    LambdaQueryWrapper<FlowRunTaskDO> wrapper =
        new LambdaQueryWrapper<FlowRunTaskDO>()
            .eq(FlowRunTaskDO::getAssigneeId, userId)
            .eq(FlowRunTaskDO::getDeleted, 0)
            .in(
                FlowRunTaskDO::getTaskStatus,
                FlowTaskStatus.PENDING.name(),
                FlowTaskStatus.CLAIMED.name());
    List<FlowRunTaskDO> tasks = taskMapper.selectList(wrapper);

    if (tasks.isEmpty()) {
      log.info("[LeaveHandler] 无待办需要转交: userId={}", userId);
      return 0;
    }

    // 3. 逐个转交
    int successCount = 0;
    String reason = "RESIGN".equals(leaveType) ? "审批人离职自动转交" : "审批人调岗自动转交";
    for (FlowRunTaskDO task : tasks) {
      try {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(task.getId());
        dto.setUserId(operatorId != null ? operatorId : userId);
        dto.setComment("[" + leaveType + "] " + reason);
        dto.setTargetUserId(resolvedReplacement);
        taskService.transfer(dto);
        successCount++;
        log.info(
            "[LeaveHandler] 转交成功: taskId={} from={} to={} type={}",
            task.getId(),
            userId,
            resolvedReplacement,
            leaveType);
      } catch (Exception e) {
        log.warn("[LeaveHandler] 转交失败: taskId={} err={}", task.getId(), e.getMessage());
      }
    }

    log.info(
        "[LeaveHandler] 离岗处理完成: userId={} type={} total={} success={} replacement={}",
        userId,
        leaveType,
        tasks.size(),
        successCount,
        resolvedReplacement);
    return successCount;
  }

  // ============================== 私有方法 ==============================

  /**
   * 解析替代审批人（按优先级）
   *
   * <p><b>解析优先级：</b>显式指定 &gt; 长期授权委派 &gt; 管理员兜底。
   *
   * <p><b>设计说明：</b>「直属上级」理论上应通过 {@code FlowAssigneeResolver} 查询， 但 {@code FlowAssigneeLeaveHandler}
   * 已被 {@code FlowTaskService} 依赖， 若再依赖 {@code FlowAssigneeResolver}（{@code FlowTaskService}
   * 也依赖）会形成循环。 实际使用中可通过 Spring Event 异步查询 leader 后补充处理。
   *
   * @param userId 原审批人 ID
   * @param explicitReplacement 显式指定的替代人 ID（可空）
   * @return 解析后的替代人 ID（非空）
   */
  private String resolveReplacement(String userId, String explicitReplacement) {
    // 1. 显式指定
    if (StringUtils.hasText(explicitReplacement)) {
      log.info("[LeaveHandler] 使用显式替代人: userId={} replacement={}", userId, explicitReplacement);
      return explicitReplacement;
    }

    // 2. 长期授权委派
    String delegateUser = findActiveDelegate(userId);
    if (StringUtils.hasText(delegateUser)) {
      log.info("[LeaveHandler] 使用授权委派替代人: userId={} delegate={}", userId, delegateUser);
      return delegateUser;
    }

    // 3. 管理员兜底
    log.info("[LeaveHandler] 无替代人，使用管理员兜底: userId={} admin={}", userId, ADMIN_FALLBACK_USER_ID);
    return ADMIN_FALLBACK_USER_ID;
  }

  /**
   * 查询用户当前有效的授权委派
   *
   * <p>查询条件：
   *
   * <ul>
   *   <li>{@code ownerUserId = userId} — 用户是授权人
   *   <li>{@code authStatus = 'ACTIVE'} — 授权状态为生效中
   *   <li>{@code startTime <= now} — 授权已生效
   *   <li>{@code endTime IS NULL OR endTime >= now} — 授权未过期
   * </ul>
   *
   * @param userId 授权人 ID
   * @return 代理人 ID（{@link FlowDelegateAuthDO#getDelegateUserId()}）；无有效授权时返回 {@code null}
   */
  private String findActiveDelegate(String userId) {
    LocalDateTime now = LocalDateTime.now();
    LambdaQueryWrapper<FlowDelegateAuthDO> wrapper =
        new LambdaQueryWrapper<FlowDelegateAuthDO>()
            .eq(FlowDelegateAuthDO::getOwnerUserId, userId)
            .eq(FlowDelegateAuthDO::getAuthStatus, "ACTIVE")
            .le(FlowDelegateAuthDO::getStartTime, now)
            .and(
                w ->
                    w.isNull(FlowDelegateAuthDO::getEndTime)
                        .or()
                        .ge(FlowDelegateAuthDO::getEndTime, now))
            .last("LIMIT 1");
    FlowDelegateAuthDO auth = delegateAuthMapper.selectOne(wrapper);
    return auth != null ? auth.getDelegateUserId() : null;
  }
}
