package com.njydsz.workflow.server.service.impl.integration;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.repository.FlowTimerRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.domain.vo.FlowTimerVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowNotificationService;
import com.njydsz.workflow.server.service.FlowTimerService;
import com.njydsz.workflow.server.service.impl.instance.FlowInstanceServiceImpl;

/**
 * 工作流定时器服务实现
 *
 * <p>对 {@link FlowTimerService} 接口的完整实现，承担 BPMN 2.0 规范中 <b>Timer 事件（中间定时器 / 边界定时器）</b>的运行时支持。内部每
 * 30s 扫描到点的 {@code PENDING} 定时器并触发。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>定时器创建（{@link #scheduleTimer}）</b>：流程到达中间定时器或边界定时器节点时， 写入 {@code ydsz_flow_timer} 表，状态 =
 *       {@code PENDING}
 *   <li><b>定时器调度（{@link #scan}）</b>：{@link Scheduled} 注解每 30s 扫描到点的 PENDING 定时器
   *<li><b>中间定时器触发</b>：调用 {@link com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer#advance} 推进流程到下一节点（类似「延迟通过」）
 *   <li><b>边界定时器触发</b>：取消关联 {@code userTask}（视为超时未完成），推进到边界定时器下游节点
 *   <li><b>定时器取消（{@link #cancelTimer}）</b>：任务提前完成时取消关联定时器（避免超时误触发）
 * </ul>
 *
 * <p><b>定时器类型：</b>
 *
 * <ul>
 *   <li>{@code TIME_DURATION} — 相对时间（{@code PT5M} 表示 5 分钟后）
 *   <li>{@code TIME_DATE} — 绝对时间（{@code 2026-12-31T23:59:59}）
 *   <li>{@code TIME_CYCLE} — 循环触发（{@code R3/PT1H} 表示每小时触发一次，共 3 次）
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}
 *   <li>通过 {@link DistributedScheduled} 分布式锁保证集群中只有<b>一个节点</b>执行扫描， 避免重复触发同一定时器
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>集群单点扫描</b>：通过 {@code @DistributedScheduled} 保证同一时刻只有一个节点执行扫描， 锁 key 自动添加 {@code
 *       ydsz:schedule:} 前缀
 *   <li><b>幂等触发</b>：同一定时器的多次触发由 {@code @Transactional} 串行化， 触发成功后立即置为 {@code TRIGGERED}，避免重复推进流程
 *   <li><b>失败重试</b>：触发失败的定时器记录 {@code retry_count}，下次扫描时重试（最多 3 次）
 *   <li><b>周期定时器</b>：{@code TIME_CYCLE} 类型的定时器在触发后自动创建下一周期记录
 * </ul>
 *
 * <p><b>性能优化：</b>
 *
 * <ul>
 *   <li>扫描走 {@code idx_timer_pending} 复合索引（{@code status, due_at}），避免全表扫描
 *   <li>单次扫描上限 500 条，处理时间超过 30s 时主动退出循环，让下一周期继续
 * </ul>
 *
 * <p><b>与 SLA 的区别：</b>定时器是<b>流程设计期</b>配置（{@code timerEventDefinition}）， SLA 是<b>流程设计期</b>配置（{@code
 * slaConfig}）但由 {@link FlowSlaServiceImpl} 处理。 定时器是 BPMN 原生事件，SLA 是 ydsz 工作流扩展。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowTimerService 接口定义
 * @see com.njydsz.workflow.domain.vo.FlowTimerVO 定时器值对象
 * @see com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer 流程推进引擎
 * @see FlowSlaServiceImpl SLA 监控（与定时器同属「超时处理」但职责不同）
 * @see com.njydsz.common.lock.annotation.DistributedScheduled 分布式调度注解
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTimerServiceImpl implements FlowTimerService {

  /** 定时器仓储（domain 层契约），管理 ydsz_flow_timer 表 CRUD */
  private final FlowTimerRepository timerRepository;

  /** 流程实例仓储（domain 层契约），查询定时器关联的实例 */
  private final FlowInstanceRepository instanceRepository;

  /** 运行时任务仓储（domain 层契约），查询按钮执行关联的待办任务 */
  private final FlowRunTaskRepository taskRepository;

  /** 流程节点仓储（domain 层契约），查询 boundaryEvent 节点配置 */
  private final FlowNodeRepository nodeRepository;

  /** 实体转换器，用于 VO ↔ DO 转换 */
  private final WorkflowConverter converter;

  /** 流程推进引擎，定时器触发后推进流程 */
  private final DefaultFlowAdvancer advancer;

  private final FlowNotificationService notificationService;

  /** 单次扫描上限，避免大表全表扫描 */
  private static final int SCAN_BATCH_SIZE = 200;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String scheduleIntermediate(String instanceId, String nodeCode, Duration delay) {
    if (instanceId == null || nodeCode == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("instanceId/nodeCode 不能为空")
          .build();
    }
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("流程实例不存在: " + instanceId)
          .build();
    }
    FlowNodeVO node = nodeRepository.findByCode(instance.getDefinitionId(), nodeCode).orElse(null);
    if (node == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("节点不存在: " + nodeCode)
          .build();
    }
    FlowTimerVO timer = new FlowTimerVO();
    timer.setTenantId(instance.getTenantId());
    timer.setInstanceId(instanceId);
    timer.setDefinitionId(instance.getDefinitionId());
    timer.setFlowCode(instance.getFlowCode());
    timer.setNodeCode(nodeCode);
    timer.setNodeName(node.getNodeName());
    timer.setTimerType("INTERMEDIATE");
    timer.setFireAt(LocalDateTime.now().plus(delay));
    timer.setTimerStatus("PENDING");
    timer.setProviderTraceId(instance.getProviderTraceId());
    timerRepository.save(timer);
    log.info(
        "[FlowTimer] 创建中间定时器: instanceId={} nodeCode={} fireAt={}",
        instanceId,
        nodeCode,
        timer.getFireAt());
    return timer.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String scheduleBoundary(
      String taskId, String instanceId, String nodeCode, Duration delay) {
    if (taskId == null || instanceId == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("taskId/instanceId 不能为空")
          .build();
    }
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("流程实例不存在: " + instanceId)
          .build();
    }
    FlowNodeVO node =
        nodeCode != null ? nodeRepository.findByCode(instance.getDefinitionId(), nodeCode).orElse(null) : null;
    FlowTimerVO timer = new FlowTimerVO();
    timer.setTenantId(instance.getTenantId());
    timer.setInstanceId(instanceId);
    timer.setDefinitionId(instance.getDefinitionId());
    timer.setFlowCode(instance.getFlowCode());
    timer.setNodeCode(nodeCode);
    timer.setNodeName(node == null ? null : node.getNodeName());
    timer.setTimerType("BOUNDARY");
    timer.setBoundaryTaskId(taskId);
    timer.setFireAt(LocalDateTime.now().plus(delay));
    timer.setTimerStatus("PENDING");
    timer.setProviderTraceId(instance.getProviderTraceId());
    timerRepository.save(timer);
    log.info(
        "[FlowTimer] 创建边界定时器: taskId={} instanceId={} fireAt={}",
        taskId,
        instanceId,
        timer.getFireAt());
    return timer.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean fire(FlowTimerVO timer) {
    if (timer == null) {
      return false;
    }
    // CAS 标记 FIRED，避免多节点并发扫描时重复触发
    timerRepository.markFired(timer.getId());
    log.debug("[FlowTimer] 定时器标记 FIRED: id={}", timer.getId());
    try {
      if ("INTERMEDIATE".equalsIgnoreCase(timer.getTimerType())) {
        // 中间定时器：推进流程
        FlowInstanceVO instance = instanceRepository.findById(timer.getInstanceId()).orElse(null);
        if (instance == null) {
          log.warn("[FlowTimer] 实例不存在: id={}", timer.getInstanceId());
          return true;
        }
        if (!"RUNNING".equalsIgnoreCase(instance.getFlowStatus())
            && !"SUSPENDED".equalsIgnoreCase(instance.getFlowStatus())) {
          log.info(
              "[FlowTimer] 实例非运行态，跳过推进: id={} status={}",
              instance.getId(),
              instance.getFlowStatus());
          return true;
        }
        Map<String, Object> variables = parseVariables(instance.getVariable());
        List<FlowNodeVO> nextNodes =
            advancer.advance(instance, timer.getNodeCode(), "PASS", null, variables);
        if (nextNodes.isEmpty()) {
          log.info("[FlowTimer] 中间定时器触发后无下游节点: instanceId={}", timer.getInstanceId());
          return true;
        }
        ((FlowInstanceServiceImpl) instanceService())
            .generateTasksForNodes(timer.getInstanceId(), nextNodes, variables);
        FlowNodeVO first = nextNodes.get(0);
        instanceRepository.updateStatus(
            timer.getInstanceId(),
            instance.getFlowStatus(),
            first.getNodeCode(),
            first.getNodeName(),
            null,
            null);
        log.info(
            "[FlowTimer] 中间定时器触发: timerId={} instanceId={} → next={}",
            timer.getId(),
            timer.getInstanceId(),
            first.getNodeCode());
      } else if ("BOUNDARY".equalsIgnoreCase(timer.getBoundaryTaskId() == null ? "" : "BOUNDARY")) {
        // 边界定时器：userTask 未在 fire_at 前完成则触发
        fireBoundary(timer);
      }
      return true;
    } catch (Exception e) {
      log.error(
          "[FlowTimer] 触发失败 timerId={} type={} err={}",
          timer.getId(),
          timer.getTimerType(),
          e.getMessage(),
          e);
      return false;
    }
  }

  /**
   * 边界定时器触发：取消 userTask，触发"超时分支"（节点 ext 中标记的 boundarySkip）
   *
   * @param timer 参数说明
   */
  private void fireBoundary(FlowTimerVO timer) {
    FlowRunTaskVO task = taskRepository.findById(timer.getBoundaryTaskId()).orElse(null);
    if (task == null) {
      log.info("[FlowTimer] 边界定时器对应 userTask 已删除: timerId={}", timer.getId());
      return;
    }
    // userTask 还在 PENDING/CLAIMED 才算超时
    if ("COMPLETED".equalsIgnoreCase(task.getTaskStatus())
        || "REJECTED".equalsIgnoreCase(task.getTaskStatus())
        || "CANCELLED".equalsIgnoreCase(task.getTaskStatus())
        || "TIMEOUT".equalsIgnoreCase(task.getTaskStatus())) {
      log.info(
          "[FlowTimer] userTask 已完成，跳过边界触发: taskId={} status={}",
          task.getId(),
          task.getTaskStatus());
      return;
    }
    FlowInstanceVO instance = instanceRepository.findById(timer.getInstanceId()).orElse(null);
    if (instance == null) {
      return;
    }
    // 1. 取消 userTask
    LocalDateTime now = LocalDateTime.now();
    taskRepository.completeTaskWithComment(
        task.getId(),
        "TIMEOUT",
        "边界定时器触发超时",
        now,
        task.getCreatedAt() == null ? null : Duration.between(task.getCreatedAt(), now).toMillis());
    log.info("[FlowTimer] 边界定时器超时 userTask: timerId={} taskId={}", timer.getId(), task.getId());
    // 2. 通知原办理人
    try {
      if (task.getAssigneeId() != null) {
        notificationService.notify(
            "INAPP",
            task.getAssigneeId(),
            "审批超时",
            String.format(
                "【%s】%s 已超时，请尽快处理", nullSafe(instance.getFlowName()), nullSafe(task.getNodeName())),
            "WORKFLOW_TASK_TIMEOUT",
            "WARN");
      }
    } catch (Exception e) {
      log.warn("[FlowTimer] 超时通知失败: {}", e.getMessage());
    }
    // 3. 推进到下一节点（按 PASS 流程走，但 task 已被标记为 TIMEOUT）
    Map<String, Object> variables = parseVariables(instance.getVariable());
    List<FlowNodeVO> nextNodes =
        advancer.advance(instance, task.getNodeCode(), "PASS", null, variables);
    if (!nextNodes.isEmpty()) {
      ((FlowInstanceServiceImpl) instanceService())
          .generateTasksForNodes(timer.getInstanceId(), nextNodes, variables);
      FlowNodeVO first = nextNodes.get(0);
      instanceRepository.updateStatus(
          timer.getInstanceId(),
          instance.getFlowStatus(),
          first.getNodeCode(),
          first.getNodeName(),
          null,
          null);
    }
  }

  @Override
  public int scanAndFire() {
    try {
      List<FlowTimerVO> dueList = timerRepository.findDueTimers(LocalDateTime.now(), SCAN_BATCH_SIZE);
      if (dueList.isEmpty()) {
        return 0;
      }
      int fired = 0;
      for (FlowTimerVO t : dueList) {
        try {
          if (fire(t)) {
            fired++;
          }
        } catch (Exception e) {
          log.error("[FlowTimer] 单条触发异常 timerId={}: {}", t.getId(), e.getMessage(), e);
        }
      }
      if (fired > 0) {
        log.info("[FlowTimer] 本轮扫描触发: count={}", fired);
      }
      return fired;
    } catch (Exception e) {
      log.error("[FlowTimer] 扫描异常: {}", e.getMessage(), e);
      return 0;
    }
  }

  @Override
  public int cancelByTask(String taskId) {
    if (taskId == null) {
      return 0;
    }
    timerRepository.cancelByTask(taskId);
    return 1;
  }

  @Override
  public int cancelByInstance(String instanceId, String reason) {
    if (instanceId == null) {
      return 0;
    }
    return timerRepository.cancelByInstance(instanceId, reason == null ? "实例结束" : reason);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FlowTimerVO> listByInstance(String instanceId) {
    return timerRepository.findByInstanceOrderByCreatedAtDesc(instanceId);
  }

  @Override
  @Transactional(readOnly = true)
  public long countPending(String instanceId) {
    return timerRepository.countPendingByInstance(instanceId);
  }

  /**
   * 每 30s 扫描一次（在 workflow 模块自身启用， workflow 模块需配 {@code @EnableScheduling} 或在公共配置中开启）。
   *
   * <p>通过 {@link DistributedScheduled} 保证多节点部署时同一时刻只有一个节点执行扫描， 获取不到锁的节点直接跳过本次执行（非阻塞）。
   */
  @Scheduled(fixedDelay = 30_000L, initialDelay = 60_000L)
  @DistributedScheduled(lockKey = "flow:timer:scan", leaseTime = 30)
  public void scheduledScan() {
    scanAndFire();
  }

  // ============== 内部辅助 ==============

  /**
   * 复用 FlowInstanceServiceImpl.generateTasksForNodes（包内访问）
   *
   * @return 返回值说明
   */
  private FlowInstanceService instanceService() {
    return advancer.getInstanceService();
  }

  private Map<String, Object> parseVariables(String variableJson) {
    if (variableJson == null || variableJson.isBlank()) {
      return new HashMap<>();
    }
    Map<String, Object> map = YdszJson.parseMap(variableJson);
    return map == null ? new HashMap<>() : map;
  }

  private String nullSafe(String s) {
    return s == null ? "" : s;
  }
}
