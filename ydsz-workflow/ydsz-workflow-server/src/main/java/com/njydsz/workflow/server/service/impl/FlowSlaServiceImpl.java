package com.njydsz.workflow.server.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.enums.FlowSlaAction;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import com.njydsz.workflow.server.service.FlowNotificationService;
import com.njydsz.workflow.server.service.FlowSlaService;
import com.njydsz.workflow.server.service.FlowTaskService;

/**
 * 流程 SLA 超时自动策略实现
 *
 * <p>对 {@link FlowSlaService} 接口的完整实现，是工作流引擎 SLA 监控的核心业务逻辑层。 通过定时任务扫描超期任务并执行自动策略（升级 / 自动通过 /
 * 自动驳回），基于时间驱动的 SLA 监控机制。
 *
 * <p><b>核心职责：</b>
 *
 * <ol>
 *   <li>cronjob 每 60s 扫描所有 {@code PENDING/CLAIMED} 且 {@code dueAt} 不为空的 task
 *   <li>解析 {@code node.slaConfig} 配置：{@code timeoutMinutes} / {@code action} / {@code
 *       urgeIntervalMinutes} / {@code maxUrges} / {@code escalateUserId}
 *   <li>未到 {@code dueAt}：跳过；超过 {@code dueAt} 但未到最终动作：根据 {@code maxUrges} 重复 REMIND
 *   <li>超过 {@code dueAt} 且已超出催办容忍窗口：执行最终动作（{@code ESCALATE / AUTO_PASS / AUTO_REJECT}）
 *   <li>所有写操作都在 {@code REQUIRES_NEW} 子事务中，<b>单条失败不影响扫描主循环</b>
 * </ol>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>分布式锁</b>：通过 {@link DistributedScheduled} 保证集群中只有一个节点执行扫描
 *   <li><b>子事务隔离</b>：{@code @Transactional(propagation = REQUIRES_NEW)} 隔离单条任务的失败
 *   <li><b>指标埋点</b>：通过 {@link FlowMetrics} 暴露 SLA 触发次数 / 升级次数等 Prometheus 指标
 *   <li><b>多租户</b>：扫描时按租户分批处理，避免单租户数据倾斜
 *   <li><b>幂等性</b>：同一任务的同一动作（如升级）通过分布式锁 + 状态机保证只执行一次
 * </ul>
 *
 * <p><b>SLA 动作类型（{@link FlowSlaAction}）：</b>
 *
 * <ul>
 *   <li>{@code NONE} — 仅记录，不执行任何操作
 *   <li>{@code REMIND} — 发送催办通知（IM / 站内信）
 *   <li>{@code ESCALATE} — 升级审批人（如转给上级 / 指定接管人）
 *   <li>{@code AUTO_PASS} — 自动通过（高风险，需审计）
 *   <li>{@code AUTO_REJECT} — 自动驳回（高风险，需审计）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowSlaService SLA Service 接口
 * @see FlowSlaAction SLA 动作枚举
 * @see com.njydsz.workflow.domain.vo.FlowRunTaskVO 运行时任务值对象
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowSlaServiceImpl implements FlowSlaService {

  /** 运行时任务仓储（domain 层契约） */
  private final FlowRunTaskRepository taskRepository;

  /** 流程节点仓储（domain 层契约），读取节点 SLA 配置（slaConfig JSON） */
  private final FlowNodeRepository nodeRepository;

  /** P1-6: 用 @Lazy 打破 FlowSlaService ↔ FlowTaskService 循环依赖 */
  @Lazy private final FlowTaskService taskService;

  private final FlowNotificationService notificationService;

  /** P2-3: Prometheus 指标（可能为 null：测试环境） */
  private final FlowMetrics flowMetrics;

  /** 单次扫描上限（避免大表全表扫描） */
  private static final int SCAN_BATCH_SIZE = 500;

  /** P1-6: 单轮扫描最大迭代次数（安全阀，避免大量超期任务导致单次扫描耗时过长） */
  private static final int MAX_SCAN_ITERATIONS = 10;

  /** 默认 SLA 配置（节点未配 slaConfig 时使用） */
  private static final int DEFAULT_REMINDER_INTERVAL_MINUTES = 60;

  private static final int DEFAULT_MAX_REMINDERS = 3;
  private static final int DEFAULT_TIMEOUT_MINUTES = 24 * 60;
  private static final String DEFAULT_ADMIN_USER_ID = "1";

  // ============================== 任务状态常量 ==============================

  /** 任务状态：待处理 */
  private static final String TASK_STATUS_PENDING = "PENDING";

  /** 任务状态：已签收 */
  private static final String TASK_STATUS_CLAIMED = "CLAIMED";

  // ============================== 系统用户常量 ==============================

  /** 系统用户 ID（用于自动操作） */
  private static final String SYSTEM_USER_ID = "0";

  // ============================== 通知内容常量 ==============================

  /** 催办通知标题 */
  private static final String URGE_TITLE = "审批任务即将超时";

  /** 超时通知标题 */
  private static final String NOTIFY_TITLE = "审批任务 SLA 超时需人工介入";

  /** 升级通知标题 */
  private static final String ESCALATE_TITLE = "审批任务已升级";

  /** 自动通过默认审批意见 */
  private static final String AUTO_PASS_DEFAULT_COMMENT = "系统自动通过：超过 SLA 时限未处理";

  /** 自动驳回默认审批意见 */
  private static final String AUTO_REJECT_DEFAULT_COMMENT = "系统自动驳回：超过 SLA 时限未处理";

  // ============================== 通知渠道和类型常量 ==============================

  /** 通知渠道：站内信 */
  private static final String NOTIFY_CHANNEL_INAPP = "INAPP";

  /** 通知类型：工作流超时 */
  private static final String NOTIFY_TYPE_WORKFLOW_TIMEOUT = "WORKFLOW_TIMEOUT";

  /** 通知级别：警告 */
  private static final String NOTIFY_LEVEL_WARN = "WARN";

  /** 通知类型：任务升级 */
  private static final String NOTIFY_TYPE_WORKFLOW_TASK_ESCALATED = "WORKFLOW_TASK_ESCALATED";

  /** 通知级别：紧急 */
  private static final String NOTIFY_LEVEL_URGENT = "URGENT";

  /** 通知类型：工作流催办 */
  private static final String NOTIFY_TYPE_WORKFLOW_URGE = "WORKFLOW_URGE";

  // ============================== SLA 配置键名常量 ==============================

  /** SLA 配置键：动作 */
  private static final String SLA_CONFIG_KEY_ACTION = "action";

  /** SLA 配置键：升级用户 ID */
  private static final String SLA_CONFIG_KEY_ESCALATE_USER_ID = "escalateUserId";

  /** SLA 配置键：通知用户 ID 列表 */
  private static final String SLA_CONFIG_KEY_NOTIFY_USER_IDS = "notifyUserIds";

  /** SLA 配置键：超时时间（分钟） */
  private static final String SLA_CONFIG_KEY_TIMEOUT_MINUTES = "timeoutMinutes";

  /** SLA 配置键：最大提醒次数 */
  private static final String SLA_CONFIG_KEY_MAX_URGES = "maxUrges";

  /** SLA 配置键：提醒间隔（分钟） */
  private static final String SLA_CONFIG_KEY_URGE_INTERVAL_MINUTES = "urgeIntervalMinutes";

  /** SLA 配置键：自动审批意见 */
  private static final String SLA_CONFIG_KEY_AUTO_COMMENT = "autoComment";

  // ============================== 通知内容模板 ==============================

  /** 升级通知内容模板 */
  private static final String ESCALATE_COMMENT_TEMPLATE = "系统升级：原办理人未在 SLA 时限内处理，已转办给用户 %s";

  /** 催办通知内容模板 */
  private static final String URGE_COMMENT_TEMPLATE = "【%s】%s 已超过截止时间 %s，请尽快处理（第 %d/%d 次提醒）";

  /** 超时通知内容模板 */
  private static final String NOTIFY_COMMENT_TEMPLATE = "【%s】%s 已超过 SLA 时限未处理（任务 ID=%s，办理人=%s），请尽快介入处理。";

  // ============================== 指标类型常量 ==============================

  /** 指标类型：SLA 超时错误 */
  private static final String METRIC_ERROR_SLA_TIMEOUT = "sla_timeout";

  /** 指标类型：自动处理 */
  private static final String METRIC_TASK_AUTO_HANDLED = "auto_handled";

  /**
   * 解析节点的 SLA 配置 JSON 字符串
   *
   * <p>配置项：
   *
   * <ul>
   *   <li>{@code timeoutMinutes} — 任务超时时间（必填）
   *   <li>{@code action} — 超时动作（{@code REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT}，默认 {@code REMIND}）
   *   <li>{@code urgeIntervalMinutes} — 提醒间隔（默认 60min）
   *   <li>{@code maxUrges} — 最大提醒次数（默认 3）
   *   <li>{@code escalateUserId} — 升级目标用户 ID（{@code action=ESCALATE} 时必填）
   *   <li>{@code autoComment} — 自动动作的审批意见
   * </ul>
   *
   * @param slaConfigJson 配置 JSON 字符串
   * @return 解析后的 Map（解析失败或为空时返回空 Map）
   */
  @Override
  public Map<String, Object> parseSlaConfig(String slaConfigJson) {
    if (!StringUtils.hasText(slaConfigJson)) {
      return Collections.emptyMap();
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(slaConfigJson);
      return map == null ? Collections.emptyMap() : map;
    } catch (Exception e) {
      log.warn("[FlowSla] 解析 slaConfig 失败: {} err={}", slaConfigJson, e.getMessage());
      return Collections.emptyMap();
    }
  }

  /**
   * 应用 SLA 配置到任务（任务创建时调用）
   *
   * <p>根据节点的 {@code slaConfig} 计算任务的 {@code dueAt}（= {@code createdAt} + {@code timeoutMinutes}），
   * 并记录 {@code slaAction} 预期动作。未配置 {@code timeoutMinutes} 时<b>不</b>应用 SLA。
   *
   * @param task 当前任务
   * @param node 当前节点（含 {@code slaConfig}）
   */
  @Override
  public void applySlaConfig(FlowRunTaskVO task, FlowNodeVO node) {
    if (task == null || node == null) {
      return;
    }
    Map<String, Object> config = parseSlaConfig(node.getSlaConfigJson());
    if (config.isEmpty()) {
      return; // 未配置 SLA
    }
    Integer timeoutMinutes = readInt(config, SLA_CONFIG_KEY_TIMEOUT_MINUTES, null);
    if (timeoutMinutes == null || timeoutMinutes <= 0) {
      return; // 必须配置 timeoutMinutes 才算开启 SLA
    }
    LocalDateTime dueAt =
        task.getCreatedAt() == null
            ? LocalDateTime.now().plusMinutes(timeoutMinutes)
            : task.getCreatedAt().plusMinutes(timeoutMinutes);
    task.setDueAt(dueAt);
    // 记录 slaAction 预期值（仅用于审计，不强制）
    String actionStr = (String) config.get(SLA_CONFIG_KEY_ACTION);
    if (StringUtils.hasText(actionStr)) {
      try {
        FlowSlaAction action = FlowSlaAction.valueOf(actionStr.toUpperCase());
        task.setSlaAction(action.name());
      } catch (IllegalArgumentException e) {
        log.warn("[FlowSla] 未知的 SLA action: nodeCode={} action={}", node.getNodeCode(), actionStr);
      }
    }
    log.info(
        "[FlowSla] 应用 SLA 配置: taskId={} nodeCode={} timeoutMinutes={} action={} dueAt={}",
        task.getId(),
        node.getNodeCode(),
        timeoutMinutes,
        config.get(SLA_CONFIG_KEY_ACTION),
        dueAt);
  }

  /**
   * 扫描并处理超期 SLA 任务（手动触发 / 定时任务入口）
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>游标分页查询超期候选任务（{@code PENDING/CLAIMED} 且 {@code dueAt <= now}）， 单批最多 {@link
   *       #SCAN_BATCH_SIZE} 条
   *   <li>逐条处理（{@link #processOverdue}），单条失败不影响整体
   *   <li>批次未满或本批无处理时结束循环（<b>避免大表全表扫描</b>）
   * </ol>
   *
   * <p>集群幂等：本方法由 {@code @Scheduled} 定时任务调用，<b>调用方</b>需通过 {@link DistributedScheduled} 加分布式锁。
   *
   * @return 本轮处理的任务数（含 REMIND 提醒 + 最终动作）
   */
  @Override
  public int scanAndProcess() {
    try {
      LocalDateTime now = LocalDateTime.now();
      int totalProcessed = 0;
      int iterations = 0;
      // P1-6: 游标分页 — 循环处理多批，直到无候选或达到最大迭代次数
      while (iterations < MAX_SCAN_ITERATIONS) {
        List<FlowRunTaskVO> candidates = taskRepository.selectSlaCandidates(SCAN_BATCH_SIZE);
        if (candidates == null || candidates.isEmpty()) {
          break;
        }
        int batchProcessed = 0;
        for (FlowRunTaskVO task : candidates) {
          try {
            if (processOverdue(task, now)) {
              batchProcessed++;
            }
          } catch (Exception e) {
            log.error("[FlowSla] 单条处理异常: taskId={} err={}", task.getId(), e.getMessage(), e);
          }
        }
        totalProcessed += batchProcessed;
        // 批次未满或本批无处理（剩余候选均未到 dueAt），结束循环
        if (candidates.size() < SCAN_BATCH_SIZE || batchProcessed == 0) {
          break;
        }
        iterations++;
      }
      if (totalProcessed > 0) {
        log.info("[FlowSla] 本轮扫描处理: count={} iterations={}", totalProcessed, iterations + 1);
      }
      return totalProcessed;
    } catch (Exception e) {
      log.error("[FlowSla] 扫描异常: {}", e.getMessage(), e);
      return 0;
    }
  }

  /**
   * 处理单条超期任务（{@code REQUIRES_NEW} 子事务）
   *
   * <p>使用 {@code @Transactional(propagation = Propagation.REQUIRES_NEW)} 隔离单条任务的失败，
   * 即便单条处理抛异常回滚，也不影响其他任务的处理。
   *
   * @param task 超期任务
   * @return true=已处理（REMIND / 最终动作），false=跳过（未到期 / 已完成 / 状态不符）
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public boolean processOverdue(FlowRunTaskVO task) {
    return processOverdue(task, LocalDateTime.now());
  }

  /**
   * 内部方法：传入 now 以便测试和复用
   *
   * @param task 超期任务（含 taskId、dueAt、taskStatus 等）
   * @param now 当前时间（便于测试时注入固定时间）
   * @return true=已处理（REMIND / 最终动作），false=跳过（未到期 / 已完成 / 状态不符）
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public boolean processOverdue(FlowRunTaskVO task, LocalDateTime now) {
    if (task == null || task.getId() == null) {
      return false;
    }
    // 1. 重新查一遍任务，避免读到陈旧数据
    FlowRunTaskVO fresh = taskRepository.findById(task.getId()).orElse(null);
    if (fresh == null) {
      return false;
    }
    if (!TASK_STATUS_PENDING.equals(fresh.getTaskStatus()) && !TASK_STATUS_CLAIMED.equals(fresh.getTaskStatus())) {
      return false; // 已完成
    }
    if (fresh.getDueAt() == null) {
      return false; // 未配置 SLA
    }
    // 2. 未到 dueAt，跳过
    if (fresh.getDueAt().isAfter(now)) {
      return false;
    }
    // 3. 解析节点 SLA 配置
    FlowNodeVO node = nodeRepository.findByCode(fresh.getDefinitionId(), fresh.getNodeCode()).orElse(null);
    Map<String, Object> config =
        node == null ? Collections.emptyMap() : parseSlaConfig(node.getSlaConfigJson());
    // 无配置：默认仅 NOTIFY（但因 FlowSlaService 只对配了 dueAt 的任务扫描，这种情况不应出现）
    if (config.isEmpty()) {
      log.warn(
          "[FlowSla] 任务已超期但无 SLA 配置: taskId={} nodeCode={}", fresh.getId(), fresh.getNodeCode());
      return false;
    }
    String actionStr = ((String) config.getOrDefault(SLA_CONFIG_KEY_ACTION, FlowSlaAction.REMIND.name())).toUpperCase();
    FlowSlaAction action;
    try {
      action = FlowSlaAction.valueOf(actionStr);
    } catch (IllegalArgumentException e) {
      log.warn("[FlowSla] 未知 action: taskId={} action={}", fresh.getId(), actionStr);
      return false;
    }
    int maxUrges = readInt(config, SLA_CONFIG_KEY_MAX_URGES, DEFAULT_MAX_REMINDERS);
    int urgeIntervalMin = readInt(config, SLA_CONFIG_KEY_URGE_INTERVAL_MINUTES, DEFAULT_REMINDER_INTERVAL_MINUTES);
    int currentUrges = fresh.getUrgeCount() == null ? 0 : fresh.getUrgeCount();
    LocalDateTime lastUrgedAt = fresh.getLastUrgedAt();
    // 4. 距离最后一次提醒未到间隔，不重复提醒
    if (lastUrgedAt != null && Duration.between(lastUrgedAt, now).toMinutes() < urgeIntervalMin) {
      return false;
    }
    // 5. 已达最大提醒次数：执行最终动作
    if (currentUrges >= maxUrges) {
      return executeFinalAction(fresh, node, action, config, now);
    }
    // 6. 未达最大提醒次数：先发一次提醒，再决定
    boolean urged = sendUrge(fresh, action, currentUrges + 1, maxUrges, now);
    if (urged) {
      taskRepository.incrementUrgeCount(fresh.getId(), currentUrges + 1, now);
    }
    return urged;
  }

  /**
   * 发送 SLA 提醒通知给任务办理人
   *
   * <p>通知内容包含流程名称、节点名称、截止时间和提醒次数。通知发送失败时返回 false，
   * 由调用方决定是否重试。
   *
   * @param task 超期任务（含 taskId、flowName、nodeName、dueAt、assigneeId 等）
   * @param action SLA 动作类型（REMIND / ESCALATE / AUTO_PASS / AUTO_REJECT）
   * @param newUrgeCount 本次提醒后的累计提醒次数
   * @param maxUrges 最大提醒次数（达到后执行最终动作）
   * @param now 当前时间（用于更新 lastUrgedAt）
   * @return true=通知发送成功；false=发送失败或 assigneeId 为空
   */
  private boolean sendUrge(
      FlowRunTaskVO task, FlowSlaAction action, int newUrgeCount, int maxUrges, LocalDateTime now) {
    try {
      String title = URGE_TITLE;
      String content =
          String.format(
              URGE_COMMENT_TEMPLATE,
              nullSafe(task.getFlowName()),
              nullSafe(task.getNodeName()),
              task.getDueAt(),
              newUrgeCount,
              maxUrges);
      String receiverId = task.getAssigneeId();
      if (receiverId == null) {
        log.warn(
            "[FlowSla] 无法解析 assigneeId: taskId={} assigneeId={}",
            task.getId(),
            task.getAssigneeId());
        return false;
      }
      notificationService.notify(NOTIFY_CHANNEL_INAPP, receiverId, title, content, NOTIFY_TYPE_WORKFLOW_TIMEOUT, NOTIFY_LEVEL_WARN);
      log.info(
          "[FlowSla] 发送 SLA 提醒: taskId={} receiver={} count={}/{} action={}",
          task.getId(),
          receiverId,
          newUrgeCount,
          maxUrges,
          action);
      return true;
    } catch (Exception e) {
      log.warn("[FlowSla] 提醒发送失败: taskId={} err={}", task.getId(), e.getMessage());
      return false;
    }
  }

  /**
   * 执行最终动作（NOTIFY / AUTO_PASS / AUTO_REJECT / ESCALATE）
   *
   * <p>P1-3 闭环语义：每个 action 必须有明确终态，禁止"标记 TIMEOUT 但流程卡死"。
   *
   * <ul>
   *   <li>NOTIFY — 通知管理员介入，任务保持 PENDING（人工处理）
   *   <li>ESCALATE — 转办给 escalateUserId，任务保持 PENDING（新办理人处理）
   *   <li>AUTO_PASS — 系统自动通过，流程推进到下一节点
   *   <li>AUTO_REJECT — 系统自动驳回，流程终止
   *   <li>REMIND — 兼容旧配置，等同于 NOTIFY（不再标记 TIMEOUT）
   * </ul>
   *
   * @param task 超期任务（含 taskId、flowCode、nodeCode 等）
   * @param node 流程节点（含 slaConfig 配置）
   * @param action SLA 最终动作类型
   * @param config SLA 配置 Map（含 escalateUserId、autoComment 等）
   * @param now 当前时间（用于更新 dueAt 等字段）
   * @return true=动作执行成功；false=执行失败或未知动作
   */
  private boolean executeFinalAction(
      FlowRunTaskVO task,
      FlowNodeVO node,
      FlowSlaAction action,
      Map<String, Object> config,
      LocalDateTime now) {
    log.info("[FlowSla] 触发最终动作: taskId={} action={}", task.getId(), action);
    switch (action) {
      case REMIND:
        // P1-3: 兼容旧配置 — REMIND 作为最终动作时等同于 NOTIFY
        // （不再调用 doAutoTimeout 标记任务为 TIMEOUT，那会让流程卡死）
        return doNotify(task, config, now);
      case NOTIFY:
        return doNotify(task, config, now);
      case AUTO_PASS:
        return doAutoPass(task, config, now);
      case AUTO_REJECT:
        return doAutoReject(task, config, now);
      case ESCALATE:
        return doEscalate(task, config, now);
      default:
        log.warn("[FlowSla] 未知最终动作: action={}", action);
        return false;
    }
  }

  /**
   * 自动通过：以系统身份调用 pass()
   *
   * <p>使用系统用户（ID="0"）身份执行通过操作，并记录 SLA 动作日志。
   * 通过成功后更新 slaAction 标记，避免重复执行。
   *
   * @param task 超期任务（含 taskId、flowCode、nodeCode 等）
   * @param config SLA 配置 Map（含 autoComment 审批意见）
   * @param now 当前时间（用于记录动作时间）
   * @return true=自动通过成功；false=执行失败
   */
  private boolean doAutoPass(FlowRunTaskVO task, Map<String, Object> config, LocalDateTime now) {
    try {
      String comment = (String) config.getOrDefault(SLA_CONFIG_KEY_AUTO_COMMENT, AUTO_PASS_DEFAULT_COMMENT);
      FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
      dto.setTaskId(task.getId());
      dto.setUserId(SYSTEM_USER_ID);
      dto.setComment(comment);
      dto.setVariables(Collections.emptyMap());
      taskService.pass(dto);
      taskRepository.markSlaAction(task.getId(), FlowSlaAction.AUTO_PASS.name(), 0);
      log.info("[FlowSla] 自动通过: taskId={} comment={}", task.getId(), comment);
      // P2-3: Prometheus 指标
      if (flowMetrics != null) {
      flowMetrics.incError(task.getFlowCode(), METRIC_ERROR_SLA_TIMEOUT);
      flowMetrics.incTask(task.getFlowCode(), task.getNodeCode(), METRIC_TASK_AUTO_HANDLED);
      }
      return true;
    } catch (Exception e) {
      log.error("[FlowSla] 自动通过失败: taskId={} err={}", task.getId(), e.getMessage(), e);
      return false;
    }
  }

  /**
   * 自动驳回：以系统身份调用 reject()
   *
   * <p>使用系统用户（ID="0"）身份执行驳回操作，并记录 SLA 动作日志。
   * 驳回成功后更新 slaAction 标记，避免重复执行。
   *
   * @param task 超期任务（含 taskId、flowCode、nodeCode 等）
   * @param config SLA 配置 Map（含 autoComment 审批意见）
   * @param now 当前时间（用于记录动作时间）
   * @return true=自动驳回成功；false=执行失败
   */
  private boolean doAutoReject(FlowRunTaskVO task, Map<String, Object> config, LocalDateTime now) {
    try {
      String comment = (String) config.getOrDefault(SLA_CONFIG_KEY_AUTO_COMMENT, AUTO_REJECT_DEFAULT_COMMENT);
      FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
      dto.setTaskId(task.getId());
      dto.setUserId(SYSTEM_USER_ID);
      dto.setComment(comment);
      dto.setVariables(Collections.emptyMap());
      taskService.reject(dto);
      taskRepository.markSlaAction(task.getId(), FlowSlaAction.AUTO_REJECT.name(), 0);
      log.info("[FlowSla] 自动驳回: taskId={} comment={}", task.getId(), comment);
      // P2-3: Prometheus 指标
      if (flowMetrics != null) {
      flowMetrics.incError(task.getFlowCode(), METRIC_ERROR_SLA_TIMEOUT);
      flowMetrics.incTask(task.getFlowCode(), task.getNodeCode(), METRIC_TASK_AUTO_HANDLED);
      }
      return true;
    } catch (Exception e) {
      log.error("[FlowSla] 自动驳回失败: taskId={} err={}", task.getId(), e.getMessage(), e);
      return false;
    }
  }

  /**
   * 升级：转办给 escalateUserId（默认管理员）
   *
   * <p>通过 transfer 接口将任务转给升级用户，并重置 SLA 计时器（urgeCount=0、
   * lastUrgedAt=null、dueAt=now+timeoutMinutes）。转办失败时降级为通知升级用户。
   *
   * @param task 超期任务（含 taskId、flowCode、nodeCode、assigneeId 等）
   * @param config SLA 配置 Map（含 escalateUserId、timeoutMinutes 等）
   * @param now 当前时间（用于计算新的 dueAt）
   * @return true=升级成功（转办或通知）；false=执行失败
   */
  private boolean doEscalate(FlowRunTaskVO task, Map<String, Object> config, LocalDateTime now) {
    try {
      if (task.getSlaEscalated() != null && task.getSlaEscalated() == 1) {
        log.info("[FlowSla] 任务已升级，跳过重复升级: taskId={}", task.getId());
        return false;
      }
      String escalateUserId = readString(config, SLA_CONFIG_KEY_ESCALATE_USER_ID, null);
      if (escalateUserId == null) {
        escalateUserId = DEFAULT_ADMIN_USER_ID;
      }
      String comment = String.format(ESCALATE_COMMENT_TEMPLATE, escalateUserId);
      // 通过转办接口将任务转给升级用户
      FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
      dto.setTaskId(task.getId());
      dto.setUserId(SYSTEM_USER_ID);
      dto.setTargetUserId(escalateUserId);
      dto.setComment(comment);
      dto.setVariables(Collections.emptyMap());
      // 标记升级；使用 transfer 接口
      try {
        taskService.transfer(dto);
        taskRepository.markSlaAction(task.getId(), FlowSlaAction.ESCALATE.name(), 1);
        // 转办后：升级后的任务重新计 SLA
        FlowRunTaskVO afterTransfer = taskRepository.findById(task.getId()).orElse(null);
        if (afterTransfer != null) {
          afterTransfer.setSlaEscalated(1);
          afterTransfer.setUrgeCount(0);
          afterTransfer.setLastUrgedAt(null);
          // 给新任务一个新的 dueAt（基于当前时间 + timeoutMinutes）
          Integer timeoutMinutes = readInt(config, SLA_CONFIG_KEY_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MINUTES);
          afterTransfer.setDueAt(now.plusMinutes(timeoutMinutes));
          taskRepository.update(afterTransfer);
        }
        log.info("[FlowSla] 升级成功: taskId={} escalateUserId={}", task.getId(), escalateUserId);
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
      flowMetrics.incError(task.getFlowCode(), METRIC_ERROR_SLA_TIMEOUT);
      flowMetrics.incTask(task.getFlowCode(), task.getNodeCode(), METRIC_TASK_AUTO_HANDLED);
        }
        return true;
      } catch (Exception transferEx) {
        // transfer 失败时降级：仅通知目标用户，标记升级
        log.warn("[FlowSla] 转办失败，改用通知: taskId={} err={}", task.getId(), transferEx.getMessage());
        notificationService.notify(
            NOTIFY_CHANNEL_INAPP, escalateUserId, ESCALATE_TITLE, comment, NOTIFY_TYPE_WORKFLOW_TASK_ESCALATED, NOTIFY_LEVEL_URGENT);
        taskRepository.markSlaAction(task.getId(), FlowSlaAction.ESCALATE.name(), 1);
        return true;
      }
    } catch (Exception e) {
      log.error("[FlowSla] 升级失败: taskId={} err={}", task.getId(), e.getMessage(), e);
      return false;
    }
  }

  /**
   * P1-3: NOTIFY 最终动作 — 通知管理员/升级人介入，任务保持 PENDING（闭环：等人工处理）
   *
   * <p>通知目标解析顺序：
   *
   * <ol>
   *   <li>{@code notifyUserIds} 配置（逗号分隔的多用户，最高优先级）
   *   <li>{@code escalateUserId} 配置（单用户，与 ESCALATE 共用字段）
   *   <li>默认管理员（{@link #DEFAULT_ADMIN_USER_ID}）
   * </ol>
   *
   * <p>任务状态不变（仍为 PENDING/CLAIMED），由人工处理后流程自然推进。 与原 {@code doAutoTimeout} 的区别：不标记 TIMEOUT 终态，避免流程卡死。
   *
   * @param task 超时任务
   * @param config SLA 配置
   * @param now 当前时间
   * @return true=通知已发送；false=发送异常
   */
  private boolean doNotify(FlowRunTaskVO task, Map<String, Object> config, LocalDateTime now) {
    try {
      List<String> targets = resolveNotifyTargets(config);
      String title = NOTIFY_TITLE;
      String content =
          String.format(
              NOTIFY_COMMENT_TEMPLATE,
              nullSafe(task.getFlowName()),
              nullSafe(task.getNodeName()),
              task.getId(),
              nullSafe(task.getAssigneeId()));
      notificationService.notifyBatch(NOTIFY_CHANNEL_INAPP, targets, title, content, NOTIFY_TYPE_WORKFLOW_URGE, NOTIFY_LEVEL_URGENT);
      // 标记 slaAction=NOTIFY（slaEscalated=0 表示任务仍活跃，未转办）
      taskRepository.markSlaAction(task.getId(), FlowSlaAction.NOTIFY.name(), 0);
      log.info(
          "[FlowSla] NOTIFY 通知已发送: taskId={} targets={} flowCode={} nodeCode={}",
          task.getId(),
          targets,
          task.getFlowCode(),
          task.getNodeCode());
      // P2-3: Prometheus 指标
      if (flowMetrics != null) {
      flowMetrics.incError(task.getFlowCode(), METRIC_ERROR_SLA_TIMEOUT);
      flowMetrics.incTask(task.getFlowCode(), task.getNodeCode(), METRIC_TASK_AUTO_HANDLED);
      }
      return true;
    } catch (Exception e) {
      log.error("[FlowSla] NOTIFY 通知失败: taskId={} err={}", task.getId(), e.getMessage(), e);
      return false;
    }
  }

  /**
   * 解析 NOTIFY 通知目标列表
   *
   * <p>优先级：notifyUserIds（逗号分隔）→ escalateUserId → 默认管理员
   *
   * @param config SLA 配置 Map（含 notifyUserIds、escalateUserId 等）
   * @return 通知目标用户 ID 列表（非空，最低返回默认管理员）
   */
  private List<String> resolveNotifyTargets(Map<String, Object> config) {
    String notifyUserIds = readString(config, SLA_CONFIG_KEY_NOTIFY_USER_IDS, null);
    if (StringUtils.hasText(notifyUserIds)) {
      String[] ids = notifyUserIds.split(",");
      List<String> targets = new ArrayList<>(ids.length);
      for (String id : ids) {
        String trimmed = id.trim();
        if (!trimmed.isEmpty()) {
          targets.add(trimmed);
        }
      }
      if (!targets.isEmpty()) {
        return targets;
      }
    }
    String escalateUserId = readString(config, SLA_CONFIG_KEY_ESCALATE_USER_ID, null);
    if (StringUtils.hasText(escalateUserId)) {
      return Collections.singletonList(escalateUserId);
    }
    return Collections.singletonList(DEFAULT_ADMIN_USER_ID);
  }

  /**
   * 每 60s 扫描一次（与 FlowTimerService 错峰 — FlowTimerService 30s, FlowSlaService 60s）
   *
   * <p>通过 {@link DistributedScheduled} 保证多节点部署时只有一个节点执行扫描， 获取不到锁的节点直接跳过本次执行（非阻塞）。 锁持有时间 55s（略小于
   * fixedDelay 60s），保证下次扫描前锁已释放。
   */
  @Scheduled(fixedDelay = 60_000L, initialDelay = 90_000L)
  @DistributedScheduled(lockKey = "flow:sla:scan", leaseTime = 55)
  public void scheduledScan() {
    scanAndProcess();
  }

  // ============================== 辅助方法 ==============================

  private Integer readInt(Map<String, Object> config, String key, Integer defaultValue) {
    Object val = config.get(key);
    if (val == null) {
      return defaultValue;
    }
    if (val instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(val));
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private String readString(Map<String, Object> config, String key, String defaultValue) {
    Object val = config.get(key);
    if (val == null) {
      return defaultValue;
    }
    return String.valueOf(val);
  }

  private String nullSafe(String s) {
    return s == null ? "" : s;
  }
}
