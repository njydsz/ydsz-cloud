package com.njydsz.common.excel.spring;

import com.njydsz.common.excel.exception.ExcelException;
import com.njydsz.common.excel.exception.ExcelExceptionCode;
import com.njydsz.common.excel.exception.ExcelReadException;
import com.njydsz.common.excel.exception.ExcelWriteException;
import com.njydsz.common.excel.util.ExcelI18nHelper;

/**
 * 全局异常体系适配器 —— 将 Excel 模块自包含的异常 / 异常码桥接到全局错误处理。
 *
 * <p><b>注意</b>：ydsz-common-excel 是 L1 工具模块，不可反向依赖业务模块的 "全局 ErrorCode 接口"。
 * 本适配器的设计原则：
 *
 * <ul>
 *   <li>仅提供 <b>只读</b> 访问方法（getCode/getKey/getHttpStatus），不要求实现接口</li>
 *   <li>通过 {@link ExcelI18nHelper} 解析消息的 i18n 文本，全局处理器可直接使用 {@link
 *       #resolveMessage(ExcelException)}</li>
 *   <li>兼容性：{@code null} 输入、非 Excel 异常均安全返回默认值</li>
 * </ul>
 *
 * <h3>使用示例（全局 ControllerAdvice 中）</h3>
 *
 * <pre>{@code
 * @ExceptionHandler(ExcelException.class)
 * public ResponseEntity<ApiResult<?>> handleExcel(ExcelException ex, HttpServletRequest req) {
 *     Locale locale = RequestContextUtils.getLocale(req);
 *     String msg = ExcelExceptionAdapter.resolveMessage(ex, locale);
 *     return ResponseEntity
 *         .status(ExcelExceptionAdapter.getHttpStatus(ex))
 *         .body(ApiResult.fail(ExcelExceptionAdapter.getCode(ex), msg));
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class ExcelExceptionAdapter {

  private static final int DEFAULT_HTTP_STATUS = 400;

  private ExcelExceptionAdapter() {}

  /**
   * 获取 Excel 异常的机器可读错误码（如"H01002"）。
   *
   * @param ex 异常；非 {@link ExcelException} 时返回 {@code "UNKNOWN"}
   * @return 错误码字符串
   */
  public static String getCode(Throwable ex) {
    ExcelExceptionCode code = getExceptionCode(ex);
    return code != null ? code.getCode() : "UNKNOWN";
  }

  /**
   * 获取 Excel 异常的本地化消息。
   *
   * <p>解析逻辑：
   * <ol>
   *   <li>若 {@link ExcelException#getMessage()} 为 null/空，走 i18n key 解析</li>
   *   <li>否则返回 {@code getMessage()}（通常已携带上下文细节，如"文件路径不存在：/data/x.xlsx"）</li>
   *   <li>i18n 解析：委托 {@link ExcelI18nHelper#getMessage(String, Object[],
   *       String)}，MessageUtils 不可用时回退到 messageKey</li>
   * </ol>
   *
   * @param ex Excel 异常
   * @param locale 请求 Locale（可为 null，此时使用系统默认）
   * @return 解析后的本地化消息；null 安全
   */
  public static String resolveMessage(ExcelException ex, java.util.Locale locale) {
    if (ex == null) {
      return null;
    }
    String direct = ex.getMessage();
    if (direct != null && !direct.isEmpty()) {
      return direct;
    }
    String key = ex.getMessageKey();
    Object[] ctx = ex.getContext();
    return ExcelI18nHelper.getMessage(key, ctx, key);
  }

  /**
   * 获取此 Excel 异常对应的推荐 HTTP 状态码。
   *
   * <ul>
   *   <li>读取 / 写入 / 转换异常 → 400</li>
   *   <li>配置异常 → 500</li>
   * </ul>
   *
   * @param ex 异常；非 Excel 异常时返回 400
   * @return HTTP 状态码
   */
  public static int getHttpStatus(Throwable ex) {
    ExcelExceptionCode code = getExceptionCode(ex);
    return code != null ? code.getHttpStatus() : DEFAULT_HTTP_STATUS;
  }

  /**
   * 获取 Excel 异常码枚举（业务方可据此 switch 做精细处理）。
   *
   * @param ex 异常
   * @return ExcelExceptionCode；非 Excel 异常时为 null
   */
  public static ExcelExceptionCode getExceptionCode(Throwable ex) {
    if (ex instanceof ExcelException excelEx) {
      return excelEx.getExceptionCode();
    }
    return null;
  }

  /**
   * 判断异常是否属于 Excel 模块。
   *
   * @param ex 异常
   * @return true 表示是 Excel 模块的异常
   */
  public static boolean isExcelException(Throwable ex) {
    return ex instanceof ExcelException
        || ex instanceof ExcelReadException
        || ex instanceof ExcelWriteException;
  }
}
