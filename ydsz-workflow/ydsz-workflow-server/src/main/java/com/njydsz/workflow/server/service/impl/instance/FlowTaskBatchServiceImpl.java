package com.njydsz.workflow.server.service.impl.instance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;

/**
 * 流程任务批量操作服务实现。
 *
 * <p>提供任务级别的批量操作：批量同意、批量拒绝、批量转办、批量催办。
 *
 * <p><b>事务策略（P1 优化）：</b>本类方法<b>不开启 {@code @Transactional}</b>，而是通过委托
 * {@link FlowTaskCompleteServiceImpl} 执行单条操作（每条独立事务），避免长事务锁等待。
 * 单个任务失败不影响其他任务，返回成功/失败明细。
 *
 * <p><b>并发策略：</b>批量操作使用 {@link ExecutorUtils#newVirtualThreadExecutor(String)} 虚拟线程池并发执行，
 * 每条任务在独立虚拟线程中运行（IO 密集型场景适合虚拟线程）。虚拟线程由 JVM 调度，可创建数百万个而不耗尽系统线程资源。
 * 各事务通过 HikariCP 连接池自然限流，无需额外并发度控制。
 *
 * <p>支持最大 500 条/批，避免单次请求过大。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskBatchServiceImpl {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;


  /** 单条任务完成服务（每条操作独立事务） */
  private final FlowTaskCompleteServiceImpl completeService;

  /** 虚拟线程执行器：每任务一线程，IO 密集型场景下可充分利用数据库连接池和外部 HTTP 调用 */
  private static final ExecutorService VIRTUAL_EXECUTOR =
      ExecutorUtils.newVirtualThreadExecutor("flow-batch-");

  /** 单次批量操作的任务数量上限 */
  private static final int BATCH_TASK_ID_LIMIT = 500;

  /**
   * P2-26: 批量审批 — 虚拟线程并发执行 pass，每条独立事务。
   *
   * <p>使用虚拟线程池并发处理全部任务，单任务 IO 等待（DB / 流程推进）期间不阻塞载体线程。
   * 单个任务失败不影响其他任务，返回成功/失败明细。
   *
   * @param taskIds 任务 ID 列表
   * @param userId 操作人 ID
   * @param comment 审批意见
   * @return 批量操作结果，包含 successCount / failedCount / failedItems
   */
  public Map<String, Object> batchPass(List<String> taskIds, String userId, String comment) {
    if (taskIds == null || taskIds.isEmpty()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.task.batch.empty")
          .build();
    }
    if (taskIds.size() > BATCH_TASK_ID_LIMIT) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.batch.size.exceeded")
          .params(taskIds.size(), BATCH_TASK_ID_LIMIT)
          .build();
    }

    // 将每条审批任务提交到虚拟线程池，全部提交后再统一 join 收集结果
    List<CompletableFuture<Map<String, Object>>> futures = IntStream.range(0, taskIds.size())
        .mapToObj(i -> CompletableFuture.supplyAsync(
            () -> executeSinglePass(taskIds.get(i), i + 1, userId, comment),
            VIRTUAL_EXECUTOR))
        .toList();

    return collectBatchResult(futures, taskIds.size(), "批量审批");
  }

  /**
   * 执行单条任务审批，返回结果 Map。
   *
   * <p>成功时返回 {"success": true, "index": N, "taskId": "xxx"}，
   * 失败时返回 {"success": false, "index": N, "taskId": "xxx", "reason": "..."}。
   *
   * @param taskId 任务 ID
   * @param index 1-based 序号
   * @param userId 操作人 ID
   * @param comment 审批意见
   * @return 结果 Map
   */
  private Map<String, Object> executeSinglePass(
      String taskId, int index, String userId, String comment) {
    try {
      FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
      dto.setTaskId(taskId);
      dto.setUserId(userId);
      dto.setComment(comment);
      dto.setAction("PASS");
      completeService.pass(dto);
      log.info("[Flow] 批量审批第 {} 条成功: taskId={}", index, taskId);
      return Map.of("success", true, "index", index, "taskId", taskId);
    } catch (Exception e) {
      String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.warn("[Flow] 批量审批第 {} 条失败: taskId={} reason={}", index, taskId, reason);
      return Map.of("success", false, "index", index, "taskId", taskId, "reason", reason);
    }
  }

  /**
   * P1-4: 批量驳回 — 虚拟线程并发执行 reject，每条独立事务。
   *
   * <p>批量驳回时所有任务使用相同的退回目标节点（targetNodeCode）和审批意见，
   * 单个任务失败不影响其他任务。
   *
   * @param taskIds 任务 ID 列表
   * @param userId 操作人 ID
   * @param comment 审批意见
   * @param targetNodeCode 退回目标节点编码（可选，为空时走默认退回逻辑）
   * @return 批量操作结果，包含 successCount / failedCount / failedItems
   */
  public Map<String, Object> batchReject(
      List<String> taskIds, String userId, String comment, String targetNodeCode) {
    if (taskIds == null || taskIds.isEmpty()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.task.batch.empty")
          .build();
    }
    if (taskIds.size() > BATCH_TASK_ID_LIMIT) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.batch.size.exceeded")
          .params(taskIds.size(), BATCH_TASK_ID_LIMIT)
          .build();
    }

    // 将每条驳回任务提交到虚拟线程池，全部提交后再统一 join 收集结果
    List<CompletableFuture<Map<String, Object>>> futures = IntStream.range(0, taskIds.size())
        .mapToObj(i -> CompletableFuture.supplyAsync(
            () -> executeSingleReject(taskIds.get(i), i + 1, userId, comment, targetNodeCode),
            VIRTUAL_EXECUTOR))
        .toList();

    return collectBatchResult(futures, taskIds.size(), "批量驳回");
  }

  /**
   * 执行单条任务驳回，返回结果 Map。
   */
  private Map<String, Object> executeSingleReject(
      String taskId, int index, String userId, String comment, String targetNodeCode) {
    try {
      FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
      dto.setTaskId(taskId);
      dto.setUserId(userId);
      dto.setComment(comment);
      dto.setAction("REJECT");
      dto.setTargetNodeCode(targetNodeCode);
      completeService.reject(dto);
      log.info("[Flow] 批量驳回第 {} 条成功: taskId={}", index, taskId);
      return Map.of("success", true, "index", index, "taskId", taskId);
    } catch (Exception e) {
      String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.warn("[Flow] 批量驳回第 {} 条失败: taskId={} reason={}", index, taskId, reason);
      return Map.of("success", false, "index", index, "taskId", taskId, "reason", reason);
    }
  }

  /**
   * P1-4: 批量转办 — 虚拟线程并发执行 transfer，每条独立事务。
   *
   * <p>批量转办时所有任务转给同一目标人，单个任务失败不影响其他任务。
   *
   * @param taskIds 任务 ID 列表
   * @param userId 操作人 ID
   * @param comment 转办说明
   * @param targetUserId 目标人 ID
   * @param targetUserName 目标人姓名
   * @return 批量操作结果，包含 successCount / failedCount / failedItems
   */
  public Map<String, Object> batchTransfer(
      List<String> taskIds,
      String userId,
      String comment,
      String targetUserId,
      String targetUserName) {
    if (taskIds == null || taskIds.isEmpty()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.task.batch.empty")
          .build();
    }
    if (taskIds.size() > BATCH_TASK_ID_LIMIT) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.batch.size.exceeded")
          .params(taskIds.size(), BATCH_TASK_ID_LIMIT)
          .build();
    }

    // 将每条转办任务提交到虚拟线程池，全部提交后再统一 join 收集结果
    List<CompletableFuture<Map<String, Object>>> futures = IntStream.range(0, taskIds.size())
        .mapToObj(i -> CompletableFuture.supplyAsync(
            () -> executeSingleTransfer(
                taskIds.get(i), i + 1, userId, comment, targetUserId, targetUserName),
            VIRTUAL_EXECUTOR))
        .toList();

    return collectBatchResult(futures, taskIds.size(), "批量转办");
  }

  /**
   * 执行单条任务转办，返回结果 Map。
   */
  private Map<String, Object> executeSingleTransfer(
      String taskId, int index, String userId, String comment,
      String targetUserId, String targetUserName) {
    try {
      FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
      dto.setTaskId(taskId);
      dto.setUserId(userId);
      dto.setComment(comment);
      dto.setAction("TRANSFER");
      dto.setTargetUserId(targetUserId);
      dto.setTargetUserName(targetUserName);
      completeService.transfer(dto);
      log.info("[Flow] 批量转办第 {} 条成功: taskId={}", index, taskId);
      return Map.of("success", true, "index", index, "taskId", taskId);
    } catch (Exception e) {
      String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.warn("[Flow] 批量转办第 {} 条失败: taskId={} reason={}", index, taskId, reason);
      return Map.of("success", false, "index", index, "taskId", taskId, "reason", reason);
    }
  }

  /**
   * P1-4: 批量催办 — 虚拟线程并发执行 urge。
   *
   * <p>批量催办不使用 @Transactional（催办无数据库写操作，仅发送通知），
   * 单个实例催办失败不影响其他实例，失败记录日志后继续。
   * 催办为 IO 密集型（外部通知服务调用），非常适合虚拟线程并发。
   *
   * @param instanceIds 实例 ID 列表
   * @param operatorId 操作人 ID
   * @param comment 催办说明
   * @return 成功催办的实例数量
   */
  public int batchUrge(List<String> instanceIds, String operatorId, String comment) {
    if (instanceIds == null || instanceIds.isEmpty()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.task.batch.empty")
          .build();
    }
    // 将每个催办提交到虚拟线程池，并发发送通知
    List<CompletableFuture<Integer>> futures = instanceIds.stream()
        .map(instanceId -> CompletableFuture.supplyAsync(
            () -> executeSingleUrge(instanceId, operatorId, comment),
            VIRTUAL_EXECUTOR))
        .toList();

    // 等待全部完成并统计成功数
    return futures.stream()
        .mapToInt(CompletableFuture::join)
        .sum();
  }

  /**
   * 执行单条催办，返回是否成功（1/0 用于求和）。
   */
  private int executeSingleUrge(String instanceId, String operatorId, String comment) {
    try {
      completeService.urge(instanceId, operatorId, comment);
      return 1;
    } catch (Exception e) {
      log.warn("[Flow] 批量催办单条失败（继续处理其他）: instanceId={} err={}", instanceId,
          e.getMessage());
      return 0;
    }
  }

  /**
   * 收集批量操作结果，统一汇总 success/failed 明细。
   *
   * <p>使用 {@link CompletableFuture#join()} 等待全部任务完成（已经在执行中），
   * 然后按成功/失败分类，组装返回结果 Map。
   *
   * @param futures Future 列表
   * @param total 总任务数
   * @param actionName 操作名称（用于日志）
   * @return 批量操作结果 Map，含 successCount / failedCount / failedItems
   */
  private Map<String, Object> collectBatchResult(
      List<CompletableFuture<Map<String, Object>>> futures, int total, String actionName) {
    int successCount = 0;
    List<Map<String, Object>> failedItems = new ArrayList<>(COLLECTION_CAPACITY);
    for (CompletableFuture<Map<String, Object>> future : futures) {
      Map<String, Object> item = future.join();
      if (Boolean.TRUE.equals(item.get("success"))) {
        successCount++;
      } else {
        Map<String, Object> fail = new LinkedHashMap<>(COLLECTION_CAPACITY);
        fail.put("index", item.get("index"));
        fail.put("taskId", item.get("taskId"));
        fail.put("reason", item.get("reason"));
        failedItems.add(fail);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>(COLLECTION_CAPACITY);
    result.put("successCount", successCount);
    result.put("failedCount", failedItems.size());
    result.put("failedItems", failedItems);
    log.info("[Flow] {}完成: total={} success={} failed={}", actionName, total, successCount,
        failedItems.size());
    return result;
  }
}
