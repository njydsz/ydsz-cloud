package com.njydsz.agent.server.asynctask;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.asynctask.AsyncTask;
import com.njydsz.agent.domain.code.CodeExecutionRequest;
import com.njydsz.agent.domain.code.CodeExecutionResult;
import com.njydsz.agent.domain.code.CodeExecutionService;
import com.njydsz.common.json.YdszJson;

/**
 * 代码沙箱执行异步任务执行器。
 *
 * <p>在隔离的 Python 沙箱中执行用户代码，适用于数据分析、统计计算等场景。
 * 仅当 CodeExecutionService Bean 存在时激活。
 *
 * <p>inputPayload 期望格式（JSON）：
 * <pre>{@code
 * {
 *   "code": "print('hello')",
 *   "inputJson": "{}",
 *   "timeoutSeconds": 30,
 *   "allowedModules": ["json", "math"]
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@Component
@ConditionalOnBean(CodeExecutionService.class)
public class CodeExecutionTaskExecutor implements AsyncTaskExecutor {

  /** 代码最大长度限制 */
  private static final int MAX_CODE_LENGTH = 50000;

  private final CodeExecutionService codeExecutionService;

  public CodeExecutionTaskExecutor(CodeExecutionService codeExecutionService) {
    this.codeExecutionService = codeExecutionService;
  }

  @Override
  public String supportedType() {
    return "CODE_EXECUTION";
  }

  @Override
  public void execute(AsyncTask task, Consumer<Integer> progressConsumer) {
    log.info("[AsyncTask:CODE_EXECUTION] 开始执行: taskId={}", task.getId());
    consumeProgress(progressConsumer, 5);

    try {
      Map<String, Object> params = parseInputPayload(task.getInputPayload());
      String code = getStringParam(params, "code");
      String inputJson = getStringParam(params, "inputJson");

      if (code == null || code.isBlank()) {
        task.fail("code 不能为空");
        return;
      }
      if (code.length() > MAX_CODE_LENGTH) {
        task.fail("代码长度超过限制（最大 " + MAX_CODE_LENGTH + " 字符）");
        return;
      }

      // 解析超时时间
      int timeoutSeconds = 30;
      Object timeoutObj = params.get("timeoutSeconds");
      if (timeoutObj instanceof Number num) {
        timeoutSeconds = num.intValue();
      }

      // 解析模块白名单
      @SuppressWarnings("unchecked")
      List<String> allowedModules = params.get("allowedModules") instanceof List<?> list
          ? list.stream().map(Object::toString).toList()
          : null;

      consumeProgress(progressConsumer, 10);
      log.info("[AsyncTask:CODE_EXECUTION] 执行代码: taskId={}, timeout={}s, codeLen={}",
          task.getId(), timeoutSeconds, code.length());

      CodeExecutionRequest request = new CodeExecutionRequest(
          code, inputJson, timeoutSeconds, allowedModules);

      CodeExecutionResult result = codeExecutionService.execute(request);

      if (result.success()) {
        task.succeed(YdszJson.toJson(Map.of(
            "success", true,
            "output", result.output() != null ? result.output() : "",
            "durationMs", result.durationMs())));
      } else {
        // 代码执行失败（业务层级），标记任务失败但携带 stdout 信息
        task.fail(result.error() != null ? result.error() : "代码执行失败");
        log.warn("[AsyncTask:CODE_EXECUTION] 代码执行失败: taskId={}, error={}",
            task.getId(), result.error());
      }

      consumeProgress(progressConsumer, 100);
      log.info("[AsyncTask:CODE_EXECUTION] 执行完成: taskId={}, success={}",
          task.getId(), result.success());
    } catch (Exception e) {
      log.error("[AsyncTask:CODE_EXECUTION] 执行失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
      task.fail("代码执行异常: " + truncate(e.getMessage(), 200));
    }
  }

  private Map<String, Object> parseInputPayload(String payload) {
    if (payload == null || payload.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> result = YdszJson.parseMap(payload);
      return result != null ? result : Map.of();
    } catch (Exception e) {
      log.warn("[AsyncTask:CODE_EXECUTION] 解析输入 JSON 失败: {}", e.getMessage());
      return Map.of();
    }
  }

  private String getStringParam(Map<String, Object> params, String key) {
    Object value = params.get(key);
    if (value == null) {
      return null;
    }
    String str = value.toString().trim();
    return str.isEmpty() ? null : str;
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
