package com.njydsz.common.util.exception;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 异常处理工具集 — 平台统一的堆栈/根因提取入口。
 *
 * <p>补位能力缺口（P2-4 整改）：此前业务模块（如 cronjob）为获取完整堆栈字符串
 * 直连 commons-lang3 {@code ExceptionUtils}，本类收敛该能力至 common-util，
 * 业务模块不再需要为单一工具引入 commons-lang3 依赖。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * log.error("job failed: {}", ExceptionUtils.getStackTrace(t));
 * log.warn("root cause: {}", ExceptionUtils.getRootCauseMessage(t));
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.14
 */
public final class ExceptionUtils {

  /** 工具类禁止实例化 */
  private ExceptionUtils() {
    throw new UnsupportedOperationException("utility class");
  }

  /**
   * 将异常完整堆栈转换为字符串。
   *
   * @param throwable 目标异常，不可为 null
   * @return 含全链路堆栈的多行字符串
   */
  public static String getStackTrace(Throwable throwable) {
    StringWriter sw = new StringWriter();
    try (PrintWriter pw = new PrintWriter(sw)) {
      throwable.printStackTrace(pw);
    }
    return sw.toString();
  }

  /**
   * 提取异常链的根因。
   *
   * <p>沿 {@code getCause()} 逐层下钻直至最底层；自引用环以首次出现的异常截断，
   * 保证不会死循环。
   *
   * @param throwable 目标异常，不可为 null
   * @return 根因异常（无 cause 时返回原异常）
   */
  public static Throwable getRootCause(Throwable throwable) {
    Throwable result = throwable;
    Throwable cause = result.getCause();
    while (cause != null && cause != result) {
      result = cause;
      cause = result.getCause();
    }
    return result;
  }

  /**
   * 提取根因的消息描述。
   *
   * @param throwable 目标异常，不可为 null
   * @return 根因 message；为空时返回根因类名，保证不为 null
   */
  public static String getRootCauseMessage(Throwable throwable) {
    Throwable rootCause = getRootCause(throwable);
    return rootCause.getMessage() != null
        ? rootCause.getMessage()
        : rootCause.getClass().getSimpleName();
  }
}
