package com.njydsz.agent.infra.tool;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.code.CodeExecutionException;
import com.njydsz.agent.domain.code.CodeExecutionRequest;
import com.njydsz.agent.domain.code.CodeExecutionResult;
import com.njydsz.agent.domain.code.CodeExecutionService;
import com.njydsz.agent.domain.tool.Tool;
import com.njydsz.agent.domain.tool.ToolParam;
import com.njydsz.common.json.YdszJson;

/**
 * Python 代码执行工具包装类（注册到 ToolRegistry）。
 *
 * <p>允许 Agent 在沙箱中安全执行 Python 代码块，用于数据分析、统计计算和简单 ML 预测。
 * 通过 {@link Tool} 注解自动注册到工具注册中心，LLM 可直接调用。
 *
 * <p>安全特性由 {@link CodeExecutionService} 实现层保证（网络隔离、内存限制、模块白名单、超时等）。
 *
 * <p>当 {@link CodeExecutionService} Bean 不存在时不注册本工具（由 {@link ConditionalOnBean} 控制）。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeExecutionTool {

  /** 默认超时时间（秒） */
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;

  /** 最大超时时间（秒） */
  private static final int MAX_TIMEOUT_SECONDS = 60;

  /** 代码执行服务 */
  private final CodeExecutionService codeExecutionService;

  /**
   * 执行 Python 代码块（供 LLM 通过工具调用执行）。
   *
   * <p>通过环境变量 YDSZ_INPUT 向 Python 代码传递 JSON 输入数据。
   * 在 Python 中可通过以下方式读取：
   * <pre>{@code
   * import os, json
   * data = json.loads(os.environ.get("YDSZ_INPUT", "{}"))
   * }</pre>
   *
   * @param code Python 代码字符串（支持 import json, math, statistics 等白名单模块）
   * @param inputJson 可选 JSON 格式输入数据（通过环境变量 YDSZ_INPUT 传入 Python 代码）
   * @param timeoutSeconds 可选执行超时秒数（默认 30s，最大 60s）
   * @return JSON 格式执行结果
   */
  @Tool(
      name = "execute_python",
      description = "在沙箱中安全执行 Python 代码块。支持数据分析（统计、求和、均值等）、"
          + "日期计算、字符串处理、简单数学运算。"
          + "输入数据通过 inputJson 参数以 JSON 格式传入（Python 中读取 os.environ['YDSZ_INPUT']）。"
          + "可用模块: json, math, statistics, itertools, collections, datetime, functools, operator, re, string。"
          + "禁止网络访问、文件读写、import 非白名单模块。")
  public String executePython(
      @ToolParam("Python 代码字符串（必填），如: import statistics; data=json.loads(os.environ['YDSZ_INPUT']); print(statistics.mean(data['values']))")
          String code,
      @ToolParam("JSON 格式输入数据（可选），如: {\"values\": [1,2,3,4,5]}") String inputJson,
      @ToolParam("执行超时秒数（可选，默认30，最大60）") Integer timeoutSeconds) {

    if (code == null || code.isBlank()) {
      return YdszJson.toJson(Map.of("error", "代码不能为空"));
    }

    if (!codeExecutionService.isAvailable()) {
      return YdszJson.toJson(Map.of("error", "代码执行环境不可用，请检查配置"));
    }

    // 限制超时范围
    int timeout = DEFAULT_TIMEOUT_SECONDS;
    if (timeoutSeconds != null && timeoutSeconds > 0 && timeoutSeconds <= MAX_TIMEOUT_SECONDS) {
      timeout = timeoutSeconds;
    }

    log.info("[CodeExecutionTool] 执行 Python 代码: timeout={}s, inputProvided={}",
        timeout, inputJson != null && !inputJson.isBlank());

    try {
      CodeExecutionRequest request = new CodeExecutionRequest(
          code,
          inputJson,
          timeout,
          codeExecutionService.listAllowedModules());

      CodeExecutionResult result = codeExecutionService.execute(request);

      return YdszJson.toJson(Map.of(
          "success", result.success(),
          "output", result.output() != null ? result.output() : "",
          "exitCode", result.exitCode(),
          "durationMs", result.durationMs()));

    } catch (CodeExecutionException e) {
      log.warn("[CodeExecutionTool] 代码执行失败: {}", e.getMessage());
      return YdszJson.toJson(Map.of(
          "success", false,
          "error", e.getMessage(),
          "exitCode", -1));
    } catch (Exception e) {
      log.error("[CodeExecutionTool] 未知异常: {}", e.getMessage(), e);
      return YdszJson.toJson(Map.of(
          "success", false,
          "error", "代码执行异常: " + e.getMessage(),
          "exitCode", -1));
    }
  }
}
