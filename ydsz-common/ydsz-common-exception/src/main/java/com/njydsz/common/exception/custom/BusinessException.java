package com.njydsz.common.exception.custom;

import java.util.Map;

import lombok.ToString;
import org.springframework.http.HttpStatus;

import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.core.ExceptionContext;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 业务异常类
 *
 * <p>用于封装业务逻辑中的异常情况，支持国际化消息处理、异常分类、级别定义等功能。 异常包含错误码、消息键、参数、HTTP状态码等完整上下文信息。
 *
 * <p><b>默认值：</b>
 *
 * <ul>
 *   <li>HTTP 状态码：400 Bad Request
 *   <li>异常级别：ERROR
 *   <li>异常分类：BUSINESS
 * </ul>
 *
 * <p><b>推荐使用方式（1.0.0）：</b>
 *
 * <pre>{@code
 * // 1. 预定义异常码（推荐）
 * throw BusinessException.of(CoreExceptionCode.NOT_FOUND);
 *
 * // 2. 预定义异常码 + 原始异常
 * throw new BusinessException(CoreExceptionCode.DATABASE_ERROR, cause);
 *
 * // 3. 全参数链式构建（自定义错误码场景）
 * throw BusinessException.builder()
 *     .code("USER_NOT_FOUND")
 *     .key("user.not.found")
 *     .httpStatus(404)
 *     .build();
 *
 * // 4. 链式附加数据
 * throw BusinessException.of(CoreExceptionCode.PARAM_ERROR)
 *     .data("field", "username");
 * }</pre>
 *
 * <p><b>精简设计（1.0.0）：</b>仅保留 3 个核心构造函数， 其他参数化构造通过 {@link #builder()} 链式 Builder 完成，消除 15+ 构造函数爆炸问题。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 简单抛出
 * throw BusinessException.of(CoreExceptionCode.NOT_FOUND);
 *
 * // 带原始异常
 * throw new BusinessException(CoreExceptionCode.DATABASE_ERROR, cause);
 *
 * // 完整参数链式构建
 * throw BusinessException.builder()
 *     .code("USER_NOT_FOUND")
 *     .key("user.not.found")
 *     .httpStatus(404)
 *     .build();
 *
 * // 携带业务数据
 * throw BusinessException.of(CoreExceptionCode.PARAM_ERROR)
 *     .data("field", "username");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CoreExceptionCode
 * @see ExceptionCategory#BUSINESS
 */
@ToString(callSuper = true)
public class BusinessException extends AbstractYdszException {

  private static final long serialVersionUID = 1L;

  /**
   * 默认 HTTP 状态码
   *
   * @return 处理结果
   */
  private static final int DEFAULT_HTTP_STATUS = HttpStatus.BAD_REQUEST.value();

  /** 默认异常级别 */
  private static final ExceptionLevel DEFAULT_LEVEL = ExceptionLevel.ERROR;

  /** 默认异常分类 */
  private static final ExceptionCategory DEFAULT_CATEGORY = ExceptionCategory.BUSINESS;

  /**
   * 默认错误码
   *
   * @return 处理结果
   */
  private static final String DEFAULT_CODE = CoreExceptionCode.FAIL.getCode();

  // ==================== 核心构造函数 ====================

  /** 默认构造函数，初始化为 400 Bad Request / ERROR / BUSINESS */
  public BusinessException() {
    super();
    initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
  }

  /**
   * 使用异常码枚举构造业务异常
   *
   * @param exceptionCode 异常码枚举
   */
  public BusinessException(ExceptionCode exceptionCode) {
    super();
    init(exceptionCode, new Object[] {}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
  }

  /**
   * 使用异常码枚举和原始异常构造业务异常
   *
   * @param exceptionCode 异常码枚举
   * @param cause 原始异常
   */
  public BusinessException(ExceptionCode exceptionCode, Throwable cause) {
    super(null, cause);
    init(exceptionCode, new Object[] {}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
  }

  // ==================== 业务方法 ====================

  /**
   * 添加附加数据（链式调用）。
   *
   * <p>写入 {@link com.njydsz.common.exception.core.ExceptionContext} 的 extData， 与 Builder 的 {@code
   * extData(...)} 共用同一存储，避免双轨存储导致的数据覆盖与语义混乱。
   *
   * @param key 数据键
   * @param value 数据值
   * @return 当前异常对象
   */
  public BusinessException data(String key, Object value) {
    if (this.ctx == null) {
      this.ctx = new ExceptionContext();
    }
    this.ctx.addData(key, value);
    return this;
  }

  /**
   * 转换为可序列化的异常响应体，供全局异常处理器写回 HTTP 响应。
   *
   * <p>会触发国际化消息的懒加载解析，应在请求线程内调用以保证取到正确的 Locale。 注意：通过 {@link #data(String, Object)}
   * 附加的业务数据不会进入返回对象， 需要透出时由调用方自行从 {@code getExtData()} 读取并填充 {@code details}。
   *
   * @return 新建的异常信息对象，永不为 {@code null}
   */
  public ExceptionInfo toExceptionInfo() {
    return buildExceptionInfo();
  }

  /**
   * 获取业务异常构建器
   *
   * @return BusinessExceptionBuilder 实例
   */
  public static BusinessExceptionBuilder builder() {
    return new BusinessExceptionBuilder();
  }

  /**
   * 从预定义的异常码创建业务异常。
   *
   * @param exceptionCode 异常码（含 code、key、默认消息、HTTP 状态）
   * @return 携带该异常码默认配置的 BusinessException 实例
   */
  public static BusinessException of(ExceptionCode exceptionCode) {
    return new BusinessException(exceptionCode);
  }

  // ==================== Builder ====================

  /** 业务异常构建器，预置默认的错误码、HTTP状态码、级别和分类 */
  public static class BusinessExceptionBuilder extends YdszExceptionBuilder<BusinessException> {

    public BusinessExceptionBuilder() {
      this.code = DEFAULT_CODE;
      this.httpStatus = DEFAULT_HTTP_STATUS;
      this.level = DEFAULT_LEVEL;
      this.category = DEFAULT_CATEGORY;
    }

    /**
     * 便捷方法：设置 {@link ExceptionCode} 作为错误码（推荐）。
     *
     * <p>自动提取 code / key / httpStatus，完整保留 i18n 语义。
     *
     * @param exceptionCode 异常码枚举
     * @return 处理结果
     */
    public BusinessExceptionBuilder resultCode(ExceptionCode exceptionCode) {
      if (exceptionCode != null) {
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.httpStatus = exceptionCode.getHttpStatus();
      }
      return this;
    }

    /**
     * 兼容路径：从 {@link ResultCode} 提取错误码。
     *
     * <p>ResultCode 不包含 HTTP 状态码，使用默认值 {@code 400}。 如需精确 HTTP 语义，请使用 {@link
     * #resultCode(ExceptionCode)} 传入异常码。
     *
     * @param resultCode 统一结果码
     * @return 处理结果
     */
    public BusinessExceptionBuilder resultCode(ResultCode resultCode) {
      if (resultCode != null) {
        this.code = resultCode.getCode();
        this.key = resultCode.getKey();
        this.httpStatus = DEFAULT_HTTP_STATUS;
      }
      return this;
    }

    /**
     * 便捷方法：设置 {@link YdszResultCode} 作为错误码（兼容路径）。
     *
     * <p>YdszResultCode 不包含 HTTP 状态码，SUCCESS 使用 200，其他使用默认值。
     *
     * @param resultCode 统一结果码
     * @return 处理结果
     */
    public BusinessExceptionBuilder resultCode(YdszResultCode resultCode) {
      if (resultCode != null) {
        this.code = resultCode.getCode();
        this.key = resultCode.getKey();
        this.httpStatus = (resultCode == YdszResultCode.SUCCESS) ? 200 : DEFAULT_HTTP_STATUS;
      }
      return this;
    }

    /**
     * 便捷方法：设置国际化消息参数（变长参数版）
     *
     * <p>覆盖基类的 {@code params(Object[])} 以提供变长参数调用方式。
     *
     * @param params 消息参数
     * @return 处理结果
     */
    @Override
    public BusinessExceptionBuilder params(Object... params) {
      this.params = params;
      return this;
    }

    /**
     * 覆盖业务错误码（保持链式返回子类类型，便于后续继续调用变长 {@link #params(Object...)}）。
     *
     * @param code 错误码
     * @return 处理结果
     */
    @Override
    public BusinessExceptionBuilder code(String code) {
      this.code = code;
      return this;
    }

    /**
     * 设置国际化消息键（保持链式返回子类类型）。
     *
     * @param key 消息键
     * @return 处理结果
     */
    @Override
    public BusinessExceptionBuilder key(String key) {
      this.key = key;
      return this;
    }

    @Override
    protected BusinessException doBuild(
        String code,
        String key,
        Object[] params,
        int httpStatus,
        ExceptionLevel level,
        ExceptionCategory category,
        Throwable cause,
        Map<String, Object> extData,
        String message) {
      BusinessException exception = new BusinessException();
      exception.initFields(code, key, params);
      exception.setHttpStatus(httpStatus);
      exception.setLevel(level);
      exception.setCategory(category);
      exception.setExtData(extData);
      if (cause != null) {
        exception.initCause(cause);
      }
      if (message != null) {
        exception.setMessage(message);
      }
      return exception;
    }
  }
}
