package com.njydsz.agent.server.asynctask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.entity.AsyncTask;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.server.chat.TokenCostCalculator;
import com.njydsz.common.json.YdszJson;

/**
 * 批量对话异步任务执行器。
 *
 * <p>按配置批量向多用户/多会话发送消息并收集响应结果。
 * 适用于通知播报、定期报告推送等场景。
 *
 * <p>inputPayload 期望格式（JSON）：
 * <pre>{@code
 * {
 *   "conversations": [
 *     {"conversationId": "conv-001", "userId": "user-001"},
 *     {"conversationId": "conv-002", "userId": "user-002"}
 *   ],
 *   "systemPrompt": "系统提示词（可选）",
 *   "userMessage": "批量发送的消息内容",
 *   "model": "模型名称（可选）"
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@Component
public class BatchChatTaskExecutor implements AsyncTaskExecutor {

  /** 进度参数校验完成 */
  private static final int PROGRESS_PARAMS_READY = 5;

  private final TokenCostCalculator tokenCostCalculator;

  public BatchChatTaskExecutor(TokenCostCalculator tokenCostCalculator) {
    this.tokenCostCalculator = tokenCostCalculator;
  }

  @Override
  public String supportedType() {
    return "BATCH_CHAT";
  }

  @Override
  public void execute(AsyncTask task, Consumer<Integer> progressConsumer) {
    log.info("[AsyncTask:BATCH_CHAT] 开始执行: taskId={}", task.getId());
    consumeProgress(progressConsumer, PROGRESS_PARAMS_READY);

    try {
      Map<String, Object> params = parseInputPayload(task.getInputPayload());
      List<Map<String, Object>> conversations = getListParam(params, "conversations");
      String userMessage = getStringParam(params, "userMessage");
      String systemPrompt = getStringParam(params, "systemPrompt");
      String model = getStringParam(params, "model", "gpt-4o-mini");

      if (conversations == null || conversations.isEmpty()) {
        task.fail("conversations 不能为空");
        return;
      }
      if (userMessage == null || userMessage.isBlank()) {
        task.fail("userMessage 不能为空");
        return;
      }

      int total = conversations.size();
      int successCount = 0;
      int failCount = 0;
      long totalEstimatedTokens = 0;

      log.info("[AsyncTask:BATCH_CHAT] 开始批量处理: total={}", total);

      for (int i = 0; i < conversations.size(); i++) {
        Map<String, Object> conv = conversations.get(i);
        String conversationId = conv.get("conversationId") != null
            ? conv.get("conversationId").toString() : null;
        try {
          // 构建本轮请求
          ChatRequest request = buildChatRequest(userMessage, systemPrompt, model);

          // TODO: 实际部署时注入 ChatService 完成 LLM 调用
          // 当前骨架：估算 Token 成本并计入统计
          totalEstimatedTokens += tokenCostCalculator != null
              ? tokenCostCalculator.estimateBeforeCall(request).getEstimatedTotalTokens()
              : userMessage.length() / 4;
          successCount++;
        } catch (Exception e) {
          failCount++;
          log.warn("[AsyncTask:BATCH_CHAT] 会话 {} 处理失败: {}", conversationId, e.getMessage());
        }

        // 回传进度
        int progress = (int) ((i + 1) * 100.0 / total);
        if (progress > 100) {
          progress = 100;
        }
        consumeProgress(progressConsumer, progress);
      }

      task.succeed(YdszJson.toJson(Map.of(
          "total", total,
          "successCount", successCount,
          "failCount", failCount,
          "totalEstimatedTokens", totalEstimatedTokens)));

      log.info("[AsyncTask:BATCH_CHAT] 执行完成: taskId={}, total={}, success={}, fail={}",
          task.getId(), total, successCount, failCount);
    } catch (Exception e) {
      log.error("[AsyncTask:BATCH_CHAT] 执行失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
      task.fail("批量对话失败: " + truncate(e.getMessage(), 200));
    }
  }

  private ChatRequest buildChatRequest(String userMessage, String systemPrompt, String model) {
    List<com.njydsz.agent.domain.model.ChatMessage> messages = new ArrayList<>();
    if (systemPrompt != null && !systemPrompt.isBlank()) {
      messages.add(com.njydsz.agent.domain.model.ChatMessage.system(systemPrompt));
    }
    messages.add(com.njydsz.agent.domain.model.ChatMessage.user(userMessage, null));

    return ChatRequest.builder()
        .model(model)
        .messages(messages)
        .temperature(0.7)
        .maxTokens(2048)
        .build();
  }

  private Map<String, Object> parseInputPayload(String payload) {
    if (payload == null || payload.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> result = YdszJson.parseMap(payload);
      return result != null ? result : Map.of();
    } catch (Exception e) {
      log.warn("[AsyncTask:BATCH_CHAT] 解析输入 JSON 失败: {}", e.getMessage());
      return Map.of();
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> getListParam(Map<String, Object> params, String key) {
    Object value = params.get(key);
    if (value instanceof List<?> list) {
      return (List<Map<String, Object>>) value;
    }
    return null;
  }

  private String getStringParam(Map<String, Object> params, String key) {
    return getStringParam(params, key, null);
  }

  private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
    Object value = params.get(key);
    if (value == null) {
      return defaultValue;
    }
    String str = value.toString().trim();
    return str.isEmpty() ? defaultValue : str;
  }

  private void consumeProgress(Consumer<Integer> progressConsumer, int percent) {
    if (progressConsumer != null) {
      progressConsumer.accept(percent);
    }
  }

  private static String truncate(String str, int maxLength) {
    if (str == null) {
      return "null";
    }
    return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
  }
}
