package com.njydsz.agent.domain.code;

import java.util.List;

/**
 * 代码执行请求值对象（不可变 record）。
 *
 * <p>描述一次 Python 代码块沙箱执行的完整输入，包括代码本身、可选输入数据、超时配置和模块白名单。
 *
 * @param code Python 代码字符串
 * @param inputJson 可选 JSON 输入数据
 * @param timeoutSeconds 超时秒数
 * @param allowedModules 允许 import 的模块白名单
 * @author ydsz-team
 * @since 26.09.07
 */
public record CodeExecutionRequest(
    String code,
    String inputJson,
    int timeoutSeconds,
    List<String> allowedModules) {

  /** 默认超时时间（秒） */
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;

  /** 默认允许 import 的模块白名单 */
  private static final List<String> DEFAULT_ALLOWED_MODULES =
      List.of("json", "math", "statistics", "itertools", "collections", "datetime",
          "functools", "operator", "re", "string");

  /**
   * 简化构造：仅传入代码字符串，其他参数使用默认值。
   *
   * @param code Python 代码字符串
   */
  public CodeExecutionRequest(String code) {
    this(code, null, DEFAULT_TIMEOUT_SECONDS, DEFAULT_ALLOWED_MODULES);
  }

  /**
   * 全参数构造。
   *
   * @param code Python 代码字符串
   * @param inputJson 可选 JSON 输入数据
   * @param timeoutSeconds 超时秒数
   * @param allowedModules 允许 import 的模块白名单
   */
  public CodeExecutionRequest {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("代码不能为空");
    }
    if (timeoutSeconds <= 0) {
      timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    }
    if (allowedModules == null || allowedModules.isEmpty()) {
      allowedModules = DEFAULT_ALLOWED_MODULES;
    }
  }
}
