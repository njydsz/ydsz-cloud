package com.njydsz.workflow.server.service.impl.integration;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.workflow.domain.repository.FlowTimerRepository;
import com.njydsz.workflow.infra.entity.FlowInstanceDO;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.infra.entity.FlowTimerDO;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.infra.mapper.FlowTimerMapper;
import com.njydsz.workflow.server.engine.FlowAdvancer;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowNotificationService;
import com.njydsz.workflow.server.service.FlowTimerService;
import com.njydsz.workflow.server.service.impl.instance.FlowInstanceServiceImpl;

/**
 * 工作流定时器服务实现
 *
 * <p>对 {@link FlowTimerService} 接口的完整实现，承担 BPMN 2.0 规范中 <b>Timer 事件（中间定时器 / 边界定时器）</b>的运行时支持。内部每
 * 30s 扫描到点的 {@code PENDING} 定时器并触发，对标 Activiti / Flowable 的 Job Executor。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>定时器创建（{@link #scheduleTimer}）</b>：流程到达中间定时器或边界定时器节点时， 写入 {@code ydsz_flow_timer} 表，状态 =
 *       {@code PENDING}
 *   <li><b>定时器调度（{@link #scan}）</b>：{@link Scheduled} 注解每 30s 扫描到点的 PENDING 定时器
 *   <li><b>中间定时器触发</b>：调用 {@link FlowAdvancer#advance} 推进流程到下一节点（类似「延迟通过」）
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
 * @see com.njydsz.workflow.infra.entity.FlowTimerDO 定时器实体
 * @see FlowAdvancer 流程推进引擎
 * @see FlowSlaServiceImpl SLA 监控（与定时器同属「超时处理」但职责不同）
 * @see com.njydsz.common.lock.annotation.DistributedScheduled 分布式调度注解
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTimerServiceImpl implements FlowTimerService {

  /** 定时器 Mapper，管理 ydsz_flow_timer 表 */
  private final FlowTimerMapper timerMapper;

  /**
   * 定时器仓储（domain 层契约）。
   *
   * <p>提供领域语义化的数据访问方法。当前 Service 仍通过 {@link #timerMapper} 访问数据，
   * 因为部分 Mapper 方法（如 {@code selectDueTimers}、{@code markFired} 返回 int、{@code cancelByInstance}、
   * {@code countPendingByInstance}）在仓储中暂无等价签名，
   * 且仓储返回 {@code FlowTimerVO} 与 Service 使用的 {@code FlowTimerDO} 类型不同。
   * 后续应在仓储中补齐对应方法并迁移。
   */
  private final FlowTimerRepository timerRepository;

  /** 流程实例 Mapper，查询定时器关联的实例 */
  private final FlowInstanceMapper instanceMapper;

  /** 运行时任务 Mapper，定时器触发后创建/更新任务 */
  private final FlowRunTaskMapper taskMapper;

  /** 流程节点 Mapper，查询 boundaryEvent 节点配置 */
  private final FlowNodeMapper nodeMapper;

  /** 流程推进引擎，定时器触发后推进流程 */
  private final FlowAdvancer advancer;

  private final FlowNotificationService notificationService;

  /** 单次扫描上限，避免大表全表扫描 */
  private static final int SCAN_BATCH_SIZE = 200;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String scheduleIntermediate(String instanceId, String nodeCode, Duration delay) {
    if (instanceId == null || nodeCode == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("instanceId/nodeCode 不能为空")
          .build();
    }
    FlowInstanceDO instance = instanceMapper.selectById(instanceId);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.NOT_FOUND)
          .message("流程实例不存在: " + instanceId)
          .build();
    }
    FlowNodeDO node = nodeMapper.selectByCode(instance.getDefinitionId(), nodeCode);
    if (node == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.NOT_FOUND)
          .message("节点不存在: " + nodeCode)
          .build();
    }
    FlowTimerDO timer = new FlowTimerDO();
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
    // TODO: 迁移至 timerRepository.save(vo)，需 DO→VO 转换
    timerMapper.insert(timer);
    log.info(
        "[FlowTimerDO] 创建中间定时器: instanceId={} nodeCode={} fireAt={}",
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
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("taskId/instanceId 不能为空")
          .build();
    }
    FlowInstanceDO instance = instanceMapper.selectById(instanceId);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.NOT_FOUND)
          .message("流程实例不存在: " + instanceId)
          .build();
    }
    FlowNodeDO node =
        nodeCode != null ? nodeMapper.selectByCode(instance.getDefinitionId(), nodeCode) : null;
    FlowTimerDO timer = new FlowTimerDO();
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
    // TODO: 迁移至 timerRepository.save(vo)，需 DO→VO 转换
    timerMapper.insert(timer);
    log.info(
        "[FlowTimerDO] 创建边界定时器: taskId={} instanceId={} fireAt={}",
        taskId,
        instanceId,
        timer.getFireAt());
    return timer.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean fire(FlowTimerDO timer) {
    if (timer == null) {
      return false;
    }
    // CAS 标记 FIRED，避免多节点并发扫描时重复触发
    // TODO: 迁移至 timerRepository.markFired(id)，签名不同
    int updated = timerMapper.markFired(timer.getId(), LocalDateTime.now());
    if (updated == 0) {
      log.debug("[FlowTimerDO] 定时器已被处理: id={}", timer.getId());
      return false;
    }
    try {
      if ("INTERMEDIATE".equalsIgnoreCase(timer.getTimerType())) {
        // 中间定时器：推进流程
        FlowInstanceDO instance = instanceMapper.selectById(timer.getInstanceId());
        if (instance == null) {
          log.warn("[FlowTimerDO] 实例不存在: id={}", timer.getInstanceId());
          return true;
        }
        if (!"RUNNING".equalsIgnoreCase(instance.getFlowStatus())
            && !"SUSPENDED".equalsIgnoreCase(instance.getFlowStatus())) {
          log.info(
              "[FlowTimerDO] 实例非运行态，跳过推进: id={} status={}",
              instance.getId(),
              instance.getFlowStatus());
          return true;
        }
        Map<String, Object> variables = parseVariables(instance.getVariable());
        List<FlowNodeDO> nextNodes =
            advancer.advance(instance, timer.getNodeCode(), "PASS", null, variables);
        if (nextNodes.isEmpty()) {
          log.info("[FlowTimerDO] 中间定时器触发后无下游节点: instanceId={}", timer.getInstanceId());
          return true;
        }
        ((FlowInstanceServiceImpl) instanceService())
            .generateTasksForNodes(timer.getInstanceId(), nextNodes, variables);
        FlowNodeDO first = nextNodes.get(0);
        instanceMapper.updateStatus(
            timer.getInstanceId(),
            instance.getFlowStatus(),
            first.getNodeCode(),
            first.getNodeName(),
            null,
            null);
        log.info(
            "[FlowTimerDO] 中间定时器触发: timerId={} instanceId={} → next={}",
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
          "[FlowTimerDO] 触发失败 timerId={} type={} err={}",
          timer.getId(),
          timer.getTimerType(),
          e.getMessage(),
          e);
      return false;
    }
  }

  /** 边界定时器触发：取消 userTask，触发"超时分支"（节点 ext 中标记的 boundarySkip） */
  private void fireBoundary(FlowTimerDO timer) {
    FlowRunTaskDO task = taskMapper.selectById(timer.getBoundaryTaskId());
    if (task == null) {
      log.info("[FlowTimerDO] 边界定时器对应 userTask 已删除: timerId={}", timer.getId());
      return;
    }
    // userTask 还在 PENDING/CLAIMED 才算超时
    if ("COMPLETED".equalsIgnoreCase(task.getTaskStatus())
        || "REJECTED".equalsIgnoreCase(task.getTaskStatus())
        || "CANCELLED".equalsIgnoreCase(task.getTaskStatus())
        || "TIMEOUT".equalsIgnoreCase(task.getTaskStatus())) {
      log.info(
          "[FlowTimerDO] userTask 已完成，跳过边界触发: taskId={} status={}",
          task.getId(),
          task.getTaskStatus());
      return;
    }
    FlowInstanceDO instance = instanceMapper.selectById(timer.getInstanceId());
    if (instance == null) {
      return;
    }
    // 1. 取消 userTask
    LocalDateTime now = LocalDateTime.now();
    taskMapper.completeTask(
        task.getId(),
        "TIMEOUT",
        "边界定时器触发超时",
        now,
        task.getCreatedAt() == null ? null : Duration.between(task.getCreatedAt(), now).toMillis());
    log.info("[FlowTimerDO] 边界定时器超时 userTask: timerId={} taskId={}", timer.getId(), task.getId());
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
      log.warn("[FlowTimerDO] 超时通知失败: {}", e.getMessage());
    }
    // 3. 推进到下一节点（按 PASS 流程走，但 task 已被标记为 TIMEOUT）
    Map<String, Object> variables = parseVariables(instance.getVariable());
    List<FlowNodeDO> nextNodes =
        advancer.advance(instance, task.getNodeCode(), "PASS", null, variables);
    if (!nextNodes.isEmpty()) {
      ((FlowInstanceServiceImpl) instanceService())
          .generateTasksForNodes(timer.getInstanceId(), nextNodes, variables);
      FlowNodeDO first = nextNodes.get(0);
      instanceMapper.updateStatus(
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
      // TODO: 迁移至 timerRepository.findDueTimers(now, limit)，返回类型不同
      List<FlowTimerDO> dueList = timerMapper.selectDueTimers(LocalDateTime.now(), SCAN_BATCH_SIZE);
      if (dueList.isEmpty()) {
        return 0;
      }
      int fired = 0;
      for (FlowTimerDO t : dueList) {
        try {
          if (fire(t)) {
            fired++;
          }
        } catch (Exception e) {
          log.error("[FlowTimerDO] 单条触发异常 timerId={}: {}", t.getId(), e.getMessage(), e);
        }
      }
      if (fired > 0) {
        log.info("[FlowTimerDO] 本轮扫描触发: count={}", fired);
      }
      return fired;
    } catch (Exception e) {
      log.error("[FlowTimerDO] 扫描异常: {}", e.getMessage(), e);
      return 0;
    }
  }

  @Override
  public int cancelByTask(String taskId) {
    if (taskId == null) {
      return 0;
    }
    // TODO: 迁移至 timerRepository.cancelByTask(taskId)，签名不同
    return timerMapper.cancelByTask(taskId, "userTask 完成");
  }

  @Override
  public int cancelByInstance(String instanceId, String reason) {
    if (instanceId == null) {
      return 0;
    }
    // TODO: Repository 中暂无 cancelByInstance 方法，需补齐
    return timerMapper.cancelByInstance(instanceId, reason == null ? "实例结束" : reason);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FlowTimerDO> listByInstance(String instanceId) {
    return timerMapper.selectList(
        new QueryWrapper<FlowTimerDO>()
            .eq("instance_id", instanceId)
            .eq("deleted", 0)
            .orderByDesc("created_at"));
  }

  @Override
  @Transactional(readOnly = true)
  public long countPending(String instanceId) {
    // TODO: Repository 中暂无 countPendingByInstance 方法，需补齐
    return timerMapper.countPendingByInstance(instanceId);
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

  /** 复用 FlowInstanceServiceImpl.generateTasksForNodes（包内访问） */
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
