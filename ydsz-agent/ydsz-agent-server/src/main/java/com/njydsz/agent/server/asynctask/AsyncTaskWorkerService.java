package com.njydsz.agent.server.asynctask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.asynctask.AsyncTask;
import com.njydsz.agent.domain.asynctask.AsyncTaskStore;
import com.njydsz.agent.domain.asynctask.AsyncTaskType;

/**
 * 异步任务 Worker 调度服务（简化版 Job）
 *
 * <p>使用 Spring {@link Scheduled} 定时轮询 PENDING 任务并认领执行。
 * 作为简化版实现，当前仅打印日志占位具体执行逻辑，预留 {@link AsyncTaskExecutor}
 * 扩展点供后续接入真实执行器（报告生成、文档摄入、批量对话等）。
 *
 * <p><b>调度策略</b>：
 * <ul>
 *   <li>pollAndExecute — 按配置的 fixedDelay 轮询 PENDING 任务（默认 5s），
 *       逐条 claim 并执行骨架逻辑</li>
 *   <li>releaseTimeout — 定期释放超时 RUNNING 任务（默认 30s），
 *       重新置为 PENDING 供其他 Worker 认领</li>
 * </ul>
 *
 * <p><b>扩展点</b>：实现 {@link AsyncTaskExecutor} 接口并在 {@link #executeTask} 中
 * 根据类型分发到对应执行器，即可完整实现异步处理流水线。
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

  /** Worker ID 随机后缀长度 */
  private static final int WORKER_ID_SUFFIX_LENGTH = 8;

  /** 占位进度值（骨架执行模拟用） */
  private static final int PLACEHOLDER_PROGRESS = 50;

  /** 超时阈值偏移量（秒）：RUNNING 任务超过此秒未完成视为超时 */
  private static final int TIMEOUT_OFFSET_SECONDS = 300;

  private final AsyncTaskStore taskStore;

  /**
   * 构造器注入异步任务存储网关并生成 Worker 标识。
   *
   * @param taskStore 异步任务存储接口
   */
  public AsyncTaskWorkerService(AsyncTaskStore taskStore) {
    this.taskStore = taskStore;
    this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, WORKER_ID_SUFFIX_LENGTH);
    log.info("[AsyncTask-Worker] Worker 初始化完成: workerId={}, storeType={}",
        workerId, taskStore.getType());
  }

  /**
   * 轮询并执行 PENDING 任务。
   *
   * <p>每 5000ms（5s）轮询一次待处理任务列表，逐条 claim 并执行骨架逻辑。
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
   * 处理单条任务：claim → 执行骨架 → 结果/异常处理。
   *
   * <p>若任务已被其他 Worker 认领（claim 返回 empty），则跳过。
   *
   * @param taskId 任务 ID
   */
  private void processTask(Long taskId) {
    Optional<AsyncTask> claimed = taskStore.claim(taskId, workerId);
    if (claimed.isEmpty()) {
      log.debug("[AsyncTask-Worker] 任务已被其他 Worker 认领，跳过: id={}", taskId);
      return;
    }
    AsyncTask task = claimed.get();
    log.info("[AsyncTask-Worker] 认领任务成功: id={}, type={}", taskId, task.getTaskType());

    try {
      executeTask(task);
    } catch (Exception e) {
      log.error("[AsyncTask-Worker] 任务执行异常: id={}, error={}", taskId, e.getMessage(), e);
      taskStore.findById(taskId).ifPresent(t -> {
        t.fail("执行异常: " + e.getMessage());
        taskStore.save(t);
      });
    }
  }

  /**
   * 执行任务骨架（预留扩展点）。
   *
   * <p>当前仅打印日志并模拟进度更新。完整实现应接入 {@link AsyncTaskExecutor}：
   *
   * <pre>{@code
   * AsyncTaskExecutor executor = executorRegistry.getExecutor(task.getTaskType());
   * executor.execute(task, progress -> taskStore.updateProgress(task.getId(), progress, workerId));
   * }</pre>
   *
   * @param task 已认领的任务实体
   */
  private void executeTask(AsyncTask task) {
    Long taskId = task.getId();
    String taskType = task.getTaskType();

    log.info("[AsyncTask-Worker] 开始执行任务: id={}, type={}, inputPayload={}",
        taskId, taskType, task.getInputPayload());

    // 打印日志占位，后续替换为真实执行器
    AsyncTaskType type = AsyncTaskType.fromCode(taskType);
    String typeDesc = type != null ? type.getDescription() : taskType;
    log.info("[AsyncTask-Worker] [占位] 任务执行骨架: id={}, type={}, desc={}",
        taskId, taskType, typeDesc);

    // 模拟进度更新 → 100% → 成功（骨架占位，实际应由 AsyncTaskExecutor 驱动进度）
    taskStore.updateProgress(taskId, PLACEHOLDER_PROGRESS, workerId);
    taskStore.findById(taskId).ifPresent(t -> {
      t.succeed("{\"result\":\"任务执行完成（占位）\"}");
      taskStore.save(t);
    });

    log.info("[AsyncTask-Worker] 任务执行完成(骨架): id={}, type={}", taskId, taskType);
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
