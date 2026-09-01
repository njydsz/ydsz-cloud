package com.njydsz.workflow.server.service.impl.instance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
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
 * <p>支持最大 500 条/批，避免单次请求过大。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskBatchServiceImpl {

  /** 单条任务完成服务（每条操作独立事务） */
  private final FlowTaskCompleteServiceImpl completeService;

  /** 批量操作默认并发度限制 */
  private static final int DEFAULT_MAX_CONCURRENCY = 10;

  /** 单次批量操作的任务数量上限 */
  private static final int BATCH_TASK_ID_LIMIT = 500;

  /**
   * P2-26: 批量审批 — 对多个任务逐一执行 pass，每条独立事务。
   *
   * <p>单个任务失败不影响其他任务，返回成功/失败明细。
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

    int successCount = 0;
    List<Map<String, Object>> failedItems = new ArrayList<>();

    for (int i = 0; i < taskIds.size(); i++) {
      String taskId = taskIds.get(i);
      try {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(taskId);
        dto.setUserId(userId);
        dto.setComment(comment);
        dto.setAction("PASS");
        completeService.pass(dto);
        successCount++;
      } catch (Exception e) {
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("index", i + 1);
        fail.put("taskId", taskId);
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        fail.put("reason", reason);
        failedItems.add(fail);
        log.warn("[Flow] 批量审批第 {} 条失败: taskId={} reason={}", i + 1, taskId, reason);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("successCount", successCount);
    result.put("failedCount", failedItems.size());
    result.put("failedItems", failedItems);
    log.info("[Flow] 批量审批完成: total={} success={} failed={}", taskIds.size(), successCount,
        failedItems.size());
    return result;
  }

  /**
   * P1-4: 批量驳回 — 对多个任务逐一执行 reject，每条独立事务。
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

    int successCount = 0;
    List<Map<String, Object>> failedItems = new ArrayList<>();

    for (int i = 0; i < taskIds.size(); i++) {
      String taskId = taskIds.get(i);
      try {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(taskId);
        dto.setUserId(userId);
        dto.setComment(comment);
        dto.setAction("REJECT");
        dto.setTargetNodeCode(targetNodeCode);
        completeService.reject(dto);
        successCount++;
      } catch (Exception e) {
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("index", i + 1);
        fail.put("taskId", taskId);
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        fail.put("reason", reason);
        failedItems.add(fail);
        log.warn("[Flow] 批量驳回第 {} 条失败: taskId={} reason={}", i + 1, taskId, reason);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("successCount", successCount);
    result.put("failedCount", failedItems.size());
    result.put("failedItems", failedItems);
    log.info("[Flow] 批量驳回完成: total={} success={} failed={}", taskIds.size(), successCount,
        failedItems.size());
    return result;
  }

  /**
   * P1-4: 批量转办 — 对多个任务逐一执行 transfer，每条独立事务。
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

    int successCount = 0;
    List<Map<String, Object>> failedItems = new ArrayList<>();

    for (int i = 0; i < taskIds.size(); i++) {
      String taskId = taskIds.get(i);
      try {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(taskId);
        dto.setUserId(userId);
        dto.setComment(comment);
        dto.setAction("TRANSFER");
        dto.setTargetUserId(targetUserId);
        dto.setTargetUserName(targetUserName);
        completeService.transfer(dto);
        successCount++;
      } catch (Exception e) {
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("index", i + 1);
        fail.put("taskId", taskId);
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        fail.put("reason", reason);
        failedItems.add(fail);
        log.warn("[Flow] 批量转办第 {} 条失败: taskId={} reason={}", i + 1, taskId, reason);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("successCount", successCount);
    result.put("failedCount", failedItems.size());
    result.put("failedItems", failedItems);
    log.info("[Flow] 批量转办完成: total={} success={} failed={}", taskIds.size(), successCount,
        failedItems.size());
    return result;
  }

  /**
   * P1-4: 批量催办 — 对多个实例逐一执行 urge。
   *
   * <p>批量催办不使用 @Transactional（催办无数据库写操作，仅发送通知），
   * 单个实例催办失败不影响其他实例，失败记录日志后继续。
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
    int success = 0;
    for (String instanceId : instanceIds) {
      try {
        completeService.urge(instanceId, operatorId, comment);
        success++;
      } catch (Exception e) {
        log.warn("[Flow] 批量催办单条失败（继续处理其他）: instanceId={} err={}", instanceId,
            e.getMessage());
      }
    }
    log.info("[Flow] 批量催办完成: operatorId={} success={}/{}", operatorId, success,
        instanceIds.size());
    return success;
  }
}
