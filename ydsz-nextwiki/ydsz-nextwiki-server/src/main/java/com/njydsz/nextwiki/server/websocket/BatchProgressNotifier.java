package com.njydsz.nextwiki.server.websocket;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.socket.push.RealtimePushTemplate;

/**
 * 批量操作 WebSocket 进度推送器（S3-P2-04）。
 *
 * <p>封装批量任务执行过程中的实时进度推送，通过 ydsz-common-socket 的 {@link RealtimePushTemplate} 将进度消息推送给前端。
 *
 * <p><b>推送协议：</b>
 *
 * <pre>
 *   目标用户: userId
 *   消息类型: BATCH_PROGRESS
 *   消息格式: {
 *     "taskId": "123456789",
 *     "taskType": "batch_delete",
 *     "status": "RUNNING",
 *     "totalCount": 100,
 *     "processedCount": 45,
 *     "progress": 45.0,
 *     "currentItem": "file_node_123",
 *     "message": "正在删除..."
 *   }
 * </pre>
 *
 * <p><b>降级策略：</b>当 WebSocket 模块未引入时（{@link RealtimePushTemplate} 不可用），静默降级为 no-op，不影响批量任务正常执行。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class BatchProgressNotifier {

  /** WebSocket 推送模板（可选依赖，未引入时降级为 no-op） */
  private final ObjectProvider<RealtimePushTemplate> pushTemplateProvider;

  /** 消息类型常量 */
  public static final String TYPE_BATCH_PROGRESS = "BATCH_PROGRESS";

  /**
   * 构造方法注入。
   *
   * @param pushTemplateProvider WebSocket 推送模板提供者
   */
  public BatchProgressNotifier(ObjectProvider<RealtimePushTemplate> pushTemplateProvider) {
    this.pushTemplateProvider = pushTemplateProvider;
  }

  /**
   * 推送任务开始通知。
   *
   * @param userId 用户 ID
   * @param taskId 任务 ID
   * @param taskType 任务类型
   * @param totalCount 待处理总数
   */
  public void notifyTaskStarted(String userId, String taskId, String taskType, int totalCount) {
    Map<String, Object> payload = new HashMap<>(16);
    payload.put("taskId", taskId);
    payload.put("taskType", taskType);
    payload.put("status", "RUNNING");
    payload.put("totalCount", totalCount);
    payload.put("processedCount", 0);
    payload.put("progress", 0.0);
    payload.put("message", "任务开始执行");

    pushToUser(userId, payload);
  }

  /**
   * 推送进度更新。
   *
   * @param userId 用户 ID
   * @param taskId 任务 ID
   * @param taskType 任务类型
   * @param totalCount 待处理总数
   * @param processedCount 已处理数量
   * @param currentItem 当前处理项（可为 null）
   */
  public void notifyProgressUpdated(
      String userId,
      String taskId,
      String taskType,
      int totalCount,
      int processedCount,
      String currentItem) {

    double progress = totalCount > 0 ? (double) processedCount / totalCount * 100 : 0;

    Map<String, Object> payload = new HashMap<>(16);
    payload.put("taskId", taskId);
    payload.put("taskType", taskType);
    payload.put("status", "RUNNING");
    payload.put("totalCount", totalCount);
    payload.put("processedCount", processedCount);
    payload.put("progress", Math.min(progress, 100.0));
    payload.put("currentItem", currentItem);
    payload.put("message", String.format("已处理 %d/%d", processedCount, totalCount));

    pushToUser(userId, payload);
  }

  /**
   * 推送任务完成通知。
   *
   * @param userId 用户 ID
   * @param taskId 任务 ID
   * @param taskType 任务类型
   * @param totalCount 待处理总数
   * @param successCount 成功数量
   * @param failCount 失败数量
   */
  public void notifyTaskCompleted(
      String userId,
      String taskId,
      String taskType,
      int totalCount,
      int successCount,
      int failCount) {

    Map<String, Object> payload = new HashMap<>(16);
    payload.put("taskId", taskId);
    payload.put("taskType", taskType);
    payload.put("status", "COMPLETED");
    payload.put("totalCount", totalCount);
    payload.put("processedCount", totalCount);
    payload.put("progress", 100.0);
    payload.put("successCount", successCount);
    payload.put("failCount", failCount);
    payload.put("message", String.format("任务完成，成功 %d，失败 %d", successCount, failCount));

    pushToUser(userId, payload);
  }

  /**
   * 推送任务失败通知。
   *
   * @param userId 用户 ID
   * @param taskId 任务 ID
   * @param taskType 任务类型
   * @param errorMessage 错误信息
   */
  public void notifyTaskFailed(
      String userId, String taskId, String taskType, String errorMessage) {

    Map<String, Object> payload = new HashMap<>(16);
    payload.put("taskId", taskId);
    payload.put("taskType", taskType);
    payload.put("status", "FAILED");
    payload.put("message", "任务失败: " + errorMessage);

    pushToUser(userId, payload);
  }

  // ==================== 私有方法 ====================

  /**
   * 向指定用户推送消息（带离线补偿）。
   *
   * <p>WebSocket 模块未引入时静默降级。
   *
   * @param userId 用户 ID
   * @param payload 消息内容
   */
  private void pushToUser(String userId, Map<String, Object> payload) {
    RealtimePushTemplate pushTemplate = pushTemplateProvider.getIfAvailable();
    if (pushTemplate == null) {
      // WebSocket 模块未引入，降级为 no-op（仅记录调试日志）
      log.debug("[BatchProgressNotifier] WebSocket 模块未引入，跳过进度推送: userId={}", userId);
      return;
    }

    try {
      // 使用 taskId 作为消息 ID 实现幂等去重
      String messageId = payload.get("taskId") + "_" + payload.get("processedCount");
      pushTemplate.pushToUserWithOffline(userId, TYPE_BATCH_PROGRESS, payload, messageId);
      log.debug("[BatchProgressNotifier] 进度推送成功: userId={}, taskId={}, progress={}",
          userId, payload.get("taskId"), payload.get("progress"));
    } catch (Exception e) {
      // 推送失败不影响批量任务执行
      log.warn("[BatchProgressNotifier] 进度推送失败: userId={}, err={}", userId, e.getMessage());
    }
  }
}
