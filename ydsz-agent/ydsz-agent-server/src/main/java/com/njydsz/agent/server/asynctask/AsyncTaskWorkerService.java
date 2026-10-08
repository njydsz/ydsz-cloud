package com.njydsz.agent.server.asynctask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.asynctask.AsyncTaskStatus;
import com.njydsz.agent.domain.asynctask.AsyncTaskStore;
import com.njydsz.agent.domain.asynctask.AsyncTaskType;
import com.njydsz.agent.domain.entity.AsyncTask;
import com.njydsz.common.notify.helper.NotifyHelper;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 异步任务 Worker 调度服务。
 *
 * <p>使用 Spring {@link Scheduled} 定时轮询 PENDING 任务并认领执行。
 * 通过 {@link AsyncTaskExecutorRegistry} 将任务分发到对应类型的执行器。
 *
 * <p><b>调度策略</b>：
 * <ul>
 *   <li>pollAndExecute — 按配置的 fixedDelay 轮询 PENDING 任务（默认 5s），
 *       逐条 claim 并分发给 {@link AsyncTaskExecutor} 执行</li>
 *   <li>releaseTimeout — 定期释放超时 RUNNING 任务（默认 30s），
 *       重新置为 PENDING 供其他 Worker 认领</li>
 * </ul>
 *
 * <p><b>扩展点</b>：实现 {@link AsyncTaskExecutor} 接口并声明为 Spring Bean，
 * 即可自动注册到 {@link AsyncTaskExecutorRegistry} 中参与任务分发。
 *
 * <p><b>部署注意</b>：多副本部署时，需启用分布式锁或借助数据库乐观锁防止重复执行。
 * 当前 JdbcAsyncTaskStore 的 claimTask 已使用乐观锁，可安全多副本运行。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Component
public class AsyncTaskWorkerService {

  /** Worker 标识（基于 UUID 唯一标识当前实例） */
  private final String workerId;

  /** 单次轮询最大任务数 */
  private static final int POLL_LIMIT = 10;

  /** 超时阈值偏移量（秒）：RUNNING 任务超过此秒未完成视为超时 */
  private static final int TIMEOUT_OFFSET_SECONDS = 300;

  private final AsyncTaskStore taskStore;
  private final AsyncTaskExecutorRegistry executorRegistry;
  private final NotifyHelper notifyHelper;

  /**
   * 构造器注入异步任务存储网关、任务执行器注册表和通知辅助工具。
   *
   * @param taskStore        异步任务存储接口
   * @param executorRegistry 任务执行器注册表
   * @param notifyHelper     统一通知辅助类
   */
  public AsyncTaskWorkerService(AsyncTaskStore taskStore,
                                 AsyncTaskExecutorRegistry executorRegistry,
                                 NotifyHelper notifyHelper) {
    this.taskStore = taskStore;
    this.executorRegistry = executorRegistry;
    this.notifyHelper = notifyHelper;
    this.workerId = "worker-" + IdGenerator.nextIdStr();
    log.info("[AsyncTask-Worker] Worker 初始化完成: workerId={}, storeType={}",
        workerId, taskStore.getType());
  }

  /**
   * 轮询并执行 PENDING 任务。
   *
   * <p>每 5000ms（5s）轮询一次待处理任务列表，逐条 claim 并分发给对应执行器。
   * 异常隔离：单条任务执行失败不影响其他任务处理。
   *
   * <p>fixedDelay 保证上次执行完毕后等待指定间隔再开始下一次，
   * 避免与长时间运行的任务产生并发冲突。
   */
  @Scheduled(fixedDelayString = "${ydsz.agent.async-task.poll-interval-ms:5000}")
  public void pollAndExecute() {
    try {
      List<AsyncTask> pendingTasks = taskStore.pollPending(null, POLL_LIMIT);
      if (pendingTasks == null || pendingTasks.isEmpty()) {
        return;
      }
      log.debug("[AsyncTask-Worker] 轮询到 {} 条待处理任务", pendingTasks.size());

      for (AsyncTask task : pendingTasks) {
        if (task == null || task.getId() == null) {
          continue;
        }
        processTask(task.getId());
      }
    } catch (Exception e) {
      log.error("[AsyncTask-Worker] 轮询任务异常: {}", e.getMessage(), e);
    }
  }

  /**
   * 释放超时 RUNNING 任务。
   *
   * <p>每 30000ms（30s）扫描一次 RUNNING 状态任务，将超过
   * {@link #TIMEOUT_OFFSET_SECONDS} 未完成的任务重置为 PENDING。
   *
   * <p>使用 LocalDateTime.now().minusSeconds 作为阈值：
   * expireAt 早于此阈值的 RUNNING 任务将被重置。
   */
  @Scheduled(fixedDelayString = "${ydsz.agent.async-task.timeout-check-interval-ms:30000}")
  public void releaseTimeout() {
    try {
      LocalDateTime threshold = LocalDateTime.now().minusSeconds(TIMEOUT_OFFSET_SECONDS);
      int released = taskStore.releaseTimeoutTasks(threshold);
      if (released > 0) {
        log.info("[AsyncTask-Worker] 释放超时任务完成: count={}", released);
      }
    } catch (Exception e) {
      log.error("[AsyncTask-Worker] 释放超时任务异常: {}", e.getMessage(), e);
    }
  }

  /**
   * 处理单条任务：claim → 查找执行器 → 执行 → 结果/异常处理。
   *
   * <p>若任务已被其他 Worker 认领（claim 返回 empty），则跳过。
   * 若无匹配的执行器，则任务标记失败（需添加对应 AsyncTaskExecutor 实现）。
   *
   * @param taskId 任务 ID
   */
  private void processTask(String taskId) {
    Optional<AsyncTask> claimed = taskStore.claim(taskId, workerId);
    if (claimed.isEmpty()) {
      log.debug("[AsyncTask-Worker] 任务已被其他 Worker 认领，跳过: id={}", taskId);
      return;
    }
    AsyncTask task = claimed.get();
    String taskType = task.getTaskType();
    log.info("[AsyncTask-Worker] 认领任务成功: id={}, type={}", taskId, taskType);

    // 查找对应执行器
    Optional<AsyncTaskExecutor> executorOpt = executorRegistry.findExecutor(taskType);
    if (executorOpt.isEmpty()) {
      log.error("[AsyncTask-Worker] 未找到任务类型的执行器: id={}, type={}", taskId, taskType);
      task.fail("未找到任务类型 " + taskType + " 对应的执行器");
      taskStore.save(task);
      // 系统告警：执行器缺失属于运维问题
      sendSystemAlertOrLog(taskId, taskType, task.getUserId(), "执行器缺失");
      return;
    }

    AsyncTaskExecutor executor = executorOpt.get();
    try {
      // 构建进度回调：将执行器的进度更新同步到任务存储
      Consumer<Integer> progressConsumer = percent -> {
        try {
          taskStore.updateProgress(taskId, percent, workerId);
        } catch (Exception e) {
          log.warn("[AsyncTask-Worker] 更新进度失败: id={}, percent={}, error={}",
              taskId, percent, e.getMessage());
        }
      };

      executor.execute(task, progressConsumer);

      // 持久化任务最终状态（执行器内部会更新 task 状态，但需要显式保存）
      taskStore.save(task);

      // 任务到达终态时发送站内信通知
      sendTaskCompletionNotification(task);

      log.info("[AsyncTask-Worker] 任务执行完成: id={}, type={}, status={}",
          taskId, taskType, task.getStatus());
    } catch (Exception e) {
      log.error("[AsyncTask-Worker] 任务执行异常: id={}, error={}", taskId, e.getMessage(), e);
      taskStore.findById(taskId).ifPresent(t -> {
        t.fail("执行异常: " + e.getMessage());
        taskStore.save(t);
        // 异常导致失败时发送通知
        sendTaskCompletionNotification(t);
      });
    }
  }

  /**
   * 任务到达终态时向触发用户发送站内信通知。
   *
   * <p>仅对 SUCCEEDED 和 FAILED（终态）发送通知，避免用户被中间状态打扰。
   * 通知发送异常不影响主流程（NotifyHelper 内部已做异常隔离）。
   *
   * @param task 已执行完毕的任务实体
   */
  private void sendTaskCompletionNotification(AsyncTask task) {
    if (task == null || task.getUserId() == null || task.getUserId().isBlank()) {
      return;
    }

    String status = task.getStatus();
    String taskType = task.getTaskType();
    String taskIdStr = task.getId() != null ? task.getId().toString() : "unknown";

    if (AsyncTaskStatus.SUCCEEDED.getCode().equals(status)) {
      String taskDesc = resolveTaskTypeDescription(taskType);
      notifyHelper.sendInApp(task.getUserId(),
          "任务完成",
          String.format("您的「%s」任务已完成，任务ID: %s", taskDesc, taskIdStr));
    } else if (AsyncTaskStatus.FAILED.getCode().equals(status) && !task.isRetryable()) {
      String taskDesc = resolveTaskTypeDescription(taskType);
      notifyHelper.sendInApp(task.getUserId(),
          "任务失败",
          String.format("您的「%s」任务执行失败，任务ID: %s，失败原因: %s",
              taskDesc, taskIdStr, task.getErrorMessage()));
    }
  }

  /**
   * 解析任务类型编码为可读描述。
   *
   * @param taskType 任务类型编码
   * @return 可读描述（如 "洞察报告生成"、"文档摄入"），未知类型返回编码本身
   */
  private String resolveTaskTypeDescription(String taskType) {
    AsyncTaskType type = AsyncTaskType.fromCode(taskType);
    if (type == null) {
      return taskType;
    }
    return switch (type) {
      case REPORT_GENERATE -> "洞察报告生成";
      case DOC_INGEST -> "文档摄入";
      case BATCH_CHAT -> "批量对话";
      case CODE_EXECUTION -> "代码执行";
    };
  }

  /**
   * 发送系统告警通知（运维异常场景）。
   *
   * <p>当任务缺少对应执行器等系统级问题时，发送系统告警以便运维人员及时处理。
   *
   * @param taskId   任务 ID
   * @param taskType 任务类型
   * @param userId   关联用户 ID（可能为空）
   * @param reason   告警原因简述
   */
  private void sendSystemAlertOrLog(String taskId, String taskType, String userId, String reason) {
    if (userId != null && !userId.isBlank()) {
      notifyHelper.sendSystemAlert(
          "异步任务执行异常",
          String.format("任务ID: %s，类型: %s，原因: %s", taskId, taskType, reason),
          userId);
    } else {
      notifyHelper.sendSystemAlert(
          "异步任务执行异常",
          String.format("任务ID: %s，类型: %s，原因: %s", taskId, taskType, reason));
    }
  }

  /**
   * 获取当前 Worker 标识。
   *
   * @return Worker 唯一标识
   */
  public String getWorkerId() {
    return workerId;
  }
}
